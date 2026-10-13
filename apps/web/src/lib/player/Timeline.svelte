<script lang="ts">
	import { player } from './player.svelte.ts';
	import { clock } from '#lib/format.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';

	/** Local value while dragging, so we seek once on release instead of on every pixel. */
	let scrub = $state<number | null>(null);
	/** Mouse hover position (0..1) for a time preview; touch uses the drag bubble instead. */
	let hover = $state<number | null>(null);

	function onHover(e: PointerEvent) {
		if (e.pointerType !== 'mouse') return;
		const box = (e.currentTarget as HTMLElement).getBoundingClientRect();
		hover = Math.min(1, Math.max(0, (e.clientX - box.left - 7) / (box.width - 14)));
	}
	const value = $derived(scrub ?? player.currentTime);
	/** The whole book or the current chapter (Settings > Player > Timeline). */
	const scope = $derived(player.scope);
	const span = $derived(Math.max(0.001, scope.end - scope.start));
	/** Fraction of the scope; positions map onto the thumb's travel (see --x in CSS). */
	const frac = (t: number) => Math.min(1, Math.max(0, (t - scope.start) / span));
	/** Times on the track read from the start of the scope (like the big clock). */
	const at = (t: number) => clock(Math.max(0, t - scope.start));
	const pins = $derived(
		player.bookmarks.filter((b) => b.time >= scope.start && b.time <= scope.end)
	);
</script>

<div
	class="timeline"
	style:--f={frac(value)}
	role="presentation"
	onpointermove={onHover}
	onpointerleave={() => (hover = null)}
>
	<div class="track" aria-hidden="true">
		<div class="fill"></div>
		{#if !scope.chapter}
			{#each player.chapters.slice(1) as c (c.id)}
				<span class="tick" style:--f={frac(c.start)}></span>
			{/each}
		{/if}
	</div>
	<input
		type="range"
		min={scope.start}
		max={scope.end || 1}
		step="1"
		{value}
		aria-label={i18n.m.player.seek}
		aria-valuetext={at(value)}
		oninput={(e) => (scrub = +e.currentTarget.value)}
		onchange={(e) => {
			player.seek(+e.currentTarget.value);
			scrub = null;
		}}
	/>
	{#each pins as b (b.time)}
		<button
			class="pin"
			style:--f={frac(b.time)}
			title="{clock(b.time)} · {b.title}"
			aria-label="{clock(b.time)} {b.title}"
			onclick={() => player.seek(b.time)}
		></button>
	{/each}
	{#if scrub !== null}
		<output class="bubble tabular">{at(scrub)}</output>
	{:else if hover !== null}
		<output class="bubble tabular hover" style:--f={hover}>{clock(hover * span)}</output>
	{/if}
</div>

<style>
	/* The range thumb (14px) travels from 7px to 100% - 7px; everything aligns to that. */
	.timeline,
	.tick,
	.pin {
		--x: calc(7px + var(--f) * (100% - 14px));
	}

	.timeline {
		position: relative;
		height: 28px;
		touch-action: none;
	}

	.track {
		position: absolute;
		left: 0;
		right: 0;
		top: 50%;
		height: 4px;
		translate: 0 -50%;
		border-radius: var(--r-pill);
		background: var(--c-track);
	}

	.fill {
		position: absolute;
		inset: 0 auto 0 0;
		width: var(--x);
		border-radius: inherit;
		background: var(--c-accent);
	}

	.bubble.hover {
		--x: calc(7px + var(--f) * (100% - 14px));
	}

	.tick {
		position: absolute;
		left: var(--x);
		top: -4px;
		width: 2px;
		height: 12px;
		translate: -1px 0;
		border-radius: 1px;
		background: var(--c-ink);
		opacity: 0.35;
	}

	input {
		position: absolute;
		inset: 0;
		width: 100%;
		margin: 0;
		appearance: none;
		background: transparent;
		cursor: pointer;
	}

	input::-webkit-slider-runnable-track {
		height: 100%;
		background: transparent;
	}

	input::-webkit-slider-thumb {
		appearance: none;
		width: 14px;
		height: 14px;
		margin-top: 7px;
		border-radius: 50%;
		background: var(--c-accent);
		box-shadow: 0 0 0 3px var(--c-bg);
	}

	input::-moz-range-thumb {
		width: 14px;
		height: 14px;
		border: 0;
		border-radius: 50%;
		background: var(--c-accent);
		box-shadow: 0 0 0 3px var(--c-bg);
	}

	input:focus-visible {
		outline: none;
	}

	input:focus-visible::-webkit-slider-thumb {
		box-shadow:
			0 0 0 3px var(--c-bg),
			0 0 0 5px var(--c-accent);
	}

	/* 32px hit area, 12x18 visual pin. */
	.pin {
		position: absolute;
		left: var(--x);
		top: -20px;
		width: 32px;
		height: 32px;
		translate: -16px 0;
		padding: 0;
		border: 0;
		background: transparent;
		z-index: 1;
	}

	.pin::before {
		content: '';
		position: absolute;
		left: 10px;
		top: 6px;
		width: 12px;
		height: 18px;
		background: var(--c-accent);
		clip-path: path('M6 18 L0 8 A6 6 0 1 1 12 8 Z');
		transition: scale var(--motion-fast) var(--motion-ease);
		transform-origin: 50% 100%;
		animation: pin-drop 420ms var(--motion-spring);
	}

	@keyframes pin-drop {
		from {
			scale: 0;
			translate: 0 -8px;
		}
	}

	.pin:hover::before {
		scale: 1.25;
	}

	.bubble {
		position: absolute;
		bottom: 100%;
		left: var(--x);
		translate: -50% 4px;
		padding: 2px 8px;
		border-radius: var(--r-pill);
		background: var(--c-ctrl);
		color: var(--c-ctrl-ink);
		font-size: var(--fs-12);
		font-weight: var(--fw-bold);
		pointer-events: none;
	}
</style>
