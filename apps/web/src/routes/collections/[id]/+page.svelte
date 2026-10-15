<script lang="ts">
	import { page } from '$app/state';
	import { goto } from '$app/navigation';
	import { flip } from 'svelte/animate';
	import type { Collection } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { player } from '#lib/player/player.svelte.ts';
	import { progress } from '#lib/progress.svelte.ts';
	import { queue } from '#lib/queue.svelte.ts';
	import { toasts } from '#lib/toast.svelte.ts';
	import { view } from '#lib/items.ts';
	import { duration } from '#lib/format.ts';
	import { dur } from '#lib/motion.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import BookTile from '#lib/components/BookTile.svelte';
	import Cover from '#lib/components/Cover.svelte';
	import Play from '@lucide/svelte/icons/play';
	import ListPlus from '@lucide/svelte/icons/list-plus';
	import ListMusic from '@lucide/svelte/icons/list-music';
	import Pencil from '@lucide/svelte/icons/pencil';
	import Trash2 from '@lucide/svelte/icons/trash-2';
	import ArrowUpDown from '@lucide/svelte/icons/arrow-up-down';
	import ChevronUp from '@lucide/svelte/icons/chevron-up';
	import ChevronDown from '@lucide/svelte/icons/chevron-down';
	import X from '@lucide/svelte/icons/x';
	import Check from '@lucide/svelte/icons/check';

	const t = $derived(i18n.m.collections);
	let c = $state.raw<Collection | null | undefined>(undefined);

	$effect(() => {
		const id = page.params.id;
		if (!id) return;
		c = undefined;
		session.api
			.collection(id)
			.then((r) => (c = r))
			.catch(() => (c = null));
	});
	$effect(() => void progress.load());

	const books = $derived(c?.books ?? []);
	const total = $derived(books.reduce((s, b) => s + b.media.duration, 0));

	/** Renaming (name and description) and the editable list with order and removal. */
	let renaming = $state(false);
	let draftName = $state('');
	let draftDesc = $state('');
	let editing = $state(false);
	let confirmDelete = $state(false);

	async function saveOrder(bookIds: string[]) {
		if (!c) return;
		c = await session.api.updateCollection(c.id, { bookIds });
	}

	function move(index: number, by: -1 | 1) {
		const ids = books.map((b) => b.id);
		ids.splice(index + by, 0, ...ids.splice(index, 1));
		void saveOrder(ids);
	}

	async function remove(index: number) {
		if (!c) return;
		const before = books.map((b) => b.id);
		c = await session.api.removeFromCollection(c.id, before[index]);
		toasts.show(t.removedFrom(c.name), {
			label: i18n.m.toast.undo,
			run: () => {
				const id = c!.id;
				void session.api.addToCollection(id, before[index]).then(() => saveOrder(before));
			}
		});
	}

	async function rename(e: SubmitEvent) {
		e.preventDefault();
		if (!c || !draftName.trim()) return;
		c = await session.api.updateCollection(c.id, {
			name: draftName.trim(),
			description: draftDesc.trim()
		});
		renaming = false;
	}

	async function destroy() {
		if (!c) return;
		await session.api.deleteCollection(c.id);
		toasts.show(t.deleted);
		goto('/collections');
	}

	/** First book now, the rest right after it (in front of anything already queued). */
	function playAll() {
		const open = books.filter((b) => b.media.duration > 0);
		const [first, ...rest] = open;
		if (!first) return;
		rest.forEach((b, i) => {
			queue.remove(b.id);
			queue.insert(b, i);
		});
		void player.play(first.id);
		if (rest.length) toasts.show(i18n.m.seriesPage.queued(rest.length));
	}

	function queueAll() {
		const add = books.filter((b) => !queue.has(b.id) && b.id !== player.item?.id);
		for (const b of add) queue.add(b);
		toasts.show(i18n.m.seriesPage.queued(add.length));
	}

	async function asPlaylist() {
		if (!c) return;
		const p = await session.api.playlistFromCollection(c.id);
		toasts.show(t.playlistCreated);
		goto(`/playlists/${p.id}`);
	}
</script>

<svelte:head><title>{c?.name ?? t.title} · KoalaShelf</title></svelte:head>

