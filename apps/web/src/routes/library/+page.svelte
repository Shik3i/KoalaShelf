<script lang="ts">
	import { byId, unique } from '#lib/unique.ts';
	import EmptyState from '#lib/components/EmptyState.svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { untrack } from 'svelte';
	import { SvelteSet } from 'svelte/reactivity';
	import type { FilterData, LibraryItemMinified } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { progress } from '#lib/progress.svelte.ts';
	import { prefs } from '#lib/prefs.svelte.ts';
	import { toasts } from '#lib/toast.svelte.ts';
	import { cached } from '#lib/cache.ts';
	import { onVisible } from '#lib/attach.ts';
	import { view } from '#lib/items.ts';
	import { duration } from '#lib/format.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import {
		activeCount,
		LENGTHS,
		parseQuery,
		plan,
		runSet,
		type Length,
		type SortId
	} from '#lib/library-query.ts';
	import BookTile from '#lib/components/BookTile.svelte';
	import Cover from '#lib/components/Cover.svelte';
	import FacetPicker from '#lib/components/FacetPicker.svelte';
	import Segmented from '#lib/components/Segmented.svelte';
	import LayoutGrid from '@lucide/svelte/icons/layout-grid';
	import Rows3 from '@lucide/svelte/icons/rows-3';
	import ArrowDownUp from '@lucide/svelte/icons/arrow-down-up';
	import SlidersHorizontal from '@lucide/svelte/icons/sliders-horizontal';
	import Shuffle from '@lucide/svelte/icons/shuffle';
	import SearchIcon from '@lucide/svelte/icons/search';
	import X from '@lucide/svelte/icons/x';
	import Check from '@lucide/svelte/icons/check';
	import Bookmark from '@lucide/svelte/icons/bookmark-plus';
	import Disc from '@lucide/svelte/icons/disc-3';
	import SquareCheck from '@lucide/svelte/icons/square-check';
	import SelectionBar from '#lib/components/SelectionBar.svelte';
	import CrateDiggerModal from '#lib/components/CrateDiggerModal.svelte';

	const PAGE = 60;
	const t = $derived(i18n.m.library);
	let diggerOpen = $state(false);

	// All view state lives in the URL: shareable, bookmarkable, back button works.
	const params = $derived(page.url.searchParams);
	const query = $derived(parseQuery(params));
	const layout = $derived(params.get('view') === 'list' ? 'list' : 'shelf');
	const how = $derived(plan(query));

	const STATUS = $derived([
		{ id: '', label: t.all },
		{ id: 'in-progress', label: t.inProgress },
		{ id: 'not-started', label: t.notStarted },
		{ id: 'finished', label: t.finished }
	] as const);
	const SORT_LABELS = $derived<Record<SortId, string>>({
		title: t.sortTitle,
		author: t.sortAuthor,
		added: t.sortAdded,
		played: t.sortProgress,
		started: t.sortStarted,
		finished: t.sortFinished,
		duration: t.sortDuration,
		published: t.sortPublished,
		random: t.sortRandom,
		sequence: t.sortSequence
	});
	const sortIds = $derived(
		(Object.keys(SORT_LABELS) as SortId[]).filter((s) => s !== 'sequence' || query.values.series)
	);

	// View, sort and grouping are remembered; filters are not (they are per visit).
	const REMEMBER = ['view', 'sort', 'desc', 'group'];
	const MEMORY_KEY = 'ks.libraryView';

	$effect(() => {
		const q = page.url.searchParams;
		const kept = REMEMBER.filter((k) => q.has(k)).map((k) => [k, q.get(k)!]);
		if (!page.url.search) {
			try {
				const saved = localStorage.getItem(MEMORY_KEY);
				if (saved) goto(`?${saved}`, { replace: true, reset: false });
			} catch {
				/* ignore */
			}
			return;
		}
		try {
			localStorage.setItem(MEMORY_KEY, new URLSearchParams(kept).toString());
		} catch {
			/* ignore */
		}
	});

	/** Change URL parameters (arrays become repeated parameters, empty removes). */
	function set(changes: Record<string, string | string[] | null>) {
		// eslint-disable-next-line svelte/prefer-svelte-reactivity -- local, not reactive state
		const next = new URLSearchParams(page.url.search);
		for (const [k, v] of Object.entries(changes)) {
			next.delete(k);
			for (const x of Array.isArray(v) ? v : v ? [v] : []) next.append(k, x);
		}
		goto(`?${next}`, { replace: true, reset: false });
	}

	/** Filter parameters only (for "clear all" and saved views). */
	const FILTER_KEYS = [
		'status',
		'genre',
		'tag',
		'narrator',
		'language',
		'author',
		'series',
		'publisher',
		'decade',
		'length',
		'q'
	];

	let fd = $state.raw<FilterData | null>(null);
	$effect(() => {
		const lib = session.libraryId;
		if (!lib) return;
		cached(`fd:${lib}`, () => session.api.filterData(lib), 300_000)
			.then((x) => (fd = x))
			.catch(() => (fd = null));
	});
	const authorName = (id: string) => fd?.authors.find((a) => a.id === id)?.name ?? '…';
	const seriesName = (id: string) => fd?.series.find((s) => s.id === id)?.name ?? '…';

	// --- Results -----------------------------------------------------------------

	let items = $state.raw<LibraryItemMinified[]>([]);
	let total = $state(0);
	let pageNo = 0;
	/** Combined queries: the whole result, revealed in pages while scrolling. */
	let shownCount = $state(PAGE);
	let loading = $state(false);
	/** True until the first result for the current query arrived (skeleton instead of "empty"). */
	let firstLoad = $state(true);
	let error = $state<string | null>(null);
	let done = $state(false);
	let controller: AbortController | null = null;

	const visible = $derived(how.mode === 'set' ? items.slice(0, shownCount) : items);

	// Multi-select: pick books, then act on all of them (selection bar).
	let selecting = $state(false);
	const selected = new SvelteSet<string>();
	const picked = $derived(items.filter((i) => selected.has(i.id)));
	const selectable = $derived(
		visible.filter(
			(i) => ((i as { collapsedSeries?: { numBooks: number } }).collapsedSeries?.numBooks ?? 0) < 2
		)
	);
	const allPicked = $derived(selectable.length > 0 && selectable.every((i) => selected.has(i.id)));

	function pick(id: string) {
		if (!selected.delete(id)) selected.add(id);
	}

	function pickAll() {
		if (allPicked) selected.clear();
		else for (const i of selectable) selected.add(i.id);
	}

	function endSelect() {
		selecting = false;
		selected.clear();
	}

	async function loadPage() {
		const lib = session.libraryId;
		if (loading || done || !lib || how.mode !== 'paged') return;
		loading = true;
		const signal = controller?.signal;
		try {
			const res = await session.api.libraryItems(
				lib,
				{
					page: pageNo,
					limit: PAGE,
					sort: how.sort,
					desc: how.desc,
					filter: how.filter,
					collapseSeries: how.group
				},
				signal
			);
			// Pages can overlap when books are added meanwhile: each book once.
			items = unique(pageNo === 0 ? res.results : [...items, ...res.results], byId);
			total = res.total;
			pageNo++;
			done = items.length >= res.total;
		} catch (e) {
			if ((e as Error).name !== 'AbortError') error = (e as Error).message;
		} finally {
			if (!signal?.aborted) {
				loading = false;
				firstLoad = false;
			}
		}
	}

	async function loadSet() {
		const lib = session.libraryId;
		if (!lib) return;
		loading = true;
		const signal = controller?.signal;
		try {
			const result = await runSet(session.api, lib, query, (id) => progress.status(id), signal);
			if (signal?.aborted) return;
			items = unique(result, byId);
			total = result.length;
			shownCount = PAGE;
			done = result.length <= PAGE;
		} catch (e) {
			if ((e as Error).name !== 'AbortError') error = (e as Error).message;
		} finally {
			if (!signal?.aborted) {
				loading = false;
				firstLoad = false;
			}
		}
	}

	function more() {
		if (how.mode === 'set') {
			shownCount += PAGE;
			done = shownCount >= items.length;
		} else void loadPage();
	}

	// A plain string, so switching the UI language does not reload the list.
	const queryKey = $derived(JSON.stringify([session.libraryId, query]));

	$effect(() => {
		void queryKey;
		untrack(() => {
			controller?.abort();
			controller = new AbortController();
			items = [];
			pageNo = 0;
			done = false;
			error = null;
			loading = false;
			firstLoad = true;
			progress.load().then(() => (how.mode === 'set' ? loadSet() : loadPage()));
		});
	});

	// --- Text filter (debounced into the URL) ---------------------------------------

	let text = $state(untrack(() => query.q));
	$effect(() => {
		// The URL changed elsewhere (clear all, saved view): follow it.
		const q = query.q;
		untrack(() => {
			if (text.trim() !== q) text = q;
		});
	});
	$effect(() => {
		const value = text.trim();
		const timer = setTimeout(() => {
			if (value !== untrack(() => query.q)) set({ q: value || null });
		}, 300);
		return () => clearTimeout(timer);
	});

	// --- Filter panel, active filters, saved views ------------------------------------

	// Starts closed: the active filters show as chips anyway.
	let panelOpen = $state(false);
	const filterCount = $derived(activeCount(query) - (query.status ? 1 : 0) - (query.q ? 1 : 0));

	const active = $derived.by(() => {
		const out: { key: string; label: string; remove: () => void }[] = [];
		const v = query.values;
		const multi = (k: 'genre' | 'tag' | 'narrator' | 'language', label: (x: string) => string) =>
			v[k].forEach((x) =>
				out.push({
					key: `${k}:${x}`,
					label: label(x),
					remove: () => set({ [k]: v[k].filter((y) => y !== x) })
				})
			);
		if (v.author)
			out.push({ key: 'author', label: authorName(v.author), remove: () => set({ author: null }) });
		if (v.series)
			out.push({ key: 'series', label: seriesName(v.series), remove: () => set({ series: null }) });
		multi('narrator', (x) => x);
		multi('genre', (x) => x);
		multi('tag', (x) => `#${x}`);
		multi('language', (x) => x);
		if (v.publisher)
			out.push({ key: 'publisher', label: v.publisher, remove: () => set({ publisher: null }) });
		if (v.decade)
			out.push({
				key: 'decade',
				label: t.decadeLabel(v.decade),
				remove: () => set({ decade: null })
			});
		if (query.length)
			out.push({
				key: 'length',
				label: t.lengths[query.length],
				remove: () => set({ length: null })
			});
		if (query.q) out.push({ key: 'q', label: `“${query.q}”`, remove: () => set({ q: null }) });
		return out;
	});

	function clearAll() {
		set(Object.fromEntries(FILTER_KEYS.map((k) => [k, null])));
	}

	/** The current filter (and sort) parameters, as stored in a saved view. */
	const viewQuery = $derived.by(() => {
		// eslint-disable-next-line svelte/prefer-svelte-reactivity -- local, not reactive state
		const p = new URLSearchParams(params.toString());
		p.delete('view');
		p.delete('shuffle');
		p.sort();
		return p.toString();
	});
	let naming = $state(false);
	let viewName = $state('');

	function saveView(e: SubmitEvent) {
		e.preventDefault();
		const name = viewName.trim();
		if (!name) return;
		prefs.set('savedViews', [
			...prefs.savedViews,
			{ id: crypto.randomUUID(), name, query: viewQuery }
		]);
		naming = false;
		viewName = '';
		toasts.show(t.viewSaved(name));
	}

	function openView(query: string) {
		// eslint-disable-next-line svelte/prefer-svelte-reactivity -- local, not reactive state
		const next = new URLSearchParams(query);
		if (params.get('view')) next.set('view', params.get('view')!);
		goto(`?${next}`, { replace: true, reset: false });
	}

	const lengthIds = Object.keys(LENGTHS) as Length[];
