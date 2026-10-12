<script lang="ts">
	import '../app.css';
	import { goto, onNavigate } from '$app/navigation';
	import { page } from '$app/state';
	import { session } from '#lib/session.svelte.ts';
	import AppNav from '#lib/components/AppNav.svelte';
	import MiniPlayer from '#lib/player/MiniPlayer.svelte';
	import FullPlayer from '#lib/player/FullPlayer.svelte';
	import { player } from '#lib/player/player.svelte.ts';
	import type { LayoutProps } from './$types';

	let { children }: LayoutProps = $props();

	const isPublic = $derived(page.url.pathname.startsWith('/signin'));

	let full = $state<FullPlayer>();

	session.restore().then(() => session.client && player.restoreLast());

	/** Global player shortcuts, ignored while typing or on focused controls. */
	function onKey(e: KeyboardEvent) {
		if (!player.item || e.ctrlKey || e.metaKey || e.altKey) return;
		const el = e.target as HTMLElement;
		if (el.closest('input, textarea, select, [contenteditable]')) return;
		const key = e.key;
		if (key === ' ' && !el.closest('button, a')) {
			e.preventDefault();
			player.toggle();
		} else if (key === 'ArrowLeft' || key === 'ArrowRight') {
			e.preventDefault();
			const dir = key === 'ArrowLeft' ? -1 : 1;
			if (!e.shiftKey) player.skip(dir * 30);
			else if (dir < 0) player.prevChapter();
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
		if (!document.startViewTransition || matchMedia('(prefers-reduced-motion: reduce)').matches)
			return;
		if (nav.from?.url.pathname === nav.to?.url.pathname) return;
		return new Promise((resolve) => {
			document.startViewTransition(async () => {
				resolve();
				await nav.complete;
			});
		});
	});
</script>

<svelte:window onkeydown={onKey} />

{#if isPublic}
	{@render children()}
{:else if session.ready && session.client}
	<div class="shell">
		<AppNav />
		<main class:has-player={player.item}>
			{@render children()}
		</main>
	</div>
	<MiniPlayer />
	<FullPlayer bind:this={full} />
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
