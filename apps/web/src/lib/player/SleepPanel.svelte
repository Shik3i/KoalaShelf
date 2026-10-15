<script lang="ts">
	import { untrack } from 'svelte';
	import { player } from './player.svelte.ts';
	import { clampWindow, forecast, SMART_STEP } from './sleep.ts';
	import { prefs } from '#lib/prefs.svelte.ts';
	import { clock } from '#lib/format.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Segmented from '#lib/components/Segmented.svelte';
	import Minus from '@lucide/svelte/icons/minus';
	import Plus from '@lucide/svelte/icons/plus';

	const t = $derived(i18n.m.player);
	type Mode = 'exact' | 'smart';
	// Opens on the running timer; otherwise on the window used last time.
	const initial = untrack(() => (player.sleep.kind === 'window' ? player.sleep : null));
	let mode = $state<Mode>(initial ? 'smart' : 'exact');
	let min = $state(initial?.min ?? prefs.sleepMin);
	let max = $state(initial?.max ?? prefs.sleepMax);

	const running = $derived(player.sleep.kind === 'window' ? player.sleep : null);
	/** The running timer already uses this window. */
	const unchanged = $derived(running?.min === min && running?.max === max);

	/** Running: the live forecast. Otherwise: what would happen if started now. */
	const plan = $derived(
		running && unchanged
			? player.sleepForecast
			: forecast(player.chapters, player.currentTime, player.rate, min * 60, max * 60)
	);

	function bump(which: 'min' | 'max', by: number) {
		const w = clampWindow(
			which === 'min' ? min + by : min,
			which === 'max' ? max + by : max,
			which
		);
		min = w.min;
		max = w.max;
	}

	function start() {
		prefs.set('sleepMin', min);
		prefs.set('sleepMax', max);
		player.setSleepWindow(min, max);
	}

	// Visualisation: the next (latest + 15 %) minutes with the window, chapter ends and the stop.
	const span = $derived(max * 60 * 1.15);
	/** Window in listening seconds from now; a running one moves closer while playing. */
	const win = $derived.by(() => {
		if (!running || !unchanged) return { from: min * 60, to: max * 60 };
		void player.currentTime; // re-evaluate as playback moves on
		// Paused: the countdown is frozen at the moment of pausing.
		const base =
			player.sleepPausedLeft !== null ? running.maxAt - player.sleepPausedLeft : Date.now();
		return {
			from: Math.max(0, (running.minAt - base) / 1000),
			to: Math.max(0, (running.maxAt - base) / 1000)
		};
	});
	const ends = $derived(
		player.chapters
			.map((c) => (c.end - player.currentTime) / player.rate)
			.filter((s) => s > 0 && s < span)
	);
	const pct = (s: number) => `${Math.min(100, (s / span) * 100)}%`;
</script>

<h3 class="label">{t.timer}</h3>
<Segmented
	size="sm"
	label={t.timerMode}
	value={mode}
	options={[
		{ value: 'exact', label: t.timerExact },
		{ value: 'smart', label: t.timerSmart }
	]}
	onchange={(m) => (mode = m)}
/>

