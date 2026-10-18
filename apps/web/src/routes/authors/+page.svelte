<script lang="ts">
	import { byId, unique } from '#lib/unique.ts';
	import EmptyState from '#lib/components/EmptyState.svelte';
	import type { Author, Narrator } from '@koalashelf/abs-client';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { session } from '#lib/session.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Portrait from '#lib/components/Portrait.svelte';
	import Segmented from '#lib/components/Segmented.svelte';

	const t = $derived(i18n.m.authors);
	/** Authors or narrators (`?tab=narrators`). */
	const tab = $derived(page.url.searchParams.get('tab') === 'narrators' ? 'narrators' : 'authors');

	let authors = $state.raw<Author[] | null>(null);
	let narrators = $state.raw<Narrator[] | null>(null);
	let error = $state<string | null>(null);

	$effect(() => {
		const lib = session.libraryId;
		if (!lib) return;
		const byName = <T extends { name: string }>(list: T[]) =>
			list.sort((x, y) => x.name.localeCompare(y.name, i18n.tag));
		if (tab === 'authors')
			session.api
				.authors(lib)
				.then((a) => (authors = byName(a)))
				.catch((e) => (error = (e as Error).message));
		else
			session.api
				.narrators(lib)
				.then((n) => (narrators = byName(n)))
				.catch((e) => (error = (e as Error).message));
	});

	const list = $derived(tab === 'authors' ? authors : narrators);
</script>

<svelte:head><title>{tab === 'authors' ? t.title : t.narrators} · KoalaShelf</title></svelte:head>

<div class="head">
	<h1>{tab === 'authors' ? t.title : t.narrators}</h1>
	<Segmented
		label={t.show}
		value={tab}
		options={[
			{ value: 'authors', label: t.title },
			{ value: 'narrators', label: t.narrators }
		]}
		onchange={(v) => goto(v === 'narrators' ? '?tab=narrators' : '?', { replace: true })}
	/>
</div>

{#if error}<p class="muted">{i18n.m.common.error(error)}</p>{/if}
{#if list && !list.length}<EmptyState text={tab === 'authors' ? t.empty : t.noNarrators} />{/if}

<div class="grid">
	{#if !list && !error}
		{#each Array.from({ length: 12 }, (_, i) => i) as i (i)}
			<div class="skeleton" style="aspect-ratio: 1; border-radius: 50%"></div>
		{/each}
	{/if}
	{#if tab === 'authors'}
		{#each unique(authors, byId) as a (a.id)}
			<a href="/author/{a.id}" class="person">
				<span class="pic">
					<Portrait
						name={a.name}
						src={a.imagePath
							? session.api.authorImageUrl(a.id, { width: 240, updatedAt: a.updatedAt })
							: null}
					/>
				</span>
				<strong>{a.name}</strong>
				{#if a.numBooks}<span class="muted">{t.books(a.numBooks)}</span>{/if}
			</a>
		{/each}
	{:else}
		{#each unique(narrators, byId) as n (n.id)}
			<a href="/library?narrator={encodeURIComponent(n.name)}" class="person">
				<span class="pic"><Portrait name={n.name} /></span>
				<strong>{n.name}</strong>
				<span class="muted">{t.books(n.numBooks)}</span>
			</a>
		{/each}
	{/if}
</div>

<style>
	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		flex-wrap: wrap;
		gap: var(--sp-16);
		margin-bottom: var(--sp-32);
	}

	.grid {
		display: grid;
		grid-template-columns: repeat(
			auto-fill,
			minmax(min(calc(140px * var(--tile-scale)), 42%), 1fr)
		);
		gap: var(--sp-32) var(--sp-24);
	}

	.person {
		display: grid;
		justify-items: center;
		gap: var(--sp-4);
		text-align: center;
	}

	/* Hovered, the portrait becomes the label of a record and turns. */
	.pic {
		position: relative;
		isolation: isolate;
		display: block;
		width: 100%;
		margin-bottom: var(--sp-8);
	}

	.pic::before {
		content: '';
		position: absolute;
		inset: 0;
		z-index: -1;
		border-radius: 50%;
		background:
			radial-gradient(circle at 32% 28%, rgb(255 255 255 / 0.12), transparent 45%),
			repeating-radial-gradient(circle, var(--c-vinyl-a) 0 2px, var(--c-vinyl-b) 2px 4px);
		box-shadow: 0 10px 24px -12px rgb(0 0 0 / 0.6);
		opacity: 0;
		scale: 0.9;
		transition:
			opacity var(--motion-base) var(--motion-ease),
			scale 420ms var(--motion-out);
	}

	.pic :global(.portrait) {
		transition: scale 420ms var(--motion-out);
	}

	.person:is(:hover, :focus-visible) .pic::before {
		opacity: 1;
		scale: 1;
	}

	.person:is(:hover, :focus-visible) .pic :global(.portrait) {
		scale: 0.62;
		box-shadow: 0 0 0 3px rgb(0 0 0 / 0.35);
		animation: spin 6s linear 420ms infinite;
	}

	strong {
		font-weight: var(--fw-semibold);
		font-size: var(--fs-14);
	}

	.muted {
		font-size: var(--fs-13);
	}
</style>
