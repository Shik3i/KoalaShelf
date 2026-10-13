<script lang="ts">
	import { downloads, supported, type DownloadedBook } from '#lib/downloads.svelte.ts';
	import { player } from '#lib/player/player.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { bytes, duration } from '#lib/format.ts';
	import { view } from '#lib/items.ts';
	import Cover from '#lib/components/Cover.svelte';
	import EmptyState from '#lib/components/EmptyState.svelte';
	import Play from '@lucide/svelte/icons/play';
	import Trash2 from '@lucide/svelte/icons/trash-2';

	const t = $derived(i18n.m.downloads);
	let ready = $state(false);
	let usage = $state<{ size: number; quota: number; used: number } | null>(null);
	let covers = $state<Record<string, string>>({});

	downloads.init().then(() => (ready = true));

	const list = $derived(
		[...downloads.books.values()].sort((a, b) => b.downloadedAt - a.downloadedAt)
	);

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

{#if !supported()}
	<EmptyState text={t.unsupported} />
{:else if ready && !list.length}
	<EmptyState text={t.empty} />
{:else}
	<ul class="list">
		{#each list as b (b.id)}
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
</style>
