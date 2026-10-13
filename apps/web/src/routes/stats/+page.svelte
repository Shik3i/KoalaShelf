<script lang="ts">
	/* eslint-disable svelte/prefer-svelte-reactivity -- local date math inside $derived, not state */
	import EmptyState from '#lib/components/EmptyState.svelte';
	import type { ListeningStats } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { duration } from '#lib/format.ts';
	import Cover from '#lib/components/Cover.svelte';

	const t = $derived(i18n.m.stats);
	let stats = $state.raw<ListeningStats | null>(null);
	let error = $state<string | null>(null);

	$effect(() => {
		if (!session.client) return;
		session.api
			.listeningStats()
			.then((s) => (stats = s))
			.catch((e) => (error = (e as Error).message));
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

	const streak = $derived.by(() => {
		const days = stats?.days ?? {};
		const d = new Date();
		if (!days[key(d)]) d.setDate(d.getDate() - 1);
		let n = 0;
		while (days[key(d)] > 0) {
			n++;
			d.setDate(d.getDate() - 1);
		}
		return n;
	});

	const WEEKDAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
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
	const dateFmt = $derived(new Intl.DateTimeFormat(i18n.tag, { dateStyle: 'medium' }));
</script>

<svelte:head><title>{t.title} · KoalaShelf</title></svelte:head>

<h1>{t.title}</h1>

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
				>{duration(stats.totalTime)}</strong
			>
		</div>
		<div class="tile card">
			<span class="label">{t.today}</span><strong class="display">{duration(stats.today)}</strong>
		</div>
		<div class="tile card">
			<span class="label">{t.days}</span><strong class="display tabular">{activeDays}</strong>
		</div>
		<div class="tile card">
			<span class="label">{t.streak}</span><strong class="display">{t.streakDays(streak)}</strong>
		</div>
	</div>

	{#if stats.totalTime === 0}
		<EmptyState text={t.empty} />
	{:else}
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
			<section class="card block">
				<h2 class="label">{t.weekdays}</h2>
				<div class="bars">
					{#each weekdays as d (d.label)}
						<div class="bar" title={duration(d.v)}>
							<span style:--h={d.pct}></span>
							<small class="muted">{d.label}</small>
						</div>
					{/each}
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
										src={(w) => session.api.coverUrl(it.id, { width: w })}
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
	{/if}
{/if}

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
	}

	.bars {
		display: grid;
		grid-template-columns: repeat(7, 1fr);
		gap: var(--sp-8);
		align-items: end;
		height: 160px;
	}

	.bar {
		display: grid;
		grid-template-rows: 1fr auto;
		justify-items: center;
		gap: var(--sp-4);
		height: 100%;
	}

	.bar span {
		align-self: end;
		width: 100%;
		max-width: 36px;
		height: calc(max(var(--h), 0.02) * 100%);
		border-radius: 6px 6px 2px 2px;
		background: var(--c-accent);
	}

	.bar small {
		font-size: var(--fs-12);
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

	@container page (max-width: 760px) {
		.split {
			grid-template-columns: 1fr;
		}
	}
</style>
