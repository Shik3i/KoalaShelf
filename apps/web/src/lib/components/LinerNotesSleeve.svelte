<script lang="ts">
	import Cover from './Cover.svelte';
	import { clock, duration } from '#lib/format.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { haptic } from '#lib/haptics.ts';
	import type { Chapter } from '@koalashelf/abs-client';

	interface Props {
		title: string;
		author: string;
		src?: ((width: number) => string) | null;
		description?: string;
		chapters?: Chapter[];
		narrators?: string[];
		publisher?: string | null;
		year?: string | number | null;
		catalogId: string;
		durationSec?: number;
		format?: string;
		onPlayChapter?: (start: number) => void;
	}

	let {
		title,
		author,
		src = null,
		description = '',
		chapters = [],
		narrators = [],
		publisher = '',
		year = '',
		catalogId,
		durationSec = 0,
		format = '',
		onPlayChapter
	}: Props = $props();

	const t = $derived(i18n.m.book);
	let flipped = $state(false);

	function toggle() {
		flipped = !flipped;
		haptic.tap();
	}

	function onKeydown(e: KeyboardEvent) {
		if (e.key === ' ' || e.key === 'Enter') {
			e.preventDefault();
			toggle();
		}
	}
</script>

<div
	class="sleeve-card"
	class:flipped
	role="region"
	aria-label="{title} – {flipped ? t.flipFront : t.linerNotes}"
