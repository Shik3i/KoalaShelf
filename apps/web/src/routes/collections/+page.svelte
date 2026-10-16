<script lang="ts">
	import { byId, unique } from '#lib/unique.ts';
	import EmptyState from '#lib/components/EmptyState.svelte';
	import type { Collection } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { coverSrc, view } from '#lib/items.ts';
	import Crate from '#lib/components/Crate.svelte';

	const t = $derived(i18n.m.collections);
	let list = $state.raw<Collection[] | null>(null);
	let error = $state<string | null>(null);

	$effect(() => {
		const lib = session.libraryId;
		if (!lib) return;
		session.api
			.collections(lib)
			.then((c) => (list = c))
			.catch((e) => (error = (e as Error).message));
	});
</script>

<svelte:head><title>{t.title} · KoalaShelf</title></svelte:head>

<h1>{t.title}</h1>

{#if error}<p class="muted">{i18n.m.common.error(error)}</p>{/if}
{#if session.canUpdate}<p class="muted hint">{t.howToCreate}</p>{/if}
{#if list && !list.length}<EmptyState text={t.empty} />{/if}

<div class="crates">
	{#each unique(list, byId) as c (c.id)}
		<Crate
			title={c.name}
			note={t.books(c.books.length)}
			href="/collections/{c.id}"
			entries={c.books.map((b) => {
				const v = view(b);
				return {
					id: b.id,
					href: `/book/${b.id}`,
					title: v.title,
					author: v.author,
					cover: coverSrc(b)
				};
			})}
		/>
	{/each}
</div>

<style>
	h1 {
		margin-bottom: var(--sp-32);
	}

	.hint {
		max-width: 60ch;
		margin: calc(-1 * var(--sp-16)) 0 var(--sp-32);
	}

	.crates {
		display: grid;
		gap: var(--sp-32);
	}
</style>
