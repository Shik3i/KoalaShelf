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
	import ChevronDown from '@lucide/svelte/icons/chevron-down';
	import ChevronsUpDown from '@lucide/svelte/icons/chevrons-up-down';
	import Check from '@lucide/svelte/icons/check';
	import PanelLeftClose from '@lucide/svelte/icons/panel-left-close';
	import PanelLeftOpen from '@lucide/svelte/icons/panel-left-open';
	import { afterNavigate } from '$app/navigation';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { prefs } from '#lib/prefs.svelte.ts';
	import { dur } from '#lib/motion.ts';
	import AccountSwitcher from './AccountSwitcher.svelte';
	import Avatar from './Avatar.svelte';
	import ArrowLeftRight from '@lucide/svelte/icons/arrow-left-right';
	import { serverLabel } from '#lib/session.svelte.ts';

	/** Saved accounts: the name in the sidebar (and a row in "More") opens the switcher. */
	let switcher = $state(false);

	const t = $derived(i18n.m.nav);

	const primaryItems = $derived([
		{ href: '/', label: t.home, icon: House, tab: true },
		{ href: '/library', label: t.library, icon: LibraryBig, tab: true },
		{ href: '/search', label: t.search, icon: Search, tab: true }
	]);

	const isVisible = (href: string) => !prefs.navHidden.includes(href);

	const discoverItems = $derived(
		[
			{ href: '/series', label: t.series, icon: Layers, tab: false },
			{ href: '/authors', label: t.authors, icon: Users, tab: false }
		].filter((i) => isVisible(i.href))
	);

	const personalItems = $derived(
		[
			{ href: '/downloads', label: t.downloads, icon: Download, tab: true },
			{ href: '/history', label: t.history, icon: History, tab: false },
			{ href: '/bookmarks', label: t.bookmarks, icon: BookmarkIcon, tab: false },
			{ href: '/collections', label: t.collections, icon: FolderHeart, tab: false },
			{ href: '/playlists', label: t.playlists, icon: ListMusic, tab: false }
		].filter((i) => isVisible(i.href))
	);

	const settingsItem = $derived({
		href: '/settings',
		label: t.settings,
		icon: Settings,
		tab: false
	});

	const tabItems = $derived([
		{ href: '/', label: t.home, icon: House },
		{ href: '/library', label: t.library, icon: LibraryBig },
		{ href: '/downloads', label: t.downloads, icon: Download },
		{ href: '/search', label: t.search, icon: Search }
	]);

	const extra = $derived(
		[
			...discoverItems,
			...personalItems.filter((i) => !i.tab),
			{ href: '/stats', label: t.stats, icon: ChartColumn },
			settingsItem
		].filter((i) => isVisible(i.href))
	);

	const collapsed = $derived({
		discover: prefs.navCollapsed.discover ?? false,
		library: prefs.navCollapsed.library ?? false
	});

	function toggleGroup(group: 'discover' | 'library') {
		prefs.set('navCollapsed', {
			...prefs.navCollapsed,
			[group]: !collapsed[group]
		});
	}

	const active = (href: string) =>
		href === '/' ? page.url.pathname === '/' : page.url.pathname.startsWith(href);

	// Auto-expand a collapsed section if one of its items is currently visited.
	$effect(() => {
		if (discoverItems.some((i) => active(i.href)) && collapsed.discover) {
			prefs.set('navCollapsed', { ...prefs.navCollapsed, discover: false });
		}
		if (personalItems.some((i) => active(i.href)) && collapsed.library) {
			prefs.set('navCollapsed', { ...prefs.navCollapsed, library: false });
		}
	});

	/** Shortcut hint for the quick search (⌘ on Apple devices). */
	const modKey =
		typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.userAgent) ? '⌘' : 'Ctrl';

	/**
	 * The highlight is one element that glides to the current entry (CSS does the motion),
	 * instead of jumping from item to item. Hidden while no entry of this nav is current.
	 */
	function glide(nav: HTMLElement) {
		const place = () => {
			const cur = nav.querySelector<HTMLElement>('[aria-current="page"], button.current');
			nav.style.setProperty('--pill-on', cur ? '1' : '0');
			if (!cur) return;
			const navRect = nav.getBoundingClientRect();
			const curRect = cur.getBoundingClientRect();
			nav.style.setProperty('--pill-x', `${curRect.left - navRect.left}px`);
			nav.style.setProperty('--pill-y', `${curRect.top - navRect.top}px`);
			nav.style.setProperty('--pill-w', `${curRect.width}px`);
			nav.style.setProperty('--pill-h', `${curRect.height}px`);
		};
		const watch = new MutationObserver(place);
		watch.observe(nav, {
			subtree: true,
			childList: true,
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

	let libOpen = $state(false);
	let libPickerEl = $state<HTMLElement>();
	let libTriggerEl = $state<HTMLButtonElement>();
	let dropdownTop = $state(0);

	function toggleLibOpen() {
		if (!libOpen && libTriggerEl) {
			const r = libTriggerEl.getBoundingClientRect();
			dropdownTop = r.top;
		}
		libOpen = !libOpen;
	}

	function onWindowPointer(e: PointerEvent) {
		if (libOpen && libPickerEl && !libPickerEl.contains(e.target as Node)) {
			libOpen = false;
		}
	}

	function onWindowKey(e: KeyboardEvent) {
		if (e.key === 'Escape' && libOpen) {
			libOpen = false;
		}
	}

	afterNavigate(() => {
		closeSheet();
		libOpen = false;
	});

	function startResize(e: PointerEvent) {
		if (e.button !== 0) return;
		e.preventDefault();
		document.documentElement.classList.add('is-resizing');

		const onPointerMove = (ev: PointerEvent) => {
			const clientX = ev.clientX;
			if (clientX < 140) {
				if (!prefs.sidebarCollapsed) {
					prefs.set('sidebarCollapsed', true);
				}
			} else {
				if (prefs.sidebarCollapsed) {
					prefs.set('sidebarCollapsed', false);
				}
				const clamped = Math.max(180, Math.min(380, clientX));
				prefs.set('sidebarWidth', clamped);
			}
		};

		const onPointerUp = () => {
			document.documentElement.classList.remove('is-resizing');
			window.removeEventListener('pointermove', onPointerMove);
			window.removeEventListener('pointerup', onPointerUp);
			window.removeEventListener('pointercancel', onPointerUp);
		};

		window.addEventListener('pointermove', onPointerMove);
		window.addEventListener('pointerup', onPointerUp);
		window.addEventListener('pointercancel', onPointerUp);
	}

	function resetSidebar() {
		prefs.set('sidebarCollapsed', false);
		prefs.set('sidebarWidth', 240);
	}

	function handleResizeKey(e: KeyboardEvent) {
		if (e.key === 'ArrowLeft') {
			e.preventDefault();
			if (prefs.sidebarWidth <= 190) {
				prefs.set('sidebarCollapsed', true);
			} else {
				prefs.set('sidebarWidth', Math.max(180, prefs.sidebarWidth - 16));
			}
		} else if (e.key === 'ArrowRight') {
			e.preventDefault();
			if (prefs.sidebarCollapsed) {
				prefs.set('sidebarCollapsed', false);
				prefs.set('sidebarWidth', 200);
			} else {
				prefs.set('sidebarWidth', Math.min(380, prefs.sidebarWidth + 16));
			}
		} else if (e.key === 'Enter' || e.key === ' ') {
			e.preventDefault();
			prefs.set('sidebarCollapsed', !prefs.sidebarCollapsed);
		}
	}

	const themeLabel = $derived(
		({ system: t.themeSystem, light: t.themeLight, dark: t.themeDark } as const)[theme.choice]
	);
</script>

<svelte:window onpointerdown={onWindowPointer} onkeydown={onWindowKey} />

<aside class="side" class:collapsed={prefs.sidebarCollapsed}>
	<button
		type="button"
		class="resize-handle"
		aria-label="Resize sidebar"
		onpointerdown={startResize}
		ondblclick={resetSidebar}
		onkeydown={handleResizeKey}
	></button>

	<div class="side-header" class:collapsed={prefs.sidebarCollapsed}>
		<a class="brand" href="/" aria-label="KoalaShelf – {t.home}" title="KoalaShelf">
			<img src="/favicon.svg" alt="" width="26" height="26" />
			{#if !prefs.sidebarCollapsed}
				<span class="display">KoalaShelf</span>
			{/if}
		</a>
		<button
			type="button"
			class="collapse-btn"
			onclick={() => prefs.set('sidebarCollapsed', !prefs.sidebarCollapsed)}
			title={prefs.sidebarCollapsed ? t.expandNav : t.collapseNav}
			aria-label={prefs.sidebarCollapsed ? t.expandNav : t.collapseNav}
		>
			{#if prefs.sidebarCollapsed}
				<PanelLeftOpen size={18} />
			{:else}
				<PanelLeftClose size={18} />
			{/if}
		</button>
	</div>

	{#if session.libraries.length > 1}
		<div class="lib-picker" class:collapsed={prefs.sidebarCollapsed} bind:this={libPickerEl}>
			<button
				bind:this={libTriggerEl}
				type="button"
				class="lib-trigger"
				onclick={toggleLibOpen}
				aria-expanded={libOpen}
				aria-haspopup="listbox"
				title={session.library?.name ?? t.library}
			>
				<LibraryBig size={18} class="lib-icon" aria-hidden="true" />
				{#if !prefs.sidebarCollapsed}
					<span class="lib-name">{session.library?.name ?? t.library}</span>
					<span class="lib-chevron"><ChevronsUpDown size={14} aria-hidden="true" /></span>
				{/if}
			</button>
			{#if libOpen}
				<div
					class="lib-dropdown"
					role="listbox"
					aria-label={t.library}
					style:--dropdown-top="{dropdownTop}px"
				>
					<div class="lib-dropdown-header">{t.library}</div>
					{#each session.libraries as lib (lib.id)}
						{@const isSelected = lib.id === session.libraryId}
						<button
							type="button"
							role="option"
							aria-selected={isSelected}
							class="lib-opt"
							class:selected={isSelected}
							onclick={() => {
								session.selectLibrary(lib.id);
								libOpen = false;
							}}
						>
							<LibraryBig size={16} aria-hidden="true" />
							<span class="lib-opt-name">{lib.name}</span>
							{#if isSelected}
								<span class="lib-opt-check"><Check size={16} aria-hidden="true" /></span>
							{/if}
						</button>
					{/each}
				</div>
			{/if}
		</div>
	{/if}

	<nav aria-label={t.main} {@attach glide}>
		<span class="pill" aria-hidden="true"></span>
		<div class="nav-group">
			{#each primaryItems as item (item.href)}
				<a
					href={item.href}
					aria-current={active(item.href) ? 'page' : undefined}
					title={prefs.sidebarCollapsed ? item.label : undefined}
				>
					<item.icon size={18} strokeWidth={2.2} aria-hidden="true" />
					{#if !prefs.sidebarCollapsed}
						<span class="nav-text">{item.label}</span>
						{#if item.href === '/search'}<kbd class="hint" aria-hidden="true">{modKey} K</kbd>{/if}
					{/if}
				</a>
			{/each}
		</div>

		{#if discoverItems.length > 0}
			<div class="nav-section">
				{#if !prefs.sidebarCollapsed}
					<button
						type="button"
						class="nav-header"
						onclick={() => toggleGroup('discover')}
						aria-expanded={!collapsed.discover}
					>
						<span>{t.discover}</span>
						<span class="chevron" class:collapsed={collapsed.discover}>
							<ChevronDown size={14} aria-hidden="true" />
						</span>
					</button>
				{:else}
					<div class="nav-divider" role="separator" aria-hidden="true"></div>
				{/if}
				{#if prefs.sidebarCollapsed || !collapsed.discover}
					<div class="nav-group">
						{#each discoverItems as item (item.href)}
							<a
								href={item.href}
								aria-current={active(item.href) ? 'page' : undefined}
								title={prefs.sidebarCollapsed ? item.label : undefined}
							>
								<item.icon size={18} strokeWidth={2.2} aria-hidden="true" />
								{#if !prefs.sidebarCollapsed}
									<span class="nav-text">{item.label}</span>
								{/if}
							</a>
						{/each}
					</div>
				{/if}
			</div>
		{/if}

		{#if personalItems.length > 0}
			<div class="nav-section">
				{#if !prefs.sidebarCollapsed}
					<button
						type="button"
						class="nav-header"
						onclick={() => toggleGroup('library')}
						aria-expanded={!collapsed.library}
					>
						<span>{t.personal}</span>
						<span class="chevron" class:collapsed={collapsed.library}>
							<ChevronDown size={14} aria-hidden="true" />
						</span>
					</button>
				{:else}
					<div class="nav-divider" role="separator" aria-hidden="true"></div>
				{/if}
				{#if prefs.sidebarCollapsed || !collapsed.library}
					<div class="nav-group">
						{#each personalItems as item (item.href)}
							<a
								href={item.href}
								aria-current={active(item.href) ? 'page' : undefined}
								title={prefs.sidebarCollapsed ? item.label : undefined}
							>
								<item.icon size={18} strokeWidth={2.2} aria-hidden="true" />
								{#if !prefs.sidebarCollapsed}
									<span class="nav-text">{item.label}</span>
								{/if}
							</a>
						{/each}
					</div>
				{/if}
			</div>
		{/if}

		<div class="nav-group system">
			<a
				href={settingsItem.href}
				aria-current={active(settingsItem.href) ? 'page' : undefined}
				title={prefs.sidebarCollapsed ? settingsItem.label : undefined}
			>
				<settingsItem.icon size={18} strokeWidth={2.2} aria-hidden="true" />
				{#if !prefs.sidebarCollapsed}
					<span class="nav-text">{settingsItem.label}</span>
				{/if}
			</a>
		</div>
	</nav>

	<div class="profile" class:collapsed={prefs.sidebarCollapsed}>
		<div class="me">
			<button
				class="who"
				aria-haspopup="dialog"
				onclick={() => (switcher = true)}
				title={t.accounts}
				aria-label={t.accountsOf(session.user?.username ?? '')}
			>
				<Avatar name={session.user?.username ?? '?'} size={28} />
				{#if !prefs.sidebarCollapsed}
					<span class="name">{session.user?.username}</span>
				{/if}
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
	{#each tabItems as item (item.href)}
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
		<div class="sheet-libraries">
			<div class="sheet-section-title">{t.library}</div>
			<div class="sheet-lib-list">
				{#each session.libraries as lib (lib.id)}
					{@const isSelected = lib.id === session.libraryId}
					<button
						type="button"
						class="sheet-lib-btn"
						class:selected={isSelected}
						onclick={() => {
							session.selectLibrary(lib.id);
							closeSheet();
						}}
					>
						<LibraryBig size={18} aria-hidden="true" />
						<span class="sheet-lib-name">{lib.name}</span>
						{#if isSelected}
							<span class="sheet-lib-check"><Check size={18} aria-hidden="true" /></span>
						{/if}
					</button>
				{/each}
			</div>
		</div>
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
		gap: var(--sp-16);
		padding: var(--sp-20) var(--sp-12);
		background: var(--c-panel);
		border-right: 1px solid var(--c-line);
		overflow-y: auto;
		overscroll-behavior: contain;
		scrollbar-width: thin;
	}

	.resize-handle {
		position: absolute;
		top: 0;
		right: -4px;
		bottom: 0;
		width: 8px;
		padding: 0;
		border: 0;
		background: transparent;
		cursor: col-resize;
		z-index: 25;
		touch-action: none;
	}

	.resize-handle::after {
		content: '';
		position: absolute;
		top: 0;
		bottom: 0;
		left: 3px;
		width: 2px;
		background: transparent;
		transition: background var(--motion-fast) var(--motion-ease);
	}

	.resize-handle:hover::after,
	:global(html.is-resizing) .resize-handle::after {
		background: var(--c-accent);
	}

	.resize-handle:focus-visible {
		outline: none;
	}

	.resize-handle:focus-visible::after {
		background: var(--c-accent);
	}

	.side-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		min-height: 36px;
		gap: var(--sp-8);
		padding-inline: var(--sp-4);
	}

	.side-header.collapsed {
		flex-direction: column;
		justify-content: center;
		gap: var(--sp-12);
		padding-inline: 0;
	}

	.collapse-btn {
		display: grid;
		place-items: center;
		width: 32px;
		height: 32px;
		padding: 0;
		border: 0;
		border-radius: var(--r-card);
		background: transparent;
		color: var(--c-muted);
		cursor: pointer;
		transition:
			background var(--motion-fast) var(--motion-ease),
			color var(--motion-fast) var(--motion-ease);
	}

	.collapse-btn:hover {
		background: color-mix(in oklab, var(--c-ctrl) 10%, transparent);
		color: var(--c-ink);
	}

	.lib-picker {
		position: relative;
		margin-bottom: var(--sp-4);
	}

	.lib-trigger {
		display: flex;
		align-items: center;
		gap: var(--sp-8);
		width: 100%;
		min-height: 38px;
		padding: var(--sp-6) var(--sp-10);
		border: 1px solid var(--c-line);
		border-radius: var(--r-card);
		background: var(--c-card);
		color: var(--c-ink);
		font: inherit;
		font-size: var(--fs-13);
		font-weight: var(--fw-semibold);
		text-align: left;
		cursor: pointer;
		transition:
			border-color var(--motion-fast) var(--motion-ease),
			background var(--motion-fast) var(--motion-ease);
	}

	.lib-trigger:hover {
		border-color: color-mix(in oklab, var(--c-ink) 25%, var(--c-line));
		background: color-mix(in oklab, var(--c-card) 92%, var(--c-ink));
	}

	.lib-name {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.lib-chevron {
		color: var(--c-muted);
		flex: none;
	}

	.lib-dropdown {
		position: absolute;
		top: calc(100% + var(--sp-4));
		left: 0;
		right: 0;
		z-index: 50;
		padding: var(--sp-4);
		border: 1px solid var(--c-line);
		border-radius: var(--r-card);
		background: var(--c-panel);
		box-shadow: 0 12px 32px -8px rgb(0 0 0 / 0.45);
		display: grid;
		gap: 2px;
	}

	.lib-dropdown-header {
		padding: var(--sp-4) var(--sp-8);
		font-size: var(--fs-11);
		font-weight: var(--fw-bold);
		color: var(--c-muted);
		text-transform: uppercase;
		letter-spacing: 0.05em;
	}

	.lib-opt {
		display: flex;
		align-items: center;
		gap: var(--sp-8);
		width: 100%;
		padding: var(--sp-8) var(--sp-10);
		border: 0;
		border-radius: var(--r-card);
		background: none;
		color: var(--c-ink);
		font: inherit;
		font-size: var(--fs-13);
		text-align: left;
		cursor: pointer;
		transition: background var(--motion-fast) var(--motion-ease);
	}

	.lib-opt:hover {
		background: color-mix(in oklab, var(--c-ctrl) 12%, transparent);
	}

	.lib-opt.selected {
		font-weight: var(--fw-bold);
		color: var(--c-accent);
	}

	.lib-opt-name {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.lib-opt-check {
		color: var(--c-accent);
		flex: none;
	}

	.side.collapsed {
		padding: var(--sp-20) var(--sp-8);
		align-items: stretch;
	}

	.side.collapsed .brand {
		justify-content: center;
		padding: 0;
	}

	.side.collapsed nav a {
		justify-content: center;
		padding: 0;
		width: 44px;
		min-height: 40px;
		margin-inline: auto;
	}

	.side.collapsed .nav-divider {
		height: 1px;
		margin: var(--sp-8) var(--sp-4);
		background: var(--c-line);
		border: 0;
	}

	.side.collapsed .nav-group.system {
		margin-top: var(--sp-8);
		padding-top: var(--sp-8);
		border-top: 1px solid var(--c-line);
	}

	.side.collapsed .lib-picker {
		display: flex;
		justify-content: center;
	}

	.side.collapsed .lib-trigger {
		width: 44px;
		height: 40px;
		padding: 0;
		justify-content: center;
	}

	.side.collapsed .lib-dropdown {
		position: fixed;
		left: calc(var(--layout-sidebar) + 8px);
		top: var(--dropdown-top, 80px);
		right: auto;
		width: 220px;
	}

	.side.collapsed .profile .me {
		flex-direction: column;
		background: transparent;
		padding: var(--sp-4) 0;
		gap: var(--sp-8);
	}

	.side.collapsed .who {
		justify-content: center;
		padding: 0;
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

	.nav-section {
		display: grid;
		gap: var(--sp-4);
		margin-top: var(--sp-8);
	}

	.nav-header {
		display: flex;
		align-items: center;
		justify-content: space-between;
		width: 100%;
		padding: var(--sp-6) var(--sp-12);
		border: 0;
		border-radius: var(--r-card);
		background: none;
		color: var(--c-muted);
		font-size: var(--fs-12);
		font-weight: var(--fw-bold);
		letter-spacing: 0.06em;
		text-transform: uppercase;
		cursor: pointer;
		user-select: none;
		transition: color var(--motion-fast) var(--motion-ease);
	}

	.nav-header:hover {
		color: var(--c-ink);
	}

	.nav-header .chevron {
		display: flex;
		align-items: center;
		justify-content: center;
		transition: transform var(--motion-fast) var(--motion-ease);
	}

	.nav-header .chevron.collapsed {
		transform: rotate(-90deg);
	}

	.nav-group {
		display: grid;
		gap: var(--sp-4);
	}

	.nav-group.system {
		margin-top: var(--sp-8);
		padding-top: var(--sp-8);
		border-top: 1px solid var(--c-line);
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

	.sheet-libraries {
		display: grid;
		gap: var(--sp-8);
	}

	.sheet-section-title {
		font-size: var(--fs-12);
		font-weight: var(--fw-bold);
		color: var(--c-muted);
		text-transform: uppercase;
		letter-spacing: 0.05em;
	}

	.sheet-lib-list {
		display: grid;
		gap: var(--sp-6);
	}

	.sheet-lib-btn {
		display: flex;
		align-items: center;
		gap: var(--sp-12);
		width: 100%;
		min-height: 44px;
		padding: var(--sp-8) var(--sp-12);
		border: 1px solid var(--c-line);
		border-radius: var(--r-card);
		background: var(--c-card);
		color: var(--c-ink);
		font: inherit;
		font-size: var(--fs-14);
		font-weight: var(--fw-semibold);
		text-align: left;
		cursor: pointer;
	}

	.sheet-lib-btn.selected {
		border-color: var(--c-accent);
		background: color-mix(in oklab, var(--c-accent) 12%, var(--c-card));
		color: var(--c-accent);
	}

	.sheet-lib-name {
		flex: 1;
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.sheet-lib-check {
		flex: none;
		color: var(--c-accent);
	}
</style>
