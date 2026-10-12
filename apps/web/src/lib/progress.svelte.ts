import { SvelteMap } from 'svelte/reactivity';
import type { MediaProgress } from '@koalashelf/abs-client';
import { session } from './session.svelte';

/** Reactive media progress of the signed-in user, keyed by library item id. */
class ProgressStore {
	byItem = new SvelteMap<string, MediaProgress>();
	#loaded: Promise<void> | null = null;

	load(force = false) {
		if (!this.#loaded || force) {
			this.#loaded = session.api.me().then((me) => {
				this.byItem.clear();
				for (const p of me.mediaProgress) if (!p.episodeId) this.byItem.set(p.libraryItemId, p);
			});
		}
		return this.#loaded;
	}

	get(itemId: string) {
		return this.byItem.get(itemId);
	}

	set(p: MediaProgress) {
		this.byItem.set(p.libraryItemId, p);
	}

	status(itemId: string): 'new' | 'running' | 'done' {
		const p = this.byItem.get(itemId);
		if (!p || (!p.progress && !p.currentTime)) return 'new';
		return p.isFinished ? 'done' : 'running';
	}
}

export const progress = new ProgressStore();
