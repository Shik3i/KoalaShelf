<script lang="ts">
	import { player } from './player.svelte.ts';
	import { prefs } from '#lib/prefs.svelte.ts';
	import { view } from '#lib/items.ts';
	import { clock } from '#lib/format.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Vinyl from '#lib/components/Vinyl.svelte';
	import RotateCcw from '@lucide/svelte/icons/rotate-ccw';
	import PlayPauseIcon from '#lib/components/PlayPauseIcon.svelte';
	import { fly } from 'svelte/transition';
	import { flyUp, placeRecord } from '#lib/motion.ts';
	import Timer from '@lucide/svelte/icons/timer';

	const t = $derived(i18n.m.player);
	/** Remaining sleep time (or "end of chapter"), shown as a small badge. */
	const sleepText = $derived(
		player.sleep.kind === 'minutes'
			? clock(
					player.sleepPausedLeft !== null ? player.sleepPausedLeft / 1000 : player.sleepRemaining
				)
			: player.sleep.kind === 'chapter'
				? t.timerEndOfChapter
				: player.sleepForecast
					? `≈${clock(player.sleepForecast.seconds)}`
					: ''
	);
	const v = $derived(player.item ? view(player.item) : null);
	const sub = $derived(
		player.chapter
			? `${player.chapter.title} · −${clock((player.chapter.end - player.currentTime) / player.displayRate)}`
			: (v?.author ?? '')
	);
</script>

{#if v}
	<div class="mini" transition:fly={flyUp(24)} role="region" aria-label={t.player}>
		<div class="line" style:--p={player.scopeProgress} aria-hidden="true"></div>
		<button class="open" onclick={() => (player.expanded = true)} aria-label={t.open}>
			{#key player.item?.id}
				<div class="disc" in:placeRecord>
					<Vinyl
						title={v.title}
						author={v.author}
						src={v.cover}
						playing={player.playing}
						speed={player.rate}
						label={0.5}
					/>
				</div>
			{/key}
			<span class="text">
				<strong>{v.title}</strong>
				{#if player.error}
					<span class="err" role="status">{player.error === 'fatal' ? t.errorFatal : t.error}</span>
				{:else}
					<span class="muted tabular">
						{#if sleepText}<span class="sleep" title={t.timerActive(sleepText)}
								><Timer size={12} aria-hidden="true" />{sleepText}</span
							>{/if}{sub}</span
					>
				{/if}
			</span>
		</button>
		<button class="ctl back" onclick={() => player.back()} aria-label={t.back(prefs.skipBack)}>
			<RotateCcw size={20} />
		</button>
		<button
			class="ctl play"
			class:is-loading={player.loadingId !== null}
			onclick={() => player.toggle()}
			aria-label={player.playing ? t.pause : t.play}
		>
			<PlayPauseIcon playing={player.playing} size={22} />
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
		transition: left var(--motion-fast) var(--motion-ease);
	}

	:global(html.is-resizing) .mini {
		transition: none !important;
	}

	/* Read-only progress line along the top edge; seeking happens in the full player. */
	.line {
		position: absolute;
		left: 28px;
		right: 28px;
		top: 0;
		height: 3px;
		border-radius: 0 0 3px 3px;
		background: color-mix(in oklab, var(--c-ctrl-ink) 18%, transparent);
		overflow: hidden;
	}

	.line::before {
		content: '';
		position: absolute;
		inset: 0 auto 0 0;
		width: calc(var(--p) * 100%);
		background: var(--c-accent);
		transition: width 1s linear;
	}

	.open {
		flex: 1;
		min-width: 0;
		/* Focus ring follows the rounded card. */
		border-radius: var(--r-card);
		display: flex;
		align-items: center;
		gap: var(--sp-12);
		border: 0;
		background: transparent;
		color: inherit;
		text-align: left;
		padding: 0;
		transition: scale var(--motion-fast) var(--motion-ease);
	}

	.open:active {
		scale: 0.985;
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

	.sleep {
		display: inline-flex;
		align-items: center;
		gap: 3px;
		margin-right: var(--sp-8);
		padding: 0 6px;
		border-radius: var(--r-pill);
		background: color-mix(in oklab, var(--c-accent) 25%, transparent);
		color: var(--c-ctrl-ink);
		font-weight: var(--fw-bold);
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
