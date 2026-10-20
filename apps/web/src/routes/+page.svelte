<script lang="ts">
	import { byId, unique } from '#lib/unique.ts';
	import EmptyState from '#lib/components/EmptyState.svelte';
	import type { LibraryItemMinified, Shelf } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { progress } from '#lib/progress.svelte.ts';
	import { player } from '#lib/player/player.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { coverSrc, view } from '#lib/items.ts';
	import { clock, duration } from '#lib/format.ts';
	import Turntable from '#lib/components/Turntable.svelte';
	import Crate, { type CrateEntry } from '#lib/components/Crate.svelte';
	import RecordCrate, { type CrateRecord } from '#lib/components/RecordCrate.svelte';
	import PlayPauseIcon from '#lib/components/PlayPauseIcon.svelte';
	import { toasts } from '#lib/toast.svelte.ts';
	import { prefs } from '#lib/prefs.svelte.ts';
	import { shelfOrder } from '#lib/shelves.ts';
	import { inSeriesOrder } from '#lib/series-order.ts';
	import X from '@lucide/svelte/icons/x';
	import Settings from '@lucide/svelte/icons/settings';
	import Disc from '@lucide/svelte/icons/disc-3';
	import GoalRing from '#lib/components/GoalRing.svelte';
	import CrateDiggerModal from '#lib/components/CrateDiggerModal.svelte';
	import { streak } from '#lib/export.ts';

	const t = $derived(i18n.m.home);
	const WEEK = 7 * 24 * 3600 * 1000;

	let shelves = $state.raw<Shelf[] | null>(null);
	let error = $state<string | null>(null);
	let diggerOpen = $state(false);

	$effect(() => {
		const lib = session.libraryId;
		if (!lib) return;
		void progress.load(true);
		session.api
			.personalized(lib, 16)
			.then((s) => {
				shelves = s;
				for (const shelf of s) {
					if (shelf.type === 'book') {
						for (const b of shelf.entities) {
							if (b.userMediaProgress) progress.merge(b.userMediaProgress);
						}
					}
				}
			})
			.catch((e) => (error = (e as Error).message));
	});

	// Daily goal: today's listening and the streak, refreshed while playing.
	let today = $state<{ secs: number; streak: number } | null>(null);
	$effect(() => {
		if (!prefs.dailyGoal || !session.client) return;
		const playing = player.playing;
		const load = () =>
			session.api
				.listeningStats()
				.then((s) => (today = { secs: s.today, streak: streak(s.days) }))
				.catch(() => undefined);
		void load();
		if (!playing) return;
		const id = setInterval(load, 60_000);
		return () => clearInterval(id);
	});

	const greeting = $derived.by(() => {
		const h = new Date().getHours();
		return h < 11 ? t.greetingMorning : h < 18 ? t.greetingDay : t.greetingEvening;
	});

	const books = (s: Shelf | undefined) => (s?.type === 'book' ? s.entities : []);
	const continueItems = $derived(books(shelves?.find((s) => s.id === 'continue-listening')));
	const hero = $derived<LibraryItemMinified | undefined>(continueItems[0]);
	const heroView = $derived(hero ? view(hero) : null);
	const heroProgress = $derived(
		hero ? (progress.get(hero.id) ?? hero.userMediaProgress) : undefined
	);
	const heroFinished = $derived(heroProgress?.isFinished ?? false);
	const heroIsPlaying = $derived(!!hero && player.item?.id === hero.id && !heroFinished);
	const heroPos = $derived(
		hero ? (heroIsPlaying ? player.currentTime : (heroProgress?.currentTime ?? 0)) : 0
	);
	const heroPct = $derived(
		hero ? (heroFinished ? 1 : heroIsPlaying ? player.progress : (heroProgress?.progress ?? 0)) : 0
	);

	const LABELS = $derived<Record<string, string>>({
		'continue-listening': t.continue,
		'continue-series': t.continueSeries,
		'recently-added': t.recentlyAdded,
		'recent-series': t.recentSeries,
		recommended: t.discover,
		'listen-again': t.listenAgain
	});

	function entry(item: LibraryItemMinified, href = `/book/${item.id}`): CrateEntry {
		const v = view(item);
		return { id: item.id, href, title: v.title, author: v.author, cover: coverSrc(item) };
	}

	const crates = $derived.by(() => {
		if (!shelves) return [];
		return shelves.flatMap((s) => {
			const label = LABELS[s.id];
			if (!label) return [];
			let entries: CrateEntry[] = [];
			let series: { id: string; name: string; records: CrateRecord[] }[] | undefined;
			let note = '';
			if (s.type === 'book') {
				const list = s.id === 'continue-listening' ? s.entities.slice(1) : s.entities;
				entries = list.map((i) => entry(i));
				if (
					s.id === 'recently-added' &&
					list.length &&
					list.every((i) => Date.now() - i.addedAt < WEEK)
				)
					note = t.thisWeek;
			} else if (s.type === 'series') {
				// Every series is a small crate of its own: its books can be flipped through.
				series = unique(
					s.entities.filter((se) => se.books?.length),
					byId
				).map((se) => ({
					id: se.id,
					name: se.name,
					records: inSeriesOrder(se.books, se.name).map((b) => ({
						...entry(b),
						finished: progress.get(b.id)?.isFinished
					}))
				}));
			}
			return entries.length || series?.length ? [{ id: s.id, label, note, entries, series }] : [];
		});
	});

	/** Settings > Library & home: hidden shelves left out, the rest in the listener's order. */
	const shownCrates = $derived.by(() => {
		const order: string[] = shelfOrder();
		const rank = (id: string) => (id === 'continue-listening' ? -1 : order.indexOf(id));
		return crates
			.filter((c) => !prefs.hiddenShelves.includes(c.id))
			.sort((a, b) => rank(a.id) - rank(b.id));
	});

	/** Hide from "continue listening" (progress stays), then refresh the shelves. */
	async function hideHero() {
		const p = hero && progress.get(hero.id);
		if (!p || !session.libraryId) return;
		const lib = session.libraryId;
		const itemId = p.libraryItemId;
		await session.api.hideFromContinueListening(p.id);
		shelves = await session.api.personalized(lib, 16);
		toasts.show(i18n.m.toast.hidden, {
			label: i18n.m.toast.undo,
			run: async () => {
				await session.api.updateProgress(itemId, { hideFromContinueListening: false });
				shelves = await session.api.personalized(lib, 16);
			}
		});
	}

	function resume() {
		if (!hero) return;
		if (heroIsPlaying) player.toggle();
		else void player.play(hero.id);
	}
