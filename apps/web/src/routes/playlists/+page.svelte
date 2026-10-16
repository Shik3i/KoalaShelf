<script lang="ts">
	import { byId, unique } from '#lib/unique.ts';
	import type { Playlist } from '@koalashelf/abs-client';
	import { goto } from '$app/navigation';
	import { session } from '#lib/session.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { duration } from '#lib/format.ts';
	import CoverMosaic from '#lib/components/CoverMosaic.svelte';
	import EmptyState from '#lib/components/EmptyState.svelte';
	import Plus from '@lucide/svelte/icons/plus';

	const t = $derived(i18n.m.playlists);
	let lists = $state.raw<Playlist[] | null>(null);
	let error = $state<string | null>(null);
	let name = $state('');

	$effect(() => {
		const lib = session.libraryId;
		if (!lib) return;
		session.api
			.playlists(lib)
			.then((l) => (lists = l.sort((a, b) => b.lastUpdate - a.lastUpdate)))
			.catch((e) => (error = (e as Error).message));
	});

	async function create(e: SubmitEvent) {
		e.preventDefault();
		const n = name.trim();
		if (!n || !session.libraryId) return;
		const p = await session.api.createPlaylist(session.libraryId, n);
		goto(`/playlists/${p.id}`);
	}
</script>

<svelte:head><title>{t.title} · KoalaShelf</title></svelte:head>

<h1>{t.title}</h1>

<form class="new" onsubmit={create}>
	<input bind:value={name} placeholder={t.newName} aria-label={t.newName} maxlength="80" />
	<button class="btn primary" type="submit" disabled={!name.trim()}>
		<Plus size={16} aria-hidden="true" />
		{t.create}
	</button>
</form>

{#if error}<p class="muted">{i18n.m.common.error(error)}</p>{/if}
{#if lists && !lists.length}<EmptyState text={t.empty} />{/if}

<div class="grid">
	{#if !lists && !error}
		{#each [0, 1, 2] as i (i)}<div
				class="skeleton"
				style="aspect-ratio: 1; border-radius: 16px"
			></div>{/each}
	{/if}
	{#each unique(lists, byId) as p (p.id)}
		{@const items = p.items.map((i) => i.libraryItem).filter((i) => !!i)}
		<a class="list" href="/playlists/{p.id}">
			<CoverMosaic {items} sizes="(max-width: 800px) 45vw, 220px" />
			<strong>{p.name}</strong>
			<span class="muted"
				>{i18n.m.authors.books(items.length)}{items.length
					? ` · ${duration(items.reduce((s, i) => s + i.media.duration, 0))}`
					: ''}</span
			>
		</a>
	{/each}
</div>

<style>
	h1 {
		margin-bottom: var(--sp-24);
	}

	.new {
		display: flex;
		gap: var(--sp-8);
		max-width: 520px;
		margin-bottom: var(--sp-32);
	}

	.new input {
		flex: 1;
		min-width: 0;
		min-height: var(--layout-touch);
		padding: 0 var(--sp-16);
		border: 1.5px solid var(--c-field);
		border-radius: var(--r-pill);
		background: var(--c-card);
		color: var(--c-ink);
		font: inherit;
	}

	.new input:focus-visible {
		outline: none;
		border-color: var(--c-accent);
	}

	.grid {
		display: grid;
		grid-template-columns: repeat(
			auto-fill,
			minmax(min(calc(190px * var(--tile-scale)), 42%), 1fr)
		);
		gap: var(--sp-32) var(--sp-24);
	}

	.list {
		display: grid;
		gap: var(--sp-4);
	}

	.list :global(.mosaic) {
		margin-bottom: var(--sp-8);
		transition: translate var(--motion-base) var(--motion-out);
	}

	.list:hover :global(.mosaic) {
		translate: 0 -4px;
	}

	strong {
		font-weight: var(--fw-semibold);
	}

	.muted {
		font-size: var(--fs-13);
	}
</style>
