<script lang="ts">
	import { byId, unique } from '#lib/unique.ts';
	import type { Playlist } from '@koalashelf/abs-client';
	import { flip } from 'svelte/animate';
	import { goto } from '$app/navigation';
	import { session } from '#lib/session.svelte.ts';
	import { player } from '#lib/player/player.svelte.ts';
	import { progress } from '#lib/progress.svelte.ts';
	import { queue } from '#lib/queue.svelte.ts';
	import { toasts } from '#lib/toast.svelte.ts';
	import { view } from '#lib/items.ts';
	import { duration } from '#lib/format.ts';
	import { dur } from '#lib/motion.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Cover from '#lib/components/Cover.svelte';
	import CoverMosaic from '#lib/components/CoverMosaic.svelte';
	import EmptyState from '#lib/components/EmptyState.svelte';
	import Play from '@lucide/svelte/icons/play';
	import ListPlus from '@lucide/svelte/icons/list-plus';
	import Pencil from '@lucide/svelte/icons/pencil';
	import Trash2 from '@lucide/svelte/icons/trash-2';
	import ChevronUp from '@lucide/svelte/icons/chevron-up';
	import ChevronDown from '@lucide/svelte/icons/chevron-down';
	import X from '@lucide/svelte/icons/x';
	import Check from '@lucide/svelte/icons/check';
	import Download from '@lucide/svelte/icons/download';
	import { downloads, supported as downloadsSupported } from '#lib/downloads.svelte.ts';
	import { isTauri } from '#lib/platform.ts';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	const t = $derived(i18n.m.playlists);
	// Follows the loaded playlist; edits replace it with the server's answer.
	let list = $derived<Playlist | null>(data.playlist);
	$effect(() => void progress.load());

	// Each book once, and none whose book was removed from the library meanwhile.
	const items = $derived(
		unique(
			(list?.items ?? []).map((i) => i.libraryItem).filter((i) => !!i),
			byId
		)
	);
	const total = $derived(items.reduce((s, i) => s + i.media.duration, 0));

	let renaming = $state(false);
	let draft = $state('');
	let confirmDelete = $state(false);

	async function save(itemIds: string[]) {
		if (!list) return;
		list = await session.api.updatePlaylist(list.id, { itemIds });
	}

	function move(index: number, by: -1 | 1) {
		const ids = items.map((i) => i.id);
		ids.splice(index + by, 0, ...ids.splice(index, 1));
		void save(ids);
	}

	async function remove(index: number) {
		if (!list) return;
		const before = items.map((i) => i.id);
		if (before.length === 1) {
			// The server deletes a playlist with its last book; undo creates it again.
			const { name, libraryId } = list;
			await session.api.removeFromPlaylist(list.id, before[0]);
			toasts.show(t.deletedEmpty, {
				label: i18n.m.toast.undo,
				run: () =>
					void session.api
						.createPlaylist(libraryId, name, before)
						.then((p) => goto(`/playlists/${p.id}`))
			});
			goto('/playlists');
			return;
		}
		list = await session.api.removeFromPlaylist(list.id, before[index]);
		toasts.show(t.removedFrom(list.name), {
			label: i18n.m.toast.undo,
			run: () => {
				const id = list!.id;
				void session.api.addToPlaylist(id, before[index]).then(() => save(before));
			}
		});
	}

	/** First book now, the rest right after it (in front of anything already queued). */
	function playAll() {
		const [first, ...rest] = items;
		if (!first) return;
		rest.forEach((item, i) => {
			queue.remove(item.id);
			queue.insert(item, i);
		});
		void player.play(first.id);
		if (rest.length) toasts.show(i18n.m.seriesPage.queued(rest.length));
	}

	function queueAll() {
		const add = items.filter((i) => !queue.has(i.id) && i.id !== player.item?.id);
		for (const i of add) queue.add(i);
		toasts.show(i18n.m.seriesPage.queued(add.length));
	}

	/** Every book of the playlist offline, one after another. */
	const missing = $derived(items.filter((i) => !downloads.has(i.id)));
	const canDownload = $derived(!isTauri && session.canDownload && downloadsSupported());
	function downloadAll() {
		const ids = missing.map((i) => i.id);
		void (async () => {
			for (const id of ids) await downloads.download(id).catch(() => undefined);
		})();
		toasts.show(i18n.m.select.downloading(ids.length));
	}

	async function rename(e: SubmitEvent) {
		e.preventDefault();
		if (!list || !draft.trim()) return;
		list = await session.api.updatePlaylist(list.id, { name: draft.trim() });
		renaming = false;
	}

	async function destroy() {
		if (!list) return;
		await session.api.deletePlaylist(list.id);
		toasts.show(t.deleted);
		goto('/playlists');
	}
</script>

<svelte:head><title>{list?.name ?? t.notFound} · KoalaShelf</title></svelte:head>