</script>

<svelte:head><title>{t.title} · KoalaShelf</title></svelte:head>

<header>
	<div>
		<span class="label">{session.library?.name ?? t.title}</span>
		<h1>{t.title}</h1>
	</div>
	<span class="muted count tabular" aria-live="polite">{t.count(total)}</span>
</header>

{#if prefs.savedViews.length}
	<div class="chips views" role="group" aria-label={t.views}>
		{#each prefs.savedViews as v (v.id)}
			<span class="saved">
				<button class="chip" aria-pressed={v.query === viewQuery} onclick={() => openView(v.query)}
					>{v.name}</button
				>
				<button
					class="drop"
					aria-label={t.deleteView(v.name)}
					title={t.deleteView(v.name)}
					onclick={() =>
						prefs.set(
							'savedViews',
							prefs.savedViews.filter((x) => x.id !== v.id)
						)}><X size={12} /></button
				>
			</span>
		{/each}
	</div>
{/if}

<div class="toolbar">
	<div class="chips" role="group" aria-label={t.statusGroup}>
		{#each STATUS as s (s.id)}
			<button
				class="chip"
				aria-pressed={query.status === s.id}
				onclick={() => set({ status: s.id || null })}>{s.label}</button
			>
		{/each}
	</div>

	<div class="chips">
		<button
			class="chip outline"
			class:on={filterCount > 0}
			aria-expanded={panelOpen}
			aria-controls="filter-panel"
			onclick={() => (panelOpen = !panelOpen)}
		>
			<SlidersHorizontal size={14} aria-hidden="true" />
			{filterCount ? t.filtersActive(filterCount) : t.filters}
		</button>
	</div>

	<div class="chips end">
		<button
			class="chip outline"
			aria-pressed={selecting}
			onclick={() => (selecting ? endSelect() : (selecting = true))}
		>
			<SquareCheck size={14} aria-hidden="true" />
			{t.select}
		</button>
		{#if !query.values.series}
			<button
				class="chip outline"
				aria-pressed={query.group && how.mode === 'paged'}
				disabled={how.mode === 'set'}
				title={how.mode === 'set' ? t.groupNeedsSimple : undefined}
				onclick={() => set({ group: query.group ? null : '1' })}>{t.collapse}</button
			>
		{/if}
		<label class="chip outline select">
			<ArrowDownUp size={14} aria-hidden="true" />
			<span class="sr-only">{t.sort}</span>
			<select value={query.sort} onchange={(e) => set({ sort: e.currentTarget.value, desc: null })}>
				{#each sortIds as s (s)}<option value={s}>{SORT_LABELS[s]}</option>{/each}
			</select>
		</label>
		{#if query.sort === 'random'}
			<button
				class="chip outline"
				onclick={() => set({ shuffle: String(Date.now()) })}
				aria-label={t.shuffle}
				title={t.shuffle}><Shuffle size={14} /></button
			>
		{:else}
			<button
				class="chip outline"
				onclick={() => set({ desc: query.desc ? '0' : '1' })}
				aria-label={t.reverse}
			>
				{query.desc ? '↓' : '↑'}
			</button>
		{/if}
		<button
			class="chip outline"
			onclick={() => (diggerOpen = true)}
			aria-label={i18n.m.crateDigger.button}
			title={i18n.m.crateDigger.subtitle}
		>
			<Disc size={14} />
			<span>{i18n.m.crateDigger.button}</span>
		</button>
		<Segmented
			label={t.view}
			value={layout}
			options={[
				{ value: 'shelf', label: t.viewShelf, icon: LayoutGrid },
				{ value: 'list', label: t.viewList, icon: Rows3 }
			]}
			onchange={(v) => set({ view: v === 'list' ? 'list' : null })}
		/>
	</div>
</div>

<label class="find">
	<SearchIcon size={16} aria-hidden="true" />
	<span class="sr-only">{t.textFilterLabel}</span>
	<input type="search" bind:value={text} placeholder={t.textFilter} enterkeyhint="search" />
</label>

{#if panelOpen && fd}
	<section id="filter-panel" class="panel" aria-label={t.filters}>
		{#if fd.authors.length}
			<FacetPicker
				label={t.author}
				options={fd.authors.map((a) => ({ value: a.id, label: a.name }))}
				selected={query.values.author ? [query.values.author] : []}
				searchLabel={t.findIn}
				showAllLabel={t.showAll}
				onchange={(v) => set({ author: v[0] ?? null })}
			/>
		{/if}
		{#if fd.series.length}
			<FacetPicker
				label={t.series}
				options={fd.series.map((s) => ({ value: s.id, label: s.name }))}
				selected={query.values.series ? [query.values.series] : []}
				searchLabel={t.findIn}
				showAllLabel={t.showAll}
				onchange={(v) => set({ series: v[0] ?? null, sort: null })}
			/>
		{/if}
		{#if fd.narrators.length}
			<FacetPicker
				multi
				label={t.narrator}
				options={fd.narrators.map((n) => ({ value: n, label: n }))}
				selected={query.values.narrator}
				searchLabel={t.findIn}
				showAllLabel={t.showAll}
				onchange={(v) => set({ narrator: v })}
			/>
		{/if}
		{#if fd.genres.length}
			<FacetPicker
				multi
				label={t.genre}
				options={fd.genres.map((g) => ({ value: g, label: g }))}
				selected={query.values.genre}
				searchLabel={t.findIn}
				showAllLabel={t.showAll}
				onchange={(v) => set({ genre: v })}
			/>
		{/if}
		{#if fd.tags.length}
			<FacetPicker
				multi
				label={t.tag}
				options={fd.tags.map((g) => ({ value: g, label: `#${g}` }))}
				selected={query.values.tag}
				searchLabel={t.findIn}
				showAllLabel={t.showAll}
				onchange={(v) => set({ tag: v })}
			/>
		{/if}
		{#if fd.languages.length}
			<FacetPicker
				multi
				label={t.language}
				options={fd.languages.map((g) => ({ value: g, label: g }))}
				selected={query.values.language}
				searchLabel={t.findIn}
				showAllLabel={t.showAll}
				onchange={(v) => set({ language: v })}
			/>
		{/if}
		{#if fd.publishers?.length}
			<FacetPicker
				label={t.publisher}
				options={fd.publishers.map((g) => ({ value: g, label: g }))}
				selected={query.values.publisher ? [query.values.publisher] : []}
				searchLabel={t.findIn}
				showAllLabel={t.showAll}
				onchange={(v) => set({ publisher: v[0] ?? null })}
			/>
		{/if}
		{#if fd.publishedDecades?.length}
			<FacetPicker
				label={t.decade}
				options={fd.publishedDecades.map((d) => ({ value: d, label: t.decadeLabel(d) }))}
				selected={query.values.decade ? [query.values.decade] : []}
				searchLabel={t.findIn}
				showAllLabel={t.showAll}
				onchange={(v) => set({ decade: v[0] ?? null })}
			/>
		{/if}
		<FacetPicker
			label={t.length}
			options={lengthIds.map((l) => ({ value: l, label: t.lengths[l] }))}
			selected={query.length ? [query.length] : []}
			searchLabel={t.findIn}
			showAllLabel={t.showAll}
			onchange={(v) => set({ length: v[0] ?? null })}
		/>
	</section>
{/if}

{#if active.length}
	<div class="chips active" role="group" aria-label={t.filters}>
		{#each active as a (a.key)}
			<button class="chip on" onclick={a.remove}>{a.label} ✕</button>
		{/each}
		<button class="chip outline" onclick={clearAll}>{t.clearAll}</button>
		{#if naming}
			<form class="name" onsubmit={saveView}>
				<!-- svelte-ignore a11y_autofocus -->
				<input
					bind:value={viewName}
					placeholder={t.viewName}
					aria-label={t.viewName}
					maxlength="40"
					autofocus
				/>
				<button class="chip" type="submit">{t.save}</button>
				<button class="chip outline" type="button" onclick={() => (naming = false)}
					>{t.cancel}</button
				>
			</form>
		{:else}
			<button class="chip outline" onclick={() => (naming = true)}>
				<Bookmark size={14} aria-hidden="true" />
				{t.saveView}
			</button>
		{/if}
	</div>
{/if}

{#if error}
	<p class="error" role="alert">{t.loadError(error)}</p>
{:else if !firstLoad && !loading && items.length === 0}
	<EmptyState text={t.empty} />
{/if}

{#if firstLoad && items.length === 0 && !error}
	<div class="shelf" aria-busy="true">
		{#each Array.from({ length: 12 }, (_, i) => i) as i (i)}
			<div class="skel-tile"><div class="skeleton"></div></div>
		{/each}
	</div>
{:else if layout === 'shelf'}
	<div class="shelf">
		{#each visible as item, i (item.id)}
			<BookTile
				{item}
				eager={i < 12}
				index={i % PAGE}
				{selecting}
				selected={selected.has(item.id)}
				onselect={pick}
			/>
		{/each}
	</div>
{:else}
	<ul class="list">
		{#each visible as item (item.id)}
			{@const v = view(item)}
			{@const p = progress.get(item.id)}
			{#snippet row()}
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
			{/snippet}
			<li>
				{#if selecting}
					<button class="row" aria-pressed={selected.has(item.id)} onclick={() => pick(item.id)}>
						<span class="tick" aria-hidden="true"
							>{#if selected.has(item.id)}<Check size={14} strokeWidth={3} />{/if}</span
						>
						{@render row()}
					</button>
				{:else}
					<a class="row" href="/book/{item.id}">{@render row()}</a>
				{/if}
			</li>
		{/each}
	</ul>
{/if}

{#if !done && !error && !firstLoad}
	<div class="more" {@attach onVisible(more)}>{loading ? t.loading : ''}</div>
{/if}

{#if selecting}
	<SelectionBar items={picked} allSelected={allPicked} onall={pickAll} onclear={endSelect} />
{/if}

<svelte:window onkeydown={(e) => selecting && e.key === 'Escape' && endSelect()} />

<CrateDiggerModal bind:open={diggerOpen} />

<style>
	/* Saved views: chip plus a small delete button. */
	.views {
		margin-bottom: var(--sp-16);
	}

	.saved {
		display: inline-flex;
		align-items: center;
	}

	.drop {
		position: relative;
		display: grid;
		place-items: center;
		width: 24px;
		height: 24px;
		margin-left: -4px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: var(--c-muted);
	}

	.drop::after {
		content: '';
		position: absolute;
		inset: -10px -6px;
	}

	.drop:hover {
		color: var(--c-ink);
		background: var(--c-card);
	}

	.find {
		display: flex;
		align-items: center;
		gap: var(--sp-8);
		max-width: 520px;
		min-height: var(--layout-touch);
		margin: calc(-1 * var(--sp-16)) 0 var(--sp-20);
		padding: 0 var(--sp-16);
		border: 1.5px solid var(--c-field);
		border-radius: var(--r-pill);
		background: var(--c-card);
		color: var(--c-muted);
	}

	.find:focus-within {
		border-color: var(--c-accent);
	}

	.find input {
		flex: 1;
		min-width: 0;
		border: 0;
		background: transparent;
		color: var(--c-ink);
		font: inherit;
	}

	.find input:focus-visible {
		outline: none;
	}

	.panel {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(min(100%, 280px), 1fr));
		gap: var(--sp-24) var(--sp-32);
		margin-bottom: var(--sp-24);
		padding: var(--sp-20) var(--sp-24);
		border-radius: var(--r-card-lg);
		background: var(--c-card);
		animation: rise var(--motion-base) var(--motion-out);
	}

	.panel :global(.chip:not([aria-pressed='true'])) {
		background: var(--c-bg);
	}

	.name {
		display: inline-flex;
		align-items: center;
		gap: var(--sp-8);
	}

	.name input {
		min-height: 36px;
		padding: 0 var(--sp-12);
		border: 1.5px solid var(--c-accent);
		border-radius: var(--r-pill);
		background: var(--c-card);
		color: var(--c-ink);
		font: inherit;
		font-size: var(--fs-14);
	}

	.chip:disabled {
		opacity: 0.45;
	}

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

	/* The native option list must stay readable when the chip is highlighted. */
	.select option {
		color: var(--c-ink);
		background: var(--c-card);
	}

	.select select:focus-visible {
		outline: none;
	}

	.select:focus-within {
		outline: 2px solid var(--c-accent);
		outline-offset: 2px;
	}

	.shelf {
		display: grid;
		grid-template-columns: repeat(
			auto-fill,
			minmax(min(calc(150px * var(--tile-scale)), 42%), 1fr)
		);
		row-gap: var(--sp-32);
		margin: 0 calc(-1 * var(--sp-12));
	}

	.skel-tile {
		padding: 0 var(--sp-12) var(--sp-48);
	}

	.skel-tile .skeleton {
		aspect-ratio: 1;
	}

	.list {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--sp-4);
	}

	.list .row {
		position: relative;
		width: 100%;
		display: grid;
		grid-template-columns: 56px 1fr auto;
		align-items: center;
		gap: var(--sp-16);
		padding: var(--sp-8);
		border-radius: var(--r-card);
		transition: background var(--motion-fast) var(--motion-ease);
		overflow: hidden;
	}

	.list .row:hover {
		background: var(--c-card);
	}

	/* Selection mode: rows are toggle buttons with a check in front. */
	button.row {
		grid-template-columns: 28px 56px 1fr auto;
		border: 0;
		background: none;
		color: inherit;
		font: inherit;
		text-align: left;
	}

	button.row[aria-pressed='true'] {
		background: color-mix(in oklab, var(--c-accent) 14%, var(--c-card));
	}

	.tick {
		display: grid;
		place-items: center;
		width: 24px;
		height: 24px;
		border: 2px solid var(--c-field);
		border-radius: 50%;
		color: var(--c-on-accent);
	}

	[aria-pressed='true'] .tick {
		border-color: var(--c-accent);
		background: var(--c-accent);
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

	@container page (max-width: 700px) {
		.shelf {
			grid-template-columns: repeat(
				auto-fill,
				minmax(min(calc(130px * var(--tile-scale)), 42%), 1fr)
			);
		}

		/* Phones: each control group is one swipeable row instead of wrapping into many. */
		.toolbar {
			flex-direction: column;
			flex-wrap: nowrap;
			gap: var(--sp-8);
			margin-bottom: var(--sp-24);
		}

		.chips {
			flex-wrap: nowrap;
			overflow-x: auto;
			scrollbar-width: none;
			margin: 0 calc(-1 * var(--sp-16));
			padding: 4px var(--sp-16);
		}

		.chips > * {
			flex: none;
		}

		.chips.end {
			margin-left: calc(-1 * var(--sp-16));
		}

		.dur {
			display: none;
		}

		.list .row {
			grid-template-columns: 56px 1fr;
		}

		.list button.row {
			grid-template-columns: 28px 56px 1fr;
		}
	}
</style>
