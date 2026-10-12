<script lang="ts">
	import { player } from './player.svelte.ts';
	import { clock } from '#lib/format.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';

	let { compact = false }: { compact?: boolean } = $props();

	/** Local value while dragging, so we seek once on release instead of on every pixel. */
	let scrub = $state<number | null>(null);
	const value = $derived(scrub ?? player.currentTime);
	const pct = (t: number) => (player.duration ? (t / player.duration) * 100 : 0);
</script>

<div class="timeline" class:compact style:--p="{pct(value)}%">
	<div class="track" aria-hidden="true">
		<div class="fill"></div>
		{#each player.chapters.slice(1) as c (c.id)}
			<span class="tick" style:left="{pct(c.start)}%"></span>
		{/each}
	</div>
	<input
		type="range"
		min="0"
		max={player.duration || 1}
		step="1"
		{value}
		aria-label={i18n.m.player.seek}
		aria-valuetext={clock(value)}
		oninput={(e) => (scrub = +e.currentTarget.value)}
		onchange={(e) => {
			player.seek(+e.currentTarget.value);
			scrub = null;
		}}
	/>
	{#if !compact}
		{#each player.bookmarks as b (b.time)}
			<button
				class="pin"
				style:left="{pct(b.time)}%"
				title="{clock(b.time)} · {b.title}"
				aria-label="{clock(b.time)} {b.title}"
				onclick={() => player.seek(b.time)}
			></button>
		{/each}
	{/if}
	{#if scrub !== null}<output class="bubble tabular">{clock(scrub)}</output>{/if}
</div>

<style>
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
		width: var(--p);
		border-radius: inherit;
		background: var(--c-accent);
	}

	.tick {
		position: absolute;
		top: -6px;
		width: 2px;
		height: 16px;
		translate: -1px 0;
		border-radius: 1px;
		background: var(--c-ink);
		opacity: 0.55;
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
		box-shadow: 0 0 0 3px var(--c-bg), 0 0 0 5px var(--c-accent);
	}

	/* 32px hit area, 12x18 visual pin. */
	.pin {
		position: absolute;
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
	}

	.pin:hover::before {
		scale: 1.25;
	}

	.bubble {
		position: absolute;
		bottom: 100%;
		left: var(--p);
		translate: -50% -4px;
		padding: 2px 8px;
		border-radius: var(--r-pill);
		background: var(--c-ctrl);
		color: var(--c-ctrl-ink);
		font-size: var(--fs-12);
		font-weight: var(--fw-bold);
		pointer-events: none;
	}

	.compact {
		height: 4px;
	}

	.compact .tick {
		display: none;
	}

	.compact .track {
		border-radius: 0;
	}
</style>
