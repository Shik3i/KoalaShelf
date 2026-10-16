<script lang="ts">
	import { byId, unique } from '#lib/unique.ts';
	import EmptyState from '#lib/components/EmptyState.svelte';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { session } from '#lib/session.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { coverSrc, view } from '#lib/items.ts';
	import {
		clearRecentSearches,
		isEmpty,
		recentSearches,
		rememberSearch,
		search,
		type Found
	} from '#lib/search.ts';
	import Cover from '#lib/components/Cover.svelte';
	import Highlight from '#lib/components/Highlight.svelte';
	import Segmented from '#lib/components/Segmented.svelte';
	import SearchIcon from '@lucide/svelte/icons/search';
	import History from '@lucide/svelte/icons/history';
	import X from '@lucide/svelte/icons/x';

	const t = $derived(i18n.m.search);

	let q = $state(page.url.searchParams.get('q') ?? '');
	let all = $state(page.url.searchParams.get('in') === 'all');
	let result = $state.raw<Found | null>(null);
	let busy = $state(false);
	let recent = $state(recentSearches());

	// Debounced search; query and scope live in the URL so back/forward and reload keep them.
	$effect(() => {
		const query = q.trim();
		const everywhere = all;
		if (!session.libraryId || query.length < 2) {
			result = null;
			return;
		}
		const ctrl = new AbortController();
		const timer = setTimeout(async () => {
			busy = true;
			goto(`?q=${encodeURIComponent(query)}${everywhere ? '&in=all' : ''}`, {
				replace: true,
				reset: false
			});
			try {
				result = await search(query, everywhere, 24, ctrl.signal);
				recent = rememberSearch(query);
			} catch {
				/* aborted or failed: keep the previous result */
			} finally {
				busy = false;
			}
		}, 250);
		return () => {
			clearTimeout(timer);
			ctrl.abort();
		};
	});

	const empty = $derived(!!result && isEmpty(result));
	const query = $derived(q.trim());
</script>

<svelte:head><title>{t.title} · KoalaShelf</title></svelte:head>

<!-- The search field is the visual heading; screen readers still get a page title. -->
<h1 class="sr-only">{t.title}</h1>

