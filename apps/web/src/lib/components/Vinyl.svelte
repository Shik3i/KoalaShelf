<script lang="ts">
	import Cover from './Cover.svelte';
	import { rampRate, reducedMotion } from '#lib/motion.ts';
	import { prefs } from '#lib/prefs.svelte.ts';

	interface Props {
		title: string;
		author?: string;
		src?: ((width: number) => string) | null;
		/** Spins while true: spins up on start, runs down on pause, keeps its angle. */
		playing?: boolean;
		/** Chapter starts as fractions 0..1, drawn as ticks on the rim. */
		marks?: number[];
		/** Label size relative to the disc. */
		label?: number;
		/** Playback speed: a faster book turns the record faster (the motor ramps to it). */
		speed?: number;
		/** A finished book: a gold record. */
		gold?: boolean;
		/** Changes when the book was just finished: the record shines once. */
		shine?: number;
	}

	let {
		title,
		author = '',
		src = null,
		playing = false,
		marks = [],
		label = 0.4,
		speed = 1,
		gold = false,
		shine = 0
	}: Props = $props();

	let shining = $state(false);
	$effect(() => {
		if (!shine || reducedMotion()) return;
		shining = true;
		const id = setTimeout(() => (shining = false), 1800);
		return () => clearTimeout(id);
	});

	const SPIN_MS = 9000;
	const RAMP_MS = 900;

	const ticks = $derived.by(() => {
		if (!marks.length) return 'none';
		const stops = marks.map((f) => {
			const d = f * 360;
			return `transparent ${d}deg, var(--tick) ${d}deg ${d + 0.9}deg, transparent ${d + 0.9}deg`;
		});
		return `conic-gradient(${stops.join(', ')}, transparent 360deg)`;
	});

	let spinner = $state<HTMLDivElement>();
	let anim: Animation | null = null;
	let frame = 0;

	// One endless rotation (Web Animations); speed is ramped like a real turntable motor.
	$effect(() => {
		if (!spinner || !prefs.spin || reducedMotion()) return;
		const a = spinner.animate([{ rotate: '0deg' }, { rotate: '360deg' }], {
			duration: SPIN_MS,
			iterations: Infinity
		});
		a.pause();
		anim = a;
		return () => {
			cancelAnimationFrame(frame);
			a.cancel();
			anim = null;
		};
	});

	$effect(() => {
		const target = playing ? Math.min(3, Math.max(0.5, speed)) : 0;
		void spinner;
		const a = anim;
		if (!a) return;
		cancelAnimationFrame(frame);
		const from = a.playState === 'paused' ? 0 : Math.max(0, a.playbackRate);
		if (target) {
			a.playbackRate = from;
			a.play();
		}
		// Timed from the first frame (a frame timestamp can precede a performance.now() taken here).
		let start: number | null = null;
		const step = (now: number) => {
			start ??= now;
			a.playbackRate = rampRate(from, target, now - start, RAMP_MS);
			if (now - start < RAMP_MS) frame = requestAnimationFrame(step);
			else if (!target) a.pause();
		};
		frame = requestAnimationFrame(step);
	});
</script>

<div
	class="disc"
	class:gold
	class:shine={shining}
	style:--ticks={ticks}
	style:--label="{label * 100}%"
>
	<div class="spinner" bind:this={spinner}>
		<div class="ticks" aria-hidden="true"></div>
		<div class="label">
			<!-- A single small image: load it right away (lazy loading stalls inside dialogs). -->
			<Cover {title} {author} {src} sizes="160px" titleTop eager />
		</div>
	</div>
	<!-- The light reflection stays put while the grooves turn underneath, like on a real record. -->
	<div class="sheen" aria-hidden="true"></div>
	<div class="spindle" aria-hidden="true"></div>
</div>

<style>
	.disc {
		--tick: rgb(255 255 255 / 0.55);
		position: relative;
		aspect-ratio: 1;
		border-radius: 50%;
		box-shadow:
			0 10px 30px -12px rgb(0 0 0 / 0.55),
			inset 0 0 0 2px rgb(0 0 0 / 0.35);
	}

	/* Gold record: warm metallic grooves (the label stays the cover). */
	.gold {
		--c-vinyl-a: #6e5216;
		--c-vinyl-b: #a8832c;
		--tick: rgb(255 244 210 / 0.7);
	}

	.spinner {
		transition: background 600ms var(--motion-ease);
		position: absolute;
		inset: 0;
		border-radius: 50%;
		background: repeating-radial-gradient(
			circle at 50% 50%,
			var(--c-vinyl-a) 0 2px,
			var(--c-vinyl-b) 2px 4px
		);
		will-change: rotate;
	}

	.sheen {
		position: absolute;
		inset: 0;
		border-radius: 50%;
		pointer-events: none;
		background:
			radial-gradient(circle at 32% 28%, rgb(255 255 255 / 0.1), transparent 45%),
			conic-gradient(
				from 30deg,
				transparent 0 40deg,
				rgb(255 255 255 / 0.07) 60deg,
				transparent 80deg 220deg,
				rgb(255 255 255 / 0.05) 240deg,
				transparent 260deg
			);
		/* Only over the grooves, not the label. */
		mask: radial-gradient(
			circle,
			transparent calc(var(--label) / 2),
			#000 calc(var(--label) / 2 + 1%)
		);
	}

	/* One sweep of light across the record when it turns gold. */
	.shine .sheen::after {
		content: '';
		position: absolute;
		inset: 0;
		border-radius: 50%;
		background: linear-gradient(
			115deg,
			transparent 30%,
			rgb(255 240 200 / 0.75) 48%,
			rgb(255 255 255 / 0.9) 50%,
			rgb(255 240 200 / 0.75) 52%,
			transparent 70%
		);
		background-size: 300% 100%;
		animation: gold-sweep 1.4s var(--motion-ease) both;
	}

	.shine {
		animation: gold-pop 900ms var(--motion-spring);
	}

	@keyframes gold-sweep {
		from {
			background-position: 100% 0;
		}
		to {
			background-position: 0% 0;
		}
	}

	@keyframes gold-pop {
		40% {
			scale: 1.04;
		}
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
</style>
