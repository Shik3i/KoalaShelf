// Offline downloads: audio files live in the browser's Origin Private File System (OPFS),
// one folder per book with a manifest. Listening done offline is queued as local playback
// sessions and uploaded when the connection is back (the server keeps the newer progress).
import { SvelteMap } from 'svelte/reactivity';
import type { AudioTrack, LibraryItem } from '@koalashelf/abs-client';
import { scoped, session } from './session.svelte.ts';
import { toasts } from './toast.svelte.ts';
import { i18n } from './i18n/index.svelte.ts';

const ROOT = 'ks-downloads';
const QUEUE_KEY = 'ks.localSessions';

export interface DownloadedTrack {
	index: number;
	startOffset: number;
	duration: number;
	mimeType: string;
	file: string;
}

export interface DownloadedBook {
	id: string;
	item: LibraryItem;
	tracks: DownloadedTrack[];
	size: number;
	cover: boolean;
	downloadedAt: number;
	/** Accounts (user ids) that downloaded it: one copy on disk, shown to each of them. */
	owners?: string[];
}

export interface DownloadJob {
	received: number;
	total: number;
	state: 'running' | 'failed';
}

export interface LocalSession {
	id: string;
	libraryItemId: string;
	libraryId: string;
	mediaType: 'book';
	displayTitle: string;
	displayAuthor: string;
	duration: number;
	playMethod: 3;
	mediaPlayer: string;
	deviceInfo: Record<string, string>;
	startTime: number;
	currentTime: number;
	timeListening: number;
	startedAt: number;
	updatedAt: number;
	date: string;
	dayOfWeek: string;
}

export const supported = () =>
	typeof navigator !== 'undefined' && !!navigator.storage?.getDirectory;

async function root() {
	const base = await navigator.storage.getDirectory();
	return base.getDirectoryHandle(ROOT, { create: true });
}

async function bookDir(id: string, create = false) {
	return (await root()).getDirectoryHandle(id, { create });
}

/** Tracks with offsets: from the expanded item, or derived from the audio files in order. */
function tracksOf(item: LibraryItem): AudioTrack[] {
	if (item.media.tracks?.length) return item.media.tracks;
	let offset = 0;
	return [...item.media.audioFiles]
		.sort((a, b) => a.index - b.index)
		.map((f) => {
			const t: AudioTrack = {
				index: f.index,
				startOffset: offset,
				duration: f.duration,
				title: f.metadata.filename,
				contentUrl: `/api/items/${item.id}/file/${f.ino}`,
				mimeType: f.mimeType,
				metadata: f.metadata
			};
			offset += f.duration;
			return t;
		});
}

class Downloads {
	/** The active account's downloads. */
	books = new SvelteMap<string, DownloadedBook>();
	/** Every download on this device, of every saved account. */
	#all = new Map<string, DownloadedBook>();
	jobs = new SvelteMap<string, DownloadJob>();
	#controllers = new Map<string, AbortController>();
	#urls = new Map<string, string[]>();
	#ready: Promise<void> | null = null;