{#if mode === 'exact'}
	<div class="opts">
		<button
			class="chip"
			aria-pressed={player.sleep.kind === 'off'}
			onclick={() => player.setSleep({ kind: 'off' })}>{t.timerOff}</button
		>
		<button
			class="chip"
			aria-pressed={player.sleep.kind === 'chapter'}
			onclick={() => player.setSleep({ kind: 'chapter' })}
		>
			{t.timerEndOfChapter}
		</button>
		{#each [5, 10, 15, 30, 45, 60] as m (m)}
			<button
				class="chip"
				aria-pressed={player.sleep.kind === 'minutes' && player.sleep.minutes === m}
				onclick={() =>
					player.setSleep({ kind: 'minutes', minutes: m, endsAt: Date.now() + m * 60_000 })}
			>
				{t.timerMinutes(m)}
			</button>
		{/each}
	</div>
{:else}
	<p class="muted hint">{t.timerSmartHint}</p>
	<div class="bounds">
		{#each [['min', t.timerEarliest, min], ['max', t.timerLatest, max]] as const as [which, label, value] (which)}
			<div class="bound">
				<span class="label">{label}</span>
				<div class="stepper">
					<button
						class="step"
						onclick={() => bump(which, -SMART_STEP)}
						aria-label={t.timerLess(label)}
						title={t.timerLess(label)}><Minus size={16} /></button
					>
					<output class="tabular" aria-live="polite">{t.timerMinutes(value)}</output>
					<button
						class="step"
						onclick={() => bump(which, SMART_STEP)}
						aria-label={t.timerMore(label)}
						title={t.timerMore(label)}><Plus size={16} /></button
					>
				</div>
			</div>
		{/each}
	</div>

	<div class="viz" aria-hidden="true">
		<span class="win" style:left={pct(win.from)} style:width="calc({pct(win.to)} - {pct(win.from)})"
		></span>
		{#each ends as e (e)}<span class="end" style:left={pct(e)}></span>{/each}
		{#if plan}<span
				class="stop"
				class:at-chapter={plan.chapter !== null}
				style:left={pct(plan.seconds)}
			></span>{/if}
	</div>

	{#if plan}
		<p class="plan" aria-live="polite">
			{plan.chapter !== null
				? t.timerAtChapter(plan.chapter + 1, clock(plan.seconds))
				: t.timerAtLatest(clock(plan.seconds))}
		</p>
	{/if}

	<div class="go">
		{#if running && unchanged}
			<span class="live" role="status"
				><span class="dot" aria-hidden="true"></span>{t.timerRunning}</span
			>
		{:else}
			<button class="btn primary" onclick={start}>{running ? t.timerUpdate : t.timerStart}</button>
		{/if}
		{#if player.sleep.kind !== 'off'}
			<button class="btn ghost" onclick={() => player.setSleep({ kind: 'off' })}
				>{t.timerStop}</button
			>
		{/if}
	</div>
{/if}

{#if player.sleep.kind !== 'off'}
	<div class="extend" role="group" aria-label={t.sleepExtendLabel}>
		<span class="muted">{t.sleepExtendLabel}</span>
		{#each [5, 15, 30] as m (m)}
			<button class="chip" onclick={() => player.extendSleep(m)}>{t.sleepExtend(m)}</button>
		{/each}
	</div>
{/if}
<a class="auto muted" href="/settings#sleep" onclick={() => (player.expanded = false)}
	>{prefs.autoSleep ? t.autoSleepOn : t.autoSleepOff}</a
>

<style>
	.extend {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--sp-8);
		font-size: var(--fs-13);
	}

	.extend .chip {
		background: var(--c-bg);
	}

	.auto {
		font-size: var(--fs-13);
		text-decoration: underline;
		text-underline-offset: 3px;
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
	}

	.hint {
		margin: 0;
		font-size: var(--fs-13);
	}

	.bounds {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: var(--sp-12);
	}

	.bound {
		display: grid;
		gap: var(--sp-4);
	}

	.stepper {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--sp-4);
		padding: var(--sp-4);
		border-radius: var(--r-pill);
		background: var(--c-bg);
	}

	.stepper output {
		font-weight: var(--fw-bold);
		font-size: var(--fs-14);
		white-space: nowrap;
	}

	/* 36px visual, 44px touch target. */
	.step {
		position: relative;
		display: grid;
		place-items: center;
		width: 36px;
		height: 36px;
		flex: none;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: var(--c-card);
		color: var(--c-ink);
	}

	.step::after {
		content: '';
		position: absolute;
		inset: -4px;
	}

	.step:hover {
		background: color-mix(in oklab, var(--c-accent) 18%, var(--c-card));
	}

	/* Time ahead: shaded window, chapter ends as ticks, a dot where playback stops. */
	.viz {
		position: relative;
		height: 28px;
		margin: var(--sp-4) 0;
		border-radius: var(--r-pill);
		background: var(--c-bg);
		overflow: hidden;
	}

	.win {
		position: absolute;
		top: 0;
		bottom: 0;
		background: color-mix(in oklab, var(--c-accent) 22%, transparent);
		transition:
			left var(--motion-base) var(--motion-out),
			width var(--motion-base) var(--motion-out);
	}

	.end {
		position: absolute;
		top: 7px;
		bottom: 7px;
		width: 2px;
		translate: -1px 0;
		border-radius: 1px;
		background: var(--c-muted);
	}

	.stop {
		position: absolute;
		top: 50%;
		width: 12px;
		height: 12px;
		translate: -6px -50%;
		border-radius: 50%;
		background: var(--c-ink);
		box-shadow: 0 0 0 3px var(--c-bg);
		transition: left var(--motion-base) var(--motion-spring);
	}

	.stop.at-chapter {
		background: var(--c-accent);
	}

	.plan {
		margin: 0;
		font-size: var(--fs-14);
		font-weight: var(--fw-semibold);
	}

	.go {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--sp-8);
	}

	.live {
		display: inline-flex;
		align-items: center;
		gap: var(--sp-8);
		min-height: var(--layout-touch);
		padding: 0 var(--sp-16);
		border-radius: var(--r-pill);
		background: color-mix(in oklab, var(--c-accent) 16%, transparent);
		font-weight: var(--fw-bold);
		font-size: var(--fs-14);
	}

	.dot {
		width: 8px;
		height: 8px;
		border-radius: 50%;
		background: var(--c-accent);
		animation: breathe 2s var(--motion-ease) infinite;
	}

	@keyframes breathe {
		50% {
			opacity: 0.35;
			scale: 0.7;
		}
	}
</style>
