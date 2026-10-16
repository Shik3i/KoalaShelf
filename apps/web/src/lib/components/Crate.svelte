<script lang="ts" module>
	import { unique } from '#lib/unique.ts';
	export interface CrateEntry {
		id: string;
		href: string;
		title: string;
		author: string;
		cover: ((w: number) => string) | null;
	}
</script>

<script lang="ts">
	import Cover from './Cover.svelte';

	let {
		title,
		note = '',
		href,
		entries
	}: { title: string; note?: string; href?: string; entries: CrateEntry[] } = $props();
</script>

<!-- "Kiste": covers stand in a row, a dark ledge hides their lower part (titles sit on top). -->
<section class="crate" aria-label={title}>
	<div class="row">
		{#each unique(entries, (e) => e.id) as e (e.id)}
			<a href={e.href} class="slot" title="{e.title} · {e.author}">
				<Cover title={e.title} author={e.author} src={e.cover} sizes="170px" titleTop />
				<span class="sr-only">{e.title}</span>
			</a>
		{/each}
	</div>
	<svelte:element this={href ? 'a' : 'div'} class="ledge" class:link={href} {href}>
		<strong>{title}</strong>
		{#if note}<span>{note}</span>{/if}
	</svelte:element>
</section>

<style>
	/* As wide as its records (a crate with two records is a small crate), at most the row. */
	.crate {
		position: relative;
		width: fit-content;
		min-width: min(100%, 340px);
		max-width: 100%;
		border-radius: var(--r-card-lg);
		overflow: hidden;
	}

	.row {
		display: grid;
		grid-auto-flow: column;
		grid-auto-columns: 170px;
		gap: 0;
		overflow-x: auto;
		scroll-snap-type: x mandatory;
		scrollbar-width: none;
		padding-top: 12px;
	}

	.slot {
		scroll-snap-align: start;
		transition: translate var(--motion-base) var(--motion-ease);
	}

	.slot :global(.cover) {
		border-radius: 0;
		box-shadow: none;
	}

	/* Pull a record out of the crate. */
	.slot:hover,
	.slot:focus-visible {
		translate: 0 -10px;
	}

	.ledge {
		position: absolute;
		left: 0;
		right: 0;
		bottom: 0;
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: var(--sp-12);
		padding: var(--sp-12) var(--sp-16);
		background: var(--c-ctrl);
		color: var(--c-ctrl-ink);
		font-size: var(--fs-13);
		pointer-events: none;
	}

	.ledge.link {
		pointer-events: auto;
	}

	.ledge.link:hover strong {
		text-decoration: underline;
		text-underline-offset: 3px;
	}

	.ledge strong {
		font-weight: var(--fw-bold);
	}

	.ledge span {
		opacity: 0.75;
	}

	@media (max-width: 800px) {
		.row {
			grid-auto-columns: 132px;
		}
	}
</style>
