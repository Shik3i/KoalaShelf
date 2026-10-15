<script lang="ts">
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
		{#each authors ?? [] as a (a.id)}
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
		{#each narrators ?? [] as n (n.id)}
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

	.pic {
		display: block;
		width: 100%;
		margin-bottom: var(--sp-8);
		transition: scale var(--motion-base) var(--motion-ease);
	}

	.person:hover .pic {
		scale: 1.04;
	}

	strong {
		font-weight: var(--fw-semibold);
		font-size: var(--fs-14);
	}

	.muted {
		font-size: var(--fs-13);
	}
</style>
