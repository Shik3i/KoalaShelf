// Audio engine: one <audio> element, multi-track books mapped to one global timeline,
// periodic progress sync, sleep timer, Media Session integration.
import type {
	AudioTrack,
	Bookmark,
	Chapter,
	LibraryItem,
	MediaProgress,
	PlaybackSession
} from '@koalashelf/abs-client';
import { AbsError } from '@koalashelf/abs-client';
import { session } from '../session.svelte.ts';
import { progress } from '../progress.svelte.ts';
import { view } from '../items.ts';
import { bookRate, prefs, setBookRate, smartRewindSeconds } from '../prefs.svelte.ts';
import { downloads, type LocalSession } from '../downloads.svelte.ts';

const SYNC_INTERVAL_MS = 15_000;
const RATE_KEY = 'ks.rate';
const LAST_KEY = 'ks.lastItem';
const DEVICE_KEY = 'ks.deviceId';
const FADE_SECONDS = 10;

export const RATES = [0.8, 1, 1.2, 1.5, 2] as const;

export type SleepTimer =
	{ kind: 'off' } | { kind: 'minutes'; endsAt: number; minutes: number } | { kind: 'chapter' };

function storage<T>(key: string, fallback: T): T {
	try {
		const v = localStorage.getItem(key);
		return v === null ? fallback : (JSON.parse(v) as T);
	} catch {
		return fallback;
	}
}

function store(key: string, value: unknown) {
	try {
		localStorage.setItem(key, JSON.stringify(value));
	} catch {
		/* ignore */
	}
}

