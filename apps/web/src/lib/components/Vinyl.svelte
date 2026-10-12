<script lang="ts">
	import Cover from './Cover.svelte';

	interface Props {
		title: string;
		author?: string;
		src?: ((width: number) => string) | null;
		/** Spins while true; pausing keeps the current angle. */
		playing?: boolean;
		/** Chapter starts as fractions 0..1, drawn as ticks on the rim. */
		marks?: number[];
		/** Label size relative to the disc. */
		label?: number;
	}

	let {
		title,
		author = '',
		src = null,
		playing = false,
		marks = [],
		label = 0.4
	}: Props = $props();

	const ticks = $derived.by(() => {
		if (!marks.length) return 'none';
		const stops = marks.map((f) => {
			const d = f * 360;
			return `transparent ${d}deg, var(--tick) ${d}deg ${d + 0.9}deg, transparent ${d + 0.9}deg`;
		});
		return `conic-gradient(${stops.join(', ')}, transparent 360deg)`;
	});
</script>

<div class="disc" class:playing style:--ticks={ticks} style:--label="{label * 100}%">
	<div class="ticks" aria-hidden="true"></div>
	<div class="label">
		<!-- A single small image: load it right away (lazy loading stalls inside dialogs). -->
		<Cover {title} {author} {src} sizes="160px" titleTop eager />
	</div>
	<div class="spindle" aria-hidden="true"></div>
</div>

<style>
	.disc {
		--tick: rgb(255 255 255 / 0.55);
		position: relative;
		aspect-ratio: 1;
		border-radius: 50%;
		background:
			radial-gradient(circle at 32% 28%, rgb(255 255 255 / 0.1), transparent 45%),
			conic-gradient(
				from 30deg,
				transparent 0 40deg,
				rgb(255 255 255 / 0.06) 60deg,
				transparent 80deg 220deg,
				rgb(255 255 255 / 0.05) 240deg,
				transparent 260deg
			),
			repeating-radial-gradient(circle at 50% 50%, var(--c-vinyl-a) 0 2px, var(--c-vinyl-b) 2px 4px);
		box-shadow:
			0 10px 30px -12px rgb(0 0 0 / 0.55),
			inset 0 0 0 2px rgb(0 0 0 / 0.35);
		animation: spin var(--motion-spin) linear infinite;
		animation-play-state: paused;
		will-change: rotate;
	}

	.disc.playing {
		animation-play-state: running;
	}

	.ticks {
		position: absolute;
		inset: 0;
		border-radius: 50%;
		background: var(--ticks);
		mask: radial-gradient(circle, transparent 91%, #000 91.5%, #000 97%, transparent 97.5%);
	}

	.label {
		position: absolute;
		inset: calc((100% - var(--label)) / 2);
		border-radius: 50%;
		overflow: hidden;
		box-shadow: 0 0 0 3px rgb(0 0 0 / 0.35);
	}

	.label :global(.cover) {
		border-radius: 0;
		box-shadow: none;
	}

	.spindle {
		position: absolute;
		inset: calc(50% - 2.5%);
		border-radius: 50%;
		background: var(--c-bg);
		box-shadow: inset 0 1px 2px rgb(0 0 0 / 0.4);
	}

	@media (prefers-reduced-motion: reduce) {
		.disc {
			animation: none;
		}
	}
</style>
