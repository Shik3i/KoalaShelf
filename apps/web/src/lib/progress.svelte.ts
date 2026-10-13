import { SvelteMap } from 'svelte/reactivity';
import type { MediaProgress } from '@koalashelf/abs-client';
import { session } from './session.svelte';

/** Reactive media progress of the signed-in user, keyed by library item id. */
class ProgressStore {
	byItem = new SvelteMap<string, MediaProgress>();
	#loaded: Promise<void> | null = null;

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
			this.#loaded = session.api.me().then((me) => {
				session.canDownload = me.permissions?.download !== false;
				this.byItem.clear();
				for (const p of me.mediaProgress) if (!p.episodeId) this.byItem.set(p.libraryItemId, p);
			});
		}
		return this.#loaded;
	}

	/** Forget everything (sign-out / user switch). */
	reset() {
		this.#loaded = null;
		this.byItem.clear();
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
