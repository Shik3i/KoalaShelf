<script lang="ts">
	import type { LibraryItemMinified } from '@koalashelf/abs-client';
	import { view } from '#lib/items.ts';
	import { progress } from '#lib/progress.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Cover from './Cover.svelte';

	let { item, eager = false }: { item: LibraryItemMinified; eager?: boolean } = $props();

	const v = $derived(view(item));
	const p = $derived(progress.get(item.id));
</script>

<a class="tile" href="/book/{item.id}">
	<div class="cover" style:view-transition-name="cover-{item.id}">
		<Cover title={v.title} author={v.author} src={v.cover} {eager} sizes="(max-width: 800px) 44vw, 180px" />
		{#if p && !p.isFinished && p.progress > 0}
			<span class="bar" style:--p={p.progress} aria-label={i18n.m.library.listened(Math.round(p.progress * 100))}></span>
		{:else if p?.isFinished}
			<span class="done label">{i18n.m.library.done}</span>
		{/if}
	</div>
	<div class="ledge" aria-hidden="true"></div>
	<span class="title">{v.title}</span>
	<span class="author muted">{v.author}</span>
</a>

<style>
	.tile {
		display: grid;
		align-content: start;
		padding: 0 var(--sp-12);
		min-width: 0;
	}

	.cover {
		position: relative;
		transition: translate var(--motion-base) var(--motion-ease);
	}

	.tile:hover .cover {
		translate: 0 -4px;
	}

	/* Continuous shelf: tiles have no column gap, so ledge segments join up. */
	.ledge {
		height: 10px;
		margin: 0 calc(-1 * var(--sp-12)) var(--sp-12);
		background: var(--c-ledge);
		box-shadow: 0 6px 10px -6px rgb(0 0 0 / 0.4);
	}

	.title {
		font-weight: var(--fw-semibold);
		font-size: var(--fs-14);
		line-height: 1.3;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.author {
		font-size: var(--fs-13);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.bar {
		position: absolute;
		left: 6%;
		right: 6%;
		bottom: 6%;
		height: 4px;
		border-radius: var(--r-pill);
		background: rgb(0 0 0 / 0.45);
		overflow: hidden;
	}

	.bar::before {
		content: '';
		position: absolute;
		inset: 0;
		width: calc(var(--p) * 100%);
		background: var(--c-accent);
	}

	.done {
		position: absolute;
		top: 6%;
		right: 6%;
		padding: 2px 8px;
		border-radius: var(--r-pill);
		background: var(--c-accent);
		color: var(--c-on-accent);
	}
</style>
