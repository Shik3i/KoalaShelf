<script lang="ts">
	import { player, volumeAvailable } from './player.svelte.ts';
	import { prefs } from '#lib/prefs.svelte.ts';
	import { span } from '#lib/format.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Segmented from '#lib/components/Segmented.svelte';
	import Toggle from '#lib/components/Toggle.svelte';
	import Volume1 from '@lucide/svelte/icons/volume-1';
	import Volume2 from '@lucide/svelte/icons/volume-2';
	import VolumeX from '@lucide/svelte/icons/volume-x';

	const t = $derived(i18n.m.sound);
	const BOOSTS = [1, 1.5, 2];
	const pct = (v: number) => `${Math.round(v * 100)} %`;
</script>

<h3 class="label">{t.title}</h3>

{#if volumeAvailable}
	<div class="volume">
		<button
			class="mute"
			onclick={() => player.setVolume(prefs.volume ? 0 : 1)}
			aria-label={prefs.volume ? t.mute : t.unmute}
			title={prefs.volume ? t.mute : t.unmute}
		>
			{#if !prefs.volume}<VolumeX size={20} />{:else if prefs.volume < 0.5}<Volume1
					size={20}
				/>{:else}<Volume2 size={20} />{/if}
		</button>
		<input
			type="range"
			min="0"
			max="1"
			step="0.05"
			value={prefs.volume}
			aria-label={t.volume}
			aria-valuetext={pct(prefs.volume)}
			oninput={(e) => player.setVolume(+e.currentTarget.value)}
		/>
		<span class="tabular muted">{pct(prefs.volume)}</span>
	</div>
{/if}

{#if player.levelingAvailable}
	<div class="row">
		<span class="stack">
			{t.boost}
			<small class="muted">{t.boostHint}</small>
		</span>
		<Segmented
			size="sm"
			label={t.boost}
			value={prefs.boost}
			options={BOOSTS.map((b) => ({ value: b, label: b === 1 ? t.off : `+${pct(b - 1)}` }))}
			onchange={(b) => player.setSound('boost', b)}
		/>
	</div>
	<Toggle
		label={t.voice}
		hint={t.voiceHint}
		checked={prefs.voiceBoost}
		onchange={(on) => player.setSound('voiceBoost', on)}
	/>
	<Toggle
		label={i18n.m.settings.leveling}
		hint={i18n.m.settings.levelingHint}
		checked={prefs.leveling}
		onchange={(on) => player.setSound('leveling', on)}
	/>
	<Toggle
		label={t.mono}
		hint={t.monoHint}
		checked={prefs.mono}
		onchange={(on) => player.setSound('mono', on)}
	/>
	{#if player.silenceAvailable}
		<Toggle
			label={t.silence}
			hint={t.silenceHint}
			checked={prefs.skipSilence}
			onchange={(on) => player.setSound('skipSilence', on)}
		/>
		{#if prefs.skipSilence && player.silenceSavedTotal >= 1}
			<p class="saved muted" aria-live="polite">
				{t.saved(span(player.silenceSavedBook), span(player.silenceSavedTotal))}
			</p>
		{/if}
	{/if}
	<Toggle
		label={t.needleDrop}
		hint={t.needleDropHint}
		checked={prefs.needleDrop}
		onchange={(on) => player.setSound('needleDrop', on)}
	/>
	<Toggle
		label={t.vinylCrackle}
		hint={t.vinylCrackleHint}
		checked={prefs.vinylCrackle}
		onchange={(on) => player.setSound('vinylCrackle', on)}
	/>
	{#if prefs.vinylCrackle}
		<div class="row">
			<span class="stack">
				{t.crackleVolume}
			</span>
			<div class="volume">
				<input
					type="range"
					min="0.01"
					max="0.12"
					step="0.01"
					value={prefs.vinylCrackleVolume}
					aria-label={t.crackleVolume}
					oninput={(e) => player.setSound('vinylCrackleVolume', +e.currentTarget.value)}
				/>
				<span class="tabular muted">{Math.round(prefs.vinylCrackleVolume * 800)}%</span>
			</div>
		</div>
	{/if}
{:else}
	<p class="muted hint">{t.unavailable}</p>
{/if}

<style>
	.volume {
		display: flex;
		align-items: center;
		gap: var(--sp-8);
	}

	.volume input {
		flex: 1;
		min-width: 0;
		accent-color: var(--c-accent);
	}

	.volume .muted {
		min-width: 4.5ch;
		text-align: right;
		font-size: var(--fs-13);
	}

	.mute {
		display: grid;
		place-items: center;
		width: var(--layout-touch);
		height: var(--layout-touch);
		border: 0;
		border-radius: 50%;
		background: var(--c-bg);
		color: var(--c-ink);
	}

	.row {
		display: grid;
		gap: var(--sp-8);
	}

	.stack {
		display: grid;
		gap: var(--sp-4);
	}

	.stack small,
	.hint,
	.saved {
		font-size: var(--fs-13);
	}

	.hint,
	.saved {
		margin: 0;
	}
</style>
