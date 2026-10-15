<script lang="ts">
	import type { LibraryItem, YearStats } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { coverSrc, view } from '#lib/items.ts';
	import { duration } from '#lib/format.ts';
	import Cover from './Cover.svelte';
	import CountUp from './CountUp.svelte';
	import { renderYearCard } from '#lib/year-card.ts';
	import { saveFile } from '#lib/export.ts';
	import { toasts } from '#lib/toast.svelte.ts';
	import ImageDown from '@lucide/svelte/icons/image-down';

	/** Years with listening, newest first (from the day statistics). */
	let { years }: { years: number[] } = $props();

	const t = $derived(i18n.m.yearReview);
	let year = $state(new Date().getFullYear());
	let data = $state.raw<YearStats | null>(null);
	let covers = $state.raw<LibraryItem[]>([]);

	$effect(() => {
		const y = year;
		if (!session.client) return;
		data = null;
		covers = [];
		session.api
			.yearStats(y)
			.then(async (s) => {
				if (y !== year) return;
				data = s;
				const ids = s.booksWithCovers.slice(0, 12);
				covers = ids.length ? await session.api.items(ids).catch(() => []) : [];
			})
			.catch(() => (data = null));
	});

	const options = $derived(years.length ? years : [new Date().getFullYear()]);

	/** The year as a picture: shared on phones, saved elsewhere. */
	let drawing = $state(false);
	async function shareCard() {
		if (!data) return;
		drawing = true;
		try {
			const blob = await renderYearCard(year, data, covers);
			const file = new File([blob], `koalashelf-${year}.png`, { type: 'image/png' });
			if (matchMedia('(pointer: coarse)').matches && navigator.canShare?.({ files: [file] }))
				await navigator.share({ files: [file], title: t.cardTitle(year) }).catch(() => undefined);
			else saveFile(file.name, blob);
		} catch {
			toasts.show(t.cardFailed);
		} finally {
			drawing = false;
		}
	}
	const monthName = $derived((m: number) =>
		new Intl.DateTimeFormat(i18n.tag, { month: 'long' }).format(new Date(2024, m, 1))
	);
	const maxAuthor = $derived(Math.max(1, ...(data?.topAuthors.map((a) => a.time) ?? [1])));
	const maxGenre = $derived(Math.max(1, ...(data?.topGenres.map((g) => g.time) ?? [1])));
</script>

