<script lang="ts">
	import type { Bookmark, LibraryItem } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { player } from '#lib/player/player.svelte.ts';
	import { toasts } from '#lib/toast.svelte.ts';
	import { copyMomentLink } from '#lib/share.ts';
	import { view } from '#lib/items.ts';
	import { clock } from '#lib/format.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Cover from '#lib/components/Cover.svelte';
	import EmptyState from '#lib/components/EmptyState.svelte';
	import Play from '@lucide/svelte/icons/play';
	import Link from '@lucide/svelte/icons/link';
	import Trash2 from '@lucide/svelte/icons/trash-2';
	import FileText from '@lucide/svelte/icons/file-text';
	import Sheet from '@lucide/svelte/icons/sheet';
	import { saveFile, toCsv, today } from '#lib/export.ts';

	const t = $derived(i18n.m.bookmarksPage);
	let marks = $state.raw<Bookmark[] | null>(null);
	let books = $state.raw(new Map<string, LibraryItem>());
	let error = $state<string | null>(null);
	/** Settles once the books (titles, authors) are known: exports wait for it. */
	let loaded: Promise<unknown> = Promise.resolve();

	$effect(() => {
		if (!session.client) return;
		loaded = session.api
			.allBookmarks()
			.then(async (b) => {
				marks = b;
				const ids = [...new Set(b.map((x) => x.libraryItemId))];
				const found = ids.length ? await session.api.items(ids).catch(() => []) : [];
				books = new Map(found.map((i) => [i.id, i]));
			})
			.catch((e) => (error = (e as Error).message));
	});

	/** Books with their bookmarks in book order; the book bookmarked last comes first. */
	const groups = $derived.by(() => {
		// eslint-disable-next-line svelte/prefer-svelte-reactivity -- local grouping inside a derived value
		const by = new Map<string, Bookmark[]>();
		for (const m of marks ?? []) by.set(m.libraryItemId, [...(by.get(m.libraryItemId) ?? []), m]);
		return [...by.entries()]
			.map(([id, list]) => ({
				id,
				item: books.get(id),
				list: list.sort((a, b) => a.time - b.time),
				latest: Math.max(...list.map((m) => m.createdAt))
			}))
			.sort((a, b) => b.latest - a.latest);
	});

	const link = (m: Bookmark) =>
		`${location.origin}/book/${m.libraryItemId}?t=${Math.floor(m.time)}`;

	/** All bookmarks as a readable Markdown list, by book. */
	async function exportMarkdown() {
		await loaded;
		const lines = [`# ${t.title} · KoalaShelf · ${today()}`, ''];
		for (const g of groups) {
			const v = g.item ? view(g.item) : null;
			lines.push(`## ${v ? `${v.title} — ${v.author}` : i18n.m.history.removed}`, '');
			for (const m of g.list) lines.push(`- [${clock(m.time)}](${link(m)}) ${m.title}`);
			lines.push('');
		}
		saveFile(`koalashelf-bookmarks-${today()}.md`, lines.join('\n'), 'text/markdown');
	}

	async function exportCsv() {
		await loaded;
		const rows: (string | number)[][] = [
			['book', 'author', 'time', 'seconds', 'note', 'created', 'link']
		];
		for (const g of groups) {
			const v = g.item ? view(g.item) : null;
			for (const m of g.list)
				rows.push([
					v?.title ?? i18n.m.history.removed,
					v?.author ?? '',
					clock(m.time),
					Math.floor(m.time),
					m.title,
					new Date(m.createdAt).toISOString(),
					link(m)
				]);
		}
		saveFile(`koalashelf-bookmarks-${today()}.csv`, toCsv(rows), 'text/csv');
	}

	async function remove(m: Bookmark) {
		await session.api.deleteBookmark(m.libraryItemId, m.time);
		marks = (marks ?? []).filter((x) => x !== m);
		toasts.show(i18n.m.toast.bookmarkDeleted, {
			label: i18n.m.toast.undo,
			run: async () => {
				const back = await session.api.createBookmark(m.libraryItemId, m.time, m.title);
				marks = [...(marks ?? []), back];
			}
		});
	}
