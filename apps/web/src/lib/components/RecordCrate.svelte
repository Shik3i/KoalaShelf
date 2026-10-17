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

	let {
		name,
		href,
		records,
		note = '',
		sizes = '170px'
	}: {
		name: string;
		href: string;
		records: CrateRecord[];
		/** Second line under the name, e.g. "7 books". */
		note?: string;
		sizes?: string;
	} = $props();

	/** More records than this only make the crate deeper, not easier to browse. */
	const MAX = 9;
	/** Covers on show before the crate is first touched; the rest load when it is pulled out. */
	const PEEK = 3;

	const uid = $props.id();
	const shown = $derived(unique(records, (r) => r.id).slice(0, MAX));
	const t = $derived(i18n.m.series);

	let open = $state(false);
	let woken = $state(false);
	let at = $state(0);
	const current = $derived(shown[Math.min(at, shown.length - 1)]);

	let bin = $state<HTMLElement>();
	let touchX: number | null = null;
	let swiped = false;

	function pull() {
		open = true;
		woken = true;
	}

	function push() {
		open = false;
		at = 0;
	}

	function flipTo(i: number) {
		at = Math.max(0, Math.min(shown.length - 1, i));
	}

	function onpointermove(e: PointerEvent) {
		if (!bin || shown.length < 2) return;
		if (e.pointerType === 'mouse' || e.pointerType === 'pen') {
			pull();
			const r = bin.getBoundingClientRect();
			flipTo(Math.floor(((e.clientX - r.left) / r.width) * shown.length));
		} else if (touchX !== null) {
			// Touch: every short swipe step flips one record, like thumbing through a crate.
			const step = 26;
			const dx = e.clientX - touchX;
			if (Math.abs(dx) >= step) {
				pull();
				swiped = true;
				flipTo(at + Math.trunc(dx / step));
				touchX = e.clientX;
			}
		}
	}

	function onkeydown(e: KeyboardEvent) {
		if (shown.length < 2) return;
		const keys: Record<string, number> = {
			ArrowRight: at + 1,
			ArrowLeft: at - 1,
			Home: 0,
			End: shown.length - 1
		};
		if (!(e.key in keys)) return;
		e.preventDefault();
		pull();
		flipTo(keys[e.key]);
	}

	/** Light jitter per record, so the crate looks hand-filled rather than stamped. */
	const tilt = (i: number) => (((i * 7919) % 5) - 2) * 0.6;
</script>

<a
	class="crate"
	class:open
	{href}
	aria-describedby={shown.length > 1 ? `${uid}-hint` : undefined}
	onpointerenter={(e) => e.pointerType !== 'touch' && pull()}
	onpointerleave={push}
	onfocus={pull}
	onblur={push}
	{onkeydown}
	onclick={(e) => {
		// A swipe through the crate is browsing, not a tap on it.
		if (swiped) e.preventDefault();
		swiped = false;
	}}
>
	<div
		class="bin"
		bind:this={bin}
		aria-hidden="true"
		style:--n={shown.length}
		{onpointermove}
		onpointerdown={(e) => {
			if (e.pointerType === 'touch') touchX = e.clientX;
			swiped = false;
		}}
		onpointerup={() => (touchX = null)}
		onpointercancel={() => (touchX = null)}
	>
		<div class="records">
			{#each shown as r, i (r.id)}
				{@const d = i - at}
				<div
					class="rec"
					class:front={d < 0}
					class:cur={d === 0}
					class:done={r.finished}
					style:--d={d}
					style:--rz="{tilt(i)}deg"
					style:z-index={shown.length - Math.abs(d)}
				>
					{#if i < PEEK || woken}
						<Cover title={r.title} author={r.author} src={r.cover} {sizes} titleTop />
					{:else}
						<div class="blank"></div>
					{/if}
				</div>
			{/each}
		</div>
		<div class="wall">
			<span class="grip"></span>
		</div>
	</div>
	<strong>{name}</strong>
	{#if shown.length > 1}<span id="{uid}-hint" hidden>{t.flipHint}</span>{/if}
	<span class="note muted">
		{#if open && shown.length > 1 && current}
			<span class="tabular">{at + 1}/{shown.length}</span> · {current.title}
		{:else}
			{note}
		{/if}
	</span>
</a>

<style>
	.crate {
		position: relative;
		display: grid;
		gap: var(--sp-4);
		min-width: 0;
		outline-offset: 4px;
		border-radius: var(--r-card);
		-webkit-touch-callout: none;
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