{#if c === null}
	<p class="muted">{t.notFound}</p>
{:else if c}
	<header>
		<a class="label" href="/collections">{t.title}</a>
		{#if renaming}
			<form class="rename" onsubmit={rename}>
				<!-- svelte-ignore a11y_autofocus -->
				<input bind:value={draftName} aria-label={t.name} maxlength="80" autofocus />
				<textarea bind:value={draftDesc} aria-label={t.description} rows="2" maxlength="1000"
				></textarea>
				<div class="row">
					<button class="btn primary" type="submit"><Check size={16} />{t.save}</button>
					<button class="btn ghost" type="button" onclick={() => (renaming = false)}
						>{t.cancel}</button
					>
				</div>
			</form>
		{:else}
			<h1>{c.name}</h1>
			<p class="muted">{t.books(books.length)}{books.length ? ` · ${duration(total)}` : ''}</p>
			{#if c.description}<p class="desc">{c.description}</p>{/if}
		{/if}
		<div class="cta">
			<button class="btn primary" onclick={playAll} disabled={!books.length}>
				<Play size={16} fill="currentColor" aria-hidden="true" />
				{i18n.m.playlists.play}
			</button>
			<button class="btn ghost" onclick={queueAll} disabled={!books.length}>
				<ListPlus size={16} aria-hidden="true" />
				{i18n.m.playlists.queueAll}
			</button>
			<button class="btn ghost" onclick={asPlaylist} disabled={!books.length}>
				<ListMusic size={16} aria-hidden="true" />
				{t.asPlaylist}
			</button>
			{#if session.canUpdate}
				<button
					class="btn ghost"
					onclick={() => (
						(renaming = true),
						(draftName = c!.name),
						(draftDesc = c!.description ?? '')
					)}
				>
					<Pencil size={16} aria-hidden="true" />
					{t.rename}
				</button>
				<button class="btn ghost" aria-pressed={editing} onclick={() => (editing = !editing)}>
					<ArrowUpDown size={16} aria-hidden="true" />
					{t.arrange}
				</button>
			{/if}
			{#if session.canDelete}
				{#if confirmDelete}
					<span class="confirm" role="alert">{t.deleteConfirm(c.name)}</span>
					<button class="btn danger" onclick={destroy}>{t.delete}</button>
					<button class="btn ghost" onclick={() => (confirmDelete = false)}>{t.cancel}</button>
				{:else}
					<button class="btn ghost" onclick={() => (confirmDelete = true)}>
						<Trash2 size={16} aria-hidden="true" />
						{t.delete}
					</button>
				{/if}
			{/if}
		</div>
	</header>

	{#if editing}
		<ol class="items">
			{#each books as b, i (b.id)}
				{@const v = view(b)}
				<li animate:flip={{ duration: dur(260) }}>
					<a href="/book/{b.id}">
						<span class="n display">{i + 1}</span>
						<span class="mini"
							><Cover title={v.title} author={v.author} src={v.cover} sizes="56px" /></span
						>
						<span class="meta">
							<strong>{v.title}</strong>
							<span class="muted">{v.author} · {duration(v.duration)}</span>
						</span>
					</a>
					<button
						class="icon"
						disabled={i === 0}
						onclick={() => move(i, -1)}
						aria-label={i18n.m.playlists.moveUp(v.title)}
						title={i18n.m.playlists.moveUp(v.title)}><ChevronUp size={18} /></button
					>
					<button
						class="icon"
						disabled={i === books.length - 1}
						onclick={() => move(i, 1)}
						aria-label={i18n.m.playlists.moveDown(v.title)}
						title={i18n.m.playlists.moveDown(v.title)}><ChevronDown size={18} /></button
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
	{:else}
		<div class="shelf">
			{#each books as b, i (b.id)}<BookTile item={b} eager={i < 12} />{/each}
		</div>
	{/if}
{/if}

<style>
	header {
		display: grid;
		gap: var(--sp-8);
		justify-items: start;
		margin-bottom: var(--sp-32);
	}

	.desc {
		max-width: 640px;
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

	.btn[aria-pressed='true'] {
		box-shadow: inset 0 0 0 2px var(--c-accent);
	}

	.rename {
		display: grid;
		gap: var(--sp-8);
		width: min(100%, 560px);
	}

	.rename input,
	.rename textarea {
		padding: var(--sp-8) var(--sp-16);
		border: 1.5px solid var(--c-accent);
		border-radius: var(--r-card);
		background: var(--c-card);
		color: var(--c-ink);
		font: inherit;
	}

	.rename input {
		min-height: 52px;
		font-family: var(--font-display);
		font-size: var(--fs-34);
	}

	.row {
		display: flex;
		gap: var(--sp-8);
	}

	.shelf {
		display: grid;
		grid-template-columns: repeat(
			auto-fill,
			minmax(min(calc(150px * var(--tile-scale)), 42%), 1fr)
		);
		row-gap: var(--sp-32);
		margin: 0 calc(-1 * var(--sp-12));
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
		.shelf {
			grid-template-columns: repeat(
				auto-fill,
				minmax(min(calc(130px * var(--tile-scale)), 42%), 1fr)
			);
		}

		.rename input {
			font-size: var(--fs-24);
		}

		li a {
			grid-template-columns: 1.5em 44px 1fr;
			gap: var(--sp-12);
			padding: var(--sp-8);
		}

		li .icon:nth-of-type(2) {
			display: none;
		}
	}
</style>
