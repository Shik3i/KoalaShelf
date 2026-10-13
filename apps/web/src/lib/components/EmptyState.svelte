<script lang="ts">
	import type { Snippet } from 'svelte';

	let { text, children }: { text: string; children?: Snippet } = $props();
</script>

<!-- An empty crate with one record leaning in: shown whenever a view has nothing to list. -->
<div class="empty">
	<svg viewBox="0 0 160 120" aria-hidden="true">
		<circle cx="96" cy="52" r="34" class="disc" />
		<circle cx="96" cy="52" r="26" class="groove" />
		<circle cx="96" cy="52" r="18" class="groove" />
		<circle cx="96" cy="52" r="11" class="label" />
		<circle cx="96" cy="52" r="2" class="hole" />
		<path d="M24 66 H136 V104 a6 6 0 0 1 -6 6 H30 a6 6 0 0 1 -6 -6 Z" class="crate" />
		<path d="M24 66 H136" class="rim" />
	</svg>
	<p>{text}</p>
	{@render children?.()}
</div>

<style>
	.empty {
		display: grid;
		justify-items: center;
		gap: var(--sp-16);
		padding: var(--sp-48) var(--sp-16);
		text-align: center;
	}

	svg {
		width: 140px;
	}

	.disc {
		fill: var(--c-vinyl-a);
	}

	.groove {
		fill: none;
		stroke: var(--c-vinyl-b);
		stroke-width: 2;
	}

	.label {
		fill: var(--c-accent);
	}

	.hole {
		fill: var(--c-bg);
	}

	.crate {
		fill: var(--c-ctrl);
	}

	.rim {
		stroke: var(--c-ledge);
		stroke-width: 3;
		stroke-linecap: round;
	}

	p {
		max-width: 420px;
		color: var(--c-muted);
	}
</style>
