<script lang="ts">
	import { page } from '$app/state';
	import { session } from '#lib/session.svelte.ts';
	import { theme } from '#lib/theme.svelte.ts';
	import House from '@lucide/svelte/icons/house';
	import LibraryBig from '@lucide/svelte/icons/library-big';
	import Layers from '@lucide/svelte/icons/layers';
	import Users from '@lucide/svelte/icons/users';
	import Search from '@lucide/svelte/icons/search';
	import FolderHeart from '@lucide/svelte/icons/folder-heart';
	import ChartColumn from '@lucide/svelte/icons/chart-column';
	import Download from '@lucide/svelte/icons/download';
	import SunMoon from '@lucide/svelte/icons/sun-moon';
	import Sun from '@lucide/svelte/icons/sun';
	import Moon from '@lucide/svelte/icons/moon';
	import LogOut from '@lucide/svelte/icons/log-out';
	import History from '@lucide/svelte/icons/history';
	import ListMusic from '@lucide/svelte/icons/list-music';
	import BookmarkIcon from '@lucide/svelte/icons/bookmark';
	import Ellipsis from '@lucide/svelte/icons/ellipsis';
	import Settings from '@lucide/svelte/icons/settings';
	import { afterNavigate } from '$app/navigation';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import LocaleSwitch from './LocaleSwitch.svelte';
	import { dur } from '#lib/motion.ts';
	import AccountSwitcher from './AccountSwitcher.svelte';
	import Avatar from './Avatar.svelte';
	import ArrowLeftRight from '@lucide/svelte/icons/arrow-left-right';
	import { serverLabel } from '#lib/session.svelte.ts';

	/** Saved accounts: the name in the sidebar (and a row in "More") opens the switcher. */
	let switcher = $state(false);

	const t = $derived(i18n.m.nav);
	const items = $derived([
		{ href: '/', label: t.home, icon: House, tab: true },
		{ href: '/library', label: t.library, icon: LibraryBig, tab: true },
		{ href: '/series', label: t.series, icon: Layers, tab: false },
		{ href: '/authors', label: t.authors, icon: Users, tab: false },
		{ href: '/collections', label: t.collections, icon: FolderHeart, tab: false },
		{ href: '/playlists', label: t.playlists, icon: ListMusic, tab: false },
		{ href: '/bookmarks', label: t.bookmarks, icon: BookmarkIcon, tab: false },
		{ href: '/history', label: t.history, icon: History, tab: false },
		{ href: '/stats', label: t.stats, icon: ChartColumn, tab: false },
		{ href: '/downloads', label: t.downloads, icon: Download, tab: true },
		{ href: '/search', label: t.search, icon: Search, tab: true },
		{ href: '/settings', label: t.settings, icon: Settings, tab: false }
	]);

	/** Shortcut hint for the quick search (⌘ on Apple devices). */
	const modKey = /Mac|iPhone|iPad/.test(navigator.userAgent) ? '⌘' : 'Ctrl';

	/**
	 * The highlight is one element that glides to the current entry (CSS does the motion),
	 * instead of jumping from item to item. Hidden while no entry of this nav is current.
	 */
	function glide(nav: HTMLElement) {
		const place = () => {
			const cur = nav.querySelector<HTMLElement>('[aria-current="page"], button.current');
			nav.style.setProperty('--pill-on', cur ? '1' : '0');
			if (!cur) return;
			nav.style.setProperty('--pill-x', `${cur.offsetLeft}px`);
			nav.style.setProperty('--pill-y', `${cur.offsetTop}px`);
			nav.style.setProperty('--pill-w', `${cur.offsetWidth}px`);
			nav.style.setProperty('--pill-h', `${cur.offsetHeight}px`);
		};
		const watch = new MutationObserver(place);
		watch.observe(nav, {
			subtree: true,
			attributes: true,
			attributeFilter: ['aria-current', 'class']
		});
		const size = new ResizeObserver(place);
		size.observe(nav);
		place();
		return () => {
			watch.disconnect();
			size.disconnect();
		};
	}

	const active = (href: string) =>
		href === '/' ? page.url.pathname === '/' : page.url.pathname.startsWith(href);

	// Phones: pages beyond the tab bar live in a "More" sheet.
	const extra = $derived(items.filter((i) => !i.tab));
	const moreActive = $derived(extra.some((i) => active(i.href)));
	let sheet = $state<HTMLDialogElement>();
	let closing = $state(false);

	function closeSheet() {
		if (!sheet?.open || closing) return;
		closing = true;
		const d = sheet;
		setTimeout(() => {
			d.close();
			closing = false;
		}, dur(200));
	}

	afterNavigate(() => closeSheet());

	const themeLabel = $derived(
		({ system: t.themeSystem, light: t.themeLight, dark: t.themeDark } as const)[theme.choice]
	);