</script>

<svelte:head><title>{t.title} · KoalaShelf</title></svelte:head>

<div class="top">
	<h1>{t.title}</h1>
	{#if marks?.length}
		<div class="export" role="group" aria-label={i18n.m.exports.label}>
			<span class="muted">{i18n.m.exports.label}</span>
			<button class="chip outline" onclick={exportMarkdown}
				><FileText size={14} aria-hidden="true" />Markdown</button
			>
			<button class="chip outline" onclick={exportCsv}
				><Sheet size={14} aria-hidden="true" />CSV</button
			>
		</div>
	{/if}
</div>

{#if error}<p class="muted">{i18n.m.common.error(error)}</p>{/if}
{#if marks && !marks.length}<EmptyState text={t.empty} />{/if}
{#if !marks && !error}
	<div class="skeleton" style="height: 160px; border-radius: 22px; max-width: 760px"></div>
{/if}

<div class="groups">
	{#each groups as g (g.id)}
		{@const v = g.item ? view(g.item) : null}
		<section class="card">
			<a class="book" href="/book/{g.id}">
				<span class="c"
					><Cover
						title={v?.title ?? '…'}
						author={v?.author ?? ''}
						src={v?.cover ?? null}
						sizes="56px"
					/></span
				>
				<span class="meta">
					<strong>{v?.title ?? i18n.m.history.removed}</strong>
					{#if v}<span class="muted">{v.author}</span>{/if}
				</span>
			</a>
			<ul>
				{#each g.list as m (m.time)}
					<li>
						<button
							class="jump"
							onclick={() => player.play(g.id, m.time)}
							aria-label="{t.playAt(clock(m.time))}: {m.title}"
						>
							<Play size={12} fill="currentColor" aria-hidden="true" />
							<span class="tabular time">{clock(m.time)}</span>
							<span class="title">{m.title}</span>
						</button>
						<button
							class="icon"
							onclick={() => copyMomentLink(g.id, m.time)}
							aria-label={i18n.m.player.copyLink}
							title={i18n.m.player.copyLink}><Link size={16} /></button
						>
						<button
							class="icon"
							onclick={() => remove(m)}
							aria-label={i18n.m.player.delete}
							title={i18n.m.player.delete}><Trash2 size={16} /></button
						>
					</li>
				{/each}
			</ul>
		</section>
	{/each}
</div>

<style>
	.top {
		display: flex;
		flex-wrap: wrap;
		align-items: end;
		justify-content: space-between;
		gap: var(--sp-12) var(--sp-24);
		max-width: 760px;
		margin-bottom: var(--sp-24);
	}

	.export {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--sp-8);
		font-size: var(--fs-13);
	}

	.groups {
		display: grid;
		gap: var(--sp-16);
		max-width: 760px;
	}

	.card {
		display: grid;
		gap: var(--sp-8);
		padding: var(--sp-16);
		border-radius: var(--r-card-lg);
	}

	.book {
		display: grid;
		grid-template-columns: 56px 1fr;
		gap: var(--sp-16);
		align-items: center;
	}

	.c {
		border-radius: 8px;
		overflow: hidden;
	}

	.meta {
		display: grid;
		min-width: 0;
	}

	.meta strong:hover {
		text-decoration: underline;
		text-underline-offset: 3px;
	}

	.meta .muted {
		font-size: var(--fs-13);
	}

	ul {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: 2px;
	}

	li {
		display: flex;
		align-items: center;
		gap: 2px;
	}

	.jump {
		flex: 1;
		min-width: 0;
		display: flex;
		align-items: center;
		gap: var(--sp-12);
		min-height: var(--layout-touch);
		padding: 0 var(--sp-12);
		border: 0;
		border-radius: var(--r-card);
		background: transparent;
		color: inherit;
		text-align: left;
	}

	.jump:hover {
		background: var(--c-bg);
	}

	.time {
		color: var(--c-accent);
		font-weight: var(--fw-bold);
		font-size: var(--fs-13);
	}

	.title {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
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

	.icon:hover {
		color: var(--c-ink);
		background: var(--c-bg);
	}
</style>
