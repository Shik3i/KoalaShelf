<script lang="ts">
	import { page } from '$app/state';
	import { session } from '#lib/session.svelte.ts';
	import { theme } from '#lib/theme.svelte.ts';
	import House from '@lucide/svelte/icons/house';
	import LibraryBig from '@lucide/svelte/icons/library-big';
	import Layers from '@lucide/svelte/icons/layers';
	import Users from '@lucide/svelte/icons/users';
	import Search from '@lucide/svelte/icons/search';
	import SunMoon from '@lucide/svelte/icons/sun-moon';
	import Sun from '@lucide/svelte/icons/sun';
	import Moon from '@lucide/svelte/icons/moon';
	import LogOut from '@lucide/svelte/icons/log-out';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import LocaleSwitch from './LocaleSwitch.svelte';

	const t = $derived(i18n.m.nav);
	const items = $derived([
		{ href: '/', label: t.home, icon: House, tab: true },
		{ href: '/library', label: t.library, icon: LibraryBig, tab: true },
		{ href: '/series', label: t.series, icon: Layers, tab: true },
		{ href: '/authors', label: t.authors, icon: Users, tab: false },
		{ href: '/search', label: t.search, icon: Search, tab: true }
	]);

	const active = (href: string) =>
		href === '/' ? page.url.pathname === '/' : page.url.pathname.startsWith(href);

	const themeLabel = $derived(
		({ system: t.themeSystem, light: t.themeLight, dark: t.themeDark } as const)[theme.choice]
	);
</script>

<aside class="side">
	<a class="brand" href="/" aria-label={t.home}>
		<img src="/favicon.svg" alt="" width="28" height="28" />
		<span class="display">KoalaShelf</span>
	</a>
	<nav aria-label={t.main}>
		{#each items as item (item.href)}
			<a href={item.href} aria-current={active(item.href) ? 'page' : undefined}>
				<item.icon size={18} strokeWidth={2.2} aria-hidden="true" />
				{item.label}
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
			<a class="who" href="/settings" title={t.settings}>
				<span class="avatar" aria-hidden="true"
					>{session.user?.username.slice(0, 1).toUpperCase()}</span
				>
				<span class="name">{session.user?.username}</span>
			</a>
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

<nav class="tabs" aria-label={t.main}>
	{#each items.filter((i) => i.tab) as item (item.href)}
		<a href={item.href} aria-current={active(item.href) ? 'page' : undefined}>
			<item.icon size={22} strokeWidth={2} aria-hidden="true" />
			<span>{item.label}</span>
		</a>
	{/each}
</nav>

<style>
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

	.side nav a[aria-current='page'] {
		background: var(--c-sel);
		color: var(--c-sel-ink);
	}

	.profile {
		margin-top: auto;
		display: grid;
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
		border-radius: var(--r-card);
	}

	.who:hover .name {
		text-decoration: underline;
		text-underline-offset: 3px;
	}

	.avatar {
		display: grid;
		place-items: center;
		width: 28px;
		height: 28px;
		flex: none;
		border-radius: 50%;
		background: var(--c-accent);
		color: var(--c-on-accent);
		font-weight: var(--fw-bold);
		font-size: var(--fs-13);
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
			background: color-mix(in oklab, var(--c-panel) 86%, transparent);
			backdrop-filter: blur(16px) saturate(1.4);
			border-top: 1px solid var(--c-line);
		}

		.tabs a {
			display: grid;
			justify-items: center;
			gap: 2px;
			min-height: 52px;
			align-content: center;
			font-size: var(--fs-12);
			font-weight: var(--fw-bold);
			color: var(--c-muted);
		}

		.tabs a[aria-current='page'] {
			color: var(--c-accent);
		}
	}
</style>
