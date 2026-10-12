<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { encodeFilter, type LibraryItemMinified } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { progress } from '#lib/progress.svelte.ts';
	import { cached } from '#lib/cache.ts';
	import { onVisible } from '#lib/attach.ts';
	import { view } from '#lib/items.ts';
	import { duration } from '#lib/format.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import BookTile from '#lib/components/BookTile.svelte';
	import Cover from '#lib/components/Cover.svelte';
	import LayoutGrid from '@lucide/svelte/icons/layout-grid';
	import Rows3 from '@lucide/svelte/icons/rows-3';
	import ArrowDownUp from '@lucide/svelte/icons/arrow-down-up';

	const PAGE = 60;
	const t = $derived(i18n.m.library);
	const STATUS = $derived([
		{ id: '', label: t.all },
		{ id: 'in-progress', label: t.inProgress, local: 'running' },
		{ id: 'not-started', label: t.notStarted, local: 'new' },
		{ id: 'finished', label: t.finished, local: 'done' }
	] as const);
	const SORTS = $derived([
		{ id: 'title', key: 'media.metadata.title', label: t.sortTitle, desc: false },
		{ id: 'author', key: 'media.metadata.authorNameLF', label: t.sortAuthor, desc: false },
		{ id: 'added', key: 'addedAt', label: t.sortAdded, desc: true },
		{ id: 'played', key: 'progress', label: t.sortProgress, desc: true },
		{ id: 'duration', key: 'media.duration', label: t.sortDuration, desc: false },
		{ id: 'published', key: 'media.metadata.publishedYear', label: t.sortPublished, desc: true }
	]);

	// All view state lives in the URL: shareable, bookmarkable, back button works.
	const q = $derived(page.url.searchParams);
	const layout = $derived(q.get('view') === 'list' ? 'list' : 'shelf');
	// Inside a series the natural order is the volume number.
	const sort = $derived(
		SORTS.find((s) => s.id === q.get('sort')) ??
			(q.get('series')
				? { id: 'sequence', key: 'sequence', label: t.series, desc: false }
				: SORTS[0])
	);
	const desc = $derived(q.has('desc') ? q.get('desc') === '1' : sort.desc);
	const status = $derived(STATUS.find((s) => s.id === (q.get('status') ?? '')) ?? STATUS[0]);
	const genre = $derived(q.get('genre') ?? '');
	const narrator = $derived(q.get('narrator') ?? '');
	const series = $derived(q.get('series') ?? '');
	const author = $derived(q.get('author') ?? '');

	function set(changes: Record<string, string | null>) {
		// eslint-disable-next-line svelte/prefer-svelte-reactivity -- local, not reactive state
		const next = new URLSearchParams(page.url.search);
		for (const [k, v] of Object.entries(changes)) {
			if (v === null || v === '') next.delete(k);
			else next.set(k, v);
		}
		goto(`?${next}`, { replace: true, reset: false });
	}

	const filterData = $derived(
		session.libraryId
			? cached(`fd:${session.libraryId}`, () => session.api.filterData(session.libraryId!), 300_000)
			: null
	);

	let items = $state.raw<LibraryItemMinified[]>([]);
	let total = $state(0);
	let pageNo = 0;
	let loading = $state(false);
	let error = $state<string | null>(null);
	let done = $state(false);
	let controller: AbortController | null = null;

	/** Server takes one filter; the most selective one goes to the server, the rest is applied here. */
	const plan = $derived.by(() => {
		const active: { server: string; local?: (i: LibraryItemMinified) => boolean }[] = [];
		if (series) active.push({ server: encodeFilter('series', series) });
		// Minified items carry no author ids, so the author filter only works server-side.
		if (author) active.push({ server: encodeFilter('authors', author) });
		if (narrator)
			active.push({
				server: encodeFilter('narrators', narrator),
				local: (i) => i.media.metadata.narratorName.split(', ').includes(narrator)
			});
		if (genre)
			active.push({
				server: encodeFilter('genres', genre),
				local: (i) => i.media.metadata.genres.includes(genre)
			});
		if ('local' in status) {
			const local = status.local;
			active.push({
				server: encodeFilter('progress', status.id),
				local: (i) => progress.status(i.id) === local
			});
		}
		const [primary, ...rest] = active;
		return {
			filter: primary?.server,
			local: rest.map((r) => r.local!).filter(Boolean),
			key: JSON.stringify([session.libraryId, sort.key, desc, active.map((a) => a.server)])
		};
	});

	// A plain string, so switching the UI language does not reload the list.
	const planKey = $derived(plan.key);

	async function loadMore() {
		if (loading || done || !session.libraryId) return;
		loading = true;
		const signal = controller?.signal;
		try {
			const all = plan.local.length > 0;
			const res = await session.api.libraryItems(
				session.libraryId,
				{
					page: pageNo,
					limit: all ? 0 : PAGE,
					sort: sort.key,
					desc,
					filter: plan.filter,
					collapseSeries: false
				},
				signal
			);
			const found = all ? res.results.filter((i) => plan.local.every((f) => f(i))) : res.results;
			items = pageNo === 0 ? found : [...items, ...found];
			total = all ? found.length : res.total;
			pageNo++;
			done = all || items.length >= res.total;
		} catch (e) {
			if ((e as Error).name !== 'AbortError') error = (e as Error).message;
		} finally {
			if (!signal?.aborted) loading = false;
		}
	}

	$effect(() => {
		void planKey;
		controller?.abort();
		controller = new AbortController();
		items = [];
		pageNo = 0;
		done = false;
		error = null;
		loading = false;
		progress.load().then(loadMore);
	});
