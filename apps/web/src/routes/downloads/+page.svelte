<script lang="ts">
	import { byId, unique } from '#lib/unique.ts';
	import { downloads, supported, type DownloadedBook } from '#lib/downloads.svelte.ts';
	import { player } from '#lib/player/player.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { bytes, duration } from '#lib/format.ts';
	import { view } from '#lib/items.ts';
	import { progress } from '#lib/progress.svelte.ts';
	import { toasts } from '#lib/toast.svelte.ts';
	import { haptic } from '#lib/haptics.ts';
	import Cover from '#lib/components/Cover.svelte';
	import EmptyState from '#lib/components/EmptyState.svelte';
	import Play from '@lucide/svelte/icons/play';
	import Trash2 from '@lucide/svelte/icons/trash-2';
	import HardDrive from '@lucide/svelte/icons/hard-drive';
	import CheckCheck from '@lucide/svelte/icons/check-check';

	const t = $derived(i18n.m.downloads);
	let ready = $state(false);
	let usage = $state<{ size: number; quota: number; used: number } | null>(null);
	let covers = $state<Record<string, string>>({});
	let cleaning = $state(false);

	downloads.init().then(() => (ready = true));

	const list = $derived(
		[...downloads.books.values()].sort((a, b) => b.downloadedAt - a.downloadedAt)
	);

	const finishedDownloads = $derived(list.filter((b) => progress.status(b.id) === 'done'));

	async function cleanFinished() {
		if (cleaning || finishedDownloads.length === 0) return;
		cleaning = true;
		haptic.tick();
		const toRemove = [...finishedDownloads];
		for (const b of toRemove) {
			await downloads.remove(b.id);
		}
		haptic.success();
		toasts.show(t.cleaned(toRemove.length));
		cleaning = false;
	}

	// Recompute storage use whenever the set of downloads changes.
	$effect(() => {
		void list.length;
		downloads.usage().then((u) => (usage = u));
	});

	// Local cover blobs (work offline); revoked when leaving the page.
	$effect(() => {
		const urls: string[] = [];
		for (const b of list) {
			if (covers[b.id] || !b.cover) continue;
			downloads.coverUrl(b.id).then((u) => {
				if (!u) return;
				urls.push(u);
				covers = { ...covers, [b.id]: u };
			});
		}
		return () => urls.forEach((u) => URL.revokeObjectURL(u));
	});

	const src = (b: DownloadedBook) => (covers[b.id] ? () => covers[b.id] : null);
</script>

<svelte:head><title>{t.title} · KoalaShelf</title></svelte:head>

