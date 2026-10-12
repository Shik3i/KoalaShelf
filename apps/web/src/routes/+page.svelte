<script lang="ts">
	import type { LibraryItemMinified, Shelf } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { progress } from '#lib/progress.svelte.ts';
	import { player } from '#lib/player/player.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { coverSrc, view } from '#lib/items.ts';
	import { clock, duration } from '#lib/format.ts';
	import Turntable from '#lib/components/Turntable.svelte';
	import Crate, { type CrateEntry } from '#lib/components/Crate.svelte';
	import Play from '@lucide/svelte/icons/play';
	import Pause from '@lucide/svelte/icons/pause';
	import X from '@lucide/svelte/icons/x';
	import Settings from '@lucide/svelte/icons/settings';

	const t = $derived(i18n.m.home);
	const WEEK = 7 * 24 * 3600 * 1000;

	let shelves = $state.raw<Shelf[] | null>(null);
	let error = $state<string | null>(null);

	$effect(() => {
		const lib = session.libraryId;
		if (!lib) return;
		progress.load();
		session.api
			.personalized(lib, 16)
			.then((s) => (shelves = s))
			.catch((e) => (error = (e as Error).message));
	});

	const greeting = $derived.by(() => {
		const h = new Date().getHours();
		return h < 11 ? t.greetingMorning : h < 18 ? t.greetingDay : t.greetingEvening;
	});

	const books = (s: Shelf | undefined) => (s?.type === 'book' ? s.entities : []);
	const continueItems = $derived(books(shelves?.find((s) => s.id === 'continue-listening')));
	const hero = $derived<LibraryItemMinified | undefined>(continueItems[0]);
	const heroView = $derived(hero ? view(hero) : null);
	const heroIsPlaying = $derived(!!hero && player.item?.id === hero.id);
	const heroPos = $derived(
		hero ? (heroIsPlaying ? player.currentTime : (progress.get(hero.id)?.currentTime ?? 0)) : 0
	);
	const heroPct = $derived(
		hero ? (heroIsPlaying ? player.progress : (progress.get(hero.id)?.progress ?? 0)) : 0
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
				entries = s.entities
					.filter((se) => se.books?.length)
					.map((se) => ({
						...entry(se.books[0], `/library?series=${se.id}`),
						id: se.id,
						title: se.name
					}));
			}
			return entries.length ? [{ id: s.id, label, note, entries }] : [];
		});
	});

	/** Hide from "continue listening" (progress stays), then refresh the shelves. */
	async function hideHero() {
		const p = hero && progress.get(hero.id);
		if (!p || !session.libraryId) return;
		await session.api.hideFromContinueListening(p.id);
		shelves = await session.api.personalized(session.libraryId, 16);
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
	<div class="tools">
		<a class="round" href="/settings" aria-label={i18n.m.nav.settings}>
			<Settings size={20} />
		</a>
	</div>
</header>

{#if error}
	<p class="muted">{i18n.m.common.error(error)}</p>
{:else if shelves && !hero && crates.length === 0}
	<div class="empty">
		<p class="muted">{t.empty}</p>
		<a class="btn primary" href="/library">{t.toLibrary}</a>
	</div>
{/if}

{#if hero && heroView}
	<section class="hero card">
		<a class="deck" href="/book/{hero.id}" aria-label={heroView.title}>
			<Turntable
				title={heroView.title}
				author={heroView.author}
				src={heroView.cover}
				playing={heroIsPlaying && player.playing}
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
				{#if heroIsPlaying && player.playing}
					<Pause size={18} fill="currentColor" /> {i18n.m.player.pause}
				{:else}
					<Play size={18} fill="currentColor" /> {i18n.m.book.resume(clock(heroPos))}
				{/if}
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
	{#each crates as c (c.id)}
		<Crate title={c.label} note={c.note} entries={c.entries} />
	{/each}
</div>

<style>
	.top {
		display: flex;
		justify-content: space-between;
		align-items: end;
		gap: var(--sp-16);
		margin-bottom: var(--sp-24);
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

	.empty {
		display: grid;
		justify-items: center;
		gap: var(--sp-16);
		padding: var(--sp-64) 0;
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