</script>

<svelte:head><title>{t.title} · KoalaShelf</title></svelte:head>

<header>
	<div>
		<span class="label">{session.library?.name ?? t.title}</span>
		<h1>{t.title}</h1>
	</div>
	<span class="muted count tabular">{t.count(total)}</span>
</header>

<div class="toolbar">
	<div class="chips" role="group" aria-label={t.statusGroup}>
		{#each STATUS as s (s.id)}
			<button class="chip" aria-pressed={status.id === s.id} onclick={() => set({ status: s.id })}
				>{s.label}</button
			>
		{/each}
	</div>

	{#await filterData then fd}
		{#if fd}
			<div class="chips">
				<label class="chip outline select" class:on={genre}>
					<span class="sr-only">{t.genre}</span>
					<select value={genre} onchange={(e) => set({ genre: e.currentTarget.value })}>
						<option value="">{t.genre}</option>
						{#each fd.genres as g (g)}<option value={g}>{g}</option>{/each}
					</select>
				</label>
				<label class="chip outline select" class:on={narrator}>
					<span class="sr-only">{t.narrator}</span>
					<select value={narrator} onchange={(e) => set({ narrator: e.currentTarget.value })}>
						<option value="">{t.narrator}</option>
						{#each fd.narrators as n (n)}<option value={n}>{n}</option>{/each}
					</select>
				</label>
				<label class="chip outline select" class:on={series}>
					<span class="sr-only">{t.series}</span>
					<select value={series} onchange={(e) => set({ series: e.currentTarget.value })}>
						<option value="">{t.series}</option>
						{#each fd.series as s (s.id)}<option value={s.id}>{s.name}</option>{/each}
					</select>
				</label>
			</div>
		{/if}
	{/await}

	<div class="chips end">
		<label class="chip outline select">
			<ArrowDownUp size={14} aria-hidden="true" />
			<span class="sr-only">{t.sort}</span>
			<select value={sort.id} onchange={(e) => set({ sort: e.currentTarget.value, desc: null })}>
				{#each SORTS as s (s.id)}<option value={s.id}>{s.label}</option>{/each}
			</select>
		</label>
		<button
			class="chip outline"
			onclick={() => set({ desc: desc ? '0' : '1' })}
			aria-label={t.reverse}
		>
			{desc ? '↓' : '↑'}
		</button>
		<div class="seg" role="group" aria-label={t.view}>
			<button
				aria-pressed={layout === 'shelf'}
				onclick={() => set({ view: null })}
				aria-label={t.viewShelf}
			>
				<LayoutGrid size={16} />
			</button>
			<button
				aria-pressed={layout === 'list'}
				onclick={() => set({ view: 'list' })}
				aria-label={t.viewList}
			>
				<Rows3 size={16} />
			</button>
		</div>
	</div>
</div>

{#await filterData then fd}
	{@const authorName = author && fd?.authors.find((a) => a.id === author)?.name}
	{@const seriesName = series && fd?.series.find((x) => x.id === series)?.name}
	{#if authorName || seriesName}
		<div class="chips active">
			{#if authorName}<button class="chip on" onclick={() => set({ author: null })}
					>{authorName} ✕</button
				>{/if}
			{#if seriesName}<button class="chip on" onclick={() => set({ series: null })}
					>{seriesName} ✕</button
				>{/if}
		</div>
	{/if}
{/await}

{#if error}
	<p class="error" role="alert">{t.loadError(error)}</p>
{:else if !loading && items.length === 0}
	<p class="empty muted">{t.empty}</p>
{/if}

{#if layout === 'shelf'}
	<div class="shelf">
		{#each items as item, i (item.id)}
			<BookTile {item} eager={i < 12} />
		{/each}
	</div>
{:else}
	<ul class="list">
		{#each items as item (item.id)}
			{@const v = view(item)}
			{@const p = progress.get(item.id)}
			<li>
				<a href="/book/{item.id}">
					<div class="mini">
						<Cover title={v.title} author={v.author} src={v.cover} sizes="56px" />
					</div>
					<div class="meta">
						<strong>{v.title}</strong>
						<span class="muted">{v.author}{v.narrator ? ` · ${t.readBy(v.narrator)}` : ''}</span>
					</div>
					<span class="muted tabular dur">{duration(v.duration)}</span>
					<span class="pbar" style:--p={p?.isFinished ? 1 : (p?.progress ?? 0)} aria-hidden="true"
					></span>
				</a>
			</li>
		{/each}
	</ul>
{/if}

{#if !done && !error}
	<div class="more" {@attach onVisible(loadMore)}>{loading ? t.loading : ''}</div>
{/if}

<style>
	header {
		display: flex;
		align-items: end;
		justify-content: space-between;
		gap: var(--sp-16);
		margin-bottom: var(--sp-20);
	}

	.count {
		font-size: var(--fs-13);
	}

	.toolbar {
		display: flex;
		flex-wrap: wrap;
		gap: var(--sp-12) var(--sp-20);
		margin-bottom: var(--sp-32);
	}

	.chips {
		display: flex;
		gap: var(--sp-8);
		flex-wrap: wrap;
		align-items: center;
	}

	.chips.end {
		margin-left: auto;
	}

	.chips.active {
		margin: calc(-1 * var(--sp-16)) 0 var(--sp-24);
	}

	.select {
		padding-right: var(--sp-8);
	}

	.select select {
		appearance: none;
		border: 0;
		background: transparent;
		font-weight: inherit;
		padding: 0 var(--sp-16) 0 0;
		background-image:
			linear-gradient(45deg, transparent 50%, currentColor 50%),
			linear-gradient(135deg, currentColor 50%, transparent 50%);
		background-position:
			right 6px center,
			right 2px center;
		background-size: 4px 4px;
		background-repeat: no-repeat;
		cursor: pointer;
		max-width: 160px;
		text-overflow: ellipsis;
	}

	.select select:focus-visible {
		outline: none;
	}

	.select:focus-within {
		outline: 2px solid var(--c-accent);
		outline-offset: 2px;
	}

	.seg {
		display: flex;
		padding: 3px;
		border-radius: var(--r-pill);
		background: var(--c-card);
	}

	.seg button {
		display: grid;
		place-items: center;
		width: 40px;
		height: 32px;
		border: 0;
		border-radius: var(--r-pill);
		background: transparent;
		color: var(--c-muted);
	}

	.seg button[aria-pressed='true'] {
		background: var(--c-sel);
		color: var(--c-sel-ink);
	}

	.shelf {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
		row-gap: var(--sp-32);
		margin: 0 calc(-1 * var(--sp-12));
	}

	.list {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--sp-4);
	}

	.list a {
		position: relative;
		display: grid;
		grid-template-columns: 56px 1fr auto;
		align-items: center;
		gap: var(--sp-16);
		padding: var(--sp-8);
		border-radius: var(--r-card);
		transition: background var(--motion-fast) var(--motion-ease);
		overflow: hidden;
	}

	.list a:hover {
		background: var(--c-card);
	}

	.meta {
		display: grid;
		min-width: 0;
	}

	.meta > * {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.meta strong {
		font-weight: var(--fw-semibold);
	}

	.meta span,
	.dur {
		font-size: var(--fs-13);
	}

	.pbar {
		position: absolute;
		left: 80px;
		right: var(--sp-8);
		bottom: 4px;
		height: 2px;
		background: linear-gradient(to right, var(--c-accent) calc(var(--p) * 100%), transparent 0);
	}

	.more {
		min-height: 80px;
		display: grid;
		place-items: center;
		color: var(--c-muted);
	}

	.error {
		color: var(--c-accent);
	}

	.empty {
		padding: var(--sp-48) 0;
		text-align: center;
	}

	@media (max-width: 800px) {
		.shelf {
			grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
		}

		.chips.end {
			margin-left: 0;
		}

		.dur {
			display: none;
		}

		.list a {
			grid-template-columns: 56px 1fr;
		}
	}
</style>