<label class="box">
	<SearchIcon size={20} aria-hidden="true" />
	<span class="sr-only">{t.title}</span>
	<!-- svelte-ignore a11y_autofocus -->
	<input bind:value={q} type="search" placeholder={t.placeholder} autofocus enterkeyhint="search" />
	{#if q}
		<button class="clear" type="button" onclick={() => (q = '')} aria-label={t.clear}>
			<X size={18} />
		</button>
	{/if}
</label>

{#if session.libraries.length > 1}
	<div class="scope">
		<Segmented
			size="sm"
			label={t.scope}
			value={all ? 'all' : 'one'}
			options={[
				{ value: 'one', label: session.library?.name ?? t.thisLibrary },
				{ value: 'all', label: t.allLibraries }
			]}
			onchange={(v) => (all = v === 'all')}
		/>
	</div>
{/if}

{#if !result && !busy}
	{#if recent.length && query.length < 2}
		<section class="recent" aria-labelledby="recent-title">
			<div class="head">
				<h2 class="label" id="recent-title">{t.recent}</h2>
				<button
					class="link-btn"
					onclick={() => {
						clearRecentSearches();
						recent = [];
					}}>{t.clearRecent}</button
				>
			</div>
			<div class="chips">
				{#each recent as r (r)}
					<button class="chip" onclick={() => (q = r)}
						><History size={14} aria-hidden="true" />{r}</button
					>
				{/each}
			</div>
		</section>
	{:else}
		<EmptyState text={t.hint} />
	{/if}
{:else if empty}
	<EmptyState text={t.nothing(query)} />
{/if}

{#if result}
	{#if result.book.length}
		<h2 class="label">{t.books}</h2>
		<div class="books">
			{#each unique(result.book, (b) => b.libraryItem.id) as { libraryItem: item } (item.id)}
				{@const v = view(item)}
				{@const lib = result.libraryOf.get(item.id)}
				<a href="/book/{item.id}" class="book">
					<div class="c">
						<Cover title={v.title} author={v.author} src={coverSrc(item)} sizes="72px" />
					</div>
					<span class="meta">
						<strong><Highlight text={v.title} {query} /></strong>
						<span class="muted"
							><Highlight text={v.author} {query} />{v.narrator
								? ' · '
								: ''}{#if v.narrator}<Highlight text={v.narrator} {query} />{/if}</span
						>
						{#if lib}<span class="lib">{lib}</span>{/if}
					</span>
				</a>
			{/each}
		</div>
	{/if}

	{#if result.series.length}
		<h2 class="label">{t.series}</h2>
		<div class="chips">
			{#each result.series as s (s.series.id)}
				<a class="chip" href="/series/{s.series.id}"
					><Highlight text={s.series.name} {query} />
					<span class="n">{s.books.length}</span></a
				>
			{/each}
		</div>
	{/if}

	{#if result.authors.length}
		<h2 class="label">{t.authors}</h2>
		<div class="chips">
			{#each unique(result.authors, byId) as a (a.id)}
				<a class="chip" href="/author/{a.id}"><Highlight text={a.name} {query} /></a>
			{/each}
		</div>
	{/if}

	{#if result.narrators.length}
		<h2 class="label">{t.narrators}</h2>
		<div class="chips">
			{#each unique(result.narrators, (n) => n.name) as n (n.name)}
				<a class="chip" href="/library?narrator={encodeURIComponent(n.name)}"
					><Highlight text={n.name} {query} /> <span class="n">{n.numBooks}</span></a
				>
			{/each}
		</div>
	{/if}

	{#if result.genres.length}
		<h2 class="label">{t.genres}</h2>
		<div class="chips">
			{#each unique(result.genres, (g) => g.name) as g (g.name)}
				<a class="chip" href="/library?genre={encodeURIComponent(g.name)}"
					><Highlight text={g.name} {query} /> <span class="n">{g.numItems}</span></a
				>
			{/each}
		</div>
	{/if}

	{#if result.tags.length}
		<h2 class="label">{t.tags}</h2>
		<div class="chips">
			{#each unique(result.tags, (g) => g.name) as g (g.name)}
				<a class="chip" href="/library?tag={encodeURIComponent(g.name)}"
					>#<Highlight text={g.name} {query} /> <span class="n">{g.numItems}</span></a
				>
			{/each}
		</div>
	{/if}
{/if}

<style>
	.scope {
		margin: calc(-1 * var(--sp-16)) 0 var(--sp-24);
	}

	.recent .head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--sp-12);
	}

	.link-btn {
		min-height: 36px;
		padding: 0 var(--sp-12);
		border: 0;
		border-radius: var(--r-pill);
		background: transparent;
		color: var(--c-muted);
		font-weight: var(--fw-bold);
		font-size: var(--fs-13);
	}

	.link-btn:hover {
		color: var(--c-ink);
		background: var(--c-card);
	}

	.chip .n {
		margin-left: 4px;
		color: var(--c-muted);
		font-size: var(--fs-12);
	}

	.lib {
		font-size: var(--fs-12);
		font-weight: var(--fw-bold);
		color: var(--c-accent);
	}

	.box {
		display: flex;
		align-items: center;
		gap: var(--sp-12);
		min-height: 56px;
		padding: 0 var(--sp-20);
		border-radius: var(--r-pill);
		background: var(--c-card);
		color: var(--c-muted);
		margin-bottom: var(--sp-32);
	}

	.box {
		box-shadow: inset 0 0 0 1.5px var(--c-field);
	}

	.box:focus-within {
		box-shadow: 0 0 0 2px var(--c-accent);
	}

	/* Own clear button instead of the browser's (blue, unstyleable) one. */
	input::-webkit-search-cancel-button {
		appearance: none;
		display: none;
	}

	.clear {
		display: grid;
		place-items: center;
		width: 36px;
		height: 36px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: var(--c-muted);
	}

	.clear:hover {
		color: var(--c-ink);
		background: var(--c-bg);
	}

	input {
		flex: 1;
		min-width: 0;
		border: 0;
		background: transparent;
		color: var(--c-ink);
		font-size: var(--fs-18);
		outline: none;
	}

	h2 {
		margin: var(--sp-24) 0 var(--sp-12);
	}

	.books {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
		gap: var(--sp-8);
	}

	.book {
		display: flex;
		align-items: center;
		gap: var(--sp-12);
		padding: var(--sp-8);
		border-radius: var(--r-card);
	}

	.book:hover {
		background: var(--c-card);
	}

	.c {
		width: 56px;
		flex: none;
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

	.meta .muted {
		font-size: var(--fs-13);
	}

	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: var(--sp-8);
	}
</style>
