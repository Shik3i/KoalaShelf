<script lang="ts">
	import type { Component } from 'svelte';
	import { goto } from '$app/navigation';
	import { session } from '#lib/session.svelte.ts';
	import { player } from '#lib/player/player.svelte.ts';
	import { theme } from '#lib/theme.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { coverSrc, view } from '#lib/items.ts';
	import { recentSearches, rememberSearch, search, type Found } from '#lib/search.ts';
	import Cover from './Cover.svelte';
	import Highlight from './Highlight.svelte';
	import SearchIcon from '@lucide/svelte/icons/search';
	import House from '@lucide/svelte/icons/house';
	import LibraryBig from '@lucide/svelte/icons/library-big';
	import Layers from '@lucide/svelte/icons/layers';
	import Users from '@lucide/svelte/icons/users';
	import ListMusic from '@lucide/svelte/icons/list-music';
	import BookmarkIcon from '@lucide/svelte/icons/bookmark';
	import History from '@lucide/svelte/icons/history';
	import ChartColumn from '@lucide/svelte/icons/chart-column';
	import Download from '@lucide/svelte/icons/download';
	import Settings from '@lucide/svelte/icons/settings';
	import Play from '@lucide/svelte/icons/play';
	import Disc from '@lucide/svelte/icons/disc-3';
	import Timer from '@lucide/svelte/icons/timer';
	import SunMoon from '@lucide/svelte/icons/sun-moon';
	import Mic from '@lucide/svelte/icons/mic';
	import User from '@lucide/svelte/icons/user';
	import Clock from '@lucide/svelte/icons/clock';
	import UserPlus from '@lucide/svelte/icons/user-plus';
	import { addAccountUrl, switchAccount } from '#lib/accounts.ts';
	import { serverLabel } from '#lib/session.svelte.ts';

	/** Quick search from anywhere (Ctrl/⌘ K): books, people, pages and actions. */
	let { open = $bindable(false) }: { open?: boolean } = $props();

	const t = $derived(i18n.m.palette);
	let dialog = $state<HTMLDialogElement>();
	let input = $state<HTMLInputElement>();
	let q = $state('');
	let found = $state.raw<Found | null>(null);
	let active = $state(0);

	$effect(() => {
		if (!dialog) return;
		if (open && !dialog.open) {
			q = '';
			found = null;
			active = 0;
			dialog.showModal();
			queueMicrotask(() => input?.focus());
		}
		if (!open && dialog.open) dialog.close();
	});

	$effect(() => {
		const query = q.trim();
		active = 0;
		if (query.length < 2 || !session.libraryId) {
			found = null;
			return;
		}
		const ctrl = new AbortController();
		const timer = setTimeout(() => {
			search(query, false, 6, ctrl.signal)
				.then((r) => (found = r))
				.catch(() => undefined);
		}, 160);
		return () => {
			clearTimeout(timer);
			ctrl.abort();
		};
	});

	interface Option {
		id: string;
		group: string;
		label: string;
		sub?: string;
		icon?: Component;
		cover?: { title: string; author: string; src: ((w: number) => string) | null };
		run: () => void;
	}

	const go = (href: string) => () => goto(href);

	const pages = $derived<Option[]>([
		{ id: 'p-home', group: t.pages, label: i18n.m.nav.home, icon: House, run: go('/') },
		{
			id: 'p-lib',
			group: t.pages,
			label: i18n.m.nav.library,
			icon: LibraryBig,
			run: go('/library')
		},
		{ id: 'p-series', group: t.pages, label: i18n.m.nav.series, icon: Layers, run: go('/series') },
		{
			id: 'p-authors',
			group: t.pages,
			label: i18n.m.nav.authors,
			icon: Users,
			run: go('/authors')
		},
		{
			id: 'p-playlists',
			group: t.pages,
			label: i18n.m.nav.playlists,
			icon: ListMusic,
			run: go('/playlists')
		},
		{
			id: 'p-bookmarks',
			group: t.pages,
			label: i18n.m.nav.bookmarks,
			icon: BookmarkIcon,
			run: go('/bookmarks')
		},
		{
			id: 'p-history',
			group: t.pages,
			label: i18n.m.nav.history,
			icon: History,
			run: go('/history')
		},
		{
			id: 'p-stats',
			group: t.pages,
			label: i18n.m.nav.stats,
			icon: ChartColumn,
			run: go('/stats')
		},
		{
			id: 'p-downloads',
			group: t.pages,
			label: i18n.m.nav.downloads,
			icon: Download,
			run: go('/downloads')
		},
		{
			id: 'p-settings',
			group: t.pages,
			label: i18n.m.nav.settings,
			icon: Settings,
			run: go('/settings')
		}
	]);

	const actions = $derived<Option[]>([
		...(player.item
			? [
					{
						id: 'a-play',
						group: t.actions,
						label: player.playing ? i18n.m.player.pause : i18n.m.player.play,
						sub: view(player.item).title,
						icon: Play,
						run: () => player.toggle()
					},
					{
						id: 'a-open',
						group: t.actions,
						label: t.openPlayer,
						icon: Disc,
						run: () => (player.expanded = true)
					},
					{
						id: 'a-sleep',
						group: t.actions,
						label: t.sleep30,
						icon: Timer,
						run: () =>
							player.setSleep({ kind: 'minutes', minutes: 30, endsAt: Date.now() + 30 * 60_000 })
					}
				]
			: []),
		{ id: 'a-theme', group: t.actions, label: t.theme, icon: SunMoon, run: () => theme.cycle() },
		// Saved accounts: switch by typing a name or a server.
		...session.accounts
			.filter((a) => a.key !== session.activeKey)
			.map((a) => ({
				id: `acc-${a.key}`,
				group: t.actions,
				label: i18n.m.accounts.switchToName(a.user.username),
				sub: serverLabel(a.baseUrl),
				icon: User,
				run: () => void switchAccount(a.key)
			})),
		{
			id: 'a-add-account',
			group: t.actions,
			label: i18n.m.accounts.add,
			icon: UserPlus,
			run: go(addAccountUrl())
		}
	]);

	/** Words of the query all appear in the label (accent- and case-insensitive). */
	const fold = (s: string) =>
		s
			.normalize('NFD')
			.replace(/\p{Diacritic}/gu, '')
			.toLowerCase();
	const matches = (o: Option, query: string) =>
		fold(query)
			.split(/\s+/)
			.every((w) => fold(`${o.label} ${o.sub ?? ''}`).includes(w));

	const options = $derived.by<Option[]>(() => {
		const query = q.trim();
		if (!query) {
			const recent = recentSearches()
				.slice(0, 4)
				.map<Option>((r) => ({
					id: `r-${r}`,
					group: i18n.m.search.recent,
					label: r,
					icon: Clock,
					run: () => (q = r)
				}));
			return [...recent, ...actions, ...pages];
		}
		const out: Option[] = [];
		const s = i18n.m.search;
		for (const { libraryItem: item } of found?.book ?? []) {
			const v = view(item);
			out.push({
				id: `b-${item.id}`,
				group: s.books,
				label: v.title,
				sub: v.author,
				cover: { title: v.title, author: v.author, src: coverSrc(item) },
				run: go(`/book/${item.id}`)
			});
		}
		for (const x of (found?.series ?? []).slice(0, 3))
			out.push({
				id: `s-${x.series.id}`,
				group: s.series,
				label: x.series.name,
				sub: i18n.m.authors.books(x.books.length),
				icon: Layers,
				run: go(`/series/${x.series.id}`)
			});
		for (const a of (found?.authors ?? []).slice(0, 3))
			out.push({
				id: `au-${a.id}`,
				group: s.authors,
				label: a.name,
				icon: User,
				run: go(`/author/${a.id}`)
			});
		for (const n of (found?.narrators ?? []).slice(0, 3))
			out.push({
				id: `n-${n.name}`,
				group: s.narrators,
				label: n.name,
				sub: i18n.m.authors.books(n.numBooks),
				icon: Mic,
				run: go(`/library?narrator=${encodeURIComponent(n.name)}`)
			});
		out.push(...[...actions, ...pages].filter((o) => matches(o, query)));
		out.push({
			id: 'all',
			group: t.more,
			label: t.searchAll(query),
			icon: SearchIcon,
			run: go(`/search?q=${encodeURIComponent(query)}`)
		});
		return out;
	});

	function choose(o: Option | undefined) {
		if (!o) return;
		if (q.trim().length >= 2 && !o.id.startsWith('r-')) rememberSearch(q.trim());
		const keepOpen = o.id.startsWith('r-');
		if (!keepOpen) open = false;
		o.run();
	}

	function onKey(e: KeyboardEvent) {
		const n = options.length;
		if (e.key === 'ArrowDown') {
			e.preventDefault();
			active = (active + 1) % n;
		} else if (e.key === 'ArrowUp') {
			e.preventDefault();
			active = (active - 1 + n) % n;
		} else if (e.key === 'Enter') {
			e.preventDefault();
			choose(options[active]);
		}
	}

	$effect(() => {
		// Keep the active option in view while moving with the keyboard.
		void active;
		dialog?.querySelector('[aria-selected="true"]')?.scrollIntoView({ block: 'nearest' });
	});
