<script lang="ts">
	import { goto } from '$app/navigation';
	import type { LibraryItemMinified } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { progress } from '#lib/progress.svelte.ts';
	import { player } from '#lib/player/player.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { haptic } from '#lib/haptics.ts';
	import { coverSrc, view } from '#lib/items.ts';
	import { duration as formatDuration } from '#lib/format.ts';
	import Cover from './Cover.svelte';
	import X from '@lucide/svelte/icons/x';
	import Play from '@lucide/svelte/icons/play';
	import Disc from '@lucide/svelte/icons/disc-3';
	import Sparkles from '@lucide/svelte/icons/sparkles';
	import ChevronLeft from '@lucide/svelte/icons/chevron-left';
	import ChevronRight from '@lucide/svelte/icons/chevron-right';
	import RotateCw from '@lucide/svelte/icons/rotate-cw';
	import BookOpen from '@lucide/svelte/icons/book-open';

	let { open = $bindable(false) }: { open?: boolean } = $props();

	const t = $derived(i18n.m.crateDigger);

	let dialog = $state<HTMLDialogElement>();
	let items = $state<LibraryItemMinified[]>([]);
	let loading = $state(false);
	let error = $state<string | null>(null);
	let filter = $state<'all' | 'unfinished' | 'short' | 'long'>('unfinished');
	let at = $state(0);

	const filtered = $derived.by(() => {
		return items.filter((item) => {
			const d = item.media?.duration ?? 0;
			const isDone = progress.status(item.id) === 'done';
			if (filter === 'unfinished' && isDone) return false;
			if (filter === 'short' && d >= 5 * 3600) return false;
			if (filter === 'long' && d <= 10 * 3600) return false;
			return true;
		});
	});

	const current = $derived<LibraryItemMinified | undefined>(filtered[at]);
	const currentView = $derived(current ? view(current) : null);
	const currentDuration = $derived(current?.media?.duration ? formatDuration(current.media.duration) : '');

	async function load() {
		const lib = session.libraryId;
		if (!lib) return;
		loading = true;
		error = null;
		haptic.tick();
		try {
			const res = await session.api.libraryItems(lib, {
				limit: 40,
				sort: 'random'
			});
			items = res.results;
			at = 0;
		} catch (e) {
			error = (e as Error).message;
		} finally {
			loading = false;
		}
	}

	$effect(() => {
		if (!dialog) return;
		if (open && !dialog.open) {
			dialog.showModal();
			if (items.length === 0) void load();
		} else if (!open && dialog.open) {
			dialog.close();
		}
	});

	function close() {
		open = false;
	}

	function next() {
		if (at < filtered.length - 1) {
			at++;
			haptic.tick();
		}
	}

	function prev() {
		if (at > 0) {
			at--;
			haptic.tick();
		}
	}

	function playCurrent() {
		if (!current) return;
		haptic.success();
		void player.play(current.id);
		close();
	}

	function viewCurrent() {
		if (!current) return;
		haptic.tap();
		void goto(`/book/${current.id}`);
		close();
	}

	function onKeyDown(e: KeyboardEvent) {
		if (e.key === 'ArrowRight') {
			next();
		} else if (e.key === 'ArrowLeft') {
			prev();
		} else if (e.key === 'Enter' && e.target === dialog) {
			playCurrent();
		}
	}
</script>

<dialog
	bind:this={dialog}
	class="digger-dialog"
	onclose={() => (open = false)}
	onclick={(e) => e.target === dialog && close()}
	onkeydown={onKeyDown}
