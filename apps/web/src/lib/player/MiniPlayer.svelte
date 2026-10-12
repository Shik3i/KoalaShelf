<script lang="ts">
	import { player } from './player.svelte.ts';
	import { prefs } from '#lib/prefs.svelte.ts';
	import { view } from '#lib/items.ts';
	import { clock } from '#lib/format.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Vinyl from '#lib/components/Vinyl.svelte';
	import Timeline from './Timeline.svelte';
	import Play from '@lucide/svelte/icons/play';
	import Pause from '@lucide/svelte/icons/pause';
	import RotateCcw from '@lucide/svelte/icons/rotate-ccw';

	const t = $derived(i18n.m.player);
	const v = $derived(player.item ? view(player.item) : null);
	const sub = $derived(
		player.chapter
			? `${player.chapter.title} · −${clock(player.chapter.end - player.currentTime)}`
			: (v?.author ?? '')
	);
</script>

{#if v}
	<div class="mini" role="region" aria-label={t.player}>
		<div class="line"><Timeline compact /></div>
		<button class="open" onclick={() => (player.expanded = true)} aria-label={t.open}>
			<div class="disc">
				<Vinyl
					title={v.title}
					author={v.author}
					src={v.cover}
					playing={player.playing}
					label={0.5}
				/>
			</div>
			<span class="text">
				<strong>{v.title}</strong>
				{#if player.error}
					<span class="err" role="status">{player.error === 'fatal' ? t.errorFatal : t.error}</span>
				{:else}
					<span class="muted tabular">{sub}</span>
				{/if}
			</span>
		</button>
		<button class="ctl back" onclick={() => player.back()} aria-label={t.back(prefs.skipBack)}>
			<RotateCcw size={20} />
		</button>
		<button
			class="ctl play"
			onclick={() => player.toggle()}
			aria-label={player.playing ? t.pause : t.play}
		>
			{#if player.playing}<Pause size={22} fill="currentColor" />{:else}<Play
					size={22}
					fill="currentColor"
				/>{/if}
		</button>
	</div>
{/if}

<style>
	.mini {
		position: fixed;
		z-index: 15;
		left: calc(var(--layout-sidebar) + var(--sp-24));
		right: var(--sp-24);
		bottom: var(--sp-16);
		display: flex;
		align-items: center;
		gap: var(--sp-8);
		padding: var(--sp-8) var(--sp-8) var(--sp-8) var(--sp-8);
		border-radius: var(--r-pill);
		background: var(--c-ctrl);
		color: var(--c-ctrl-ink);
		box-shadow: 0 18px 40px -16px rgb(0 0 0 / 0.55);
		view-transition-name: mini-player;
		overflow: hidden;
	}

	.line {
		position: absolute;
		left: 56px;
		right: 72px;
		top: 0;
	}

	.line :global(.track) {
		background: color-mix(in oklab, var(--c-ctrl-ink) 18%, transparent);
	}

	.open {
		flex: 1;
		min-width: 0;
		display: flex;
		align-items: center;
		gap: var(--sp-12);
		border: 0;
		background: transparent;
		color: inherit;
		text-align: left;
		padding: 0;
	}

	.disc {
		width: 44px;
		flex: none;
	}

	.text {
		display: grid;
		min-width: 0;
	}

	.text > * {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	strong {
		font-size: var(--fs-14);
		font-weight: var(--fw-bold);
	}

	.text .muted {
		font-size: var(--fs-12);
		color: color-mix(in oklab, var(--c-ctrl-ink) 70%, transparent);
	}

	.err {
		font-size: var(--fs-12);
		font-weight: var(--fw-bold);
		color: var(--c-accent);
	}

	.ctl {
		display: grid;
		place-items: center;
		width: var(--layout-touch);
		height: var(--layout-touch);
		flex: none;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: inherit;
	}

	.play {
		width: 48px;
		height: 48px;
		background: var(--c-accent);
		color: var(--c-on-accent);
	}

	@media (max-width: 800px) {
		.mini {
			left: var(--sp-8);
			right: var(--sp-8);
			bottom: calc(68px + env(safe-area-inset-bottom));
			border-radius: var(--r-card-lg);
		}

		.back {
			display: none;
		}
	}
</style>
