import { SvelteMap } from 'svelte/reactivity';
import type { MediaProgress } from '@koalashelf/abs-client';
import { session } from './session.svelte';

/** Reactive media progress of the signed-in user, keyed by library item id. */
class ProgressStore {
	byItem = new SvelteMap<string, MediaProgress>();
	#loaded: Promise<void> | null = null;
	/** Only the latest load may write: an older, slower response must not undo newer state. */
	#generation = 0;
	/** Entries written by this device (stamped with its own clock, not the server's). */
	#local = new WeakSet<MediaProgress>();

	constructor() {
		session.onSignOut(() => this.reset());
		// Coming back to the tab: pick up progress made on other devices meanwhile.
		document.addEventListener('visibilitychange', () => {
			if (!document.hidden && session.client && this.#loaded)
				void this.load(true).catch(() => undefined);
		});
	}

	load(force = false) {
		if (!this.#loaded || force) {
			const generation = ++this.#generation;
			this.#loaded = session.api.me().then((me) => {
				if (generation !== this.#generation) return;
				session.canDownload = me.permissions?.download !== false;
				this.byItem.clear();
				for (const p of me.mediaProgress) {
					if (p.episodeId) continue;
					// A server can list a book twice (e.g. a late sync after its progress was reset):
					// the most recent entry is the true state.
					const known = this.byItem.get(p.libraryItemId);
					if (!known || p.lastUpdate >= known.lastUpdate) this.byItem.set(p.libraryItemId, p);
				}
			});
		}
		return this.#loaded;
	}

	/** Forget everything (sign-out / user switch). */
	reset() {
		this.#loaded = null;
		this.#generation++;
		this.byItem.clear();
	}

	get(itemId: string) {
		return this.byItem.get(itemId);
	}

	/** Local update from this device's player. */
	set(p: MediaProgress) {
		this.#local.add(p);
		this.byItem.set(p.libraryItemId, p);
	}

	/** Applies a pushed update unless we already know a newer state (events can arrive late). */
	merge(p: MediaProgress) {
		const known = this.byItem.get(p.libraryItemId);
		// Timestamps are only comparable between server states (device clocks can be off).
		if (known && !this.#local.has(known) && known.lastUpdate > p.lastUpdate) return false;
		this.byItem.set(p.libraryItemId, p);
		return true;
	}

	status(itemId: string): 'new' | 'running' | 'done' {
		const p = this.byItem.get(itemId);
		if (!p || (!p.progress && !p.currentTime)) return 'new';
		return p.isFinished ? 'done' : 'running';
	}
}

export const progress = new ProgressStore();