>
	<div class="digger-panel">
		<header class="digger-header">
			<div class="digger-title-group">
				<div class="title-row">
					<Sparkles size={18} class="accent-icon" />
					<h2>{t.title}</h2>
				</div>
				<p class="subtitle">{t.subtitle}</p>
			</div>
			<div class="header-actions">
				<button class="btn-refresh" onclick={load} title={t.digNew} aria-label={t.digNew} disabled={loading}>
					<RotateCw size={16} class={loading ? 'spinning' : ''} />
					<span>{t.digNew}</span>
				</button>
				<button class="btn-close" onclick={close} aria-label="Close">
					<X size={20} />
				</button>
			</div>
		</header>

		<div class="filter-bar">
			<button
				class="chip {filter === 'all' ? 'active' : ''}"
				onclick={() => { filter = 'all'; at = 0; }}
			>{t.filterAll}</button>
			<button
				class="chip {filter === 'unfinished' ? 'active' : ''}"
				onclick={() => { filter = 'unfinished'; at = 0; }}
			>{t.filterUnfinished}</button>
			<button
				class="chip {filter === 'short' ? 'active' : ''}"
				onclick={() => { filter = 'short'; at = 0; }}
			>{t.filterShort}</button>
			<button
				class="chip {filter === 'long' ? 'active' : ''}"
				onclick={() => { filter = 'long'; at = 0; }}
			>{t.filterLong}</button>
		</div>

		{#if loading && items.length === 0}
			<div class="loading-state">
				<Disc size={36} class="spinning" />
				<p class="muted">{i18n.m.common.loading}</p>
			</div>
		{:else if error}
			<div class="error-state">
				<p class="muted">{error}</p>
				<button class="btn primary" onclick={load}>{i18n.m.common.retry}</button>
			</div>
		{:else if filtered.length === 0}
			<div class="empty-state">
				<p class="muted">{t.empty}</p>
				<button class="btn outline" onclick={() => { filter = 'all'; at = 0; }}>{t.filterAll}</button>
			</div>
		{:else if current && currentView}
			<div class="record-stage">
				<button
					class="nav-arrow left"
					onclick={prev}
					disabled={at === 0}
					aria-label="Previous record"
				>
					<ChevronLeft size={24} />
				</button>

				<div class="record-wrapper">
					<div class="sleeve-card">
						<div class="cover-container">
							<Cover
								src={currentView.cover}
								title={currentView.title}
								author={currentView.author}
								sizes="220px"
							/>
						</div>
						<!-- Stylized vinyl edge peeking out from the sleeve -->
						<div class="vinyl-disc-peak" aria-hidden="true">
							<div class="grooves"></div>
							<div class="center-label"></div>
						</div>
					</div>

					<div class="record-meta">
						<span class="count-tag">{t.recordOf(at + 1, filtered.length)}</span>
						<h3 class="record-title" title={currentView.title}>{currentView.title}</h3>
						<p class="record-author">{currentView.author}</p>
						{#if currentDuration}
							<p class="record-duration muted">{currentDuration}</p>
						{/if}

						<div class="cta-row">
							<button class="btn primary play-btn" onclick={playCurrent}>
								<Play size={16} fill="currentColor" />
								<span>{t.dropNeedle}</span>
							</button>
							<button class="btn outline sleeve-btn" onclick={viewCurrent}>
								<BookOpen size={16} />
								<span>{t.pullSleeve}</span>
							</button>
						</div>
					</div>
				</div>

				<button
					class="nav-arrow right"
					onclick={next}
					disabled={at === filtered.length - 1}
					aria-label="Next record"
				>
					<ChevronRight size={24} />
				</button>
			</div>

			<!-- Mini thumb track to jump to any record in the crate -->
			<div class="crate-track">
				{#each filtered as it, idx (it.id)}
					<button
						class="track-thumb {idx === at ? 'active' : ''}"
						onclick={() => { at = idx; haptic.tick(); }}
						title={it.media?.metadata?.title ?? ''}
					></button>
				{/each}
			</div>
		{/if}
	</div>
</dialog>

<style>
	.digger-dialog {
		margin: auto;
		padding: 0;
		border: 1px solid var(--c-line);
		border-radius: var(--r-card-lg, 20px);
		background: var(--c-card);
		color: var(--c-ink);
		max-width: 580px;
		width: calc(100vw - 32px);
		box-shadow: 0 24px 60px rgba(0, 0, 0, 0.45);
		overflow: hidden;
	}

	.digger-dialog::backdrop {
		background: rgba(0, 0, 0, 0.65);
		backdrop-filter: blur(8px);
	}

	.digger-panel {
		display: flex;
		flex-direction: column;
		padding: var(--sp-20);
		gap: var(--sp-16);
	}

	.digger-header {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: var(--sp-12);
	}

	.title-row {
		display: flex;
		align-items: center;
		gap: var(--sp-8);
	}

	.title-row h2 {
		margin: 0;
		font-size: var(--fs-20);
		font-weight: var(--fw-bold);
	}

	:global(.accent-icon) {
		color: var(--c-accent);
	}

	.subtitle {
		margin: var(--sp-4) 0 0;
		font-size: var(--fs-13);
		color: var(--c-muted);
	}

	.header-actions {
		display: flex;
		align-items: center;
		gap: var(--sp-8);
	}

	.btn-refresh {
		display: inline-flex;
		align-items: center;
		gap: var(--sp-6);
		padding: var(--sp-6) var(--sp-12);
		border: 1px solid var(--c-line);
		border-radius: 999px;
		background: var(--c-bg);
		color: var(--c-ink);
		font-size: var(--fs-13);
		font-weight: var(--fw-semibold);
		cursor: pointer;
		transition: background 0.15s ease;
	}

	.btn-refresh:hover:not(:disabled) {
		background: var(--c-card);
	}

	.btn-close {
		display: grid;
		place-items: center;
		width: 36px;
		height: 36px;
		border: 0;
		border-radius: 50%;
		background: var(--c-bg);
		color: var(--c-ink);
		cursor: pointer;
	}

	.filter-bar {
		display: flex;
		gap: var(--sp-6);
		flex-wrap: wrap;
	}

	.chip {
		padding: var(--sp-4) var(--sp-10);
		border: 1px solid var(--c-line);
		border-radius: 999px;
		background: transparent;
		color: var(--c-muted);
		font-size: var(--fs-12);
		font-weight: var(--fw-semibold);
		cursor: pointer;
		transition: all 0.15s ease;
	}

	.chip.active {
		background: var(--c-ink);
		color: var(--c-bg);
		border-color: var(--c-ink);
	}

	.record-stage {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--sp-12);
		padding: var(--sp-8) 0;
	}

	.nav-arrow {
		display: grid;
		place-items: center;
		width: 40px;
		height: 40px;
		border: 0;
		border-radius: 50%;
		background: var(--c-bg);
		color: var(--c-ink);
		cursor: pointer;
		flex-shrink: 0;
		transition: opacity 0.15s ease;
	}

	.nav-arrow:disabled {
		opacity: 0.25;
		cursor: default;
	}

	.record-wrapper {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: var(--sp-16);
		flex: 1;
		min-width: 0;
		text-align: center;
	}

	.sleeve-card {
		position: relative;
		width: 200px;
		height: 200px;
		margin: 0 auto;
	}

	.cover-container {
		position: relative;
		z-index: 2;
		width: 100%;
		height: 100%;
		border-radius: var(--r-card, 12px);
		overflow: hidden;
		box-shadow: 0 10px 24px rgba(0, 0, 0, 0.35);
	}

	.vinyl-disc-peak {
		position: absolute;
		top: 6px;
		right: -18px;
		width: 188px;
		height: 188px;
		border-radius: 50%;
		background: radial-gradient(circle at center, #111 35%, #252525 65%, #0d0d0d 100%);
		box-shadow: inset 0 0 4px rgba(255, 255, 255, 0.1), 3px 5px 12px rgba(0, 0, 0, 0.4);
		z-index: 1;
		pointer-events: none;
		transition: transform 0.3s cubic-bezier(0.2, 0.8, 0.2, 1);
	}

	.record-wrapper:hover .vinyl-disc-peak {
		transform: translateX(12px) rotate(15deg);
	}

	.vinyl-disc-peak .grooves {
		position: absolute;
		inset: 12px;
		border-radius: 50%;
		border: 1px dashed rgba(255, 255, 255, 0.08);
	}

	.vinyl-disc-peak .center-label {
		position: absolute;
		top: 50%;
		left: 50%;
		transform: translate(-50%, -50%);
		width: 44px;
		height: 44px;
		border-radius: 50%;
		background: var(--c-accent);
	}

	.record-meta {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: var(--sp-4);
		max-width: 100%;
	}

	.count-tag {
		font-size: var(--fs-12);
		font-weight: var(--fw-semibold);
		color: var(--c-accent);
		text-transform: uppercase;
		letter-spacing: 0.05em;
	}

	.record-title {
		margin: 0;
		font-size: var(--fs-18);
		font-weight: var(--fw-bold);
		line-height: 1.25;
		display: -webkit-box;
		line-clamp: 2;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}

	.record-author {
		margin: 0;
		font-size: var(--fs-14);
		color: var(--c-muted);
	}

	.record-duration {
		margin: 0;
		font-size: var(--fs-13);
	}

	.cta-row {
		display: flex;
		gap: var(--sp-10);
		margin-top: var(--sp-10);
		flex-wrap: wrap;
		justify-content: center;
	}

	.play-btn {
		display: inline-flex;
		align-items: center;
		gap: var(--sp-6);
	}

	.sleeve-btn {
		display: inline-flex;
		align-items: center;
		gap: var(--sp-6);
	}

	.crate-track {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 6px;
		padding: var(--sp-4) 0;
		overflow-x: auto;
	}

	.track-thumb {
		width: 8px;
		height: 8px;
		border-radius: 50%;
		border: 0;
		background: var(--c-line);
		padding: 0;
		cursor: pointer;
		transition: all 0.2s ease;
	}

	.track-thumb.active {
		background: var(--c-accent);
		transform: scale(1.4);
	}

	.loading-state,
	.error-state,
	.empty-state {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: var(--sp-12);
		min-height: 220px;
	}

	:global(.spinning) {
		animation: spin 1.5s linear infinite;
	}

	@keyframes spin {
		from { transform: rotate(0deg); }
		to { transform: rotate(360deg); }
	}
</style>
