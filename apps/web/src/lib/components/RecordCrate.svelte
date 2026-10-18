<script lang="ts" module>
	export interface CrateRecord {
		id: string;
		title: string;
		author: string;
		cover: ((w: number) => string) | null;
		finished?: boolean;
	}
</script>

<script lang="ts">
	import Cover from './Cover.svelte';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { unique } from '#lib/unique.ts';
	import { haptic } from '#lib/haptics.ts';
	import { prefs } from '#lib/prefs.svelte.ts';

	let {
		name,
		href,
		records,
		note = '',
		sizes = '170px'
	}: {
		name: string;
		/** The series page. A record opens its own book. */
		href: string;
		/** Every book of the series, in order: all of them can be flipped through. */
		records: CrateRecord[];
		/** Second line under the name, e.g. "7 books". */
		note?: string;
		sizes?: string;
	} = $props();

	/** Records in the page around the current one (a crate of 116 needs no 116 elements);
	   at rest only the few whose tops show. */
	const BEHIND = 9;
	const BEHIND_AT_REST = 4;
	const AHEAD = 3;
	/** Covers loaded behind the current record: deeper ones are in the crate's shadow. */
	const COVERS_BEHIND = 3;

	const uid = $props.id();
	const all = $derived(unique(records, (r) => r.id));
	const n = $derived(all.length);
	const t = $derived(i18n.m.series);

	let open = $state(false);
	let at = $state(0);
	const current = $derived(all[Math.min(at, n - 1)]);
	const slice = $derived(
		all.slice(Math.max(0, at - AHEAD), at + (open ? BEHIND : BEHIND_AT_REST)).map((r, k) => ({
			r,
			i: Math.max(0, at - AHEAD) + k
		}))
	);

	let bin = $state<HTMLElement>();
	let touchX: number | null = null;
	let lastClientX = 0;
	let lastTime = 0;
	let velocity = 0;
	let wheel = 0;
	let swiped = false;
	let inertiaRaf: number | null = null;
	let atBoundary = false;

	function cancelInertia() {
		if (inertiaRaf !== null) {
			cancelAnimationFrame(inertiaRaf);
			inertiaRaf = null;
		}
	}

	function push() {
		cancelInertia();
		open = false;
		at = 0;
		atBoundary = false;
	}

	function flipTo(i: number, triggerHaptic = true) {
		open = true;
		const target = Math.max(0, Math.min(n - 1, i));
		if (target !== at) {
			at = target;
			atBoundary = false;
			if (triggerHaptic) haptic.tick();
		} else if (i !== target && !atBoundary) {
			atBoundary = true;
			if (triggerHaptic) haptic.boundary();
		}
	}

	function startInertia(initialVelocity: number) {
		cancelInertia();
		if (prefs.motion === 'reduce') return;

		let v = initialVelocity;
		let prevTime = performance.now();
		let distance = 0;
		const step = 24;
		const friction = 0.92;

		function stepInertia(now: number) {
			const dt = Math.min(now - prevTime, 32);
			prevTime = now;
			v *= Math.pow(friction, dt / 16);
			distance += v * dt;

			if (Math.abs(distance) >= step) {
				const steps = Math.trunc(distance / step);
				distance -= steps * step;
				const prevAt = at;
				flipTo(at + steps);
				if (at === prevAt) {
					v = 0; // Hit boundary
				}
			}

			if (Math.abs(v) > 0.05 && open) {
				inertiaRaf = requestAnimationFrame(stepInertia);
			} else {
				inertiaRaf = null;
			}
		}

		inertiaRaf = requestAnimationFrame(stepInertia);
	}

	$effect(() => () => cancelInertia());
	$effect(() => {
		if (at >= n && n > 0) at = n - 1;
	});

	function onpointermove(e: PointerEvent) {
		if (!bin || n < 2) return;
		if (e.pointerType === 'mouse' || e.pointerType === 'pen') {
			// From the left edge (first book) to the right edge (last): any book is one move away.
			const r = bin.getBoundingClientRect();
			flipTo(Math.floor(((e.clientX - r.left) / r.width) * n));
		} else if (touchX !== null) {
			const now = performance.now();
			const dt = now - lastTime;
			if (dt > 8) {
				const instantV = (e.clientX - lastClientX) / dt;
				velocity = velocity * 0.3 + instantV * 0.7;
				lastClientX = e.clientX;
				lastTime = now;
			}

			// Touch: every short swipe step flips one record, like thumbing through a crate.
			const step = 24;
			const dx = e.clientX - touchX;
			if (Math.abs(dx) >= step) {
				swiped = true;
				const steps = Math.trunc(dx / step);
				flipTo(at + steps);
				touchX += steps * step;
			}
		}
	}

	/** Sideways scrolling (trackpad, Shift + wheel) flips one record per notch. */
	function onwheel(e: WheelEvent) {
		if (n < 2 || Math.abs(e.deltaX) <= Math.abs(e.deltaY)) return;
		e.preventDefault();
		wheel += e.deltaX;
		const steps = Math.trunc(wheel / 40);
		if (steps) {
			wheel -= steps * 40;
			flipTo(at + steps);
		}
	}

	/** Not passive (Svelte registers wheel handlers passively): the page must not scroll too. */
	function wheelFlip(el: HTMLElement) {
		el.addEventListener('wheel', onwheel, { passive: false });
		return () => el.removeEventListener('wheel', onwheel);
	}

	function onkeydown(e: KeyboardEvent) {
		if (n < 2) return;
		const keys: Record<string, number> = {
			ArrowRight: at + 1,
			ArrowLeft: at - 1,
			PageDown: at + 10,
			PageUp: at - 10,
			Home: 0,
			End: n - 1
		};
		if (!(e.key in keys)) return;
		e.preventDefault();
		flipTo(keys[e.key]);
	}

	/** Light jitter per record, so the crate looks hand-filled rather than stamped. */
	const tilt = (i: number) => (((i * 7919) % 5) - 2) * 0.6;
