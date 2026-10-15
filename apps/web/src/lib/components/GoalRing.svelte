<script lang="ts">
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Flame from '@lucide/svelte/icons/flame';
	import Check from '@lucide/svelte/icons/check';

	/** Today's listening against the daily goal, with the current streak. */
	let {
		seconds,
		goal,
		streak,
		size = 64,
		href
	}: { seconds: number; goal: number; streak: number; size?: number; href?: string } = $props();

	const t = $derived(i18n.m.goal);
	const minutes = $derived(Math.floor(seconds / 60));
	const frac = $derived(Math.min(1, seconds / Math.max(60, goal * 60)));
	const done = $derived(frac >= 1);
	const R = 26;
	const C = 2 * Math.PI * R;
	const label = $derived(
		`${done ? t.reached : t.today(minutes, goal)}${streak ? ` · ${t.streak(streak)}` : ''}`
	);
</script>

<svelte:element
	this={href ? 'a' : 'div'}
	{href}
	class="goal"
	class:done
	role={href ? undefined : 'img'}
	aria-label={label}
	title={label}
>
	<svg viewBox="0 0 64 64" width={size} height={size} aria-hidden="true">
		<circle class="track" cx="32" cy="32" r={R} />
		<circle
			class="fill"
			cx="32"
			cy="32"
			r={R}
			stroke-dasharray={C}
			stroke-dashoffset={C * (1 - frac)}
		/>
	</svg>
	<span class="mid" aria-hidden="true">
		{#if done}<Check size={size * 0.36} strokeWidth={3} />{:else}<span class="n tabular"
				>{minutes}</span
			><span class="of">/{goal}</span>{/if}
	</span>
	{#if streak}
		<span class="streak tabular" aria-hidden="true"><Flame size={12} />{streak}</span>
	{/if}
</svelte:element>

<style>
	.goal {
		position: relative;
		display: inline-grid;
		place-items: center;
		color: inherit;
	}

	svg {
		grid-area: 1 / 1;
		rotate: -90deg;
	}

	circle {
		fill: none;
		stroke-width: 6;
	}

	.track {
		stroke: color-mix(in oklab, var(--c-ink) 12%, transparent);
	}

	.fill {
		stroke: var(--c-accent);
		stroke-linecap: round;
		transition: stroke-dashoffset 900ms var(--motion-out);
	}

	.mid {
		grid-area: 1 / 1;
		display: flex;
		align-items: baseline;
		font-weight: var(--fw-bold);
		line-height: 1;
	}

	.done .mid {
		color: var(--c-accent);
		animation: pop 600ms var(--motion-spring);
	}

	.n {
		font-size: var(--fs-14);
	}

	.of {
		font-size: 0.625rem;
		color: var(--c-muted);
	}

	.streak {
		position: absolute;
		right: -6px;
		bottom: -4px;
		display: inline-flex;
		align-items: center;
		gap: 1px;
		padding: 1px 6px 1px 4px;
		border-radius: var(--r-pill);
		background: var(--c-ctrl);
		color: var(--c-ctrl-ink);
		font-size: 0.6875rem;
		font-weight: var(--fw-bold);
	}

	@keyframes pop {
		from {
			scale: 0.4;
		}
	}

	@media (forced-colors: active) {
		.fill {
			stroke: Highlight;
		}

		.track {
			stroke: CanvasText;
		}
	}
</style>
