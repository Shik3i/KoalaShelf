<script lang="ts">
	import { byId, unique } from '#lib/unique.ts';
	import { untrack } from 'svelte';
	import type { LibraryItem, ListeningSession, ServerUser } from '@koalashelf/abs-client';
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
	let users = $state.raw<ServerUser[]>([]);
	let selectedUserId = $state<string | null>(null);
	const selectedUser = $derived(users.find((u) => u.id === selectedUserId) ?? null);

	$effect(() => {
		void session.activeKey;
		if (!session.client || !session.isAdmin) {
			users = [];
			selectedUserId = null;
			return;
		}
		selectedUserId = null;
		session.api
			.users()
			.then((u) => (users = u))
			.catch(() => (users = []));
	});

	async function load(next: number) {
		loading = true;
		error = false;
		try {
			const uid = session.isAdmin ? (selectedUserId ?? undefined) : undefined;
			const r = await session.api.listeningSessions(next, PAGE_SIZE, uid);
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
		void selectedUserId;
		untrack(() => load(0));
	});

	/** Every listening session (all pages) as a spreadsheet. */
	let exporting = $state(false);
	async function exportCsv() {
		exporting = true;
		try {
			const uid = session.isAdmin ? (selectedUserId ?? undefined) : undefined;
			const all: ListeningSession[] = [];
			for (let p = 0, n = 1; p < n && p < 200; p++) {
				const r = await session.api.listeningSessions(p, 200, uid);
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
			const prefix = selectedUser ? `${selectedUser.username}-` : '';
			saveFile(`koalashelf-history-${prefix}${today()}.csv`, toCsv(rows), 'text/csv');
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
		{#if selectedUser && selectedUserId !== session.user?.id}
			<p class="muted admin-viewing-subtitle">
				<span class="admin-badge">{i18n.m.stats.adminBadge}</span>
				<span>{i18n.m.stats.viewingUser(selectedUser.username)}</span>
				<button type="button" class="link-btn" onclick={() => (selectedUserId = null)}
					>{i18n.m.stats.resetToMe}</button
				>
			</p>
		{:else}
			<p class="muted intro">{t.intro}</p>
		{/if}
	</div>
	<div class="actions">
		{#if session.isAdmin && users.length > 1}
			<label for="admin-user-select-history" class="sr-only">{i18n.m.stats.adminUserSelect}</label>
			<select
				id="admin-user-select-history"
				class="select-user"
				aria-label={i18n.m.stats.adminUserSelect}
				value={selectedUserId ?? ''}
				onchange={(e) => (selectedUserId = e.currentTarget.value || null)}
			>
				<option value="">👤 {i18n.m.stats.viewingSelf} ({session.user?.username})</option>
				{#each users as u (u.id)}
					{#if u.id !== session.user?.id}
						<option value={u.id}
							>👥 {u.username}
							{u.type === 'root' || u.type === 'admin' ? `(${u.type})` : ''}</option
						>
					{/if}
				{/each}
			</select>
		{/if}
		{#if sessions.length}
			<button class="chip outline" class:is-loading={exporting} onclick={exportCsv}>
				<Sheet size={14} aria-hidden="true" />{i18n.m.exports.csv}
			</button>
		{/if}
	</div>
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

	.actions {
		display: flex;
		align-items: center;
		gap: var(--sp-8);
		flex-wrap: wrap;
	}

	.admin-viewing-subtitle {
		margin: var(--sp-6) 0 0;
		display: inline-flex;
		align-items: center;
		gap: var(--sp-8);
		font-size: var(--fs-14);
	}

	.admin-badge {
		background: color-mix(in oklab, var(--c-amber) 25%, transparent);
		color: var(--c-amber-high, var(--c-amber));
		border: 1px solid color-mix(in oklab, var(--c-amber) 40%, transparent);
		padding: 2px 8px;
		border-radius: 999px;
		font-size: var(--fs-12);
		font-weight: var(--fw-semibold);
		text-transform: uppercase;
		letter-spacing: 0.04em;
	}

	.link-btn {
		background: none;
		border: none;
		padding: 0;
		color: var(--c-ink);
		text-decoration: underline;
		cursor: pointer;
		font-size: var(--fs-13);
	}

	.link-btn:hover {
		color: var(--c-accent);
	}

	.select-user {
		font: inherit;
		font-size: var(--fs-14);
		padding: var(--sp-6) var(--sp-12);
		background: var(--c-card);
		color: var(--c-ink);
		border: 1px solid var(--c-line);
		border-radius: var(--rad-pill, 999px);
		cursor: pointer;
		outline: none;
		box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
	}

	.select-user:focus-visible {
		border-color: var(--c-accent);
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
