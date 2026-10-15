<script lang="ts">
	import { player, MAX_RATE, MIN_RATE, RATE_STEP } from './player.svelte.ts';
	import { prefs } from '#lib/prefs.svelte.ts';
	import { duration, speed } from '#lib/format.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Minus from '@lucide/svelte/icons/minus';
	import Plus from '@lucide/svelte/icons/plus';

	const t = $derived(i18n.m.player);
	const CHOICES = [0.75, 1, 1.1, 1.2, 1.3, 1.5, 1.75, 2, 2.5, 3];
	const left = $derived(Math.max(0, player.duration - player.currentTime) / player.rate);
</script>

<h3 class="label">{t.speed}</h3>
<div class="now">
	<button
		class="step"
		disabled={player.rate <= MIN_RATE}
		onclick={() => player.setRate(player.rate - RATE_STEP)}
		aria-label={t.slower}
		title={t.slower}><Minus size={18} /></button
	>
	<output class="value display tabular" aria-live="polite">{speed(player.rate)}</output>
	<button
		class="step"
		disabled={player.rate >= MAX_RATE}
		onclick={() => player.setRate(player.rate + RATE_STEP)}
		aria-label={t.faster}
		title={t.faster}><Plus size={18} /></button
	>
</div>
<input
	class="slider"
	type="range"
	min={MIN_RATE}
	max={MAX_RATE}
	step={RATE_STEP}
	value={player.rate}
	aria-label={t.speed}
	aria-valuetext={speed(player.rate)}
	oninput={(e) => player.setRate(+e.currentTarget.value)}
/>
<div class="opts" role="group" aria-label={t.speedChoices}>
	{#each CHOICES as r (r)}
		<button class="chip" aria-pressed={player.rate === r} onclick={() => player.setRate(r)}
			>{speed(r)}</button
		>
	{/each}
</div>
<p class="muted hint">
	{prefs.rememberRate ? t.speedPerBook : t.speedEveryBook} · {t.speedLeft(duration(left))}
</p>

<style>
	.now {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--sp-12);
	}

	.value {
		font-size: var(--fs-34);
		line-height: 1;
	}

	.step {
		display: grid;
		place-items: center;
		width: var(--layout-touch);
		height: var(--layout-touch);
		border: 0;
		border-radius: 50%;
		background: var(--c-bg);
		color: var(--c-ink);
	}

	.step:disabled {
		opacity: 0.4;
	}

	.slider {
		width: 100%;
		accent-color: var(--c-accent);
	}

	.opts {
		display: flex;
		flex-wrap: wrap;
		gap: var(--sp-8);
	}

	.opts .chip {
		background: var(--c-bg);
	}

	.opts .chip[aria-pressed='true'] {
		background: var(--c-accent);
		color: var(--c-on-accent);
	}

	.hint {
		margin: 0;
		font-size: var(--fs-13);
	}
</style>