>
	<div class="flipper">
		<!-- Front Side: Cover & Flip Pill -->
		<div class="side front" aria-hidden={flipped} inert={flipped}>
			<Cover {title} {author} {src} sizes="(max-width: 800px) 70vw, 300px" eager />
			<button
				type="button"
				class="flip-pill"
				onclick={(e) => {
					e.stopPropagation();
					toggle();
				}}
				onkeydown={onKeydown}
				title="{t.linerNotes} (3D Flip)"
				aria-label={t.linerNotes}
			>
				<span class="icon" aria-hidden="true">↻</span>
				<span>{t.linerNotes}</span>
			</button>
		</div>

		<!-- Back Side: Vintage Liner Notes / Record Sleeve Back -->
		<div class="side back" aria-hidden={!flipped} inert={!flipped}>
			<div class="back-inner">
				<header class="back-header">
					<div class="vintage-top">
						<span class="cat-num">KS-{catalogId.slice(0, 6).toUpperCase()}</span>
						<span class="hifi-badge">{t.stereoHifi}</span>
					</div>
					<h2 class="vintage-title">{title}</h2>
					<p class="vintage-by">
						<strong>{author}</strong>
						{#if narrators.length}
							<span class="narrator-line">· {t.voiceLabel}: {narrators.join(', ')}</span>
						{/if}
					</p>
				</header>

				<div class="back-scroll">
					{#if chapters.length > 0}
						<div class="section">
							<h3 class="section-title">{t.tracklist}</h3>
							<ol class="tracklist">
								{#each chapters.slice(0, 16) as c, idx (idx)}
									<li>
										<button
											type="button"
											class="track-row"
											onclick={() => onPlayChapter?.(c.start)}
											title={c.title}
										>
											<span class="idx">{idx + 1}.</span>
											<span class="name">{c.title}</span>
											<span class="time">{clock(c.end - c.start)}</span>
										</button>
									</li>
								{/each}
								{#if chapters.length > 16}
									<li class="more-tracks">
										+{chapters.length - 16}
										{t.chapterCount(chapters.length)}
									</li>
								{/if}
							</ol>
						</div>
					{/if}

					{#if description}
						<div class="section">
							<h3 class="section-title">{t.description}</h3>
							<p class="notes-text">{description}</p>
						</div>
					{/if}
				</div>

				<footer class="back-footer">
					<div class="specs">
						{#if durationSec}<span>{duration(durationSec)}</span>{/if}
						{#if format}<span>{format}</span>{/if}
						{#if year}<span>{year}</span>{/if}
						{#if publisher}<span>{publisher}</span>{/if}
					</div>
					<button
						type="button"
						class="flip-back-btn"
						onclick={(e) => {
							e.stopPropagation();
							toggle();
						}}
						aria-label={t.flipFront}
					>
						↻ {t.flipFront}
					</button>
				</footer>
			</div>
		</div>
	</div>
</div>

<style>
	.sleeve-card {
		position: absolute;
		inset: 0 auto 0 0;
		width: var(--size, 300px);
		height: var(--size, 300px);
		z-index: 1;
		perspective: 1200px;
	}

	.flipper {
		position: relative;
		width: 100%;
		height: 100%;
		transform-style: preserve-3d;
		transition: transform 650ms var(--motion-ease, cubic-bezier(0.2, 0.8, 0.2, 1));
	}

	.sleeve-card.flipped .flipper {
		transform: rotateY(180deg);
	}

	.side {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		backface-visibility: hidden;
		-webkit-backface-visibility: hidden;
		border-radius: var(--r-card, 6px);
		overflow: hidden;
		box-shadow:
			0 12px 28px -8px rgb(0 0 0 / 0.45),
			0 0 0 1px rgb(0 0 0 / 0.15);
	}

	.side.front {
		z-index: 2;
		transform: rotateY(0deg);
	}

	.side.back {
		transform: rotateY(180deg);
		background: var(--c-card);
		color: var(--c-ink);
		display: flex;
		flex-direction: column;
		border: 1px solid var(--c-line);
	}

	.sleeve-card:not(.flipped) .side.back {
		visibility: hidden;
	}

	.sleeve-card.flipped .side.front {
		visibility: hidden;
	}

	.flip-pill {
		position: absolute;
		right: var(--sp-10, 10px);
		bottom: var(--sp-10, 10px);
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 5px 10px;
		border-radius: var(--r-pill, 9999px);
		background: rgb(0 0 0 / 0.72);
		backdrop-filter: blur(8px);
		color: #ffffff;
		font-size: var(--fs-12, 12px);
		font-weight: var(--fw-semibold, 600);
		border: 1px solid rgb(255 255 255 / 0.2);
		cursor: pointer;
		transition:
			background var(--motion-fast, 150ms) var(--motion-ease),
			scale var(--motion-fast, 150ms) var(--motion-ease);
	}

	.flip-pill:hover,
	.flip-pill:focus-visible {
		background: var(--c-accent);
		color: var(--c-on-accent, #ffffff);
		scale: 1.04;
		outline: none;
	}

	.flip-pill .icon {
		font-size: 14px;
	}

	.back-inner {
		display: flex;
		flex-direction: column;
		height: 100%;
		padding: var(--sp-16, 16px);
		gap: var(--sp-10, 10px);
		background:
			radial-gradient(circle at 10% 10%, rgb(255 255 255 / 0.03), transparent 60%), var(--c-card);
	}

	.back-header {
		border-bottom: 1px solid var(--c-line);
		padding-bottom: var(--sp-8, 8px);
	}

	.vintage-top {
		display: flex;
		justify-content: space-between;
		align-items: center;
		font-size: 10px;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--c-muted);
		font-weight: var(--fw-bold, 700);
		margin-bottom: 2px;
	}

	.hifi-badge {
		border: 1px solid currentColor;
		padding: 1px 4px;
		border-radius: 2px;
	}

	.vintage-title {
		font-family: var(--font-display, serif);
		font-size: var(--fs-15, 15px);
		line-height: 1.2;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		margin: 2px 0 1px;
	}

	.vintage-by {
		font-size: var(--fs-12, 12px);
		color: var(--c-muted);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		margin: 0;
	}

	.back-scroll {
		flex: 1;
		min-height: 0;
		overflow-y: auto;
		scrollbar-width: thin;
		display: flex;
		flex-direction: column;
		gap: var(--sp-12, 12px);
		padding-right: 4px;
	}

	.section-title {
		font-size: 11px;
		text-transform: uppercase;
		letter-spacing: 0.08em;
		color: var(--c-accent);
		font-weight: var(--fw-bold, 700);
		margin-bottom: 4px;
	}

	.tracklist {
		list-style: none;
		padding: 0;
		margin: 0;
		display: flex;
		flex-direction: column;
		gap: 3px;
	}

	.track-row {
		display: flex;
		width: 100%;
		gap: 6px;
		font-size: var(--fs-12, 12px);
		padding: 2px 4px;
		border-radius: 3px;
		background: transparent;
		border: none;
		color: var(--c-ink);
		cursor: pointer;
		text-align: left;
		transition: background var(--motion-fast, 150ms);
	}

	.track-row:hover {
		background: var(--c-line);
	}

	.track-row .idx {
		color: var(--c-muted);
		font-size: 11px;
		min-width: 18px;
	}

	.track-row .name {
		flex: 1;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.track-row .time {
		color: var(--c-muted);
		font-family: monospace;
		font-size: 11px;
	}

	.more-tracks {
		font-size: 11px;
		color: var(--c-muted);
		padding: 2px 4px;
	}

	.notes-text {
		font-size: var(--fs-12, 12px);
		line-height: 1.45;
		color: var(--c-ink);
		opacity: 0.9;
		margin: 0;
		white-space: pre-wrap;
	}

	.back-footer {
		border-top: 1px solid var(--c-line);
		padding-top: var(--sp-8, 8px);
		display: flex;
		justify-content: space-between;
		align-items: center;
		font-size: 11px;
		color: var(--c-muted);
	}

	.specs {
		display: flex;
		flex-wrap: wrap;
		gap: 6px;
	}

	.flip-back-btn {
		background: var(--c-ctrl, #2a2119);
		color: var(--c-ctrl-ink, #ffffff);
		border: none;
		border-radius: var(--r-pill, 9999px);
		padding: 3px 10px;
		font-size: 11px;
		font-weight: var(--fw-semibold, 600);
		cursor: pointer;
		transition: scale var(--motion-fast, 150ms);
	}

	.flip-back-btn:hover {
		scale: 1.05;
	}
</style>
