<script lang="ts">
	import { byId, unique } from '#lib/unique.ts';
	import { untrack } from 'svelte';
	import type { LibraryItem, ListeningSession } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { span } from '#lib/format.ts';
	import EmptyState from '#lib/components/EmptyState.svelte';
	import SessionRow, { deviceLabel, meaningful } from '#lib/components/SessionRow.svelte';
	import { saveFile, toCsv, today } from '#lib/export.ts';
	import { clock } from '#lib/format.ts';
	import Sheet from '@lucide/svelte/icons/sheet';

	const PAGE_SIZE = 50;
	const t = $derived(i18n.m.history);

	let sessions = $state.raw<ListeningSession[]>([]);
	let page = $state(0);
	let pages = $state(0);
	let loading = $state(true);
	let error = $state(false);
	/** Books of the listed sessions (covers); ids already asked for, found or not. */
	let items = $state.raw(new Map<string, LibraryItem>());
	let checked = $state.raw(new Set<string>());

	async function load(next: number) {
		loading = true;
		error = false;
		try {
			const r = await session.api.listeningSessions(next, PAGE_SIZE);
			const fresh = r.sessions.filter(meaningful);
			sessions = next ? [...sessions, ...fresh] : fresh;
			page = next;
			pages = r.numPages;
			void loadItems(fresh);
		} catch {
			error = true;
		} finally {
			loading = false;
		}
	}

	async function loadItems(list: ListeningSession[]) {
		const ids = [...new Set(list.map((s) => s.libraryItemId))].filter((id) => !checked.has(id));
		if (!ids.length) return;
		const found = await session.api.items(ids).catch(() => null);
		if (!found) return; // offline: keep placeholders, do not mark books as missing
		// Replaced as a whole ($state.raw), never mutated after assignment.
		// eslint-disable-next-line svelte/prefer-svelte-reactivity
		const map = new Map(items);
		for (const it of found) map.set(it.id, it);
		items = map;
		checked = new Set([...checked, ...ids]);
	}

	$effect(() => {
		if (!session.client) return;
		untrack(() => load(0));
	});

	/** Every listening session (all pages) as a spreadsheet. */
	let exporting = $state(false);
	async function exportCsv() {
		exporting = true;
		try {
			const all: ListeningSession[] = [];
			for (let p = 0, n = 1; p < n && p < 200; p++) {
				const r = await session.api.listeningSessions(p, 200);
				all.push(...r.sessions);
				n = r.numPages;
			}
			const rows: (string | number)[][] = [
				['date', 'started', 'book', 'author', 'from', 'to', 'listened_seconds', 'device']
			];
			for (const s of all) {
				const d = new Date(s.startedAt);
				rows.push([
					day(d),
					d.toTimeString().slice(0, 5),
					s.displayTitle,
					s.displayAuthor,
					clock(s.startTime),
					clock(s.currentTime),
					Math.round(s.timeListening),
					deviceLabel(s.deviceInfo) ?? ''
				]);
			}
			saveFile(`koalashelf-history-${today()}.csv`, toCsv(rows), 'text/csv');
		} finally {
			exporting = false;
		}
	}

	/** YYYY-MM-DD in the listener's local time. */
	const day = (d: Date) =>
		`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

	const days = $derived.by(() => {
		const today = new Date();
		const yesterday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1);
		const fmt = new Intl.DateTimeFormat(i18n.locale, {
			weekday: 'long',
			day: 'numeric',
			month: 'long'
		});
		const groups: { date: string; label: string; total: number; list: ListeningSession[] }[] = [];
		for (const s of sessions) {
			// The listener's own day: the server's `date` is in the server's time zone.
			const when = new Date(s.startedAt);
			const date = day(when);
			let g = groups.at(-1);
			if (g?.date !== date) {
				const label =
					date === day(today) ? t.today : date === day(yesterday) ? t.yesterday : fmt.format(when);
				g = { date, label, total: 0, list: [] };
				groups.push(g);
			}
			g.total += s.timeListening;
			g.list.push(s);
		}
		return groups;
	});
</script>

<svelte:head><title>{t.title} · KoalaShelf</title></svelte:head>

<div class="top">
	<div>
		<h1>{t.title}</h1>
		<p class="muted intro">{t.intro}</p>
	</div>
	{#if sessions.length}
		<button class="chip outline" class:is-loading={exporting} onclick={exportCsv}>
			<Sheet size={14} aria-hidden="true" />{i18n.m.exports.csv}
		</button>
	{/if}
</div>

{#if loading && !sessions.length}
	<div class="days" aria-busy="true">
		{#each { length: 4 }, i (i)}<div
				class="skeleton"
				style="height: 118px; border-radius: 18px"
			></div>{/each}
	</div>
{:else if error && !sessions.length}
	<EmptyState text={t.error} />
{:else if !sessions.length}
	<EmptyState text={t.empty} />
{:else}
	<div class="days">
		{#each days as d (d.date)}
			<section class="day rise">
				<h2 class="label">
					<span>{d.label}</span>
					<span class="tabular">{span(d.total)}</span>
				</h2>
				<ul>
					{#each unique(d.list, byId) as s (s.id)}
						<SessionRow
							{s}
							withBook
							item={items.get(s.libraryItemId)}
							missing={checked.has(s.libraryItemId) && !items.has(s.libraryItemId)}
						/>
					{/each}
				</ul>
			</section>
		{/each}
	</div>
	{#if page + 1 < pages}
		<div class="more">
			<button class="btn ghost" class:is-loading={loading} onclick={() => load(page + 1)}
				>{t.more}</button
			>
		</div>
	{/if}
{/if}

<style>
	h1 {
		margin-bottom: var(--sp-8);
	}

	.intro {
		max-width: 60ch;
		margin: 0 0 var(--sp-24);
	}

	.top {
		display: flex;
		flex-wrap: wrap;
		align-items: start;
		justify-content: space-between;
		gap: var(--sp-12) var(--sp-24);
		max-width: 760px;
	}

	.days {
		display: grid;
		gap: var(--sp-24);
		max-width: 760px;
	}

	.day h2 {
		display: flex;
		justify-content: space-between;
		gap: var(--sp-16);
		margin-bottom: var(--sp-8);
	}

	ul {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--sp-8);
	}

	.more {
		max-width: 760px;
		display: grid;
		justify-items: center;
		margin-top: var(--sp-24);
	}
</style>