</script>

<div
	class="crate"
	class:open
	role="group"
	aria-label={name}
	onpointerenter={(e) => e.pointerType !== 'touch' && n > 0 && (open = true)}
	onpointerleave={push}
>
	<!-- The record in front is a link to its book: flip to the one you want, then pick it. -->
	<a
		class="bin"
		bind:this={bin}
		href={current ? `/book/${current.id}` : href}
		aria-label={current
			? n > 1
				? t.crateRecord(current.title, name, at + 1, n)
				: current.title
			: name}
		aria-describedby={n > 1 ? `${uid}-hint` : undefined}
		style:--n={n}
		{onpointermove}
		{@attach wheelFlip}
		{onkeydown}
		onfocus={() => (open = true)}
		onblur={push}
		onpointerdown={(e) => {
			cancelInertia();
			if (e.pointerType === 'touch') {
				touchX = e.clientX;
				lastClientX = e.clientX;
				lastTime = performance.now();
				velocity = 0;
			}
			swiped = false;
		}}
		onpointerup={() => {
			if (touchX !== null && swiped && Math.abs(velocity) > 0.22) {
				startInertia(velocity);
			}
			touchX = null;
		}}
		onpointercancel={() => {
			touchX = null;
			swiped = false;
			cancelInertia();
		}}
		onclick={(e) => {
			// A swipe through the crate is browsing, not a tap on a record.
			if (swiped || inertiaRaf !== null) {
				e.preventDefault();
			} else {
				haptic.tap();
			}
			swiped = false;
		}}
	>
		<div class="records" aria-hidden="true">
			{#each slice as { r, i } (r.id)}
				{@const d = i - at}
				<div
					class="rec"
					class:front={d < 0}
					class:cur={d === 0}
					class:done={r.finished}
					style:--d={d}
					style:--rz="{tilt(i)}deg"
				>
					{#if d >= -1 && d <= COVERS_BEHIND}
						<Cover title={r.title} author={r.author} src={r.cover} {sizes} bare />
					{:else}
						<div class="blank"></div>
					{/if}
				</div>
			{/each}
		</div>
		<div class="wall" aria-hidden="true">
			<span class="grip"></span>
			{#if open && n > 1}
				<span class="scrub" style:--p={at / (n - 1)}></span>
			{/if}
		</div>
	</a>
	<a class="name" {href}><strong>{name}</strong></a>
	{#if n > 1}<span id="{uid}-hint" hidden>{t.flipHint}</span>{/if}
	<span class="note muted" aria-hidden={open && n > 1}>
		{#if open && n > 1 && current}
			<span class="tabular">{at + 1}/{n}</span> · {current.title}
		{:else}
			{note}
		{/if}
	</span>
</div>

<style>
	.crate {
		position: relative;
		display: grid;
		gap: var(--sp-4);
		min-width: 0;
		-webkit-touch-callout: none;
	}

	/* The crate clips its sides, so the focus ring goes around its front panel. */
	.bin:focus-visible {
		outline: none;
	}

	.bin:focus-visible .wall {
		outline: 2px solid var(--c-accent);
		outline-offset: 1px;
	}

	/* Where in the series the record in front is. */
	.scrub {
		position: absolute;
		left: 12%;
		right: 12%;
		bottom: 16%;
		height: 3px;
		border-radius: var(--r-pill);
		background: rgb(255 255 255 / 0.14);
	}

	.scrub::after {
		content: '';
		position: absolute;
		top: -1px;
		left: calc(var(--p) * (100% - 14px));
		width: 14px;
		height: 5px;
		border-radius: var(--r-pill);
		background: var(--c-accent);
		transition: left var(--motion-fast) var(--motion-ease);
	}

	.name {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.name:hover strong {
		text-decoration: underline;
		text-underline-offset: 3px;
	}

	/* The crate seen slightly from above: records stand on its floor, leaning back. */
	.bin {
		position: relative;
		aspect-ratio: 1 / 1.08;
		perspective: 620px;
		perspective-origin: 50% -30%;
		touch-action: pan-y;
		/* Records falling forward never stick out beside the crate; upwards they may. */
		clip-path: inset(-50% 0 0 0);
		transition: translate var(--motion-base) var(--motion-ease);
	}

	.records {
		position: absolute;
		left: 9%;
		right: 9%;
		bottom: 7%;
		aspect-ratio: 1;
		transform-style: preserve-3d;
	}

	.rec {
		--z: calc(var(--d) * -15px);
		--y: calc(var(--d) * -3.5%);
		--rx: 8deg;
		position: absolute;
		inset: 0;
		transform-origin: 50% 100%;
		transform: translate3d(0, var(--y), var(--z)) rotateX(var(--rx)) rotateZ(var(--rz));
		transition:
			transform 420ms var(--motion-out),
			opacity var(--motion-base) var(--motion-ease);
	}

	.rec :global(.cover) {
		border-radius: 3px;
		box-shadow:
			0 0 0 1px rgb(0 0 0 / 0.25),
			0 -6px 14px -6px rgb(0 0 0 / 0.55);
	}

	/* Records further back sit in the crate's shadow. */
	.rec::after {
		content: '';
		position: absolute;
		inset: 0;
		border-radius: 3px;
		background: #000;
		opacity: calc(min(var(--d), 4) * 0.12);
		pointer-events: none;
		transition: opacity var(--motion-base) var(--motion-ease);
	}

	.blank {
		aspect-ratio: 1;
		border-radius: 3px;
		background: color-mix(in oklab, var(--c-ink) 22%, var(--c-card));
		box-shadow: inset 0 0 0 1px rgb(0 0 0 / 0.2);
	}

	/* A finished book: a thin gold edge on its sleeve. */
	.done :global(.cover) {
		box-shadow:
			inset 0 3px 0 var(--c-gold, #d4a640),
			0 0 0 1px rgb(0 0 0 / 0.25),
			0 -6px 14px -6px rgb(0 0 0 / 0.55);
	}

	/* Pulled out: the crate slides towards the listener, the records fan out so the ones
	   behind show, and the one under the pointer rises out of the row. */
	.crate.open {
		z-index: 1;
	}

	.open .bin {
		translate: 0 -4px;
	}

	.open .wall {
		box-shadow:
			0 -1px 0 rgb(255 255 255 / 0.06) inset,
			0 20px 30px -14px rgb(0 0 0 / 0.7);
	}

	.open .rec {
		--z: calc(var(--d) * -22px);
		/* Fanned out, but never further up than --fan-max (rows above stay readable). */
		--y: max(calc(-1 * var(--fan-max, 34%)), calc(-6% + var(--d) * -8%));
		--rx: 13deg;
	}

	.open .rec.cur {
		--y: -11%;
		--rx: 3deg;
	}

	/* Records already flipped past fall forward against the crate wall. */
	.rec.front,
	.open .rec.front {
		--z: calc(var(--d) * -5px);
		--y: 0%;
		--rx: -62deg;
	}

	.rec.front::after {
		opacity: 0.35;
	}

	.wall {
		position: absolute;
		left: 2%;
		right: 2%;
		bottom: 0;
		height: 34%;
		border-radius: 6px 6px var(--r-card) var(--r-card);
		border-top: 3px solid color-mix(in oklab, var(--c-ctrl) 70%, var(--c-ink));
		transition: box-shadow var(--motion-base) var(--motion-ease);
		background:
			linear-gradient(to bottom, rgb(255 255 255 / 0.08), transparent 18%),
			repeating-linear-gradient(
				to bottom,
				transparent 0 calc(33.3% - 1px),
				rgb(0 0 0 / 0.22) calc(33.3% - 1px) 33.3%
			),
			var(--c-ctrl);
		box-shadow:
			0 -1px 0 rgb(255 255 255 / 0.06) inset,
			0 14px 24px -14px rgb(0 0 0 / 0.6);
	}

	/* The hand hole in the crate's front panel. */
	.grip {
		position: absolute;
		left: 50%;
		top: 22%;
		width: 30%;
		height: 15%;
		translate: -50% 0;
		border-radius: var(--r-pill);
		background: rgb(0 0 0 / 0.45);
		box-shadow: inset 0 2px 3px rgb(0 0 0 / 0.5);
	}

	strong {
		font-weight: var(--fw-semibold);
		font-size: var(--fs-14);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.note {
		font-size: var(--fs-13);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	@media (forced-colors: active) {
		.wall {
			border: 1px solid CanvasText;
		}
	}
</style>
