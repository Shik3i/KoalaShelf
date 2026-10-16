<script lang="ts">
	import { byId, unique } from '#lib/unique.ts';
	import type { AuthorDetail } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { htmlToText } from '#lib/format.ts';
	import BookTile from '#lib/components/BookTile.svelte';
	import Portrait from '#lib/components/Portrait.svelte';
	import EmptyState from '#lib/components/EmptyState.svelte';
	import LibraryBig from '@lucide/svelte/icons/library-big';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	const t = $derived(i18n.m.author);
	const author = $derived(data.author);
	const bio = $derived(htmlToText(author?.description));
	let bioOpen = $state(false);

	type SeriesItem = AuthorDetail['series'][number]['items'][number];
	/** Volume number of a book within its series (for ordering); unnumbered ones go last. */
	const volume = (item: SeriesItem) =>
		parseFloat(
			item.media.metadata.series?.sequence ??
				item.media.metadata.seriesName.match(/#([\d.]+)$/)?.[1] ??
				''
		) || Infinity;

	const series = $derived(
		(author?.series ?? []).map((s) => ({
			...s,
			items: [...s.items].sort((a, b) => volume(a) - volume(b))
		}))
	);
	const inSeries = $derived(new Set(series.flatMap((s) => s.items.map((i) => i.id))));
	const others = $derived(
		(author?.libraryItems ?? [])
			.filter((i) => !inSeries.has(i.id))
			.sort((a, b) => a.media.metadata.title.localeCompare(b.media.metadata.title, i18n.tag))
	);
</script>

<svelte:head><title>{author?.name ?? t.notFound} · KoalaShelf</title></svelte:head>

{#if !author}
	<EmptyState text={t.notFound} />
{:else}
	<header class="hero">
		<div class="pic">
			<Portrait
				name={author.name}
				src={author.imagePath
					? session.api.authorImageUrl(author.id, { width: 400, updatedAt: author.updatedAt })
					: null}
			/>
		</div>
		<div class="info">
			<span class="label">{t.label}</span>
			<h1>{author.name}</h1>
			<p class="muted">{i18n.m.authors.books(author.libraryItems.length)}</p>
			{#if bio}
				<p class="bio" class:open={bioOpen}>{bio}</p>
				{#if bio.length > 320}
					<button class="more" onclick={() => (bioOpen = !bioOpen)}>
						{bioOpen ? i18n.m.book.showLess : i18n.m.book.showMore}
					</button>
				{/if}
			{/if}
			<a class="btn ghost" href="/library?author={author.id}">
				<LibraryBig size={16} aria-hidden="true" />
				{t.inLibrary}
			</a>
		</div>
	</header>

	{#each unique(series, byId) as s (s.id)}
		<section>
			<h2><a href="/series/{s.id}">{s.name}</a></h2>
			<div class="shelf">
				{#each unique(s.items, byId) as item, i (item.id)}<BookTile {item} index={i} />{/each}
			</div>
		</section>
	{/each}

	{#if others.length}
		<section>
			<h2>{series.length ? t.otherBooks : t.books}</h2>
			<div class="shelf">
				{#each unique(others, byId) as item, i (item.id)}<BookTile {item} index={i} />{/each}
			</div>
		</section>
	{/if}
{/if}

<style>
	.hero {
		display: grid;
		grid-template-columns: minmax(0, 200px) 1fr;
		gap: var(--sp-40);
		align-items: center;
		margin-bottom: var(--sp-48);
	}

	.info {
		display: grid;
		gap: var(--sp-8);
		justify-items: start;
	}

	h1 {
		font-size: var(--fs-56);
	}

	.bio {
		max-width: 68ch;
		margin: var(--sp-8) 0 0;
		line-height: 1.6;
		display: -webkit-box;
		-webkit-line-clamp: 5;
		line-clamp: 5;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}

	.bio.open {
		display: block;
	}

	.more {
		padding: 0;
		border: 0;
		background: none;
		color: var(--c-accent);
		font-weight: var(--fw-bold);
	}

	.btn {
		margin-top: var(--sp-8);
	}

	section {
		margin-bottom: var(--sp-40);
	}

	h2 {
		font-size: var(--fs-24);
		margin-bottom: var(--sp-16);
	}

	h2 a:hover {
		text-decoration: underline;
		text-underline-offset: 4px;
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

	@container page (max-width: 700px) {
		.hero {
			grid-template-columns: 1fr;
			justify-items: center;
			text-align: center;
			gap: var(--sp-20);
		}

		.pic {
			width: min(200px, 60%);
		}

		.info {
			justify-items: center;
		}

		h1 {
			font-size: var(--fs-34);
		}
	}
</style>