</script>

<dialog
	bind:this={dialog}
	class="palette"
	onclose={() => (open = false)}
	onclick={(e) => e.target === dialog && (open = false)}
	aria-label={t.label}
>
	<div class="box">
		<SearchIcon size={20} aria-hidden="true" />
		<input
			bind:this={input}
			bind:value={q}
			onkeydown={onKey}
			type="text"
			role="combobox"
			aria-expanded="true"
			aria-controls="palette-list"
			aria-activedescendant={options[active] ? `opt-${options[active].id}` : undefined}
			aria-autocomplete="list"
			aria-label={t.label}
			placeholder={t.placeholder}
			autocomplete="off"
			spellcheck="false"
		/>
		<kbd>Esc</kbd>
	</div>
	<ul id="palette-list" role="listbox" aria-label={t.label}>
		{#each options as o, i (o.id)}
			{#if i === 0 || options[i - 1].group !== o.group}
				<li class="group" role="presentation">{o.group}</li>
			{/if}
			<li
				id="opt-{o.id}"
				role="option"
				aria-selected={i === active}
				onmousemove={() => (active = i)}
				onclick={() => choose(o)}
				onkeydown={() => undefined}
			>
				{#if o.cover}
					<span class="cv"><Cover {...o.cover} sizes="40px" /></span>
				{:else if o.icon}
					<span class="ic"><o.icon size={18} /></span>
				{/if}
				<span class="txt">
					<span class="lbl"><Highlight text={o.label} query={q} /></span>
					{#if o.sub}<span class="sub muted">{o.sub}</span>{/if}
				</span>
			</li>
		{/each}
	</ul>
	<p class="foot muted">{t.foot}</p>
</dialog>

<style>
	.palette {
		width: min(94vw, 640px);
		max-height: min(80dvh, 640px);
		margin: 10dvh auto auto;
		padding: 0;
		border: 0;
		border-radius: var(--r-card-lg);
		background: var(--c-card);
		color: var(--c-ink);
		box-shadow: 0 40px 80px -30px rgb(0 0 0 / 0.6);
		overflow: hidden;
	}

	.palette[open] {
		display: grid;
		grid-template-rows: auto 1fr auto;
		animation: drop var(--motion-base) var(--motion-out);
	}

	.palette::backdrop {
		background: rgb(0 0 0 / 0.4);
		backdrop-filter: blur(3px);
	}

	@keyframes drop {
		from {
			opacity: 0;
			translate: 0 -12px;
			scale: 0.98;
		}
	}

	.box {
		display: flex;
		align-items: center;
		gap: var(--sp-12);
		padding: var(--sp-16) var(--sp-20);
		border-bottom: 1px solid var(--c-line);
		color: var(--c-muted);
	}

	input {
		flex: 1;
		min-width: 0;
		border: 0;
		background: transparent;
		color: var(--c-ink);
		font: inherit;
		font-size: var(--fs-18);
	}

	input:focus-visible {
		outline: none;
	}

	kbd {
		padding: 2px 8px;
		border-radius: 6px;
		background: var(--c-bg);
		box-shadow: inset 0 -2px 0 var(--c-line);
		font: inherit;
		font-size: var(--fs-12);
		font-weight: var(--fw-bold);
	}

	ul {
		list-style: none;
		margin: 0;
		padding: var(--sp-8);
		overflow: auto;
		overscroll-behavior: contain;
	}

	.group {
		padding: var(--sp-12) var(--sp-12) var(--sp-4);
		color: var(--c-muted);
		font-size: var(--fs-12);
		font-weight: var(--fw-bold);
		letter-spacing: var(--tracking-label);
		text-transform: uppercase;
	}

	[role='option'] {
		display: flex;
		align-items: center;
		gap: var(--sp-12);
		min-height: var(--layout-touch);
		padding: var(--sp-4) var(--sp-12);
		border-radius: var(--r-card);
		cursor: pointer;
	}

	[role='option'][aria-selected='true'] {
		background: var(--c-sel);
		color: var(--c-sel-ink);
	}

	[role='option'][aria-selected='true'] .muted {
		color: inherit;
	}

	.cv {
		width: 40px;
		flex: none;
		border-radius: 5px;
		overflow: hidden;
	}

	.ic {
		display: grid;
		place-items: center;
		width: 40px;
		flex: none;
	}

	.txt {
		display: grid;
		min-width: 0;
	}

	.lbl,
	.sub {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.lbl {
		font-weight: var(--fw-semibold);
	}

	.sub {
		font-size: var(--fs-13);
	}

	.foot {
		margin: 0;
		padding: var(--sp-8) var(--sp-20);
		border-top: 1px solid var(--c-line);
		font-size: var(--fs-12);
	}

	@media (max-width: 600px) {
		.palette {
			margin-top: var(--sp-16);
		}

		.foot {
			display: none;
		}
	}
</style>
