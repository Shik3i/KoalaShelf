<script lang="ts">
	import '../app.css';
	import { goto, onNavigate } from '$app/navigation';
	import { page } from '$app/state';
	import { session } from '#lib/session.svelte.ts';
	import AppNav from '#lib/components/AppNav.svelte';
	import type { LayoutProps } from './$types';

	let { children }: LayoutProps = $props();

	const isPublic = $derived(page.url.pathname.startsWith('/signin'));

	session.restore();

	// Auth guard: everything except /signin needs a session.
	$effect(() => {
		if (!session.ready) return;
		if (!session.client && !isPublic) goto(`/signin?next=${encodeURIComponent(page.url.pathname)}`, { replace: true });
	});

	// Smooth page changes via the View Transitions API (skipped when unsupported or reduced motion).
	onNavigate((nav) => {
		if (!document.startViewTransition || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
		if (nav.from?.url.pathname === nav.to?.url.pathname) return;
		return new Promise((resolve) => {
			document.startViewTransition(async () => {
				resolve();
				await nav.complete;
			});
		});
	});
</script>

{#if isPublic}
	{@render children()}
{:else if session.ready && session.client}
	<div class="shell">
		<AppNav />
		<main>
			{@render children()}
		</main>
	</div>
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

	@media (max-width: 800px) {
		.shell {
			grid-template-columns: 1fr;
		}

		main {
			padding: var(--sp-20) var(--sp-16) calc(var(--sp-64) + 64px + env(safe-area-inset-bottom));
		}
	}
</style>
