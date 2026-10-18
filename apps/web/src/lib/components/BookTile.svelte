<script lang="ts">
	import type { LibraryItem, LibraryItemMinified } from '@koalashelf/abs-client';
	import { view } from '#lib/items.ts';
	import { progress } from '#lib/progress.svelte.ts';
	import { player } from '#lib/player/player.svelte.ts';
	import { prefs } from '#lib/prefs.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Cover from './Cover.svelte';
	import Check from '@lucide/svelte/icons/check';

	let {
		item,
		eager = false,
		index = 0,
		selecting = false,
		selected = false,
		onselect
	}: {
		item: LibraryItem | LibraryItemMinified;
		eager?: boolean;
		index?: number;
		/** Multi-select: the tile toggles its selection instead of opening the book. */
		selecting?: boolean;
		selected?: boolean;
		onselect?: (id: string) => void;
	} = $props();

	const v = $derived(view(item));
	const p = $derived(progress.get(item.id));
	/** The book in the player keeps its record half out of the sleeve (turning while it plays). */
	const inPlayer = $derived(player.item?.id === item.id);
	// With "group series" the server returns one item per series; a one-book series stays a book.
	const group = $derived(
		'collapsedSeries' in item && (item.collapsedSeries?.numBooks ?? 0) > 1
			? item.collapsedSeries
			: undefined
	);
</script>

{#snippet body()}
	<div
		class="cover"
		class:in-player={inPlayer}
		class:spinning={inPlayer && player.playing && prefs.spin}
		style:view-transition-name={group || selecting ? undefined : `cover-${item.id}`}
	>
		{#if !group && !selecting}<span class="disc sleeve-disc" aria-hidden="true"></span>{/if}
		<Cover
			title={v.title}
			author={v.author}
			src={v.cover}
			{eager}
			sizes="(max-width: 800px) 44vw, 180px"
		/>
		{#if group}
			<span class="count label">{i18n.m.library.seriesBooks(group.numBooks)}</span>
		{:else if p && !p.isFinished && p.progress > 0}
			<span
				class="bar"
				style:--p={p.progress}
				aria-label={i18n.m.library.listened(Math.round(p.progress * 100))}
			></span>
		{:else if p?.isFinished}
			<span class="done label">{i18n.m.library.done}</span>
		{/if}
	</div>
	<div class="ledge" aria-hidden="true"></div>
	<span class="title">{group ? group.name : v.title}</span>
	<span class="author muted">{v.author}</span>
{/snippet}

{#if selecting && !group}
	<button
		class="tile pick rise"
		style:--i={index % 60}
		aria-pressed={selected}
		onclick={() => onselect?.(item.id)}
	>
		<span class="check" aria-hidden="true"
			>{#if selected}<Check size={16} strokeWidth={3} />{/if}</span
		>
		{@render body()}
	</button>
{:else}
	<a
		class="tile rise"
		style:--i={index % 60}
		class:group
		class:muted-group={selecting}
		href={group ? `/library?series=${group.id}` : `/book/${item.id}`}
	>
		{@render body()}
	</a>
{/if}

<style>
	.tile {
		/* Big libraries: off-screen tiles skip layout and paint until scrolled near. */
		content-visibility: auto;
		contain-intrinsic-size: auto 260px;
		display: grid;
		align-content: start;
		/* Headroom for the record peeking out of its sleeve. */
		padding: var(--sp-20) var(--sp-12) 0;
		min-width: 0;
	}

	/* Selection mode: the whole tile is a toggle button. */
	.pick {
		position: relative;
		border: 0;
		background: none;
		color: inherit;
		text-align: left;
		font: inherit;
		cursor: pointer;
	}

	.check {
		position: absolute;
		z-index: 1;
		top: 8px;
		left: calc(var(--sp-12) + 8px);
		display: grid;
		place-items: center;
		width: 28px;
		height: 28px;
		border: 2px solid #fff;
		border-radius: 50%;
		background: rgb(0 0 0 / 0.35);
		color: var(--c-on-accent);
		box-shadow: 0 2px 6px rgb(0 0 0 / 0.35);
	}

	.pick[aria-pressed='true'] .check {
		border-color: var(--c-accent);
		background: var(--c-accent);
	}

	.pick[aria-pressed='true'] .cover {
		outline: 3px solid var(--c-accent);
		outline-offset: 3px;
		border-radius: var(--r-cover);
	}

	.pick[aria-pressed='false'] .cover {
		opacity: 0.85;
	}

	.muted-group {
		opacity: 0.5;
	}

	.cover {
		position: relative;
		isolation: isolate;
		transition: translate var(--motion-base) var(--motion-ease);
	}

	.tile:hover .cover {
		translate: 0 -4px;
	}

	/* The record slides up a little on hover and stays out for the book in the player. */

	.tile:hover .disc,
	.tile:focus-visible .disc {
		translate: 0 -16px;
	}

	.in-player .disc,
	.tile:hover .in-player .disc {
		translate: 0 -20px;
	}

	.spinning .disc {
		animation: spin 1.8s linear infinite;
	}

	/* Continuous shelf: tiles have no column gap, so ledge segments join up. */
	.ledge {
		height: 10px;
		margin: 0 calc(-1 * var(--sp-12)) var(--sp-12);
		background: var(--c-ledge);
		box-shadow: 0 6px 10px -6px rgb(0 0 0 / 0.4);
	}

	/* A grouped series looks like a small stack of records. */
	.group .cover {
		box-shadow:
			6px -6px 0 -1px var(--c-ledge),
			12px -12px 0 -2px color-mix(in oklab, var(--c-ledge) 60%, transparent);
		border-radius: var(--r-cover);
		transition:
			translate var(--motion-base) var(--motion-ease),
			box-shadow 420ms var(--motion-spring);
	}

	/* Hovered, the stack fans out a little further: there is more than one book in it. */
	.group:hover .cover {
		box-shadow:
			9px -8px 0 -1px var(--c-ledge),
			18px -15px 0 -2px color-mix(in oklab, var(--c-ledge) 60%, transparent);
	}

	.count {
		position: absolute;
		top: 6%;
		right: 6%;
		padding: 2px 8px;
		border-radius: var(--r-pill);
		background: var(--c-ctrl);
		color: var(--c-ctrl-ink);
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
