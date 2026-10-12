<script lang="ts">
	import { i18n, LOCALES, type Locale } from '#lib/i18n/index.svelte.ts';

	const locales = Object.keys(LOCALES) as Locale[];
</script>

<div class="seg" role="group" aria-label={i18n.m.nav.language}>
	{#each locales as l (l)}
		<button aria-pressed={i18n.locale === l} onclick={() => i18n.set(l)} lang={l} title={LOCALES[l]}>
			{l.toUpperCase()}
		</button>
	{/each}
</div>

<style>
	.seg {
		display: inline-flex;
		padding: 3px;
		border-radius: var(--r-pill);
		background: var(--c-card);
	}

	button {
		min-width: 40px;
		height: 28px;
		border: 0;
		border-radius: var(--r-pill);
		background: transparent;
		color: var(--c-muted);
		font-size: var(--fs-12);
		font-weight: var(--fw-bold);
		position: relative;
	}

	button::after {
		content: '';
		position: absolute;
		inset: -8px 0;
	}

	button[aria-pressed='true'] {
		background: var(--c-sel);
		color: var(--c-sel-ink);
	}
</style>
