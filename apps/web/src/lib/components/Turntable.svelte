<script lang="ts">
	import Vinyl from './Vinyl.svelte';
	import { placeRecord } from '#lib/motion.ts';
	import { haptic } from '#lib/haptics.ts';

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
		/** Enable interactive turntable DJ scratching / cueing */
		interactive?: boolean;
		onScratch?: (deltaSeconds: number) => void;
		onScratchStart?: () => void;
		onScratchEnd?: () => void;
	}

	let {
		progress = 0,
		playing = false,
		interactive = false,
		onScratch,
		onScratchStart,
		onScratchEnd,
		...rest
	}: Props = $props();

	/*
	 * Geometry in deck units (0..100). Disc: centre (47, 50), radius 41, label radius ~16.
	 * Pivot (93, 5), arm length 44. Rotating the arm clockwise from straight down puts the
	 * stylus at r ≈ 33 for 18° (outer grooves) and r ≈ 21 for 40° (just outside the label).
	 */
	const PIVOT = { x: 93, y: 5 };
	const LENGTH = 44;
	/** Resting on its post (just off the rim) while paused, swinging in when playback starts. */
	const REST = 4;

	let platterEl = $state<HTMLDivElement>();
	let isScratching = $state(false);
	let lastAngle = 0;
	let scratchAccum = 0;
	let totalScratchSeconds = $state(0);
	let scratchRotateDeg = $state(0);

	const angle = $derived(
		playing || isScratching ? 18 + Math.min(1, Math.max(0, progress)) * 22 : REST
	);

	function getPointerAngle(e: PointerEvent): number {
		if (!platterEl) return 0;
		const r = platterEl.getBoundingClientRect();
		const cx = r.left + r.width / 2;
		const cy = r.top + r.height / 2;
		return Math.atan2(e.clientY - cy, e.clientX - cx);
	}

	function onPointerDown(e: PointerEvent) {
		if (!interactive || !platterEl) return;
		platterEl.setPointerCapture(e.pointerId);
		isScratching = true;
		lastAngle = getPointerAngle(e);
		scratchAccum = 0;
		totalScratchSeconds = 0;
		haptic.tap();
		onScratchStart?.();
	}

	function onPointerMove(e: PointerEvent) {
		if (!isScratching) return;
		const curAngle = getPointerAngle(e);
		let delta = curAngle - lastAngle;
		while (delta > Math.PI) delta -= 2 * Math.PI;
		while (delta < -Math.PI) delta += 2 * Math.PI;
		lastAngle = curAngle;

		const deltaDeg = (delta * 180) / Math.PI;
		scratchRotateDeg += deltaDeg;
		scratchAccum += deltaDeg;

		// 360 degrees = 30 seconds of audio => 1 deg = 30/360 = 0.0833s
		const SECONDS_PER_DEG = 30 / 360;
		const dt = deltaDeg * SECONDS_PER_DEG;
		totalScratchSeconds += dt;
		onScratch?.(dt);

		// Tactile tick every ~12 degrees of spin
		if (Math.abs(scratchAccum) >= 12) {
			haptic.tick();
			scratchAccum = 0;
		}
	}

	function onPointerUp() {
		if (!isScratching) return;
		isScratching = false;
		scratchRotateDeg = 0;
		totalScratchSeconds = 0;
		onScratchEnd?.();
		haptic.tap();
	}
</script>

<div class="deck">
	<!-- Another book: its record is put on the platter. -->
	{#key rest.title}
		<!-- svelte-ignore a11y_no_noninteractive_tabindex -->
		<div
			class="platter"
			class:interactive
			class:scratching={isScratching}
			role={interactive ? 'slider' : undefined}
			aria-label={interactive ? 'Platte drehen / DJ Scratch' : undefined}
			aria-valuenow={interactive ? Math.round(progress * 100) : undefined}
			tabindex={interactive ? 0 : undefined}
			bind:this={platterEl}
			onpointerdown={onPointerDown}
			onpointermove={onPointerMove}
			onpointerup={onPointerUp}
			onpointercancel={onPointerUp}
			onkeydown={(e) => {
				if (!interactive) return;
				if (e.key === 'ArrowRight' || e.key === 'ArrowUp') {
					e.preventDefault();
					onScratch?.(2);
					haptic.tick();
				} else if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') {
					e.preventDefault();
					onScratch?.(-2);
					haptic.tick();
				}
			}}
			style:transform={isScratching ? `rotate(${scratchRotateDeg}deg)` : undefined}
			in:placeRecord
		>
			<Vinyl playing={playing && !isScratching} {...rest} />
			{#if isScratching}
				<div class="scratch-badge" aria-live="polite">
					{totalScratchSeconds >= 0
						? `+${totalScratchSeconds.toFixed(1)}s`
						: `${totalScratchSeconds.toFixed(1)}s`}
				</div>
			{/if}
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
		touch-action: none;
		user-select: none;
		-webkit-user-select: none;
	}

	.platter.interactive {
		cursor: grab;
	}

	.platter.scratching {
		cursor: grabbing;
	}

	.scratch-badge {
		position: absolute;
		left: 50%;
		top: 50%;
		translate: -50% -50%;
		background: rgb(0 0 0 / 0.82);
		color: var(--c-accent, #e8a33d);
		font-family: var(--font-display, monospace);
		font-size: var(--fs-14, 14px);
		font-weight: var(--fw-bold, 700);
		padding: 4px 10px;
		border-radius: var(--r-pill, 9999px);
		border: 1px solid rgb(255 255 255 / 0.2);
		pointer-events: none;
		box-shadow: 0 4px 12px rgb(0 0 0 / 0.5);
		z-index: 10;
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