</script>

<aside class="side">
	<a class="brand" href="/" aria-label="KoalaShelf – {t.home}">
		<img src="/favicon.svg" alt="" width="28" height="28" />
		<span class="display">KoalaShelf</span>
	</a>
	<nav aria-label={t.main} {@attach glide}>
		<span class="pill" aria-hidden="true"></span>
		{#each items as item (item.href)}
			<a href={item.href} aria-current={active(item.href) ? 'page' : undefined}>
				<item.icon size={18} strokeWidth={2.2} aria-hidden="true" />
				{item.label}
				{#if item.href === '/search'}<kbd class="hint" aria-hidden="true">{modKey} K</kbd>{/if}
			</a>
		{/each}
	</nav>

	<div class="profile">
		{#if session.libraries.length > 1}
			<label class="field">
				<span class="label">{t.library}</span>
				<select
					value={session.libraryId}
					onchange={(e) => session.selectLibrary(e.currentTarget.value)}
				>
					{#each session.libraries as lib (lib.id)}
						<option value={lib.id}>{lib.name}</option>
					{/each}
				</select>
			</label>
		{/if}
		<LocaleSwitch />
		<div class="me">
			<button
				class="who"
				aria-haspopup="dialog"
				onclick={() => (switcher = true)}
				title={t.accounts}
				aria-label={t.accountsOf(session.user?.username ?? '')}
			>
				<Avatar name={session.user?.username ?? '?'} size={28} />
				<span class="name">{session.user?.username}</span>
			</button>
			<button class="icon" onclick={() => theme.cycle()} title={themeLabel} aria-label={themeLabel}>
				{#if theme.choice === 'light'}<Sun size={18} />{:else if theme.choice === 'dark'}<Moon
						size={18}
					/>{:else}<SunMoon size={18} />{/if}
			</button>
			<button
				class="icon"
				onclick={() => session.signOut()}
				title={t.signOut}
				aria-label={t.signOut}
			>
				<LogOut size={18} />
			</button>
		</div>
	</div>
</aside>

<nav class="tabs" aria-label={t.main} {@attach glide}>
	<span class="pill" aria-hidden="true"></span>
	{#each items.filter((i) => i.tab) as item (item.href)}
		<a href={item.href} aria-current={active(item.href) ? 'page' : undefined}>
			<item.icon size={22} strokeWidth={2} aria-hidden="true" />
			<span>{item.label}</span>
		</a>
	{/each}
	<button
		class:current={moreActive}
		aria-haspopup="dialog"
		aria-expanded={sheet?.open ?? false}
		onclick={() => sheet?.showModal()}
	>
		<Ellipsis size={22} strokeWidth={2} aria-hidden="true" />
		<span>{t.more}</span>
	</button>
</nav>

<dialog
	class="sheet"
	class:closing
	bind:this={sheet}
	aria-label={t.more}
	oncancel={(e) => {
		e.preventDefault();
		closeSheet();
	}}
	onclick={(e) => e.target === sheet && closeSheet()}
>
	<span class="grip" aria-hidden="true"></span>
	<button
		class="account-row"
		aria-haspopup="dialog"
		onclick={() => {
			closeSheet();
			switcher = true;
		}}
	>
		<Avatar name={session.user?.username ?? '?'} />
		<span class="acc-text">
			<strong>{session.user?.username}</strong>
			<span class="muted">{serverLabel(session.active?.baseUrl ?? '')}</span>
		</span>
		<span class="acc-switch"><ArrowLeftRight size={16} aria-hidden="true" />{t.switchAccount}</span>
	</button>
	<nav aria-label={t.more}>
		{#each extra as item (item.href)}
			<a href={item.href} aria-current={active(item.href) ? 'page' : undefined}>
				<item.icon size={22} strokeWidth={2} aria-hidden="true" />
				<span>{item.label}</span>
			</a>
		{/each}
	</nav>
	{#if session.libraries.length > 1}
		<label class="field">
			<span class="label">{t.library}</span>
			<select
				value={session.libraryId}
				onchange={(e) => session.selectLibrary(e.currentTarget.value)}
			>
				{#each session.libraries as lib (lib.id)}
					<option value={lib.id}>{lib.name}</option>
				{/each}
			</select>
		</label>
	{/if}
</dialog>

<AccountSwitcher bind:open={switcher} />

<style>
	.account-row {
		display: grid;
		grid-template-columns: auto 1fr auto;
		align-items: center;
		gap: var(--sp-12);
		width: 100%;
		min-height: 56px;
		padding: var(--sp-8) var(--sp-12);
		margin-bottom: var(--sp-8);
		border: 0;
		border-radius: var(--r-card);
		background: var(--c-card);
		color: inherit;
		font: inherit;
		text-align: left;
	}

	.acc-text {
		display: grid;
		min-width: 0;
	}

	.acc-text > * {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.acc-text .muted {
		font-size: var(--fs-13);
	}

	.acc-switch {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		font-size: var(--fs-13);
		font-weight: var(--fw-semibold);
	}

	.side {
		position: sticky;
		top: 0;
		height: 100dvh;
		display: flex;
		flex-direction: column;
		gap: var(--sp-24);
		padding: var(--sp-24) var(--sp-12);
		background: var(--c-panel);
		border-right: 1px solid var(--c-line);
		overflow-y: auto;
		overscroll-behavior: contain;
		scrollbar-width: thin;
	}

	@media (max-height: 600px) {
		.side {
			gap: var(--sp-12);
			padding-block: var(--sp-12);
		}
	}

	.brand {
		display: flex;
		align-items: center;
		gap: var(--sp-8);
		padding: 0 var(--sp-8);
		font-size: var(--fs-18);
	}

	nav {
		display: grid;
		gap: var(--sp-4);
	}

	.side nav a {
		display: flex;
		align-items: center;
		gap: var(--sp-12);
		min-height: var(--layout-touch);
		padding: 0 var(--sp-12);
		border-radius: var(--r-card);
		font-weight: var(--fw-semibold);
		font-size: var(--fs-14);
		color: var(--c-ink);
		transition: background var(--motion-fast) var(--motion-ease);
	}

	.side nav a:hover {
		background: color-mix(in oklab, var(--c-ctrl) 8%, transparent);
	}

	.hint {
		margin-left: auto;
		padding: 1px 6px;
		border-radius: 5px;
		box-shadow: inset 0 0 0 1px var(--c-line);
		color: var(--c-muted);
		font: inherit;
		font-size: var(--fs-12);
	}

	.side nav a[aria-current='page'] .hint {
		color: inherit;
		box-shadow: inset 0 0 0 1px currentColor;
	}

	.side nav a[aria-current='page'] {
		color: var(--c-sel-ink);
	}

	.side nav {
		position: relative;
		isolation: isolate;
	}

	/* One highlight under the entries, gliding with a little spring to the current one. */
	.pill {
		position: absolute;
		top: 0;
		left: 0;
		z-index: -1;
		width: var(--pill-w, 0);
		height: var(--pill-h, 0);
		translate: var(--pill-x, 0) var(--pill-y, 0);
		opacity: var(--pill-on, 0);
		border-radius: var(--r-card);
		background: var(--c-sel);
		pointer-events: none;
		transition:
			translate 460ms cubic-bezier(0.3, 1.18, 0.45, 1),
			height var(--motion-base) var(--motion-ease),
			opacity var(--motion-fast) var(--motion-ease);
	}

	.profile {
		margin-top: auto;
		display: grid;
		/* The column never grows past the sidebar: long names are cut, not pushed out. */
		grid-template-columns: minmax(0, 1fr);
		gap: var(--sp-12);
	}

	.me {
		display: flex;
		align-items: center;
		gap: var(--sp-8);
		padding: var(--sp-8);
		border-radius: var(--r-card);
		background: var(--c-card);
	}

	.who {
		flex: 1;
		min-width: 0;
		display: flex;
		align-items: center;
		gap: var(--sp-8);
		min-height: 36px;
		padding: 0;
		border: 0;
		border-radius: var(--r-card);
		background: none;
		color: inherit;
		font: inherit;
		text-align: left;
		cursor: pointer;
	}

	.me {
		gap: var(--sp-4);
	}

	.who:hover .name {
		text-decoration: underline;
		text-underline-offset: 3px;
	}

	.name {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		font-weight: var(--fw-semibold);
		font-size: var(--fs-13);
	}

	.icon {
		display: grid;
		place-items: center;
		width: 32px;
		height: 32px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: var(--c-muted);
		position: relative;
	}

	.icon::after {
		content: '';
		position: absolute;
		inset: -6px;
	}

	.icon:hover {
		color: var(--c-ink);
	}

	.tabs {
		display: none;
	}

	/* = --layout-breakpoint (media queries cannot read custom properties) */
	@media (max-width: 800px) {
		.side {
			display: none;
		}

		.tabs {
			position: fixed;
			inset: auto 0 0;
			z-index: 20;
			display: grid;
			grid-auto-flow: column;
			grid-auto-columns: 1fr;
			padding: var(--sp-4) var(--sp-8) calc(var(--sp-4) + env(safe-area-inset-bottom));
			/* Opaque: labels keep their contrast over any cover scrolling underneath. */
			background: var(--c-panel);
			border-top: 1px solid var(--c-line);
		}

		.tabs a,
		.tabs button {
			display: grid;
			justify-items: center;
			gap: 2px;
			min-height: 52px;
			align-content: center;
			font-size: var(--fs-12);
			font-weight: var(--fw-bold);
			color: var(--c-muted);
		}

		.tabs button {
			padding: 0;
			border: 0;
			background: transparent;
			font: inherit;
			font-size: var(--fs-12);
			font-weight: var(--fw-bold);
			color: var(--c-muted);
		}

		.tabs button.current,
		.tabs a[aria-current='page'] {
			color: var(--c-accent);
		}

		/* On the tab bar the highlight is a short bar along the top edge. */
		.tabs .pill {
			width: 28px;
			height: 3px;
			translate: calc(var(--pill-x, 0) + var(--pill-w, 0) / 2 - 14px) 0;
			border-radius: 0 0 var(--r-pill) var(--r-pill);
			background: var(--c-accent);
		}
	}

	/* "More" sheet (phones): slides up from the tab bar. */
	.sheet {
		inset: auto 0 0;
		width: 100%;
		max-width: none;
		margin: 0;
		padding: var(--sp-8) var(--sp-16) calc(var(--sp-24) + env(safe-area-inset-bottom));
		border: 0;
		border-radius: var(--r-card-lg) var(--r-card-lg) 0 0;
		background: var(--c-panel);
		color: var(--c-ink);
		box-shadow: 0 -20px 40px -20px rgb(0 0 0 / 0.4);
	}

	.sheet[open] {
		display: grid;
		gap: var(--sp-16);
		animation: sheet-up var(--motion-base) var(--motion-out);
	}

	.sheet.closing {
		animation: sheet-down 200ms var(--motion-ease) forwards;
	}

	.sheet::backdrop {
		background: rgb(0 0 0 / 0.35);
		animation: fade-in var(--motion-base) var(--motion-ease);
	}

	@keyframes sheet-up {
		from {
			translate: 0 100%;
		}
	}

	@keyframes sheet-down {
		to {
			translate: 0 100%;
		}
	}

	@keyframes fade-in {
		from {
			opacity: 0;
		}
	}

	.grip {
		justify-self: center;
		width: 40px;
		height: 4px;
		border-radius: 2px;
		background: var(--c-line);
	}

	.sheet nav {
		grid-template-columns: repeat(3, 1fr);
		gap: var(--sp-8);
	}

	.sheet nav a {
		display: grid;
		justify-items: center;
		align-content: center;
		gap: var(--sp-8);
		min-height: 84px;
		padding: var(--sp-8);
		border-radius: var(--r-card);
		background: var(--c-card);
		font-size: var(--fs-13);
		font-weight: var(--fw-bold);
		text-align: center;
	}

	.sheet nav a[aria-current='page'] {
		background: var(--c-sel);
		color: var(--c-sel-ink);
	}
</style>