{#if !list}
	<EmptyState text={t.notFound} />
{:else}
	<header>
		<div class="art"><CoverMosaic {items} sizes="(max-width: 800px) 60vw, 240px" /></div>
		<div class="info">
			<span class="label">{t.label}</span>
			{#if renaming}
				<form class="rename" onsubmit={rename}>
					<!-- svelte-ignore a11y_autofocus -->
					<input bind:value={draft} aria-label={t.rename} maxlength="80" autofocus />
					<button class="icon" type="submit" aria-label={t.save}><Check size={18} /></button>
					<button
						class="icon"
						type="button"
						onclick={() => (renaming = false)}
						aria-label={t.cancel}><X size={18} /></button
					>
				</form>
			{:else}
				<h1>{list.name}</h1>
			{/if}
			<p class="muted">
				{i18n.m.authors.books(items.length)}{items.length ? ` · ${duration(total)}` : ''}
			</p>
			<div class="cta">
				<button class="btn primary" onclick={playAll} disabled={!items.length}>
					<Play size={16} fill="currentColor" aria-hidden="true" />
					{t.play}
				</button>
				<button class="btn ghost" onclick={queueAll} disabled={!items.length}>
					<ListPlus size={16} aria-hidden="true" />
					{t.queueAll}
				</button>
				{#if canDownload && missing.length}
					<button class="btn ghost" onclick={downloadAll}>
						<Download size={16} aria-hidden="true" />
						{t.downloadAll}
					</button>
				{/if}
				<button class="btn ghost" onclick={() => ((renaming = true), (draft = list!.name))}>
					<Pencil size={16} aria-hidden="true" />
					{t.rename}
				</button>
				{#if confirmDelete}
					<span class="confirm" role="alert">{t.deleteConfirm(list.name)}</span>
					<button class="btn danger" onclick={destroy}>{t.delete}</button>
					<button class="btn ghost" onclick={() => (confirmDelete = false)}>{t.cancel}</button>
				{:else}
					<button class="btn ghost" onclick={() => (confirmDelete = true)}>
						<Trash2 size={16} aria-hidden="true" />
						{t.delete}
					</button>
				{/if}
			</div>
		</div>
	</header>

	{#if !items.length}
		<EmptyState text={t.emptyList} />
	{:else}
		<ol class="items">
			{#each unique(items, byId) as item, i (item.id)}
				{@const v = view(item)}
				{@const status = progress.status(item.id)}
				<li animate:flip={{ duration: dur(260) }}>
					<a href="/book/{item.id}">
						<span class="n display">{i + 1}</span>
						<span class="mini"
							><Cover title={v.title} author={v.author} src={v.cover} sizes="56px" /></span
						>
						<span class="meta">
							<strong>{v.title}</strong>
							<span class="muted"
								>{v.author} · {duration(v.duration)}{status === 'done'
									? ` · ${i18n.m.library.done}`
									: ''}</span
							>
						</span>
					</a>
					<button
						class="icon"
						disabled={i === 0}
						onclick={() => move(i, -1)}
						aria-label={t.moveUp(v.title)}
						title={t.moveUp(v.title)}><ChevronUp size={18} /></button
					>
					<button
						class="icon"
						disabled={i === items.length - 1}
						onclick={() => move(i, 1)}
						aria-label={t.moveDown(v.title)}
						title={t.moveDown(v.title)}><ChevronDown size={18} /></button
					>
					<button
						class="icon"
						onclick={() => remove(i)}
						aria-label={t.removeItem(v.title)}
						title={t.removeItem(v.title)}><X size={18} /></button
					>
				</li>
			{/each}
		</ol>
	{/if}
{/if}

<style>
	header {
		display: grid;
		grid-template-columns: minmax(0, 240px) 1fr;
		gap: var(--sp-40);
		align-items: center;
		margin-bottom: var(--sp-40);
	}

	.info {
		display: grid;
		gap: var(--sp-8);
		justify-items: start;
	}

	h1 {
		font-size: var(--fs-56);
	}

	.rename {
		display: flex;
		align-items: center;
		gap: var(--sp-4);
	}

	.rename input {
		min-height: 52px;
		padding: 0 var(--sp-16);
		border: 1.5px solid var(--c-accent);
		border-radius: var(--r-card);
		background: var(--c-card);
		color: var(--c-ink);
		font-family: var(--font-display);
		font-size: var(--fs-34);
		width: min(100%, 560px);
	}

	.cta {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--sp-12);
		margin-top: var(--sp-8);
	}

	.confirm {
		flex-basis: 100%;
		font-weight: var(--fw-semibold);
	}

	.btn.danger {
		background: var(--c-accent);
		color: var(--c-on-accent);
	}

	.items {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--sp-4);
		max-width: 900px;
	}

	li {
		display: flex;
		align-items: center;
		gap: 2px;
	}

	li a {
		flex: 1;
		min-width: 0;
		display: grid;
		grid-template-columns: 2em 56px 1fr;
		align-items: center;
		gap: var(--sp-16);
		padding: var(--sp-8) var(--sp-12);
		border-radius: var(--r-card);
	}

	li a:hover {
		background: var(--c-card);
	}

	.n {
		font-size: var(--fs-18);
		text-align: center;
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
		flex: none;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: var(--c-muted);
	}

	.icon:hover:not(:disabled) {
		color: var(--c-ink);
		background: var(--c-card);
	}

	.icon:disabled {
		opacity: 0.3;
	}

	@container page (max-width: 700px) {
		header {
			grid-template-columns: 1fr;
			gap: var(--sp-24);
		}

		.art {
			width: min(240px, 70%);
		}

		h1 {
			font-size: var(--fs-34);
		}

		li a {
			grid-template-columns: 1.5em 44px 1fr;
			gap: var(--sp-12);
			padding: var(--sp-8);
		}

		/* Phones: reordering stays possible, but "down" gives room to the title. */
		li .icon:nth-of-type(2) {
			display: none;
		}
	}
</style>