<header>
	<h1>{t.title}</h1>
	{#if usage && list.length}
		<span class="muted small"
			>{t.usage(bytes(usage.size), bytes(Math.max(0, usage.quota - usage.used)))}</span
		>
	{/if}
</header>

{#if usage && list.length}
	{@const bookPct = Math.min(100, Math.max(0, (usage.size / (usage.quota || 1)) * 100))}
	{@const otherUsed = Math.max(0, usage.used - usage.size)}
	{@const otherPct = Math.min(100 - bookPct, Math.max(0, (otherUsed / (usage.quota || 1)) * 100))}
	{@const freePct = Math.max(0, 100 - bookPct - otherPct)}
	<section class="radar-card card">
		<div class="radar-header">
			<div class="radar-title">
				<HardDrive size={18} class="accent-icon" />
				<strong>{t.storageRadar}</strong>
			</div>
			{#if finishedDownloads.length > 0}
				<button class="btn-clean" onclick={cleanFinished} disabled={cleaning}>
					<CheckCheck size={16} />
					<span>{t.cleanFinished(finishedDownloads.length)}</span>
				</button>
			{/if}
		</div>

		<div
			class="radar-meter"
			role="progressbar"
			aria-label={t.storageRadar}
			aria-valuemin={0}
			aria-valuemax={100}
			aria-valuenow={Math.round(bookPct)}
		>
			<div
				class="bar-segment books"
				style:width="{Math.max(2, bookPct)}%"
				title="{t.audiobooksUsed}: {bytes(usage.size)}"
			></div>
			<div
				class="bar-segment other"
				style:width="{otherPct}%"
				title="{t.otherUsed}: {bytes(otherUsed)}"
			></div>
			<div
				class="bar-segment free"
				style:width="{freePct}%"
				title="{t.freeSpace}: {bytes(Math.max(0, usage.quota - usage.used))}"
			></div>
		</div>

		<div class="radar-legend">
			<div class="legend-item">
				<span class="dot books"></span>
				<span class="muted">{t.audiobooksUsed}</span>
				<strong>{bytes(usage.size)}</strong>
			</div>
			<div class="legend-item">
				<span class="dot other"></span>
				<span class="muted">{t.otherUsed}</span>
				<strong>{bytes(otherUsed)}</strong>
			</div>
			<div class="legend-item">
				<span class="dot free"></span>
				<span class="muted">{t.freeSpace}</span>
				<strong>{bytes(Math.max(0, usage.quota - usage.used))}</strong>
			</div>
		</div>
	</section>
{/if}

{#if !supported()}
	<EmptyState text={t.unsupported} />
{:else if ready && !list.length}
	<EmptyState text={t.empty} />
{:else}
	<ul class="list">
		{#each unique(list, byId) as b (b.id)}
			{@const v = view(b.item)}
			<li>
				<a class="c" href="/book/{b.id}"
					><Cover title={v.title} author={v.author} src={src(b)} sizes="64px" /></a
				>
				<a class="meta" href="/book/{b.id}">
					<strong>{v.title}</strong>
					<span class="muted">{v.author} · {duration(v.duration)} · {bytes(b.size)}</span>
				</a>
				<button class="icon play" onclick={() => player.play(b.id)} aria-label={i18n.m.player.play}>
					<Play size={18} fill="currentColor" />
				</button>
				<button
					class="icon"
					onclick={() => downloads.remove(b.id)}
					title={t.remove}
					aria-label={t.remove}
				>
					<Trash2 size={18} />
				</button>
			</li>
		{/each}
	</ul>
{/if}

<style>
	header {
		display: flex;
		align-items: end;
		justify-content: space-between;
		flex-wrap: wrap;
		gap: var(--sp-8) var(--sp-16);
		margin-bottom: var(--sp-24);
	}

	.small {
		font-size: var(--fs-13);
	}

	.list {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--sp-8);
	}

	li {
		display: grid;
		grid-template-columns: 64px minmax(0, 1fr) auto auto;
		align-items: center;
		gap: var(--sp-12);
		padding: var(--sp-8);
		border-radius: var(--r-card);
		background: var(--c-card);
	}

	.meta {
		display: grid;
		min-width: 0;
	}

	.meta > * {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.meta .muted {
		font-size: var(--fs-13);
	}

	.icon {
		display: grid;
		place-items: center;
		width: var(--layout-touch);
		height: var(--layout-touch);
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: var(--c-muted);
	}

	.icon:hover {
		color: var(--c-ink);
		background: var(--c-bg);
	}

	.play {
		background: var(--c-accent);
		color: var(--c-on-accent);
	}

	.play:hover {
		background: var(--c-accent);
		color: var(--c-on-accent);
		filter: brightness(1.06);
	}

	.radar-card {
		padding: var(--sp-16) var(--sp-20);
		margin-bottom: var(--sp-24);
		display: flex;
		flex-direction: column;
		gap: var(--sp-12);
	}

	.radar-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--sp-12);
		flex-wrap: wrap;
	}

	.radar-title {
		display: flex;
		align-items: center;
		gap: var(--sp-8);
		font-size: var(--fs-15);
	}

	:global(.accent-icon) {
		color: var(--c-accent);
	}

	.btn-clean {
		display: inline-flex;
		align-items: center;
		gap: var(--sp-6);
		padding: var(--sp-6) var(--sp-12);
		border: 1px solid var(--c-line);
		border-radius: 999px;
		background: var(--c-bg);
		color: var(--c-ink);
		font-size: var(--fs-12);
		font-weight: var(--fw-semibold);
		cursor: pointer;
		transition: all 0.15s ease;
	}

	.btn-clean:hover:not(:disabled) {
		background: var(--c-card);
		border-color: var(--c-accent);
		color: var(--c-accent);
	}

	.btn-clean:disabled {
		opacity: 0.5;
		cursor: default;
	}

	.radar-meter {
		display: flex;
		height: 10px;
		width: 100%;
		border-radius: 999px;
		background: var(--c-bg);
		overflow: hidden;
		gap: 2px;
	}

	.bar-segment {
		height: 100%;
		transition: width 0.3s ease;
	}

	.bar-segment.books {
		background: var(--c-accent);
	}

	.bar-segment.other {
		background: var(--c-muted);
		opacity: 0.6;
	}

	.bar-segment.free {
		background: var(--c-line);
	}

	.radar-legend {
		display: flex;
		gap: var(--sp-16);
		flex-wrap: wrap;
		font-size: var(--fs-13);
	}

	.legend-item {
		display: flex;
		align-items: center;
		gap: var(--sp-6);
	}

	.dot {
		width: 8px;
		height: 8px;
		border-radius: 50%;
	}

	.dot.books {
		background: var(--c-accent);
	}

	.dot.other {
		background: var(--c-muted);
		opacity: 0.6;
	}

	.dot.free {
		background: var(--c-line);
	}
</style>
