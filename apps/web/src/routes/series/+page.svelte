<script lang="ts">
	import { byId, unique } from '#lib/unique.ts';
	import EmptyState from '#lib/components/EmptyState.svelte';
	import { untrack } from 'svelte';
	import type { LibraryItemMinified, Series } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { onVisible } from '#lib/attach.ts';
	import { coverSrc, view } from '#lib/items.ts';
	import RecordCrate, { type CrateRecord } from '#lib/components/RecordCrate.svelte';
	import { progress } from '#lib/progress.svelte.ts';
	import { inSeriesOrder } from '#lib/series-order.ts';

	const t = $derived(i18n.m.series);

	function record(b: LibraryItemMinified): CrateRecord {
		const v = view(b);
		return {
			id: b.id,
			title: v.title,
			author: v.author,
			cover: coverSrc(b),
			finished: progress.get(b.id)?.isFinished
		};
	}
	let list = $state.raw<Series[]>([]);
	let total = $state(0);
	let page = 0;
	let loading = $state(false);
	let done = $state(false);
	let error = $state<string | null>(null);
	let controller: AbortController | null = null;

	async function more() {
		if (loading || done || !session.libraryId) return;
		loading = true;
		const signal = controller?.signal;
		try {
			const r = await session.api.series(session.libraryId, { page, limit: 48 }, signal);
			list = unique([...list, ...r.results], byId);
			total = r.total;
			page++;
			done = list.length >= r.total;
		} catch (e) {
			if ((e as Error).name !== 'AbortError') error = (e as Error).message;
		} finally {
			if (!signal?.aborted) loading = false;
		}
	}

	// Reload only when the library changes (more() must not become a dependency).
	$effect(() => {
		void session.libraryId;
		progress.load();
		untrack(() => {
			controller?.abort();
			controller = new AbortController();
			list = [];
			page = 0;
			done = false;
			error = null;
			loading = false;
			void more();
		});
	});
</script>

<svelte:head><title>{t.title} · KoalaShelf</title></svelte:head>

<header>
	<h1>{t.title}</h1>
	<span class="muted tabular">{t.count(total)}</span>
</header>

{#if error}<p class="muted">{i18n.m.common.error(error)}</p>{/if}
{#if done && !list.length}<EmptyState text={t.empty} />{/if}

<div class="grid">
	{#if !list.length && !done && !error}
		{#each Array.from({ length: 10 }, (_, i) => i) as i (i)}
			<div class="skeleton" style="aspect-ratio: 1 / 1.08"></div>
		{/each}
	{/if}
	{#each list as s, i (s.id)}
		<div class="rise" style:--i={i % 48}>
			<RecordCrate
				name={s.name}
				href="/series/{s.id}"
				note={t.books(s.books.length)}
				records={inSeriesOrder(s.books, s.name).map(record)}
			/>
		</div>
	{/each}
</div>
{#if !done && !error}<div class="sentinel" {@attach onVisible(more)}></div>{/if}

<style>
	header {
		display: flex;
		justify-content: space-between;
		align-items: end;
		margin-bottom: var(--sp-32);
	}

	.grid {
		display: grid;
		grid-template-columns: repeat(
			auto-fill,
			minmax(min(calc(170px * var(--tile-scale)), 42%), 1fr)
		);
		gap: var(--sp-32) var(--sp-24);
	}

	.sentinel {
		height: 60px;
	}

	@container page (max-width: 700px) {
		.grid {
			grid-template-columns: repeat(
				auto-fill,
				minmax(min(calc(140px * var(--tile-scale)), 42%), 1fr)
			);
		}
	}
</style>
