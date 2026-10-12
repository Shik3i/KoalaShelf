<script lang="ts">
	import { page } from '$app/state';
	import type { Collection } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { duration } from '#lib/format.ts';
	import BookTile from '#lib/components/BookTile.svelte';

	const t = $derived(i18n.m.collections);
	let c = $state.raw<Collection | null | undefined>(undefined);

	$effect(() => {
		const id = page.params.id;
		if (!id) return;
		c = undefined;
		session.api
			.collection(id)
			.then((r) => (c = r))
			.catch(() => (c = null));
	});

	const total = $derived(c ? c.books.reduce((s, b) => s + b.media.duration, 0) : 0);
</script>

<svelte:head><title>{c?.name ?? t.title} · KoalaShelf</title></svelte:head>

{#if c === null}
	<p class="muted">{t.notFound}</p>
{:else if c}
	<header>
		<a class="label" href="/collections">{t.title}</a>
		<h1>{c.name}</h1>
		<p class="muted">{t.books(c.books.length)} · {duration(total)}</p>
		{#if c.description}<p class="desc">{c.description}</p>{/if}
	</header>
	<div class="shelf">
		{#each c.books as b, i (b.id)}<BookTile item={b} eager={i < 12} />{/each}
	</div>
{/if}

<style>
	header {
		display: grid;
		gap: var(--sp-8);
		margin-bottom: var(--sp-32);
	}

	.desc {
		max-width: 640px;
	}

	.shelf {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
		row-gap: var(--sp-32);
		margin: 0 calc(-1 * var(--sp-12));
	}

	@media (max-width: 800px) {
		.shelf {
			grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
		}
	}
</style>
