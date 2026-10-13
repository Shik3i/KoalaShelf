<script lang="ts">
	import EmptyState from '#lib/components/EmptyState.svelte';
	import { untrack } from 'svelte';
	import type { Series } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { onVisible } from '#lib/attach.ts';
	import { coverSrc, view } from '#lib/items.ts';
	import Cover from '#lib/components/Cover.svelte';

	const t = $derived(i18n.m.series);
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
			list = [...list, ...r.results];
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
			<div class="skeleton" style="aspect-ratio: 1"></div>
		{/each}
	{/if}
	{#each list as s (s.id)}
		{@const books = s.books.slice(0, 3)}
		<a class="series" href="/library?series={s.id}">
			<div class="stack" style:--n={books.length}>
				{#each books.toReversed() as b, i (b.id)}
					{@const v = view(b)}
					<div class="card" style:--i={books.length - 1 - i}>
						<Cover title={v.title} author={v.author} src={coverSrc(b)} sizes="180px" />
					</div>
				{/each}
			</div>
			<strong>{s.name}</strong>
			<span class="muted">{t.books(s.books.length)}</span>
		</a>
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
		grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
		gap: var(--sp-32) var(--sp-24);
	}

	.series {
		display: grid;
		gap: var(--sp-4);
		min-width: 0;
	}

	.stack {
		position: relative;
		aspect-ratio: 1;
		margin: 0 calc(var(--sp-8) * (var(--n) - 1)) var(--sp-12) 0;
	}

	.card {
		position: absolute;
		inset: 0;
		translate: calc(var(--i) * 8px) calc(var(--i) * -6px);
		scale: calc(1 - var(--i) * 0.04);
		transform-origin: bottom left;
		filter: brightness(calc(1 - var(--i) * 0.12));
		transition: translate var(--motion-base) var(--motion-ease);
	}

	.series:hover .card {
		translate: calc(var(--i) * 18px) calc(var(--i) * -8px);
	}

	strong {
		font-weight: var(--fw-semibold);
		font-size: var(--fs-14);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.muted {
		font-size: var(--fs-13);
	}

	.sentinel {
		height: 60px;
	}

	@container page (max-width: 700px) {
		.grid {
			grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
		}
	}
</style>
