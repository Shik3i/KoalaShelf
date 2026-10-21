<script lang="ts">
	import { player } from './player.svelte.ts';
	import { prefs } from '#lib/prefs.svelte.ts';
	import { view } from '#lib/items.ts';
	import { clock } from '#lib/format.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Cover from '#lib/components/Cover.svelte';
	import PlayPauseIcon from '#lib/components/PlayPauseIcon.svelte';
	import RotateCcw from '@lucide/svelte/icons/rotate-ccw';
	import RotateCw from '@lucide/svelte/icons/rotate-cw';
	import SkipBack from '@lucide/svelte/icons/skip-back';
	import SkipForward from '@lucide/svelte/icons/skip-forward';

	/** Compact always-on-top player (Document Picture-in-Picture window). */
	const t = $derived(i18n.m.player);
	const v = $derived(player.item ? view(player.item) : null);
	const scope = $derived(player.scope);
</script>

{#if v}
	<div class="pip" style:--p={player.scopeProgress}>
		<div class="cover"><Cover title={v.title} author={v.author} src={v.cover} sizes="96px" /></div>
		<div class="text">
			<strong>{v.title}</strong>
			<span class="muted">{player.chapter?.title ?? v.author}</span>
			<span class="muted tabular"
				>{clock(player.currentTime - scope.start)} / {clock(scope.end - scope.start)}</span
			>
		</div>
		<div class="ctl">
			<button onclick={() => player.prevChapter()} aria-label={t.prevChapter}
				><SkipBack size={18} /></button
			>
			<button onclick={() => player.back()} aria-label={t.back(prefs.skipBack)}
				><RotateCcw size={20} /></button
			>
			<button
				class="play"
				onclick={() => player.toggle()}
				aria-label={player.playing ? t.pause : t.play}
			>
				<PlayPauseIcon playing={player.playing} size={22} />
			</button>
			<button onclick={() => player.forward()} aria-label={t.forward(prefs.skipForward)}
				><RotateCw size={20} /></button
			>
			<button onclick={() => player.nextChapter()} aria-label={t.nextChapter}
				><SkipForward size={18} /></button
			>
		</div>
		<div class="line" aria-hidden="true"></div>
	</div>
{:else}
	<div class="pip empty">
		<div class="empty-state">
			<strong>KoalaShelf Mini</strong>
			<span class="muted">Kein aktives Hörbuch</span>
		</div>
	</div>
{/if}

<style>
	.pip {
		position: fixed;
		inset: 0;
		display: grid;
		grid-template-columns: auto 1fr;
		grid-template-rows: 1fr auto;
		align-items: center;
		gap: 10px 14px;
		padding: 14px 14px 18px;
		background: var(--c-bg);
		color: var(--c-ink);
		overflow: hidden;
		-webkit-app-region: drag;
	}

	.pip.empty {
		display: grid;
		grid-template-columns: 1fr;
		place-items: center;
		text-align: center;
	}

	.empty-state {
		display: grid;
		gap: 4px;
	}

	.cover {
		width: min(96px, 38vw);
		border-radius: 10px;
		overflow: hidden;
	}

	.text {
		display: grid;
		gap: 2px;
		min-width: 0;
	}

	.text > * {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.text .muted {
		font-size: var(--fs-13);
	}

	.ctl {
		grid-column: 1 / -1;
		display: flex;
		justify-content: center;
		gap: 6px;
	}

	.ctl button {
		-webkit-app-region: no-drag;
		display: grid;
		place-items: center;
		width: 40px;
		height: 40px;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: var(--c-ink);
	}

	.ctl button:hover {
		background: color-mix(in oklab, var(--c-ink) 8%, transparent);
	}

	.ctl .play {
		width: 48px;
		height: 48px;
		background: var(--c-accent);
		color: var(--c-on-accent);
	}

	.line {
		position: absolute;
		left: 0;
		right: 0;
		bottom: 0;
		height: 4px;
		background: color-mix(in oklab, var(--c-ink) 12%, transparent);
	}

	.line::after {
		content: '';
		position: absolute;
		inset: 0;
		transform-origin: left;
		scale: var(--p) 1;
		background: var(--c-accent);
	}

	/* Very small window: just the controls. */
	@media (max-height: 150px) {
		.cover,
		.text {
			display: none;
		}

		.pip {
			grid-template-rows: 1fr;
			padding: 8px;
		}
	}
</style>
