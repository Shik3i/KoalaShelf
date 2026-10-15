<script lang="ts">
	import { highlight } from '#lib/search.ts';

	/**
	 * Text with the words of the query marked (no HTML is interpreted). The marks are visual
	 * only: screen readers get the plain text, so names stay whole words ("Roman", not "Rom an").
	 */
	let { text, query }: { text: string; query: string } = $props();
	const parts = $derived(highlight(text, query));
	const marked = $derived(parts.some((p) => p.hit));
</script>

{#if marked}<span aria-hidden="true"
		>{#each parts as p, i (i)}{#if p.hit}<mark>{p.text}</mark>{:else}{p.text}{/if}{/each}</span
	><span class="sr-only">{text}</span>{:else}{text}{/if}

<style>
	mark {
		border-radius: 3px;
		background: color-mix(in oklab, var(--c-accent) 26%, transparent);
		color: inherit;
		box-shadow: 0 0 0 1px color-mix(in oklab, var(--c-accent) 26%, transparent);
	}
</style>