</script>

<svelte:head><title>KoalaShelf</title></svelte:head>

<header class="top">
	<div>
		<span class="label">{session.library?.name}</span>
		<h1>{greeting}{session.user ? `, ${session.user.username}` : ''}</h1>
	</div>
	<div class="right">
		{#if prefs.dailyGoal && today}
			<GoalRing seconds={today.secs} goal={prefs.dailyGoal} streak={today.streak} href="/stats" />
		{/if}
		<button
			class="round"
			onclick={() => (diggerOpen = true)}
			aria-label={i18n.m.crateDigger.button}
			title={i18n.m.crateDigger.subtitle}
		>
			<Disc size={20} />
		</button>
		<div class="tools">
			<a class="round" href="/settings" aria-label={i18n.m.nav.settings}>
				<Settings size={20} />
			</a>
		</div>
	</div>
</header>

{#if error}
	<p class="muted">{i18n.m.common.error(error)}</p>
{:else if shelves && !hero && crates.length === 0}
	<EmptyState text={t.empty}>
		<a class="btn primary" href="/library">{t.toLibrary}</a>
	</EmptyState>
{/if}

{#if hero && heroView}
	<section class="hero card">
		<a class="deck" href="/book/{hero.id}" aria-label={heroView.title}>
			<Turntable
				title={heroView.title}
				author={heroView.author}
				src={heroView.cover}
				playing={heroIsPlaying && player.playing}
				speed={player.rate}
				progress={heroPct}
			/>
		</a>
		<div class="hero-info">
			<div class="hero-top">
				<span class="label accent">{t.continue}</span>
				<button class="hide" onclick={hideHero} title={t.hide} aria-label={t.hide}
					><X size={16} /></button
				>
			</div>
			<h2><a href="/book/{hero.id}">{heroView.title}</a></h2>
			<p class="muted">{heroView.author}{heroView.narrator ? ` · ${heroView.narrator}` : ''}</p>
			<div class="bar" style:--p={heroPct}></div>
			<p class="muted tabular small">
				{Math.round(heroPct * 100)} % · {i18n.m.book.remaining(
					duration(Math.max(0, heroView.duration - heroPos))
				)}
			</p>
			<button
				class="btn primary big"
				class:is-loading={hero && player.loadingId === hero.id}
				onclick={resume}
			>
				<PlayPauseIcon playing={heroIsPlaying && player.playing} size={18} />
				{heroIsPlaying && player.playing ? i18n.m.player.pause : i18n.m.book.resume(clock(heroPos))}
			</button>
		</div>
	</section>
{/if}

{#if !shelves && !error}
	<div class="skeleton hero-skel" aria-busy="true"></div>
	<div class="crates">
		{#each [0, 1] as i (i)}<div class="skeleton crate-skel"></div>{/each}
	</div>
{/if}

<div class="crates">
	{#each unique(shownCrates, byId) as c (c.id)}
		{#if c.series}
			<section class="series-shelf" aria-label={c.label}>
				<h2 class="label">{c.label}</h2>
				<div class="series-row">
					{#each c.series as se (se.id)}
						<RecordCrate
							name={se.name}
							href="/series/{se.id}"
							note={i18n.m.series.books(se.records.length)}
							records={se.records}
							sizes="150px"
						/>
					{/each}
				</div>
			</section>
		{:else}
			<Crate title={c.label} note={c.note} entries={c.entries} />
		{/if}
	{/each}
</div>

<CrateDiggerModal bind:open={diggerOpen} />

<style>
	.top {
		display: flex;
		justify-content: space-between;
		align-items: end;
		gap: var(--sp-16);
		margin-bottom: var(--sp-24);
	}

	.right {
		display: flex;
		align-items: center;
		gap: var(--sp-16);
	}

	.tools {
		display: none;
		align-items: center;
		gap: var(--sp-8);
	}

	.round {
		display: grid;
		place-items: center;
		width: var(--layout-touch);
		height: var(--layout-touch);
		border: 0;
		border-radius: 50%;
		background: var(--c-card);
		color: var(--c-ink);
	}

	.hero {
		display: grid;
		grid-template-columns: minmax(200px, 300px) 1fr;
		gap: var(--sp-32);
		align-items: center;
		padding: var(--sp-24);
		margin-bottom: var(--sp-40);
		border-radius: var(--r-card-lg);
	}

	.hero-top {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}

	.hide {
		display: grid;
		place-items: center;
		width: 36px;
		height: 36px;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: var(--c-muted);
		position: relative;
	}

	.hide::after {
		content: '';
		position: absolute;
		inset: -4px;
	}

	.hide:hover {
		background: var(--c-bg);
		color: var(--c-ink);
	}

	.hero-info {
		display: grid;
		gap: var(--sp-8);
		align-content: center;
		max-width: 520px;
	}

	.accent {
		color: var(--c-accent);
	}

	.hero h2 {
		font-size: var(--fs-34);
	}

	.bar {
		height: 6px;
		margin-top: var(--sp-12);
		border-radius: var(--r-pill);
		background: linear-gradient(to right, var(--c-accent) calc(var(--p) * 100%), var(--c-track) 0);
	}

	.small {
		font-size: var(--fs-13);
	}

	.big {
		justify-self: start;
		min-height: 52px;
		margin-top: var(--sp-8);
		padding: 0 var(--sp-24);
	}

	.hero-skel {
		height: 260px;
		margin-bottom: var(--sp-40);
		border-radius: var(--r-card-lg);
	}

	.crate-skel {
		height: 190px;
		border-radius: var(--r-card-lg);
	}

	.crates {
		display: grid;
		gap: var(--sp-32);
	}

	.series-shelf {
		display: grid;
		gap: var(--sp-8);
		min-width: 0;
	}

	/* A row of small crates; the headroom lets records rise when one is pulled out. */
	.series-row {
		--fan-max: 22%;
		display: grid;
		grid-auto-flow: column;
		grid-auto-columns: calc(150px * var(--tile-scale));
		gap: var(--sp-20);
		overflow-x: auto;
		scrollbar-width: none;
		padding: var(--sp-48) var(--sp-4) var(--sp-4);
		margin-top: calc(-1 * var(--sp-40));
		scroll-snap-type: x proximity;
	}

	.series-row > :global(*) {
		scroll-snap-align: start;
	}

	/* Without the sidebar (phones) settings are reached from here. */
	@media (max-width: 800px) {
		.tools {
			display: flex;
		}
	}

	@container page (max-width: 700px) {
		.top {
			align-items: start;
		}

		.top h1 {
			font-size: var(--fs-24);
		}

		.hero {
			grid-template-columns: 1fr;
			padding: var(--sp-16);
		}

		.deck {
			width: min(100%, 300px);
			justify-self: center;
		}

		.hero h2 {
			font-size: var(--fs-24);
		}

		.big {
			justify-self: stretch;
		}
	}
</style>