	constructor() {
		if (typeof window === 'undefined') return;
		addEventListener('online', () => void this.flush());
		session.onSignOut(() => this.books.clear());
		session.onEnter(() => void this.init().then(() => this.#scope()));
		session.onForget((userId) => this.forgetOwner(userId));
	}

	/** Shows the active account's books; downloads from before accounts existed become its own. */
	#scope() {
		const me = session.user?.id;
		this.books.clear();
		if (!me) return;
		for (const [id, book] of this.#all) {
			if (!book.owners?.length) void this.#writeMeta({ ...book, owners: [me] });
			if (!book.owners?.length || book.owners.includes(me)) this.books.set(id, this.#all.get(id)!);
		}
	}

	async #writeMeta(book: DownloadedBook) {
		this.#all.set(book.id, book);
		const meta = await (await bookDir(book.id, true)).getFileHandle('meta.json', { create: true });
		const w = await meta.createWritable();
		await w.write(JSON.stringify(book));
		await w.close();
	}

	/** Reads all manifests once. */
	init() {
		this.#ready ??= (async () => {
			if (!supported()) return;
			try {
				for await (const [name, handle] of (await root()).entries()) {
					if (handle.kind !== 'directory') continue;
					try {
						const file = await (handle as FileSystemDirectoryHandle).getFileHandle('meta.json');
						const meta = JSON.parse(await (await file.getFile()).text()) as DownloadedBook;
						this.#all.set(name, meta);
					} catch {
						// Incomplete download (no manifest): clean up.
						await (await root()).removeEntry(name, { recursive: true }).catch(() => undefined);
					}
				}
			} catch {
				/* storage unavailable */
			}
			this.#scope();
			void this.flush();
		})();
		return this.#ready;
	}

	has(id: string) {
		return this.books.has(id);
	}

	async download(id: string) {
		if (!session.canDownload) return;
		if (this.jobs.get(id)?.state === 'running' || this.books.has(id)) return;
		// Already on this device for another account: verify server access first, then share the copy.
		const other = this.#all.get(id);
		const me = session.user?.id;
		if (other && me) {
			try {
				await session.api.item(id);
				const book = { ...other, owners: [...new Set([...(other.owners ?? []), me])] };
				await this.#writeMeta(book);
				this.books.set(id, book);
				toasts.show(i18n.m.toast.downloaded(book.item.media.metadata.title));
			} catch {
				/* account does not have server permission for this item */
			}
			return;
		}
		const ctrl = new AbortController();
		this.#controllers.set(id, ctrl);
		this.jobs.set(id, { received: 0, total: 0, state: 'running' });
		try {
			// Keep downloads when the browser is low on space (best effort, may be ignored).
			void navigator.storage.persist?.();
			const api = session.api;
			const item = await api.item(id, ctrl.signal);
			const tracks = tracksOf(item);
			const total = tracks.reduce((s, t) => s + (t.metadata?.size ?? 0), 0);
			this.jobs.set(id, { received: 0, total, state: 'running' });
			const dir = await bookDir(id, true);
			let received = 0;
			const saved: DownloadedTrack[] = [];

			for (const t of tracks) {
				const res = await api.download(`${t.contentUrl}/download`, ctrl.signal);
				const ext = t.metadata?.ext ?? '';
				const name = `track-${t.index}${ext}`;
				const writable = await (await dir.getFileHandle(name, { create: true })).createWritable();
				const reader = res.body!.getReader();
				for (;;) {
					const { done, value } = await reader.read();
					if (done) break;
					await writable.write(value);
					received += value.byteLength;
					this.jobs.set(id, { received, total: Math.max(total, received), state: 'running' });
				}
				await writable.close();
				saved.push({
					index: t.index,
					startOffset: t.startOffset,
					duration: t.duration,
					mimeType: t.mimeType,
					file: name
				});
			}

			let cover = false;
			if (item.media.coverPath) {
				try {
					const res = await fetch(api.coverUrl(id, { width: 600, updatedAt: item.updatedAt }), {
						signal: ctrl.signal
					});
					const w = await (
						await dir.getFileHandle('cover.webp', { create: true })
					).createWritable();
					await w.write(await res.blob());
					await w.close();
					cover = true;
				} catch {
					/* cover is optional */
				}
			}

			const book: DownloadedBook = {
				id,
				item,
				tracks: saved,
				size: received,
				cover,
				downloadedAt: Date.now(),
				owners: me ? [me] : []
			};
			await this.#writeMeta(book);
			this.books.set(id, book);
			this.jobs.delete(id);
			toasts.show(i18n.m.toast.downloaded(item.media.metadata.title));
		} catch (e) {
			await (await root()).removeEntry(id, { recursive: true }).catch(() => undefined);
			if ((e as Error).name === 'AbortError') this.jobs.delete(id);
			else {
				this.jobs.set(id, { received: 0, total: 0, state: 'failed' });
				toasts.show(
					i18n.m.toast.downloadFailed(this.books.get(id)?.item.media.metadata.title ?? '')
				);
			}
		} finally {
			this.#controllers.delete(id);
		}
	}

	cancel(id: string) {
		this.#controllers.get(id)?.abort();
	}

	async remove(id: string) {
		this.cancel(id);
		this.revoke(id);
		this.books.delete(id);
		this.jobs.delete(id);
		// Still downloaded by another saved account: only this account lets go of it.
		const book = this.#all.get(id);
		const me = session.user?.id;
		const rest = (book?.owners ?? []).filter((o) => o !== me);
		if (book && rest.length) return this.#writeMeta({ ...book, owners: rest });
		this.#all.delete(id);
		await (await root()).removeEntry(id, { recursive: true }).catch(() => undefined);
	}

	/** Lets go of every download of an account that is being forgotten. */
	async forgetOwner(userId: string) {
		await this.init();
		for (const book of [...this.#all.values()]) {
			if (!book.owners?.includes(userId)) continue;
			const rest = book.owners.filter((o) => o !== userId);
			if (rest.length) await this.#writeMeta({ ...book, owners: rest });
			else {
				this.#all.delete(book.id);
				this.books.delete(book.id);
				await (await root()).removeEntry(book.id, { recursive: true }).catch(() => undefined);
			}
		}
	}

	/** Blob URLs for the local files, in track order (seekable, no network). */
	async trackUrls(id: string) {
		const book = this.books.get(id);
		if (!book) return null;
		this.revoke(id);
		const dir = await bookDir(id);
		// eslint-disable-next-line svelte/prefer-svelte-reactivity -- returned lookup, not state
		const urls = new Map<number, string>();
		for (const t of book.tracks) {
			urls.set(t.index, URL.createObjectURL(await (await dir.getFileHandle(t.file)).getFile()));
		}
		this.#urls.set(id, [...urls.values()]);
		return urls;
	}

	async coverUrl(id: string) {
		if (!this.books.get(id)?.cover) return null;
		try {
			const f = await (await (await bookDir(id)).getFileHandle('cover.webp')).getFile();
			return URL.createObjectURL(f);
		} catch {
			return null;
		}
	}

	revoke(id: string) {
		for (const u of this.#urls.get(id) ?? []) URL.revokeObjectURL(u);
		this.#urls.delete(id);
	}

	async usage() {
		let size = 0;
		for (const b of this.books.values()) size += b.size;
		const est = await navigator.storage?.estimate?.().catch(() => undefined);
		return { size, quota: est?.quota ?? 0, used: est?.usage ?? 0 };
	}

	// --- Offline listening queue ----------------------------------------------

	queue(): LocalSession[] {
		try {
			return JSON.parse(localStorage.getItem(scoped(QUEUE_KEY)) ?? '[]');
		} catch {
			return [];
		}
	}

	saveLocal(s: LocalSession) {
		const list = this.queue().filter((x) => x.id !== s.id);
		list.push(s);
		try {
			localStorage.setItem(scoped(QUEUE_KEY), JSON.stringify(list));
		} catch {
			/* ignore */
		}
	}

	/** Uploads queued offline sessions; keeps the ones that fail for the next attempt. */
	async flush() {
		if (!session.client || !navigator.onLine) return;
		const rest: LocalSession[] = [];
		for (const s of this.queue()) {
			try {
				await session.api.syncLocalSession(s as unknown as Record<string, unknown>);
			} catch {
				rest.push(s);
			}
		}
		try {
			localStorage.setItem(scoped(QUEUE_KEY), JSON.stringify(rest));
		} catch {
			/* ignore */
		}
	}
}

export const downloads = new Downloads();
