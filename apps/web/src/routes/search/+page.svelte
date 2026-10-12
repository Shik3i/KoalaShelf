<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import type { AbsClient } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { coverSrc, view } from '#lib/items.ts';
	import Cover from '#lib/components/Cover.svelte';
	import SearchIcon from '@lucide/svelte/icons/search';

	type Result = Awaited<ReturnType<AbsClient['search']>>;

	const t = $derived(i18n.m.search);
	let q = $state(page.url.searchParams.get('q') ?? '');
	let result = $state.raw<Result | null>(null);
	let busy = $state(false);

	// Debounced search; the query lives in the URL so back/forward and reload keep it.
	$effect(() => {
		const query = q.trim();
		const lib = session.libraryId;
		if (!lib || query.length < 2) {
			result = null;
			return;
		}
		const ctrl = new AbortController();
		const timer = setTimeout(async () => {
			busy = true;
			goto(`?q=${encodeURIComponent(query)}`, { replace: true, reset: false });
			try {
				result = await session.api.search(lib, query, 24, ctrl.signal);
			} catch {
				/* aborted or failed: keep previous result */
			} finally {
				busy = false;
			}
		}, 250);
		return () => {
			clearTimeout(timer);
			ctrl.abort();
		};
	});

	const empty = $derived(
		!!result &&
			!result.book.length &&
			!result.series.length &&
			!result.authors.length &&
			!result.narrators.length
	);
</script>

<svelte:head><title>{t.title} · KoalaShelf</title></svelte:head>

<label class="box">
	<SearchIcon size={20} aria-hidden="true" />
	<span class="sr-only">{t.title}</span>
	<!-- svelte-ignore a11y_autofocus -->
	<input bind:value={q} type="search" placeholder={t.placeholder} autofocus enterkeyhint="search" />
</label>

{#if !result && !busy}
	<p class="muted hint">{t.hint}</p>
{:else if empty}
	<p class="muted hint">{t.nothing(q.trim())}</p>
{/if}

{#if result}
	{#if result.book.length}
		<h2 class="label">{t.books}</h2>
		<div class="books">
			{#each result.book as { libraryItem: item } (item.id)}
				{@const v = view(item)}
				<a href="/book/{item.id}" class="book">
					<div class="c">
						<Cover title={v.title} author={v.author} src={coverSrc(item)} sizes="72px" />
					</div>
					<span class="meta">
						<strong>{v.title}</strong>
						<span class="muted">{v.author}</span>
					</span>
				</a>
			{/each}
		</div>
	{/if}

	{#if result.series.length}
		<h2 class="label">{t.series}</h2>
		<div class="chips">
			{#each result.series as s (s.series.id)}
				<a class="chip" href="/library?series={s.series.id}">{s.series.name}</a>
			{/each}
		</div>
	{/if}

	{#if result.authors.length}
		<h2 class="label">{t.authors}</h2>
		<div class="chips">
			{#each result.authors as a (a.id)}
				<a class="chip" href="/library?author={a.id}">{a.name}</a>
			{/each}
		</div>
	{/if}

	{#if result.narrators.length}
		<h2 class="label">{t.narrators}</h2>
		<div class="chips">
			{#each result.narrators as n (n.name)}
				<a class="chip" href="/library?narrator={encodeURIComponent(n.name)}">{n.name}</a>
			{/each}
		</div>
	{/if}
{/if}

<style>
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

	.box:focus-within {
		box-shadow: 0 0 0 2px var(--c-accent);
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

	.hint {
		text-align: center;
		padding: var(--sp-48) 0;
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
