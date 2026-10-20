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
import { AbsError, encodeFilter } from '@koalashelf/abs-client';
import { toasts } from '../toast.svelte.ts';
import { i18n } from '../i18n/index.svelte.ts';
import { scoped, session } from '../session.svelte.ts';
import { progress } from '../progress.svelte.ts';
import { view } from '../items.ts';
import {
	bookRate,
	inNightWindow,
	prefs,
	setBookRate,
	smartRewindSeconds,
	type Prefs
} from '../prefs.svelte.ts';
import { clock } from '../format.ts';
import { downloads, type LocalSession } from '../downloads.svelte.ts';
import { queue } from '../queue.svelte.ts';
import { isTauri } from '../platform.ts';
import { needsGraph, SoundChain, type SoundSettings } from './sound.ts';
import { forecast, type SleepForecast, type SleepTimer } from './sleep.ts';
import { haptic } from '../haptics.ts';
import { clearBadge, setBadge } from '../badge.ts';

export type { SleepTimer } from './sleep.ts';

const SYNC_INTERVAL_MS = 15_000;
const RATE_KEY = 'ks.rate';
const LAST_KEY = 'ks.lastItem';
const DEVICE_KEY = 'ks.deviceId';
const SILENCE_SAVED_KEY = 'ks.silenceSaved';
/** Jumps at least this long (seconds) can be undone. */
const JUMP_UNDO_MIN = 30;

/** Quick choices; any speed between MIN_RATE and MAX_RATE works. */
export const RATES = [0.8, 1, 1.2, 1.5, 2] as const;
export const MIN_RATE = 0.5;
export const MAX_RATE = 3.5;
export const RATE_STEP = 0.05;

/** In range and on the 0.05 grid (no 1.2500000001 from repeated steps). */
export function clampRate(r: number) {
	return Math.round(Math.min(MAX_RATE, Math.max(MIN_RATE, r)) * 100) / 100;
}

/** A stored or imported speed, checked: the audio element throws on anything but a valid number. */
export function safeRate(v: unknown) {
	return typeof v === 'number' && Number.isFinite(v) ? clampRate(v) : 1;
}

