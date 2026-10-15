<script lang="ts">
	import { untrack } from 'svelte';
	import '../app.css';
	import { goto, onNavigate } from '$app/navigation';
	import { page } from '$app/state';
	import { session } from '#lib/session.svelte.ts';
	import AppNav from '#lib/components/AppNav.svelte';
	import MiniPlayer from '#lib/player/MiniPlayer.svelte';
	import FullPlayer from '#lib/player/FullPlayer.svelte';
	import { player } from '#lib/player/player.svelte.ts';
	import { isTauri } from '#lib/platform.ts';
	import { startLive } from '#lib/live.ts';
	import Toaster from '#lib/components/Toaster.svelte';
	import Shortcuts from '#lib/components/Shortcuts.svelte';
	import CommandPalette from '#lib/components/CommandPalette.svelte';
	import { RATES } from '#lib/player/player.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { dev } from '$app/env';
	import { prefs } from '#lib/prefs.svelte.ts';
	import { reducedMotion } from '#lib/motion.ts';
	import type { LayoutProps } from './$types';

	let { children }: LayoutProps = $props();

	const isPublic = $derived(page.url.pathname.startsWith('/signin'));

	let full = $state<FullPlayer>();
	let shortcuts = $state(false);
	let palette = $state(false);

	// Live progress from other devices while signed in.
	$effect(() => {
		if (!session.client) return;
		startLive();
		// The last book comes back paused, so the mini player is there after a reload.
		untrack(() => void player.restoreLast());
	});
	let online = $state(navigator.onLine);

	// Appearance settings as attributes on <html> (static/theme.js sets them before first paint).
	$effect(() => {
		const d = document.documentElement.dataset;
		d.accent = prefs.accent;
		d.text = prefs.textSize;
		d.motion = prefs.motion;
		d.cover = prefs.coverSize;
	});

	// Opening the app on "/" goes to the chosen start page (only the first time per visit).
	let started = false;
	$effect(() => {
		if (started || !session.ready || !session.client) return;
		started = true;
		if (page.url.pathname === '/' && prefs.startPage === 'library')
			goto('/library', { replace: true });
	});

	// Offline shell for the browser/PWA; the desktop app ships its files locally anyway.
	if (!dev && !isTauri && 'serviceWorker' in navigator) {
		navigator.serviceWorker
			.register('/service-worker.js', { type: 'module' })
			.catch(() => undefined);
	}

	/** Global player shortcuts, ignored while typing or on focused controls. */
	function onKey(e: KeyboardEvent) {
		// Quick search: Ctrl/⌘ K anywhere (also while typing), "/" elsewhere.
		if ((e.ctrlKey || e.metaKey) && !e.altKey && e.key.toLowerCase() === 'k' && session.client) {
			e.preventDefault();
			palette = !palette;
			return;
		}
		if (e.ctrlKey || e.metaKey || e.altKey || !prefs.shortcuts) return;
		const el = e.target as HTMLElement;
		if (el.closest('input, textarea, select, [contenteditable]')) return;
		const key = e.key;
		if (key === '/' && session.client) {
			e.preventDefault();
			palette = true;
			return;
		}
		if (key === '?') {
			shortcuts = !shortcuts;
			return;
		}
		if (!player.item) return;
		if (key === '[' || key === ']') {
			const i = RATES.findIndex((r) => r === player.rate);
			const next =
				RATES[Math.min(RATES.length - 1, Math.max(0, (i < 0 ? 1 : i) + (key === ']' ? 1 : -1)))];
			player.setRate(next);
			return;
		}
		if (key === ' ' && !el.closest('button, a')) {
			e.preventDefault();
			player.toggle();
		} else if (key === 'ArrowLeft' || key === 'ArrowRight') {
			e.preventDefault();
			const dir = key === 'ArrowLeft' ? -1 : 1;
			if (!e.shiftKey) {
				if (dir < 0) player.back();
				else player.forward();
			} else if (dir < 0) player.prevChapter();
			else player.nextChapter();
		} else if (key === 'b' || key === 'B') {
			e.preventDefault();
			full?.openBookmarks();
		}
	}

	// Auth guard: everything except /signin needs a session.
	$effect(() => {
		if (!session.ready) return;
		if (!session.client && !isPublic)
			goto(`/signin?next=${encodeURIComponent(page.url.pathname)}`, { replace: true });
	});

	// Smooth page changes via the View Transitions API (skipped when unsupported or reduced motion).
	onNavigate((nav) => {
		if (!document.startViewTransition || reducedMotion()) return;
		if (nav.from?.url.pathname === nav.to?.url.pathname) return;
		return new Promise((resolve) => {
			document.startViewTransition(async () => {
				resolve();
				await nav.complete;
			});
		});
	});
</script>

<svelte:window
	onkeydown={onKey}
	ononline={() => (online = true)}
	onoffline={() => (online = false)}
/>

{#if !online}
	<div class="offline" role="status">
		{i18n.m.common.offline} <a href="/downloads">{i18n.m.downloads.offlineHint}</a>
	</div>
{/if}

{#if isPublic}
	{@render children()}
{:else if session.ready && session.client}
	<a class="skip" href="#main">{i18n.m.nav.skip}</a>
	<div class="shell">
		<AppNav />
		<main id="main" tabindex="-1" class:has-player={player.item}>
			<div class="page">
				{#if session.librariesLoaded && !session.libraries.length && !/^\/(settings|downloads)/.test(page.url.pathname)}
					<p class="no-lib">{i18n.m.common.noLibrary}</p>
				{:else}
					{@render children()}
				{/if}
			</div>
		</main>
	</div>
	<MiniPlayer />
	<FullPlayer bind:this={full} />
	<Toaster hidden={player.expanded} />
	<Shortcuts bind:open={shortcuts} />
	<CommandPalette bind:open={palette} />
{/if}

<style>
	.shell {
		display: grid;
		grid-template-columns: var(--layout-sidebar) 1fr;
		min-height: 100dvh;
		background: var(--c-bg);
	}

	main {
		min-width: 0;
		padding: var(--sp-32) var(--sp-40) var(--sp-64);
		view-transition-name: main;
	}

	/* Lines and lists stay readable on very wide screens; tile grids still get many columns. */
	.page {
		max-width: 1600px;
		margin-inline: auto;
		/* Pages size themselves to this column, not the viewport. */
		container: page / inline-size;
	}

	main:focus {
		outline: none;
	}

	.no-lib {
		padding: var(--sp-64) var(--sp-16);
		text-align: center;
		color: var(--c-muted);
	}

	.offline a {
		text-decoration: underline;
		text-underline-offset: 3px;
	}

	.offline {
		position: sticky;
		top: 0;
		z-index: 30;
		padding: var(--sp-8) var(--sp-16);
		background: var(--c-ctrl);
		color: var(--c-ctrl-ink);
		font-size: var(--fs-13);
		font-weight: var(--fw-semibold);
		text-align: center;
	}

	main.has-player {
		padding-bottom: calc(var(--sp-64) + 80px);
	}

	@media (max-width: 800px) {
		.shell {
			grid-template-columns: 1fr;
		}

		main {
			padding: var(--sp-20) var(--sp-16) calc(var(--sp-64) + 64px + env(safe-area-inset-bottom));
		}

		main.has-player {
			padding-bottom: calc(var(--sp-64) + 144px + env(safe-area-inset-bottom));
		}
	}
</style>
