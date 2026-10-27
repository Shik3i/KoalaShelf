<script lang="ts">
	/* eslint-disable svelte/prefer-svelte-reactivity -- local date math inside $derived, not state */
	import EmptyState from '#lib/components/EmptyState.svelte';
	import type {
		LibraryItem,
		ListeningStats,
		ListeningSession,
		ServerUser
	} from '@koalashelf/abs-client';
	import { coverSrc } from '#lib/items.ts';
	import { session } from '#lib/session.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { duration, span } from '#lib/format.ts';
	import Cover from '#lib/components/Cover.svelte';
	import CountUp from '#lib/components/CountUp.svelte';
	import YearReview from '#lib/components/YearReview.svelte';
	import LibraryFacts from '#lib/components/LibraryFacts.svelte';
	import GoalRing from '#lib/components/GoalRing.svelte';
	import SessionRow, { meaningful } from '#lib/components/SessionRow.svelte';
	import { prefs } from '#lib/prefs.svelte.ts';
	import { streak as streakOf } from '#lib/export.ts';

	const t = $derived(i18n.m.stats);
	let stats = $state.raw<ListeningStats | null>(null);
	let error = $state<string | null>(null);
	/** Items of the top list (stats carry no cover info; avoids requests for missing covers). */
	let topItems = $state.raw<Map<string, LibraryItem>>(new Map());
	let recentSessions = $state.raw<ListeningSession[]>([]);
	let recentItems = $state.raw<Map<string, LibraryItem>>(new Map());
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

	$effect(() => {
		if (!session.client) return;
		const uid = session.isAdmin ? (selectedUserId ?? undefined) : undefined;
		stats = null;
		error = null;
		session.api
			.listeningStats(uid)
			.then(async (s) => {
				stats = s;
				const ids = Object.values(s.items)
					.sort((a, b) => b.timeListening - a.timeListening)
					.slice(0, 8)
					.map((i) => i.id);
				if (ids.length) {
					const items = await session.api.items(ids).catch(() => []);
					topItems = new Map(items.map((i) => [i.id, i]));
				} else {
					topItems = new Map();
				}
			})
			.catch((e) => (error = (e as Error).message));

		session.api
			.listeningSessions(0, 10, uid)
			.then(async (r) => {
				const fresh = r.sessions.filter(meaningful).slice(0, 8);
				recentSessions = fresh;
				const ids = [...new Set(fresh.map((s) => s.libraryItemId))];
				if (ids.length) {
					const items = await session.api.items(ids).catch(() => []);
					recentItems = new Map(items.map((i) => [i.id, i]));
				} else {
					recentItems = new Map();
				}
			})
			.catch(() => {
				recentSessions = [];
				recentItems = new Map();
			});
	});

	const key = (d: Date) =>
		`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

	/** 53 weeks x 7 days, Monday first, ending with the current week. */
	const weeks = $derived.by(() => {
		const days = stats?.days ?? {};
		const today = new Date();
		const start = new Date(today);
		start.setDate(today.getDate() - ((today.getDay() + 6) % 7) - 52 * 7);
		const cols: { date: string; secs: number; future: boolean }[][] = [];
		for (let w = 0; w < 53; w++) {
			const col = [];
			for (let d = 0; d < 7; d++) {
				const day = new Date(start);
				day.setDate(start.getDate() + w * 7 + d);
				col.push({ date: key(day), secs: days[key(day)] ?? 0, future: day > today });
			}
			cols.push(col);
		}
		return cols;
	});

	const level = (secs: number) =>
		secs <= 0 ? 0 : secs < 900 ? 1 : secs < 2700 ? 2 : secs < 5400 ? 3 : 4;

	const activeDays = $derived(Object.values(stats?.days ?? {}).filter((s) => s > 0).length);

	const streak = $derived(streakOf(stats?.days ?? {}));
	const GOALS = [0, 15, 30, 45, 60, 90, 120];
	/** Days of the last week that reached the goal. */
	const goalWeek = $derived.by(() => {
		const days = stats?.days ?? {};
		const out: { label: string; reached: boolean; secs: number }[] = [];
		const fmt = new Intl.DateTimeFormat(i18n.tag, { weekday: 'narrow' });
		for (let i = 6; i >= 0; i--) {
			const d = new Date();
			d.setDate(d.getDate() - i);
			const secs = days[key(d)] ?? 0;
			out.push({ label: fmt.format(d), secs, reached: secs >= prefs.dailyGoal * 60 });
		}
		return out;
	});

	const WEEKDAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
	let hoveredDay = $state<{ label: string; v: number } | null>(null);
	const maxWeekdaySecs = $derived(Math.max(0, ...WEEKDAYS.map((d) => stats?.dayOfWeek[d] ?? 0)));
	const weekdays = $derived.by(() => {
		const fmt = new Intl.DateTimeFormat(i18n.tag, { weekday: 'short' });
		const values = WEEKDAYS.map((d) => stats?.dayOfWeek[d] ?? 0);
		const max = Math.max(1, ...values);
		// 2024-01-01 was a Monday.
		return values.map((v, i) => ({ label: fmt.format(new Date(2024, 0, 1 + i)), v, pct: v / max }));
	});

	const top = $derived(
		Object.values(stats?.items ?? {})
			.sort((a, b) => b.timeListening - a.timeListening)
			.slice(0, 8)
	);
	/** Years with listening, newest first. */
	const years = $derived(
		[...new Set(Object.keys(stats?.days ?? {}).map((d) => +d.slice(0, 4)))].sort((a, b) => b - a)
	);
	const dateFmt = $derived(new Intl.DateTimeFormat(i18n.tag, { dateStyle: 'medium' }));

	function parseSessionDate(s: ListeningSession): Date {
		if (typeof s?.startedAt === 'number') {
			const d = new Date(s.startedAt);
			if (!isNaN(d.getTime())) return d;
		}
		if (typeof s?.startedAt === 'string') {
			const parsed = Date.parse(s.startedAt);
			if (!isNaN(parsed)) return new Date(parsed);
		}
		if (typeof s?.date === 'string') {
			const parsed = Date.parse(s.date);
			if (!isNaN(parsed)) return new Date(parsed);
		}
		return new Date();
	}

	const recentDays = $derived.by(() => {
		const today = new Date();
		const yesterday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1);
		const fmt = new Intl.DateTimeFormat(i18n.locale, {
			weekday: 'long',
			day: 'numeric',
			month: 'long'
		});
		const groups: { date: string; label: string; total: number; list: ListeningSession[] }[] = [];
		for (const s of recentSessions) {
			if (!s) continue;
			const when = parseSessionDate(s);
			const date = key(when);
			let g = groups.at(-1);
			if (g?.date !== date) {
				const label = (() => {
					try {
						return date === key(today)
							? i18n.m.history.today
							: date === key(yesterday)
								? i18n.m.history.yesterday
								: fmt.format(when);
					} catch {
						return date;
					}
				})();
				g = { date, label, total: 0, list: [] };
				groups.push(g);
			}
			g.total += s.timeListening ?? 0;
			g.list.push(s);
		}
		return groups;
	});
</script>

<svelte:head><title>{t.title} · KoalaShelf</title></svelte:head>

<div class="stats-header">
	<div>
		<h1>{t.title}</h1>
		{#if selectedUser && selectedUserId !== session.user?.id}
			<p class="muted admin-viewing-subtitle">
				<span class="admin-badge">{t.adminBadge}</span>
				<span>{t.viewingUser(selectedUser.username)}</span>
				<button type="button" class="link-btn" onclick={() => (selectedUserId = null)}
					>{t.resetToMe}</button
				>
			</p>
		{/if}
	</div>
	{#if session.isAdmin && users.length > 1}
		<div class="user-selector">
			<label for="admin-user-select" class="sr-only">{t.adminUserSelect}</label>
			<select
				id="admin-user-select"
				class="select-user"
				aria-label={t.adminUserSelect}
				value={selectedUserId ?? ''}
				onchange={(e) => (selectedUserId = e.currentTarget.value || null)}
			>
				<option value="">👤 {t.viewingSelf} ({session.user?.username})</option>
				{#each users as u (u.id)}
					{#if u.id !== session.user?.id}
						<option value={u.id}
							>👥 {u.username}
							{u.type === 'root' || u.type === 'admin' ? `(${u.type})` : ''}</option
						>
					{/if}
				{/each}
			</select>
		</div>
	{/if}
</div>

{#if error}
	<p class="muted">{i18n.m.common.error(error)}</p>
{:else if !stats}
	<div class="tiles" aria-busy="true">
		{#each [0, 1, 2, 3] as i (i)}<div
				class="skeleton"
				style="height: 92px; border-radius: 22px"
			></div>{/each}
	</div>
{:else if stats}
	<div class="tiles">
		<div class="tile card">
			<span class="label">{t.total}</span><strong class="display"
				><CountUp value={stats.totalTime} format={duration} /></strong
			>
		</div>
		<div class="tile card">
			<span class="label">{t.today}</span><strong class="display"
				><CountUp value={stats.today} format={duration} /></strong
			>
		</div>
		<div class="tile card">
			<span class="label">{t.days}</span><strong class="display tabular"
				><CountUp value={activeDays} /></strong
			>
		</div>
		<div class="tile card">
			<span class="label">{t.streak}</span><strong class="display"
				><CountUp value={streak} format={(n) => t.streakDays(Math.round(n))} /></strong
			>
		</div>
	</div>

	<section class="card goal" aria-labelledby="goal-title">
		{#if prefs.dailyGoal}
			<GoalRing seconds={stats.today} goal={prefs.dailyGoal} {streak} size={88} />
		{/if}
		<div class="goal-text">
			<h2 class="label" id="goal-title">{i18n.m.goal.title}</h2>
			<p class="muted">{prefs.dailyGoal ? i18n.m.goal.hint : i18n.m.goal.off}</p>
			<div class="choices" role="group" aria-label={i18n.m.goal.title}>
				{#each GOALS as m (m)}
					<button
						class="chip"
						aria-pressed={prefs.dailyGoal === m}
						onclick={() => prefs.set('dailyGoal', m)}
						>{m ? i18n.m.player.timerMinutes(m) : i18n.m.settings.off}</button
					>
				{/each}
			</div>
			{#if prefs.dailyGoal}
				<ol class="week" aria-label={i18n.m.goal.week}>
					{#each goalWeek as d, i (i)}
						<li class:reached={d.reached} title={duration(d.secs)}>
							<span class="dot" aria-hidden="true"></span>
							<span class="sr-only">{d.reached ? i18n.m.goal.reachedDay : ''}</span>
							<span class="muted">{d.label}</span>
						</li>
					{/each}
				</ol>
			{/if}
		</div>
	</section>

	{#if stats.totalTime === 0}
		<EmptyState text={selectedUserId ? t.userEmpty : t.empty} />
	{:else}
		{#if !selectedUserId || selectedUserId === session.user?.id}
			<YearReview {years} />
		{/if}

		<section class="card block">
			<h2 class="label">{t.year}</h2>
			<div class="heat" role="img" aria-label={t.year}>
				{#each weeks as col, w (w)}
					<div class="col">
						{#each col as d (d.date)}
							<span
								class="cell l{level(d.secs)}"
								class:future={d.future}
								title="{dateFmt.format(new Date(d.date))} · {d.secs ? duration(d.secs) : '–'}"
							></span>
						{/each}
					</div>
				{/each}
			</div>
			<div class="legend muted">
				{t.less}
				{#each [0, 1, 2, 3, 4] as l (l)}<span class="cell l{l}"></span>{/each}
				{t.more}
			</div>
		</section>

		<div class="split">
			<section class="card block weekday-card">
				<div class="card-head">
					<h2 class="label">{t.weekdays}</h2>
					{#if hoveredDay}
						<span class="badge-day tabular">{hoveredDay.label}: {duration(hoveredDay.v)}</span>
					{:else if maxWeekdaySecs > 0}
						<span class="max-badge muted tabular">{duration(maxWeekdaySecs)} max</span>
					{/if}
				</div>
				<div class="chart-wrap">
					<div class="chart-ceiling" aria-hidden="true"></div>
					<div class="bars" role="region" aria-label={t.weekdays}>
						{#each weekdays as d, i (d.label)}
							<div
								class="bar-col"
								role="img"
								aria-label="{d.label}: {duration(d.v)}"
								title="{d.label}: {duration(d.v)}"
								onmouseenter={() => (hoveredDay = d)}
								onmouseleave={() => (hoveredDay = null)}
							>
								{#if d.v > 0}
									<span class="fill" style:--h={d.pct} style:--i={i}></span>
								{:else}
									<span class="zero-dot" aria-hidden="true"></span>
								{/if}
							</div>
						{/each}
					</div>
					<div class="axis-line" aria-hidden="true"></div>
					<div class="axis-labels">
						{#each weekdays as d (d.label)}
							<small class="muted" class:peak={d.pct === 1 && d.v > 0}>{d.label}</small>
						{/each}
					</div>
				</div>
			</section>

			<section class="card block">
				<h2 class="label">{t.top}</h2>
				<ol class="top">
					{#each top as it (it.id)}
						<li>
							<a href="/book/{it.id}">
								<div class="c">
									<Cover
										title={it.mediaMetadata.title}
										author={it.mediaMetadata.authors?.map((a) => a.name).join(', ') ?? ''}
										src={topItems.has(it.id) ? coverSrc(topItems.get(it.id)!) : null}
										sizes="48px"
									/>
								</div>
								<span class="ttl">{it.mediaMetadata.title}</span>
								<span class="muted tabular">{duration(it.timeListening)}</span>
							</a>
						</li>
					{/each}
				</ol>
			</section>
		</div>

		{#if recentSessions.length > 0}
			<section class="card block activity-card">
				<div class="activity-head">
					<div>
						<h2 class="label">{t.recentActivity}</h2>
						<p class="muted activity-hint">{t.recentActivityHint}</p>
					</div>
					<a href="/history" class="btn ghost sm">
						<span>{t.viewAllHistory}</span>
						<span aria-hidden="true">→</span>
					</a>
				</div>

				<div class="activity-days">
					{#each recentDays as d (d.date)}
						<div class="activity-day">
							<div class="day-header">
								<span class="day-label">{d.label}</span>
								<span class="day-total muted tabular">{span(d.total)}</span>
							</div>
							<ul class="session-list">
								{#each d.list as s (s.id)}
									<SessionRow {s} withBook item={recentItems.get(s.libraryItemId)} />
								{/each}
							</ul>
						</div>
					{/each}
				</div>
			</section>
		{/if}
	{/if}
{/if}

<!-- After the listening stats: appearing first, it would be pushed down when they load. -->
{#if stats || error}<LibraryFacts />{/if}

<style>
	h1 {
		margin-bottom: var(--sp-24);
	}

	.tiles {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
		gap: var(--sp-12);
		margin-bottom: var(--sp-16);
	}

	.tile {
		display: grid;
		gap: var(--sp-8);
		padding: var(--sp-20);
		border-radius: var(--r-card-lg);
	}

	.tile strong {
		font-size: var(--fs-24);
		font-weight: var(--fw-regular);
	}

	.goal {
		display: flex;
		align-items: center;
		gap: var(--sp-24);
		padding: var(--sp-24);
		margin-bottom: var(--sp-16);
		border-radius: var(--r-card-lg);
	}

	.goal-text {
		display: grid;
		gap: var(--sp-8);
		min-width: 0;
		justify-items: start;
	}

	.goal-text p {
		margin: 0;
		font-size: var(--fs-14);
	}

	.choices {
		display: flex;
		flex-wrap: wrap;
		gap: var(--sp-8);
	}

	.choices .chip {
		background: var(--c-bg);
	}

	.choices .chip[aria-pressed='true'] {
		background: var(--c-accent);
		color: var(--c-on-accent);
	}

	.week {
		list-style: none;
		display: flex;
		gap: var(--sp-12);
		margin: var(--sp-4) 0 0;
		padding: 0;
	}

	.week li {
		display: grid;
		justify-items: center;
		gap: 4px;
		font-size: var(--fs-12);
	}

	.week .dot {
		width: 14px;
		height: 14px;
		border-radius: 50%;
		background: color-mix(in oklab, var(--c-ink) 12%, transparent);
	}

	.week .reached .dot {
		background: var(--c-accent);
	}

	@media (forced-colors: active) {
		.week .dot {
			border: 1px solid CanvasText;
		}

		.week .reached .dot {
			background: Highlight;
		}
	}

	@media (max-width: 560px) {
		.goal {
			flex-direction: column;
			align-items: flex-start;
		}
	}

	.block {
		display: grid;
		gap: var(--sp-16);
		padding: var(--sp-24);
		margin-bottom: var(--sp-16);
		border-radius: var(--r-card-lg);
		min-width: 0;
	}

	/* 53 weeks always fit the card: cells shrink instead of scrolling. */
	.heat {
		display: grid;
		grid-auto-flow: column;
		grid-auto-columns: minmax(0, 1fr);
		gap: 2px;
	}

	.col {
		display: grid;
		gap: 2px;
	}

	.cell {
		display: block;
		aspect-ratio: 1;
		width: 100%;
		min-width: 0;
		border-radius: 2px;
		background: var(--c-track);
	}

	.cell.l1 {
		background: color-mix(in oklab, var(--c-accent) 35%, var(--c-track));
	}
	.cell.l2 {
		background: color-mix(in oklab, var(--c-accent) 60%, var(--c-track));
	}
	.cell.l3 {
		background: color-mix(in oklab, var(--c-accent) 80%, var(--c-track));
	}
	.cell.l4 {
		background: var(--c-accent);
	}
	.cell.future {
		visibility: hidden;
	}

	.legend {
		display: flex;
		align-items: center;
		justify-content: flex-end;
		gap: 4px;
		font-size: var(--fs-12);
	}

	.legend .cell {
		display: inline-block;
		width: 12px;
	}

	.split {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: var(--sp-16);
		margin-bottom: var(--sp-16);
	}

	.split > .block {
		margin-bottom: 0;
	}

	.weekday-card {
		display: flex;
		flex-direction: column;
		min-height: 280px;
	}

	.card-head {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: var(--sp-8);
	}

	.max-badge,
	.badge-day {
		font-size: var(--fs-12);
	}

	.badge-day {
		color: var(--c-accent);
		font-weight: var(--fw-semibold);
	}

	.chart-wrap {
		position: relative;
		flex: 1;
		display: flex;
		flex-direction: column;
		min-height: 200px;
		margin-top: var(--sp-16);
	}

	.chart-ceiling {
		position: absolute;
		top: 0;
		left: 0;
		right: 0;
		border-top: 1px dashed color-mix(in oklab, var(--c-ink) 12%, transparent);
		pointer-events: none;
	}

	.bars {
		flex: 1;
		display: grid;
		grid-template-columns: repeat(7, 1fr);
		gap: var(--sp-8);
		align-items: end;
		min-height: 160px;
	}

	.bar-col {
		height: 100%;
		display: flex;
		flex-direction: column;
		justify-content: flex-end;
		align-items: center;
		cursor: default;
		outline: none;
	}

	.bar-col .fill {
		width: 100%;
		max-width: 42px;
		height: calc(max(var(--h), 0.02) * 100%);
		border-radius: 6px 6px 2px 2px;
		background: var(--c-accent);
		/* Rising one after another, like the level meters on an amplifier. */
		transform-origin: bottom;
		animation: meter 700ms var(--motion-out) both;
		animation-delay: calc(var(--i, 0) * 60ms + 150ms);
		transition: filter var(--dur-fast);
	}

	.bar-col:hover .fill {
		filter: brightness(1.18);
	}

	.zero-dot {
		width: 8px;
		height: 2px;
		border-radius: 1px;
		background: color-mix(in oklab, var(--c-ink) 20%, transparent);
	}

	.axis-line {
		height: 1px;
		background: color-mix(in oklab, var(--c-ink) 15%, transparent);
		margin: 0;
	}

	.axis-labels {
		display: grid;
		grid-template-columns: repeat(7, 1fr);
		gap: var(--sp-8);
		text-align: center;
		padding-top: var(--sp-8);
	}

	.axis-labels small {
		font-size: var(--fs-12);
	}

	.axis-labels small.peak {
		color: var(--c-ink);
		font-weight: var(--fw-semibold);
	}

	@media (forced-colors: active) {
		.bar-col .fill {
			background: Highlight;
		}

		.axis-line,
		.chart-ceiling {
			border-color: CanvasText;
			background: CanvasText;
		}
	}

	@keyframes meter {
		from {
			scale: 1 0;
		}
	}

	.top {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--sp-4);
	}

	.top a {
		display: grid;
		grid-template-columns: 40px 1fr auto;
		align-items: center;
		gap: var(--sp-12);
		padding: var(--sp-4);
		border-radius: var(--r-card);
		font-size: var(--fs-14);
	}

	.top a:hover {
		background: var(--c-bg);
	}

	.ttl {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		font-weight: var(--fw-semibold);
	}

	.empty {
		padding: var(--sp-48) 0;
		text-align: center;
	}

	.activity-card {
		margin-bottom: var(--sp-16);
	}

	.activity-head {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: var(--sp-16);
		flex-wrap: wrap;
	}

	.activity-hint {
		margin: var(--sp-4) 0 0;
		font-size: var(--fs-13);
	}

	.activity-days {
		display: grid;
		gap: var(--sp-20);
	}

	.activity-day {
		display: grid;
		gap: var(--sp-8);
	}

	.day-header {
		display: flex;
		justify-content: space-between;
		align-items: baseline;
		padding-inline: var(--sp-4);
	}

	.day-label {
		font-size: var(--fs-14);
		font-weight: var(--fw-semibold);
	}

	.day-total {
		font-size: var(--fs-13);
	}

	.session-list {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--sp-8);
	}

	.session-list :global(.session) {
		background: var(--c-bg);
		border: 1px solid color-mix(in oklab, var(--c-ink) 8%, transparent);
	}

	.stats-header {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		gap: var(--sp-16);
		flex-wrap: wrap;
		margin-bottom: var(--sp-16);
	}

	.stats-header h1 {
		margin: 0;
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
		padding: var(--sp-8) var(--sp-12);
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

	@container page (max-width: 760px) {
		.split {
			grid-template-columns: 1fr;
		}
	}
</style>
