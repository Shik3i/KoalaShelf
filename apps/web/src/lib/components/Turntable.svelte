<script lang="ts">
	import Vinyl from './Vinyl.svelte';

	interface Props {
		title: string;
		author?: string;
		src?: ((width: number) => string) | null;
		playing?: boolean;
		marks?: number[];
		/** Book progress 0..1, moves the tonearm from the rim towards the label. */
		progress?: number;
	}

	let { progress = 0, ...rest }: Props = $props();

	const angle = $derived(18 + Math.min(1, Math.max(0, progress)) * 22);
</script>

<div class="deck">
	<div class="platter">
		<Vinyl {...rest} />
	</div>
	<svg class="arm" viewBox="0 0 40 100" aria-hidden="true" style:rotate="{angle}deg">
		<circle cx="20" cy="10" r="9" class="base" />
		<path d="M20 10 L20 82" class="rod" />
		<rect x="15" y="80" width="10" height="14" rx="2" class="head" />
		<circle cx="20" cy="10" r="3.5" class="pin" />
	</svg>
</div>

<style>
	.deck {
		--arm-color: var(--c-ink);
		position: relative;
		aspect-ratio: 1;
		border-radius: var(--r-card-lg);
		background: var(--c-platter);
		container-type: inline-size;
	}

	.platter {
		position: absolute;
		left: 6%;
		top: 9%;
		width: 82%;
	}

	.arm {
		position: absolute;
		top: 1%;
		right: 0%;
		width: 16%;
		transform-origin: 50% 10%;
		transition: rotate 1.2s var(--motion-ease);
		filter: drop-shadow(0 4px 6px rgb(0 0 0 / 0.35));
		overflow: visible;
	}

	.base {
		fill: var(--c-ctrl);
	}

	.rod {
		stroke: var(--c-muted);
		stroke-width: 2.6;
		stroke-linecap: round;
	}

	.head {
		fill: var(--c-accent);
	}

	.pin {
		fill: var(--c-bg);
	}
</style>
