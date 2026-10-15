<script lang="ts">
	import type { LibraryItem, LibraryItemMinified } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { progress } from '#lib/progress.svelte.ts';
	import { queue } from '#lib/queue.svelte.ts';
	import { player } from '#lib/player/player.svelte.ts';
	import { downloads, supported as downloadsSupported } from '#lib/downloads.svelte.ts';
	import { toasts } from '#lib/toast.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { isTauri } from '#lib/platform.ts';
	import ListPicker from './ListPicker.svelte';
	import Check from '@lucide/svelte/icons/check';
	import RotateCcw from '@lucide/svelte/icons/rotate-ccw';
	import ListPlus from '@lucide/svelte/icons/list-plus';
	import ListMusic from '@lucide/svelte/icons/list-music';
	import Library from '@lucide/svelte/icons/library';
	import Download from '@lucide/svelte/icons/download';
	import X from '@lucide/svelte/icons/x';

	type Item = LibraryItem | LibraryItemMinified;

	/** Actions for several selected books at once (library multi-select). */
	let {
		items,
		allSelected,
		onall,
		onclear
	}: { items: Item[]; allSelected: boolean; onall: () => void; onclear: () => void } = $props();

	const t = $derived(i18n.m.select);
	const n = $derived(items.length);
	let picker = $state<'playlist' | 'collection' | null>(null);
	let busy = $state(false);

	async function finish(done: boolean) {
		busy = true;
		try {
			await session.api.updateProgressMany(
				items.map((i) => ({
					libraryItemId: i.id,
					isFinished: done,
					...(done ? {} : { currentTime: 0, progress: 0 })
				}))
			);
			await progress.load(true);
			toasts.show(done ? t.finished(n) : t.unfinished(n));
			onclear();
		} catch (e) {
			toasts.show(i18n.m.common.error((e as Error).message));
		} finally {
			busy = false;
		}
	}

	function queueAll() {
		const add = items.filter((i) => !queue.has(i.id) && i.id !== player.item?.id);
		for (const i of add) queue.add(i);
		toasts.show(i18n.m.seriesPage.queued(add.length));
		onclear();
	}

	function downloadAll() {
		const ids = items.map((i) => i.id).filter((id) => !downloads.has(id));
		// One after another: parallel downloads would compete for the same connection.
		void (async () => {
			for (const id of ids) await downloads.download(id).catch(() => undefined);
		})();
		toasts.show(t.downloading(ids.length));
		onclear();
	}

	const canDownload = $derived(!isTauri && session.canDownload && downloadsSupported());
</script>

<div class="bar" role="region" aria-label={t.region}>
	<span class="count tabular" aria-live="polite">{t.count(n)}</span>
	<button class="chip outline" onclick={onall}>{allSelected ? t.none : t.all}</button>
	<div class="actions">
		<button class="act" disabled={!n || busy} onclick={() => finish(true)}>
			<Check size={16} aria-hidden="true" />{t.markFinished}
		</button>
		<button class="act" disabled={!n || busy} onclick={() => finish(false)}>
			<RotateCcw size={16} aria-hidden="true" />{t.markUnfinished}
		</button>
		<button class="act" disabled={!n} onclick={queueAll}>
			<ListPlus size={16} aria-hidden="true" />{t.queue}
		</button>
		<button class="act" disabled={!n} onclick={() => (picker = 'playlist')}>
			<ListMusic size={16} aria-hidden="true" />{t.playlist}
		</button>
		{#if session.canUpdate}
			<button class="act" disabled={!n} onclick={() => (picker = 'collection')}>
				<Library size={16} aria-hidden="true" />{t.collection}
			</button>
		{/if}
		{#if canDownload}
			<button class="act" disabled={!n} onclick={downloadAll}>
				<Download size={16} aria-hidden="true" />{t.download}
			</button>
		{/if}
	</div>
	<button class="close" onclick={onclear} aria-label={t.done} title={t.done}><X size={18} /></button
	>
</div>

{#if picker}
	<ListPicker
		kind={picker}
		{items}
		bind:open={() => picker !== null, (o) => !o && (picker = null)}
	/>
{/if}

<style>
	.bar {
		position: sticky;
		bottom: var(--sp-16);
		z-index: 14;
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--sp-8) var(--sp-12);
		margin-top: var(--sp-24);
		padding: var(--sp-8) var(--sp-8) var(--sp-8) var(--sp-16);
		border-radius: var(--r-card-lg);
		background: var(--c-ctrl);
		color: var(--c-ctrl-ink);
		box-shadow: 0 18px 40px -16px rgb(0 0 0 / 0.55);
		animation: rise var(--motion-base) var(--motion-out);
	}

	@keyframes rise {
		from {
			translate: 0 12px;
			opacity: 0;
		}
	}

	:global(main.has-player) .bar {
		bottom: calc(80px + var(--sp-16));
	}

	.count {
		font-weight: var(--fw-bold);
	}

	.bar .chip {
		color: inherit;
		border-color: color-mix(in oklab, var(--c-ctrl-ink) 40%, transparent);
		background: transparent;
	}

	.actions {
		flex: 1;
		display: flex;
		flex-wrap: wrap;
		gap: var(--sp-4);
		min-width: 0;
	}

	.act {
		display: inline-flex;
		align-items: center;
		gap: var(--sp-8);
		min-height: 40px;
		padding: 0 var(--sp-12);
		border: 0;
		border-radius: var(--r-pill);
		background: color-mix(in oklab, var(--c-ctrl-ink) 10%, transparent);
		color: inherit;
		font-size: var(--fs-13);
		font-weight: var(--fw-semibold);
	}

	.act:hover:not(:disabled) {
		background: color-mix(in oklab, var(--c-ctrl-ink) 18%, transparent);
	}

	.act:disabled {
		opacity: 0.45;
	}

	.close {
		display: grid;
		place-items: center;
		width: var(--layout-touch);
		height: var(--layout-touch);
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: inherit;
	}

	@media (max-width: 800px) {
		.bar {
			bottom: calc(76px + env(safe-area-inset-bottom));
		}

		:global(main.has-player) .bar {
			bottom: calc(148px + env(safe-area-inset-bottom));
		}

		.actions {
			order: 3;
			flex-basis: 100%;
		}

		.act {
			font-size: var(--fs-12);
		}
	}
</style>