/** Some browsers (iOS) ignore the element volume: the hardware buttons rule there. */
export const volumeAvailable = (() => {
	if (typeof Audio === 'undefined') return false;
	const a = new Audio();
	a.volume = 0.5;
	return a.volume === 0.5;
})();

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
	rate = $state<number>(safeRate(storage(RATE_KEY, 1)));
	sleep = $state<SleepTimer>({ kind: 'off' });
	/** 'retrying' while recovering (e.g. token refresh), 'fatal' once giving up. */
	error = $state<'retrying' | 'fatal' | null>(null);
	/** Full-screen player visible. */
	expanded = $state(false);
	/** Position before the last long jump (chapter, bookmark, timeline), for "jump back". */
	lastJump = $state<number | null>(null);
	/** Speeding through a silent stretch right now. */
	inSilence = $state(false);
	/** Listening time saved by skipping silence: in this book, and ever. */
	silenceSavedBook = $state(0);
	silenceSavedTotal = $state<number>(storage(SILENCE_SAVED_KEY, 0));
	/** Cast / AirPlay: a receiver is around, and whether we are playing on it. */
	castAvailable = $state(false);
	castState = $state<'disconnected' | 'connecting' | 'connected'>('disconnected');

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
	/** Seconds left on a minutes sleep timer (updated while playing). */
	sleepRemaining = $state(0);
	/** When playback paused with a running timer (timers only count while playing). */
	#sleepPausedAt = $state<number | null>(null);
	#sound = new SoundChain();
	#silenceSince = 0;
	/** The "stopping soon" hint was shown for the running timer. */
	#sleepWarned = false;
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

	/** What timeline, clock and lock screen cover: the current chapter or the whole book. */
	readonly scope = $derived.by(() => {
		const c = this.chapter;
		return prefs.progressScope === 'chapter' && c && c.end > c.start
			? { start: c.start, end: c.end, chapter: true }
			: { start: 0, end: this.duration, chapter: false };
	});
	readonly scopeProgress = $derived(
		this.scope.end > this.scope.start
			? Math.min(
					1,
					Math.max(0, (this.currentTime - this.scope.start) / (this.scope.end - this.scope.start))
				)
			: 0
	);

	/** "Time left" displays follow the playback speed unless the listener turned that off. */
	get displayRate() {
		return prefs.speedAdjusted ? this.rate : 1;
	}

	/** Where a smart sleep timer will stop (follows seeks, speed and pauses). */
	readonly sleepForecast = $derived.by((): SleepForecast | null => {
		const s = this.sleep;
		if (s.kind !== 'window') return null;
		const base = this.#sleepPausedAt ?? Date.now();
		return forecast(
			this.chapters,
			this.currentTime,
			this.rate,
			(s.minAt - base) / 1000,
			(s.maxAt - base) / 1000
		);
	});

	constructor() {
		if (typeof window === 'undefined') return;
		this.#sound.onSilence = (silent) => this.#onSilence(silent);
		addEventListener('pagehide', () => this.#sync(true));
		// Leaving an account (switch or sign-out): close its session, keep its last book.
		session.onSignOut(() => void this.leave().catch(() => undefined));
		document.addEventListener('visibilitychange', () => document.hidden && this.#sync(true));
	}

	/** Restores the last item (paused) so the mini player is there after a reload. */
	async restoreLast() {
		const id = storage<string | null>(scoped(LAST_KEY), null);
		if (!id || this.item) return;
		try {
			const item = await session.api.item(id);
			if (this.item) return;
			if (item.userMediaProgress) progress.merge(item.userMediaProgress);
			// If already finished on another device, don't restore as an active playing item.
			if (item.userMediaProgress?.isFinished) {
				store(scoped(LAST_KEY), null);
				return;
			}
			this.item = item;
			this.chapters = item.media.chapters;
			this.duration = item.media.duration;
			this.currentTime = item.userMediaProgress?.currentTime ?? 0;
		} catch {
			store(scoped(LAST_KEY), null);
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
				if (this.#sleepPausedAt !== null) this.#shiftSleep(Date.now() - this.#sleepPausedAt);
				this.#sleepPausedAt = null;
				this.sleepPausedLeft = null;
				void this.#sound.resume();
				this.#sound.setPlaybackState(true);
				if (prefs.needleDrop) this.#sound.playNeedleDrop();
				this.#positionState();
			});
			a.addEventListener('pause', () => {
				this.#onSilence(false);
				this.playing = false;
				this.#sound.setPlaybackState(false);
				this.loadingId = null;
				this.#lastTick = 0;
				this.#pausedAt = Date.now();
				this.#holdSleep();
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
				this.inSilence = false;
				a.playbackRate = this.rate;
			});
			a.volume = prefs.volume;
			this.#watchCast(a);
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
		// Position chosen in the restored mini player (before any session existed) wins,
		// unless the book was finished or explicitly requested to start elsewhere.
		const isFinished = progress.get(id)?.isFinished ?? false;
		const preset =
			startAt ??
			(isFinished ? 0 : this.item?.id === id && !this.#session ? this.currentTime : undefined);
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
					clientVersion: __APP_VERSION__
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
		queue.remove(id);
		this.#tracks = [...s.audioTracks].sort((a, b) => a.startOffset - b.startOffset);
		this.item = s.libraryItem;
		if (s.libraryItem.userMediaProgress) progress.merge(s.libraryItem.userMediaProgress);
		const finished = isFinished || (s.libraryItem.userMediaProgress?.isFinished ?? false);
		this.chapters = s.chapters?.length ? s.chapters : s.libraryItem.media.chapters;
		this.duration = s.duration;
		let start = finished && startAt === undefined ? 0 : (preset ?? s.currentTime ?? 0);
		// A finished book starts over instead of ending immediately.
		if (start >= s.duration - 2) start = 0;
		this.currentTime = start;
		this.#listened = 0;
		this.silenceSavedBook = 0;
		this.lastJump = null;
		store(scoped(LAST_KEY), id);
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
		queue.remove(id);
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
		const isFinished =
			progress.get(id)?.isFinished || (item.userMediaProgress?.isFinished ?? false);
		const known = isFinished
			? 0
			: (progress.get(id)?.currentTime ?? item.userMediaProgress?.currentTime ?? 0);
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
			deviceInfo: {
				deviceId: deviceId(),
				clientName: 'KoalaShelf Web',
				clientVersion: __APP_VERSION__
			},
			startTime: start,
			currentTime: start,
			timeListening: 0,
			startedAt: now,
			updatedAt: now,
			date: localDate(now),
			dayOfWeek: new Date(now).toLocaleDateString('en-US', { weekday: 'long' })
		};
		store(scoped(LAST_KEY), id);
		this.#updateMediaSession();
		this.#loadTrackAt(this.currentTime);
		this.#syncTimer = setInterval(() => this.#sync(), SYNC_INTERVAL_MS);
		await this.#start();
	}

	#applyBookRate(id: string) {
		const own = prefs.rememberRate ? bookRate(id) : null;
		const wanted =
			own ?? (prefs.defaultRate === 'last' ? storage<number>(RATE_KEY, 1) : prefs.defaultRate);
		const r = safeRate(wanted);
		this.rate = r;
		if (this.#audio) this.#audio.playbackRate = r;
	}

	/** audio.play() can be refused (autoplay policy); never leave a spinner behind. */
	async #start() {
		this.#autoSleep();
		try {
			await this.#sound.resume();
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
		if (p.isFinished) {
			this.pause();
			this.currentTime = this.duration;
			return;
		}
		if (Math.abs(p.currentTime - this.currentTime) < 2) return;
		if (this.#session) this.seek(p.currentTime);
		else this.currentTime = p.currentTime;
	}

	toggle() {
		haptic.tap();
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

	/**
	 * A deliberate jump (chapter, bookmark, timeline, typed time). Long ones can be undone:
	 * a mis-tap on the timeline should never lose the listener's place.
	 */
	jump(time: number) {
		haptic.tap();
		const from = this.currentTime;
		this.seek(time);
		if (Math.abs(this.currentTime - from) < JUMP_UNDO_MIN) return;
		this.lastJump = from;
		toasts.show(i18n.m.player.jumped(clock(from)), {
			label: i18n.m.player.jumpBack,
			run: () => this.undoJump()
		});
	}

	/** Back to where the last long jump started (and the jump target becomes the undo). */
	undoJump() {
		if (this.lastJump === null) return;
		const to = this.lastJump;
		this.lastJump = this.currentTime;
		this.seek(to);
	}

	skip(seconds: number) {
		haptic.tick();
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

	setRate(rate: number) {
		const r = clampRate(rate);
		this.rate = r;
		if (this.item && prefs.rememberRate) setBookRate(this.item.id, r);
		if (this.#audio) this.#audio.playbackRate = this.inSilence ? this.#silenceRate() : r;
		store(RATE_KEY, r);
		this.#positionState();
	}

	/** Timeline and clock for the whole book or the current chapter. */
	setScope(scope: 'book' | 'chapter') {
		prefs.set('progressScope', scope);
		this.#positionState();
	}

	setSleep(t: SleepTimer) {
		this.sleep = t;
		this.#sleepPausedAt = null;
		this.sleepPausedLeft = null;
		this.#sleepWarned = false;
		if (!this.playing) this.#holdSleep();
		if (this.#audio) this.#audio.volume = prefs.volume;
	}

	/** More time on a running timer ("still awake"); a chapter-end timer becomes a countdown. */
	extendSleep(minutes: number) {
		const s = this.sleep;
		const ms = minutes * 60_000;
		if (s.kind === 'off') return;
		this.#sleepWarned = false;
		if (this.#audio) this.#audio.volume = prefs.volume;
		if (s.kind === 'minutes') {
			this.sleep = { ...s, minutes: s.minutes + minutes, endsAt: s.endsAt + ms };
			if (this.sleepPausedLeft !== null) this.sleepPausedLeft += ms;
		} else if (s.kind === 'window') {
			this.sleep = { ...s, minAt: s.minAt + ms, maxAt: s.maxAt + ms };
			if (this.sleepPausedLeft !== null) this.sleepPausedLeft += ms;
		} else {
			const left = Math.max(0, (this.chapter?.end ?? this.currentTime) - this.currentTime);
			const endsAt = Date.now() + (left / this.rate) * 1000 + ms;
			this.setSleep({ kind: 'minutes', minutes, endsAt });
		}
	}

	/** Night time: playback starting without a timer gets the listener's usual one. */
	#autoSleep() {
		if (!prefs.autoSleep || this.sleep.kind !== 'off' || this.playing) return;
		if (!inNightWindow(new Date(), prefs.autoSleepFrom, prefs.autoSleepTo)) return;
		const kind = prefs.autoSleepKind;
		if (kind === 'chapter') this.setSleep({ kind: 'chapter' });
		else if (kind === 'smart') this.setSleepWindow(prefs.sleepMin, prefs.sleepMax);
		else {
			const m = prefs.autoSleepMinutes;
			this.setSleep({ kind: 'minutes', minutes: m, endsAt: Date.now() + m * 60_000 });
		}
		toasts.show(i18n.m.player.autoSleepStarted, {
			label: i18n.m.player.timerStop,
			run: () => this.setSleep({ kind: 'off' })
		});
	}

	/** Starts a smart timer: the first chapter end between `min` and `max` minutes. */
	setSleepWindow(min: number, max: number) {
		const now = Date.now();
		this.setSleep({
			kind: 'window',
			min,
			max,
			minAt: now + min * 60_000,
			maxAt: now + max * 60_000
		});
	}

	/** Freezes a running countdown while paused. */
	#holdSleep() {
		const s = this.sleep;
		const end = s.kind === 'minutes' ? s.endsAt : s.kind === 'window' ? s.maxAt : null;
		if (end === null) return;
		const now = Date.now();
		this.#sleepPausedAt = now;
		this.sleepPausedLeft = end - now;
	}

	#shiftSleep(ms: number) {
		const s = this.sleep;
		if (s.kind === 'minutes') this.sleep = { ...s, endsAt: s.endsAt + ms };
		if (s.kind === 'window') this.sleep = { ...s, minAt: s.minAt + ms, maxAt: s.maxAt + ms };
	}

	// --- Sound: volume, boost, leveling, voice, mono, silence ---------------------

	/** Web Audio needs CORS for remote audio; the desktop shell cannot provide it. */
	get levelingAvailable() {
		return !isTauri && typeof AudioContext !== 'undefined';
	}

	/** Skipping silence also needs an audio worklet (https only). */
	get silenceAvailable() {
		return this.levelingAvailable && SoundChain.silenceAvailable;
	}

	/** For tests and meters. */
	get leveler() {
		return this.#sound;
	}

	get #soundSettings(): SoundSettings {
		return {
			leveling: prefs.leveling,
			boost: prefs.boost,
			profile: prefs.soundProfile,
			voice: prefs.soundProfile === 'voice' || prefs.voiceBoost,
			mono: prefs.mono,
			silence: prefs.skipSilence && this.silenceAvailable,
			crackle: prefs.vinylCrackle,
			crackleVolume: prefs.vinylCrackleVolume
		};
	}

	setLeveling(on: boolean) {
		this.setSound('leveling', on);
	}

	/** Changes one sound setting and applies it to what is playing. */
	setSound<
		K extends
			| 'leveling'
			| 'boost'
			| 'voiceBoost'
			| 'soundProfile'
			| 'mono'
			| 'skipSilence'
			| 'needleDrop'
			| 'vinylCrackle'
			| 'vinylCrackleVolume'
	>(key: K, value: Prefs[K]) {
		prefs.set(key, value);
		if (key === 'soundProfile') {
			prefs.set('voiceBoost', value === 'voice');
		} else if (key === 'voiceBoost') {
			prefs.set('soundProfile', value ? 'voice' : 'off');
		}
		const a = this.#audio;
		// A remote stream loaded without CORS would play silently through Web Audio: reload it.
		const reload =
			needsGraph(this.#soundSettings) &&
			!!a?.currentSrc &&
			a.crossOrigin !== 'anonymous' &&
			new URL(a.currentSrc).origin !== location.origin;
		this.#prepareSound();
		if (reload && (this.#session || this.#local)) this.#loadTrackAt(this.currentTime, this.playing);
	}

	setVolume(v: number) {
		const volume = Math.round(Math.min(1, Math.max(0, v)) * 100) / 100;
		prefs.set('volume', volume);
		if (this.#audio) this.#audio.volume = volume;
	}

	#prepareSound() {
		const settings = this.#soundSettings;
		if (!this.levelingAvailable || (!needsGraph(settings) && !this.#sound.attached)) return;
		const a = this.audio;
		a.crossOrigin = 'anonymous';
		this.#sound.attach(a);
		this.#sound.set(settings);
	}

	/** Silence plays faster (the pitch is kept). */
	#silenceRate() {
		return Math.min(4, Math.max(2.5, this.rate * 2.5));
	}

	#onSilence(silent: boolean) {
		const a = this.#audio;
		if (!a || silent === this.inSilence) return;
		if (silent) {
			if (!this.playing || !prefs.skipSilence) return;
			this.inSilence = true;
			this.#silenceSince = performance.now();
			a.playbackRate = this.#silenceRate();
			return;
		}
		this.inSilence = false;
		const fast = a.playbackRate;
		a.playbackRate = this.rate;
		// Real time saved: the stretch would have taken fast/rate times as long.
		const real = (performance.now() - this.#silenceSince) / 1000;
		const saved = Math.max(0, real * (fast / this.rate - 1));
		if (saved > 0 && saved < 600) {
			this.silenceSavedBook += saved;
			this.silenceSavedTotal += saved;
			store(SILENCE_SAVED_KEY, Math.round(this.silenceSavedTotal));
		}
	}

	// --- Cast / AirPlay -----------------------------------------------------------

	#watchCast(a: HTMLAudioElement) {
		const remote = a.remote;
		if (remote?.watchAvailability) {
			remote
				.watchAvailability((available) => (this.castAvailable = available))
				.catch(() => undefined);
			const update = () => (this.castState = remote.state);
			remote.addEventListener('connecting', update);
			remote.addEventListener('connect', update);
			remote.addEventListener('disconnect', update);
			return;
		}
		// Safari: AirPlay.
		a.addEventListener('webkitplaybacktargetavailabilitychanged', (e) => {
			this.castAvailable = (e as Event & { availability?: string }).availability === 'available';
		});
	}

	/** Opens the browser's device picker (Chromecast, AirPlay, …). */
	cast() {
		const a = this.audio as HTMLAudioElement & { webkitShowPlaybackTargetPicker?: () => void };
		if (a.remote?.prompt) void a.remote.prompt().catch(() => undefined);
		else a.webkitShowPlaybackTargetPicker?.();
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
		haptic.success();
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
		this.#prepareSound();
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
		if (this.chapter?.id !== this.#metaChapter) {
			this.#updateMediaSession();
			this.#positionState();
			this.#updateBadge();
		}
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
		if (s.kind === 'window') {
			// Past the earliest time, the next chapter end is a natural place to stop.
			if (Date.now() >= s.minAt && this.chapter && this.currentTime >= this.chapter.end - 0.4) {
				this.pause();
				a.volume = prefs.volume;
				this.sleep = { kind: 'off' };
				return;
			}
		}
		const left = ((s.kind === 'window' ? s.maxAt : s.endsAt) - Date.now()) / 1000;
		this.sleepRemaining = Math.max(0, this.sleepForecast?.seconds ?? left);
		const fadeDuration = prefs.sleepFadeDuration ?? 30;
		if (left <= 0) {
			this.pause();
			a.volume = prefs.volume;
			// Rewind a little so the listener does not miss what they fell asleep to.
			if (prefs.sleepRewind) this.skip(-prefs.sleepRewind);
			this.sleep = { kind: 'off' };
		} else if (left < fadeDuration && this.sleepForecast?.chapter == null) {
			if (prefs.sleepFade) {
				const ratio = Math.max(0.01, left / fadeDuration);
				a.volume = prefs.volume * Math.pow(ratio, 1.5);
			}
			this.#warnSleep(fadeDuration);
		}
	}

	/** A last chance to keep listening, shortly before a timer stops playback. */
	#warnSleep(durationSeconds = 30) {
		if (this.#sleepWarned) return;
		this.#sleepWarned = true;
		toasts.show(
			i18n.m.player.sleepSoon,
			{ label: i18n.m.player.sleepExtend(10), run: () => this.extendSleep(10) },
			durationSeconds * 1000
		);
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
			progress.celebrate(id);
			// Finished books need no space on the device (if the listener wants it so).
			if (prefs.removeFinished && downloads.has(id) && !this.#local) void downloads.remove(id);
			// A timer that stops at a chapter end treats the end of the book as one.
			const s = this.sleep;
			if (s.kind === 'chapter' || (s.kind === 'window' && Date.now() >= s.minAt)) {
				this.sleep = { kind: 'off' };
				return;
			}
			const next = prefs.autoQueue ? queue.shift() : undefined;
			if (next) {
				toasts.show(i18n.m.toast.nowPlaying(view(next).title));
				void this.play(next.id);
			} else if (prefs.seriesOffer) void this.#offerNextInSeries(this.item);
		}
	}

	/** Book finished: if it belongs to a series, offer the next volume right away. */
	async #offerNextInSeries(item: LibraryItem) {
		const series = item.media.metadata.series?.[0];
		if (!series) return;
		try {
			const { results } = await session.api.libraryItems(item.libraryId, {
				filter: encodeFilter('series', series.id),
				sort: 'sequence',
				limit: 0
			});
			const next = results[results.findIndex((i) => i.id === item.id) + 1];
			if (!next) return;
			toasts.show(
				i18n.m.toast.nextInSeries(next.media.metadata.title),
				{ label: i18n.m.toast.playNext, run: () => void this.play(next.id) },
				15_000
			);
		} catch {
			/* offline or no access: nothing to offer */
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

	/** Stops and forgets the last book (the mini player goes away). */
	async stop() {
		await this.leave();
		store(scoped(LAST_KEY), null);
	}

	/** Closes the playback session for an account change; its last book stays remembered. */
	async leave() {
		clearBadge();
		const closing = this.#close();
		this.item = null;
		this.chapters = [];
		this.bookmarks = [];
		this.expanded = false;
		this.sleep = { kind: 'off' };
		await closing;
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
			['previoustrack', () => (prefs.mediaKeys === 'chapter' ? this.prevChapter() : this.back())],
			['nexttrack', () => (prefs.mediaKeys === 'chapter' ? this.nextChapter() : this.forward())],
			['seekto', (d) => d.seekTime !== undefined && this.seek(d.seekTime + this.scope.start)],
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
			const { start, end } = this.scope;
			navigator.mediaSession.setPositionState({
				duration: Math.max(0.1, end - start),
				position: Math.min(Math.max(0, this.currentTime - start), end - start),
				playbackRate: this.rate
			});
		} catch {
			/* ignore */
		}
	}

	#updateBadge() {
		if (!this.item || !this.chapters.length) {
			clearBadge();
			return;
		}
		const remaining = Math.max(0, this.chapters.length - (this.chapter?.id ?? 0) - 1);
		setBadge(remaining);
	}
}

export const player = new Player();
