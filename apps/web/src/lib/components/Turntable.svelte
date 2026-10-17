<script lang="ts">
	import Vinyl from './Vinyl.svelte';
	import { placeRecord } from '#lib/motion.ts';

	interface Props {
		title: string;
		author?: string;
		src?: ((width: number) => string) | null;
		playing?: boolean;
		marks?: number[];
		speed?: number;
		gold?: boolean;
		shine?: number;
		/** Book progress 0..1, moves the tonearm from the rim towards the label. */
		progress?: number;
	}

	let { progress = 0, playing = false, ...rest }: Props = $props();

	/*
	 * Geometry in deck units (0..100). Disc: centre (47, 50), radius 41, label radius ~16.
	 * Pivot (93, 5), arm length 44. Rotating the arm clockwise from straight down puts the
	 * stylus at r ≈ 33 for 18° (outer grooves) and r ≈ 21 for 40° (just outside the label).
	 */
	const PIVOT = { x: 93, y: 5 };
	const LENGTH = 44;
	/** Resting on its post (just off the rim) while paused, swinging in when playback starts. */
	const REST = 4;
	const angle = $derived(playing ? 18 + Math.min(1, Math.max(0, progress)) * 22 : REST);
</script>

<div class="deck">
	<!-- Another book: its record is put on the platter. -->
	{#key rest.title}
		<div class="platter" in:placeRecord>
			<Vinyl {playing} {...rest} />
		</div>
	{/key}
	<svg class="arm" viewBox="0 0 100 100" aria-hidden="true">
		<circle cx={PIVOT.x} cy={PIVOT.y} r="4.6" class="base" />
		<g class="swing" style:rotate="{angle}deg" style:transform-origin="{PIVOT.x}px {PIVOT.y}px">
			<path d="M{PIVOT.x} {PIVOT.y} V{PIVOT.y + LENGTH - 4}" class="rod" />
			<rect
				x={PIVOT.x - 2.4}
				y={PIVOT.y + LENGTH - 6}
				width="4.8"
				height="7.5"
				rx="1"
				class="head"
			/>
		</g>
		<circle cx={PIVOT.x} cy={PIVOT.y} r="1.8" class="pin" />
	</svg>
</div>

<style>
	.deck {
		position: relative;
		aspect-ratio: 1;
		border-radius: var(--r-card-lg);
		background: var(--c-platter);
	}

	.platter {
		position: absolute;
		left: 6%;
		top: 9%;
		width: 82%;
	}

	.arm {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		overflow: visible;
		pointer-events: none;
		filter: drop-shadow(0 3px 4px rgb(0 0 0 / 0.35));
	}

	.swing {
		transform-box: view-box;
		transition: rotate 1.2s var(--motion-ease);
	}

	.base {
		fill: var(--c-ctrl);
		stroke: color-mix(in oklab, var(--c-ink) 18%, transparent);
		stroke-width: 0.5;
	}

	.rod {
		stroke: var(--c-muted);
		stroke-width: 1.3;
		stroke-linecap: round;
	}

	.head {
		fill: var(--c-accent);
	}

	.pin {
		fill: var(--c-bg);
	}
</style>
