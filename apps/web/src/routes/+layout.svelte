<script lang="ts">
	import { untrack } from 'svelte';
	import '../app.css';
	import { goto, onNavigate } from '$app/navigation';
	import { page } from '$app/state';
	import { session } from '#lib/session.svelte.ts';
	import AppNav from '#lib/components/AppNav.svelte';
	import MiniPlayer from '#lib/player/MiniPlayer.svelte';
	import FullPlayer from '#lib/player/FullPlayer.svelte';
	import PipPlayer from '#lib/player/PipPlayer.svelte';
	import { player } from '#lib/player/player.svelte.ts';
	import { isDesktop } from '#lib/platform.ts';
	import { startLive } from '#lib/live.ts';
	import Toaster from '#lib/components/Toaster.svelte';
	import Shortcuts from '#lib/components/Shortcuts.svelte';
	import CommandPalette from '#lib/components/CommandPalette.svelte';
	import { RATE_STEP } from '#lib/player/player.svelte.ts';
	import { progress } from '#lib/progress.svelte.ts';
	import { applyCustomAccent, customAccent } from '#lib/accent.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { dev } from '$app/env';
	import { prefs } from '#lib/prefs.svelte.ts';
	import { reducedMotion } from '#lib/motion.ts';
	import type { LayoutProps } from './$types';

	let { children }: LayoutProps = $props();

	const isPublic = $derived(page.url.pathname.startsWith('/signin'));
	let isMini = $state(typeof window !== 'undefined' && window.location.hash.includes('/mini'));

	$effect(() => {
		const check = () => {
			isMini = window.location.hash.includes('/mini');
		};
		window.addEventListener('hashchange', check);
		return () => window.removeEventListener('hashchange', check);
	});

	let full = $state<FullPlayer>();
	let shortcuts = $state(false);
	let palette = $state(false);

	// Live progress from other devices while signed in.
	$effect(() => {
		if (!session.client) return;
		startLive();
		// The last book comes back paused, so the mini player is there after a reload.
		untrack(() => void player.restoreLast());
		// Progress and the account's permissions (downloads, collections) for every page.
		untrack(() => void progress.load().catch(() => undefined));
	});
	let online = $state(navigator.onLine);

	// Appearance settings as attributes on <html> (static/theme.js sets them before first paint).
	$effect(() => {
		const d = document.documentElement.dataset;
		d.accent = prefs.accent;
		d.font = prefs.font;
		d.text = prefs.textSize;
		d.motion = prefs.motion;
		d.cover = prefs.coverSize;
		const width = prefs.sidebarCollapsed ? 68 : (prefs.sidebarWidth ?? 240);
		document.documentElement.style.setProperty('--layout-sidebar', `${width}px`);
		d.sidebarCollapsed = prefs.sidebarCollapsed ? 'true' : 'false';
	});

	// The listener's own accent color, in readable light and dark variants.
	$effect(() => {
		if (prefs.accent === 'custom') applyCustomAccent(customAccent(prefs.customAccent));
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
	if (!dev && !isDesktop && 'serviceWorker' in navigator) {
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
			player.setRate(player.rate + (key === ']' ? 2 : -2) * RATE_STEP);
			return;
		}
		if (key === 'u' || key === 'U') {
			player.undoJump();
			return;
		}
		if (key === 'm' || key === 'M') {
			player.setVolume(prefs.volume ? 0 : 1);
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
		const fromPath = nav.from?.url?.pathname;
		const toPath = nav.to?.url?.pathname;
		if (!fromPath || !toPath || fromPath === toPath) return;
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

{#if isMini}
	<PipPlayer />
{:else if isPublic}
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
					<!-- A page that fails keeps the app usable: navigation and player stay. -->
					<svelte:boundary onerror={(e) => console.error(e)}>
						{@render children()}
						{#snippet failed(error, reset)}
							<div class="broken" role="alert">
								<h1>{i18n.m.common.brokenTitle}</h1>
								<p class="muted">{i18n.m.common.brokenText}</p>
								<p class="muted small">
									<code>{error instanceof Error ? error.message : String(error)}</code>
								</p>
								<div class="broken-actions">
									<button class="btn primary" onclick={reset}>{i18n.m.common.retry}</button>
									<button class="btn ghost" onclick={() => location.reload()}
										>{i18n.m.common.reload}</button
									>
								</div>
							</div>
						{/snippet}
					</svelte:boundary>
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
	.broken {
		display: grid;
		gap: var(--sp-12);
		max-width: 52ch;
		padding: var(--sp-48) 0;
	}

	.broken .small {
		font-size: var(--fs-13);
		overflow-wrap: anywhere;
	}

	.broken-actions {
		display: flex;
		flex-wrap: wrap;
		gap: var(--sp-8);
	}

	.shell {
		display: grid;
		grid-template-columns: var(--layout-sidebar) 1fr;
		min-height: 100dvh;
		background: var(--c-bg);
		transition: grid-template-columns var(--motion-fast) var(--motion-ease);
	}

	:global(html.is-resizing) .shell,
	:global(html.is-resizing) :global(*) {
		user-select: none !important;
		cursor: col-resize !important;
		transition: none !important;
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
