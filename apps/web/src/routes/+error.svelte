<script lang="ts">
	import { page } from '$app/state';
	import { i18n } from '#lib/i18n/index.svelte.ts';

	/** Errors while showing a page land here, inside the app: navigation and player stay. */
	const t = $derived(i18n.m.common);
	const notFound = $derived(page.status === 404);
</script>

<svelte:head><title>{notFound ? t.notFoundTitle : t.brokenTitle} · KoalaShelf</title></svelte:head>

<div class="broken" role="alert">
	<h1>{notFound ? t.notFoundTitle : t.brokenTitle}</h1>
	<p class="muted">{notFound ? t.notFoundText : t.brokenText}</p>
	{#if !notFound && page.error?.message}
		<p class="muted small"><code>{page.error.message}</code></p>
	{/if}
	<div class="actions">
		<a class="btn primary" href="/">{t.home}</a>
		{#if !notFound}
			<button class="btn ghost" onclick={() => location.reload()}>{t.reload}</button>
		{/if}
	</div>
</div>

<style>
	.broken {
		display: grid;
		gap: var(--sp-12);
		max-width: 52ch;
		padding: var(--sp-48) 0;
	}

	.small {
		font-size: var(--fs-13);
		overflow-wrap: anywhere;
	}

	.actions {
		display: flex;
		flex-wrap: wrap;
		gap: var(--sp-8);
	}
</style>
