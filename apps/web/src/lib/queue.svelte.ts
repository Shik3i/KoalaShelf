// "Up next": books to play after the current one. Kept on this device only.
import type { LibraryItem, LibraryItemMinified } from '@koalashelf/abs-client';
import { scoped, session } from './session.svelte.ts';

const KEY = 'ks.queue';
const LIMIT = 100;

type QueuedItem = LibraryItem | LibraryItemMinified;

/** Only what the queue shows (title, people, cover, duration): expanded items carry every file. */
function slim(item: QueuedItem): QueuedItem {
	const media: Record<string, unknown> = { ...item.media };
	delete media.audioFiles;
	delete media.chapters;
	delete media.tracks;
	// Queued items are only read through view() (title, people, cover, duration).
	return { ...item, media } as unknown as QueuedItem;
}

/** Each account has its own queue. */
function load(): QueuedItem[] {
	try {
		return JSON.parse(localStorage.getItem(scoped(KEY)) ?? '[]');
	} catch {
		return [];
	}
}

class Queue {
	items = $state.raw<QueuedItem[]>([]);

	constructor() {
		if (typeof window === 'undefined') return;
		// The active account's queue (switching accounts swaps it, signing out forgets it).
		session.onEnter(() => (this.items = load()));
		session.onSignOut(() => (this.items = []));
	}

	has(id: string) {
		return this.items.some((i) => i.id === id);
	}

	add(item: QueuedItem) {
		if (this.has(item.id)) return;
		this.#set([...this.items, slim(item)].slice(-LIMIT));
	}

	/** Puts an item back where it was (undo). */
	insert(item: QueuedItem, index: number) {
		if (this.has(item.id)) return;
		const next = [...this.items];
		next.splice(index, 0, slim(item));
		this.#set(next);
	}

	remove(id: string) {
		if (this.has(id)) this.#set(this.items.filter((i) => i.id !== id));
	}

	move(id: string, by: number) {
		const from = this.items.findIndex((i) => i.id === id);
		const to = from + by;
		if (from < 0 || to < 0 || to >= this.items.length) return;
		const next = [...this.items];
		next.splice(to, 0, ...next.splice(from, 1));
		this.#set(next);
	}

	/** Takes the next item off the queue. */
	shift() {
		const [first, ...rest] = this.items;
		if (first) this.#set(rest);
		return first;
	}

	clear() {
		this.#set([]);
	}

	#set(items: QueuedItem[]) {
		this.items = items;
		try {
			if (session.user) localStorage.setItem(scoped(KEY), JSON.stringify(items));
		} catch {
			/* storage full or blocked: the queue still works for this visit */
		}
	}
}

export const queue = new Queue();