<section class="card review" aria-labelledby="yr-title">
	<div class="head">
		<h2 id="yr-title">{t.title(year)}</h2>
		{#if data?.totalListeningTime}
			<button class="btn ghost sm" class:is-loading={drawing} onclick={shareCard}>
				<ImageDown size={16} aria-hidden="true" />
				{t.share}
			</button>
		{/if}
		{#if options.length > 1}
			<label class="pick">
				<span class="sr-only">{t.year}</span>
				<select bind:value={year}>
					{#each options as y (y)}<option value={y}>{y}</option>{/each}
				</select>
			</label>
		{/if}
	</div>

	{#if !data}
		<div class="skeleton" style="height: 180px; border-radius: 18px"></div>
	{:else if !data.totalListeningTime}
		<p class="muted">{t.nothing(year)}</p>
	{:else}
		<div class="numbers">
			<div>
				<strong class="display"
					><CountUp value={data.totalListeningTime} format={duration} /></strong
				>
				<span class="muted">{t.listened}</span>
			</div>
			<div>
				<strong class="display"><CountUp value={data.numBooksFinished} /></strong>
				<span class="muted">{t.finished}</span>
			</div>
			<div>
				<strong class="display"><CountUp value={data.numBooksListened} /></strong>
				<span class="muted">{t.books}</span>
			</div>
			<div>
				<strong class="display"><CountUp value={data.totalListeningSessions} /></strong>
				<span class="muted">{t.sessions}</span>
			</div>
		</div>

		{#if covers.length}
			<div class="covers">
				{#each covers as item (item.id)}
					{@const v = view(item)}
					<a href="/book/{item.id}" title={v.title}>
						<Cover title={v.title} author={v.author} src={coverSrc(item)} sizes="64px" />
					</a>
				{/each}
			</div>
		{/if}

		<div class="facts">
			{#if data.topAuthors.length}
				<div>
					<h3 class="label">{t.topAuthors}</h3>
					<ol class="ranks">
						{#each data.topAuthors as a (a.name)}
							<li style:--w={a.time / maxAuthor}>
								<span>{a.name}</span><span class="muted tabular">{duration(a.time)}</span>
							</li>
						{/each}
					</ol>
				</div>
			{/if}
			{#if data.topGenres.length}
				<div>
					<h3 class="label">{t.topGenres}</h3>
					<ol class="ranks">
						{#each data.topGenres as g (g.genre)}
							<li style:--w={g.time / maxGenre}>
								<span>{g.genre}</span><span class="muted tabular">{duration(g.time)}</span>
							</li>
						{/each}
					</ol>
				</div>
			{/if}
			<div class="highlights">
				{#if data.mostListenedMonth?.time}
					<p>
						<span class="label">{t.bestMonth}</span>
						<strong>{monthName(data.mostListenedMonth.month)}</strong>
						<span class="muted">{duration(data.mostListenedMonth.time)}</span>
					</p>
				{/if}
				{#if data.mostListenedNarrator?.name}
					<p>
						<span class="label">{t.narrator}</span>
						<strong>{data.mostListenedNarrator.name}</strong>
						<span class="muted">{duration(data.mostListenedNarrator.time)}</span>
					</p>
				{/if}
				{#if data.longestAudiobookFinished}
					<p>
						<span class="label">{t.longest}</span>
						<a href="/book/{data.longestAudiobookFinished.id}"
							><strong>{data.longestAudiobookFinished.title}</strong></a
						>
						<span class="muted">{duration(data.longestAudiobookFinished.duration)}</span>
					</p>
				{/if}
			</div>
		</div>
	{/if}
</section>

<style>
	.review {
		display: grid;
		gap: var(--sp-24);
		padding: var(--sp-24);
		border-radius: var(--r-card-lg);
		margin-bottom: var(--sp-16);
		background:
			radial-gradient(
				120% 90% at 0% 0%,
				color-mix(in oklab, var(--c-accent) 14%, transparent),
				transparent 60%
			),
			var(--c-card);
	}

	.head {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--sp-8) var(--sp-16);
	}

	.head h2 {
		flex: 1 1 auto;
	}

	.btn.sm {
		min-height: 40px;
		font-size: var(--fs-13);
	}

	h2 {
		font-size: var(--fs-34);
	}

	.pick select {
		min-height: 40px;
		padding: 0 var(--sp-12);
		border: 1.5px solid var(--c-field);
		border-radius: var(--r-pill);
		background: var(--c-bg);
		color: var(--c-ink);
		font: inherit;
		font-weight: var(--fw-bold);
	}

	.numbers {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
		gap: var(--sp-16);
	}

	.numbers div {
		display: grid;
		gap: var(--sp-4);
	}

	.numbers strong {
		font-size: var(--fs-34);
	}

	.numbers .muted {
		font-size: var(--fs-13);
	}

	.covers {
		display: flex;
		gap: var(--sp-8);
		overflow-x: auto;
		scrollbar-width: none;
		padding-bottom: var(--sp-4);
	}

	.covers a {
		flex: none;
		width: 64px;
		border-radius: 6px;
		overflow: hidden;
		transition: translate var(--motion-fast) var(--motion-ease);
	}

	.covers a:hover {
		translate: 0 -3px;
	}

	.facts {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(min(100%, 240px), 1fr));
		gap: var(--sp-24);
	}

	h3 {
		margin-bottom: var(--sp-8);
	}

	.ranks {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--sp-8);
	}

	.ranks li {
		position: relative;
		display: flex;
		justify-content: space-between;
		gap: var(--sp-12);
		padding: var(--sp-8) var(--sp-12);
		border-radius: var(--r-card);
		font-size: var(--fs-14);
		isolation: isolate;
	}

	/* The row's width share as a soft bar behind it. */
	.ranks li::before {
		content: '';
		position: absolute;
		inset: 0 auto 0 0;
		width: calc(var(--w) * 100%);
		z-index: -1;
		border-radius: inherit;
		background: color-mix(in oklab, var(--c-accent) 18%, transparent);
	}

	.highlights {
		display: grid;
		gap: var(--sp-12);
		align-content: start;
	}

	.highlights p {
		display: grid;
		margin: 0;
	}

	.highlights .muted {
		font-size: var(--fs-13);
	}

	.highlights a:hover {
		text-decoration: underline;
		text-underline-offset: 3px;
	}
</style>
