<script lang="ts">
	import { byId, unique } from '#lib/unique.ts';
	import type { LibraryItem, LibraryItemMinified } from '@koalashelf/abs-client';
	import { view } from '#lib/items.ts';
	import Cover from './Cover.svelte';
	import ListMusic from '@lucide/svelte/icons/list-music';

	/** Up to four covers in a square: one fills it, more make a 2 × 2 grid. */
	let { items, sizes = '200px' }: { items: (LibraryItem | LibraryItemMinified)[]; sizes?: string } =
		$props();
	const shown = $derived(items.length >= 4 ? items.slice(0, 4) : items.slice(0, 1));
</script>

<div class="mosaic" class:grid={shown.length === 4} aria-hidden="true">
	{#each unique(shown, byId) as item (item.id)}
		{@const v = view(item)}
		<Cover title={v.title} author={v.author} src={v.cover} {sizes} titleTop />
	{:else}
		<span class="none"><ListMusic size={40} /></span>
	{/each}
</div>

<style>
	.mosaic {
		display: grid;
		aspect-ratio: 1;
		border-radius: var(--r-card);
		overflow: hidden;
		background: var(--c-card);
		box-shadow: 0 14px 28px -18px rgb(0 0 0 / 0.55);
	}

	.mosaic.grid {
		grid-template-columns: 1fr 1fr;
	}

	.none {
		display: grid;
		place-items: center;
		color: var(--c-muted);
	}
</style>