/** YYYY-MM-DD in local time (the server groups listening stats by day). */
function localDate(ms: number) {
	const d = new Date(ms);
	return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function deviceId() {
	let id = storage<string | null>(DEVICE_KEY, null);
	if (!id) {
		id = crypto.randomUUID();
		store(DEVICE_KEY, id);
	}
	return id;
}

/** MIME types this browser can play directly; everything else the server would transcode. */
function supportedMimeTypes() {
	const probe = new Audio();
	return [
		'audio/mpeg',
		'audio/mp4',
		'audio/aac',
		'audio/flac',
		'audio/ogg',
		'audio/opus',
		'audio/webm',
		'audio/wav',
		'audio/x-m4a',
		'audio/x-m4b'
	].filter((t) => probe.canPlayType(t) !== '');
}

class Player {
	item = $state.raw<LibraryItem | null>(null);
	chapters = $state.raw<Chapter[]>([]);
	bookmarks = $state.raw<Bookmark[]>([]);
	currentTime = $state(0);
	duration = $state(0);
	playing = $state(false);
	buffering = $state(false);
	/** Item whose playback was requested but has not started yet (shows a loading state). */
	loadingId = $state<string | null>(null);
	rate = $state<number>(storage(RATE_KEY, 1));
	sleep = $state<SleepTimer>({ kind: 'off' });
	/** 'retrying' while recovering (e.g. token refresh), 'fatal' once giving up. */
	error = $state<'retrying' | 'fatal' | null>(null);
	/** Full-screen player visible. */
	expanded = $state(false);

	#audio: HTMLAudioElement | null = null;
	#hls: { destroy(): void } | null = null;
	/** Downloaded files by track index (played instead of streaming). */
	#localUrls: Map<number, string> | null = null;
	/** Offline playback: progress goes to the local queue instead of the server. */
	#local: LocalSession | null = null;
	#pausedAt = 0;
	/** Increments per play request; stale requests (user picked another book meanwhile) bail out. */
	#request = 0;
	/** Sleep timer time left while paused (the countdown only runs while playing). */
	sleepPausedLeft = $state<number | null>(null);
	#session: PlaybackSession | null = null;
	#tracks: AudioTrack[] = [];
	#track = 0;
	#listened = 0;
	#lastTick = 0;
	#syncTimer: ReturnType<typeof setInterval> | null = null;
	#retries = 0;
	#pendingSeek: number | null = null;

	readonly chapter = $derived(
		this.chapters.find((c) => this.currentTime >= c.start && this.currentTime < c.end) ??
			this.chapters.at(-1) ??
			null
	);
	readonly chapterIndex = $derived(this.chapter ? this.chapters.indexOf(this.chapter) : -1);
	readonly progress = $derived(this.duration ? this.currentTime / this.duration : 0);

	constructor() {
		if (typeof window === 'undefined') return;
		addEventListener('pagehide', () => this.#sync(true));
		session.onSignOut(() => void this.stop().catch(() => undefined));
		document.addEventListener('visibilitychange', () => document.hidden && this.#sync(true));
	}

	/** Restores the last item (paused) so the mini player is there after a reload. */
	async restoreLast() {
		const id = storage<string | null>(LAST_KEY, null);
		if (!id || this.item) return;
		try {
			const item = await session.api.item(id);
			if (this.item) return;
			this.item = item;
			this.chapters = item.media.chapters;
			this.duration = item.media.duration;
			this.currentTime = item.userMediaProgress?.currentTime ?? 0;
		} catch {
			store(LAST_KEY, null);
		}
	}

	get audio() {
		if (!this.#audio) {
			const a = new Audio();
			a.preload = 'auto';
			a.addEventListener('timeupdate', () => this.#onTime());
			a.addEventListener('playing', () => {
				this.playing = true;
				this.buffering = false;
				this.loadingId = null;
				this.#retries = 0;
				this.error = null;
				this.#lastTick = performance.now();
				if (this.sleep.kind === 'minutes' && this.sleepPausedLeft !== null) {
					this.sleep = { ...this.sleep, endsAt: Date.now() + this.sleepPausedLeft };
				}
				this.sleepPausedLeft = null;
				this.#positionState();
			});
			a.addEventListener('pause', () => {
				this.playing = false;
				this.loadingId = null;
				this.#lastTick = 0;
				this.#pausedAt = Date.now();
				if (this.sleep.kind === 'minutes') this.sleepPausedLeft = this.sleep.endsAt - Date.now();
				this.#sync();
			});
			a.addEventListener('waiting', () => (this.buffering = true));
			a.addEventListener('canplay', () => (this.buffering = false));
			a.addEventListener('ended', () => this.#onEnded());
			a.addEventListener('error', () => this.#onError());
			a.addEventListener('loadedmetadata', () => {
				if (this.#pendingSeek !== null) {
					a.currentTime = this.#pendingSeek;
					this.#pendingSeek = null;
				}
				a.playbackRate = this.rate;
			});
			this.#audio = a;
		}
		return this.#audio;
	}

	/** Starts (or resumes) playback of an item. */
	async play(itemId?: string, startAt?: number) {
		const id = itemId ?? this.item?.id;
		if (!id) return;
		const req = ++this.#request;
		this.loadingId = id;
		this.error = null;
		if (this.#session && this.#session.libraryItemId === id) {
			if (startAt !== undefined) this.seek(startAt);
			else if (prefs.smartRewind && this.#pausedAt) {
				const back = smartRewindSeconds(Date.now() - this.#pausedAt);
				if (back) this.skip(-back);
			}
			this.#pausedAt = 0;
			await this.#start();
			return;
		}
		// Position chosen in the restored mini player (before any session existed) wins.
		const preset =
			startAt ?? (this.item?.id === id && !this.#session ? this.currentTime : undefined);
		await this.#close();
		if (req !== this.#request) return;
		this.buffering = true;
		await downloads.init();
		this.#localUrls = downloads.has(id) ? await downloads.trackUrls(id) : null;
		let s: PlaybackSession;
		try {
			s = await session.api.play(
				id,
				{
					deviceId: deviceId(),
					clientName: 'KoalaShelf Web',
					clientVersion: '0.1.0',
					model: navigator.userAgent.slice(0, 64)
				},
				supportedMimeTypes()
			);
		} catch {
			if (req !== this.#request) return;
			// No server, but the book is downloaded: play offline.
			if (this.#localUrls) return this.#playOffline(id, preset);
			this.error = 'fatal';
			this.buffering = false;
			this.loadingId = null;
			return;
		}
		if (req !== this.#request) {
			// Another book was requested meanwhile: discard this session.
			void session.api.closeSession(s.id).catch(() => undefined);
			return;
		}
		this.#session = s;
		this.#tracks = [...s.audioTracks].sort((a, b) => a.startOffset - b.startOffset);
		this.item = s.libraryItem;
		this.chapters = s.chapters?.length ? s.chapters : s.libraryItem.media.chapters;
		this.duration = s.duration;
		let start = preset ?? s.currentTime ?? 0;
		// A finished book starts over instead of ending immediately.
		if (start >= s.duration - 2) start = 0;
		this.currentTime = start;
		this.#listened = 0;
		store(LAST_KEY, id);
		this.#applyBookRate(id);
		this.#updateMediaSession();
		this.#loadBookmarks();
		this.#loadTrackAt(this.currentTime);
		this.#syncTimer = setInterval(() => this.#sync(), SYNC_INTERVAL_MS);
		await this.#start();
	}

	/** Offline session from a downloaded book; synced via /api/session/local later. */
	async #playOffline(id: string, preset: number | undefined) {
		const book = downloads.books.get(id)!;
		const item = book.item;
		this.#tracks = book.tracks.map((t) => ({
			index: t.index,
			startOffset: t.startOffset,
			duration: t.duration,
			title: '',
			contentUrl: '',
			mimeType: t.mimeType
		}));
		this.item = item;
		this.chapters = item.media.chapters;
		this.duration = item.media.duration;
		const known = progress.get(id)?.currentTime ?? item.userMediaProgress?.currentTime ?? 0;
		let start = preset ?? known;
		if (start >= this.duration - 2) start = 0;
		this.currentTime = start;
		this.#listened = 0;
		const now = Date.now();
		const v = view(item);
		this.#local = {
			id: crypto.randomUUID(),
			libraryItemId: id,
			libraryId: item.libraryId,
			mediaType: 'book',
			displayTitle: v.title,
			displayAuthor: v.author,
			duration: this.duration,
			playMethod: 3,
			mediaPlayer: 'html5',
			deviceInfo: { deviceId: deviceId(), clientName: 'KoalaShelf Web', clientVersion: '0.1.0' },
			startTime: start,
			currentTime: start,
			timeListening: 0,
			startedAt: now,
			updatedAt: now,
			date: localDate(now),
			dayOfWeek: new Date(now).toLocaleDateString('en-US', { weekday: 'long' })
		};
		store(LAST_KEY, id);
		this.#updateMediaSession();
		this.#loadTrackAt(this.currentTime);
		this.#syncTimer = setInterval(() => this.#sync(), SYNC_INTERVAL_MS);
		await this.#start();
	}

	#applyBookRate(id: string) {
		const r = bookRate(id) ?? storage<number>(RATE_KEY, 1);
		this.rate = r;
		if (this.#audio) this.#audio.playbackRate = r;
	}

	/** audio.play() can be refused (autoplay policy); never leave a spinner behind. */
	async #start() {
		try {
			await this.audio.play();
		} catch {
			this.loadingId = null;
			this.buffering = false;
		}
	}

	pause() {
		this.#audio?.pause();
	}

	/** Id of our own playback session (to tell our syncs apart from other devices). */
	get sessionId() {
		return this.#session?.id ?? this.#local?.id ?? null;
	}

	/** Another device moved on: follow it unless we are playing here right now. */
	applyRemoteProgress(p: MediaProgress) {
		if (!this.item || this.item.id !== p.libraryItemId || this.playing) return;
		if (Math.abs(p.currentTime - this.currentTime) < 2) return;
		if (this.#session) this.seek(p.currentTime);
		else this.currentTime = p.currentTime;
	}

	toggle() {
		if (this.playing) this.pause();
		else void this.play();
	}

	seek(time: number) {
		const t = Math.min(Math.max(0, time), Math.max(0, this.duration - 0.5));
		this.currentTime = t;
		if (!this.#session) return;
		const idx = this.#trackIndexAt(t);
		if (idx !== this.#track) this.#loadTrackAt(t, !this.audio.paused);
		else this.audio.currentTime = t - this.#tracks[idx].startOffset;
		this.#positionState();
	}

	skip(seconds: number) {
		this.seek(this.currentTime + seconds);
	}

	back() {
		this.skip(-prefs.skipBack);
	}

	forward() {
		this.skip(prefs.skipForward);
	}

	prevChapter() {
		const c = this.chapter;
		if (!c) return this.seek(0);
		const i = this.chapters.indexOf(c);
		// Like a CD player: first press goes to the chapter start.
		if (this.currentTime - c.start > 3 || i === 0) this.seek(c.start);
		else this.seek(this.chapters[i - 1].start);
	}

	nextChapter() {
		const i = this.chapterIndex;
		if (i >= 0 && i < this.chapters.length - 1) this.seek(this.chapters[i + 1].start);
	}

	setRate(r: number) {
		this.rate = r;
		if (this.item) setBookRate(this.item.id, r);
		if (this.#audio) this.#audio.playbackRate = r;
		store(RATE_KEY, r);
		this.#positionState();
	}

	setSleep(t: SleepTimer) {
		this.sleep = t;
		this.sleepPausedLeft = t.kind === 'minutes' && !this.playing ? t.endsAt - Date.now() : null;
		if (this.#audio) this.#audio.volume = 1;
	}

	// --- Bookmarks ------------------------------------------------------------

	async #loadBookmarks() {
		if (!this.item) return;
		try {
			this.bookmarks = (await session.api.bookmarks(this.item.id)).sort((a, b) => a.time - b.time);
		} catch {
			this.bookmarks = [];
		}
	}

	async addBookmark(title: string, time = this.currentTime) {
		if (!this.item) return;
		const t = Math.floor(time);
		const b = await session.api.createBookmark(this.item.id, t, title);
		this.bookmarks = [...this.bookmarks, b].sort((a, c) => a.time - c.time);
	}

	async renameBookmark(time: number, title: string) {
		if (!this.item) return;
		await session.api.updateBookmark(this.item.id, time, title);
		this.bookmarks = this.bookmarks.map((b) => (b.time === time ? { ...b, title } : b));
	}

	async deleteBookmark(time: number) {
		if (!this.item) return;
		await session.api.deleteBookmark(this.item.id, time);
		this.bookmarks = this.bookmarks.filter((b) => b.time !== time);
	}

	// --- Internals ------------------------------------------------------------

	#trackIndexAt(t: number) {
		let idx = 0;
		for (let i = 0; i < this.#tracks.length; i++) if (t >= this.#tracks[i].startOffset) idx = i;
		return idx;
	}

	#loadTrackAt(t: number, autoplay = false) {
		const idx = this.#trackIndexAt(t);
		const track = this.#tracks[idx];
		if (!track) return;
		this.#track = idx;
		this.#pendingSeek = t - track.startOffset;
		const local = this.#localUrls?.get(track.index);
		if (local) {
			this.#hls?.destroy();
			this.#hls = null;
			this.audio.src = local;
			if (autoplay) void this.audio.play().catch(() => undefined);
			return;
		}
		const url = session.api.trackUrl(track.contentUrl);
		// The server transcodes formats the browser cannot play into HLS.
		const isHls = /\.m3u8(\?|$)/.test(track.contentUrl);
		if (isHls && !this.audio.canPlayType('application/vnd.apple.mpegurl')) {
			void this.#attachHls(url, autoplay);
			return;
		}
		this.#hls?.destroy();
		this.#hls = null;
		this.audio.src = url;
		if (autoplay) void this.audio.play().catch(() => undefined);
	}

	/** hls.js is only downloaded when a transcoded stream actually needs it. */
	async #attachHls(url: string, autoplay: boolean) {
		const { default: Hls } = await import('hls.js');
		if (!Hls.isSupported()) {
			this.error = 'fatal';
			return;
		}
		this.#hls?.destroy();
		const hls = new Hls({
			xhrSetup: (xhr) => xhr.setRequestHeader('Authorization', `Bearer ${session.api.accessToken}`)
		});
		hls.on(Hls.Events.ERROR, (_e, data) => data.fatal && void this.#onError());
		hls.loadSource(url);
		hls.attachMedia(this.audio);
		this.#hls = hls;
		if (autoplay) void this.audio.play().catch(() => undefined);
	}

	#onTime() {
		const a = this.#audio;
		const track = this.#tracks[this.#track];
		if (!a || !track || this.#pendingSeek !== null) return;
		this.currentTime = track.startOffset + a.currentTime;

		// Count real listening time (wall clock while playing), robust against seeks.
		const now = performance.now();
		if (this.playing && this.#lastTick)
			this.#listened += Math.min(2, (now - this.#lastTick) / 1000);
		this.#lastTick = now;

		this.#checkSleep();
		if (this.chapter?.id !== this.#metaChapter) this.#updateMediaSession();
	}

	#checkSleep() {
		const a = this.#audio;
		const s = this.sleep;
		if (!a || s.kind === 'off') return;
		if (s.kind === 'chapter') {
			if (this.chapter && this.currentTime >= this.chapter.end - 0.4) {
				this.pause();
				this.sleep = { kind: 'off' };
			}
			return;
		}
		const left = (s.endsAt - Date.now()) / 1000;
		if (left <= 0) {
			this.pause();
			a.volume = 1;
			// Rewind a little so the listener does not miss what they fell asleep to.
			this.skip(-15);
			this.sleep = { kind: 'off' };
		} else if (left < FADE_SECONDS) {
			a.volume = Math.max(0.05, left / FADE_SECONDS);
		}
	}

	#onEnded() {
		if (this.#track < this.#tracks.length - 1) {
			this.#loadTrackAt(this.#tracks[this.#track + 1].startOffset, true);
			return;
		}
		this.currentTime = this.duration;
		this.playing = false;
		void this.#sync();
		if (this.item) {
			const id = this.item.id;
			session.api
				.updateProgress(id, { isFinished: true, currentTime: this.duration, progress: 1 })
				.catch(() => undefined);
			const p = progress.get(id);
			if (p) progress.set({ ...p, isFinished: true, progress: 1 });
		}
	}

	/** Network errors on the stream are usually an expired token: refresh and resume in place. */
	async #onError() {
		if (!this.#session || this.#retries >= 3) {
			this.error = 'fatal';
			this.playing = false;
			this.loadingId = null;
			return;
		}
		this.#retries++;
		this.error = 'retrying';
		const at = this.currentTime;
		await session.api.refresh();
		this.#loadTrackAt(at, true);
	}

	async #sync(final = false) {
		if (this.#local) return this.#syncLocal();
		const s = this.#session;
		if (!s) return;
		const listened = Math.round(this.#listened);
		const body = { currentTime: this.currentTime, timeListened: listened, duration: this.duration };
		try {
			await session.api.syncSession(s.id, body, final);
			this.#listened -= listened;
			this.#updateLocalProgress();
		} catch (e) {
			// Server forgot the session (e.g. restart): open a fresh one at the same spot.
			if (e instanceof AbsError && e.status === 404 && !final && this.item) {
				const wasPlaying = this.playing;
				this.#session = null;
				this.#clearTimer();
				await this.play(this.item.id, this.currentTime);
				if (!wasPlaying) this.pause();
			}
		}
	}

	#updateLocalProgress() {
		if (!this.item) return;
		const prev = progress.get(this.item.id);
		progress.set({
			id: prev?.id ?? this.item.id,
			libraryItemId: this.item.id,
			episodeId: null,
			duration: this.duration,
			progress: this.progress,
			currentTime: this.currentTime,
			isFinished: prev?.isFinished ?? false,
			hideFromContinueListening: false,
			lastUpdate: Date.now(),
			startedAt: prev?.startedAt ?? Date.now(),
			finishedAt: prev?.finishedAt ?? null
		});
	}

	#clearTimer() {
		if (this.#syncTimer) clearInterval(this.#syncTimer);
		this.#syncTimer = null;
	}

	#syncLocal() {
		const l = this.#local;
		if (!l) return;
		const now = Date.now();
		l.currentTime = this.currentTime;
		l.timeListening += Math.round(this.#listened);
		this.#listened = 0;
		l.updatedAt = now;
		l.date = localDate(now);
		downloads.saveLocal({ ...l });
		this.#updateLocalProgress();
		if (navigator.onLine) void downloads.flush();
	}

	async #close() {
		this.#clearTimer();
		if (this.#local) {
			this.#syncLocal();
			this.#local = null;
			this.#audio?.pause();
			return;
		}
		const s = this.#session;
		if (!s) return;
		this.#session = null;
		this.#audio?.pause();
		await session.api
			.closeSession(s.id, {
				currentTime: this.currentTime,
				timeListened: Math.round(this.#listened),
				duration: this.duration
			})
			.catch(() => undefined);
		this.#listened = 0;
	}

	/** Called on sign-out. */
	async stop() {
		await this.#close();
		this.item = null;
		this.expanded = false;
		store(LAST_KEY, null);
	}

	// --- Media Session (lock screen, headset & media keys) ----------------------

	#metaChapter: number | undefined = undefined;

	#updateMediaSession() {
		if (!('mediaSession' in navigator) || !this.item) return;
		const v = view(this.item);
		this.#metaChapter = this.chapter?.id;
		navigator.mediaSession.metadata = new MediaMetadata({
			title: v.title,
			artist: v.author,
			// Lock screens show the album line: the current chapter is the most useful there.
			album: this.chapter?.title || v.series || v.title,
			artwork: v.cover
				? [96, 256, 512].map((w) => ({ src: v.cover!(w), sizes: `${w}x${w}`, type: 'image/webp' }))
				: []
		});
		const ms = navigator.mediaSession;
		const handlers: [MediaSessionAction, MediaSessionActionHandler][] = [
			['play', () => void this.play()],
			['pause', () => this.pause()],
			['seekbackward', (d) => this.skip(-(d.seekOffset ?? prefs.skipBack))],
			['seekforward', (d) => this.skip(d.seekOffset ?? prefs.skipForward)],
			['previoustrack', () => this.prevChapter()],
			['nexttrack', () => this.nextChapter()],
			['seekto', (d) => d.seekTime !== undefined && this.seek(d.seekTime)],
			['stop', () => this.pause()]
		];
		for (const [action, handler] of handlers) {
			try {
				ms.setActionHandler(action, handler);
			} catch {
				/* action unsupported */
			}
		}
	}

	#positionState() {
		if (!('mediaSession' in navigator) || !this.duration) return;
		try {
			navigator.mediaSession.setPositionState({
				duration: this.duration,
				position: Math.min(this.currentTime, this.duration),
				playbackRate: this.rate
			});
		} catch {
			/* ignore */
		}
	}
}

export const player = new Player();
