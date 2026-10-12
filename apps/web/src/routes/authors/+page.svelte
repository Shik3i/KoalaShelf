<script lang="ts">
	import type { Author } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { coverArt } from '#lib/components/cover-art.ts';

	const t = $derived(i18n.m.authors);
	let authors = $state.raw<Author[] | null>(null);
	let error = $state<string | null>(null);

	$effect(() => {
		const lib = session.libraryId;
		if (!lib) return;
		session.api
			.authors(lib)
			.then((a) => (authors = a.sort((x, y) => x.name.localeCompare(y.name, i18n.tag))))
			.catch((e) => (error = (e as Error).message));
	});

	const initials = (name: string) =>
		name
			.split(/\s+/)
			.map((p) => p[0])
			.slice(0, 2)
			.join('')
			.toUpperCase();
</script>

<svelte:head><title>{t.title} · KoalaShelf</title></svelte:head>

<h1>{t.title}</h1>

{#if error}<p class="muted">{i18n.m.common.error(error)}</p>{/if}
{#if authors && !authors.length}<p class="muted">{t.empty}</p>{/if}

<div class="grid">
	{#if !authors && !error}
		{#each Array.from({ length: 12 }, (_, i) => i) as i (i)}
			<div class="skeleton" style="aspect-ratio: 1; border-radius: 50%"></div>
		{/each}
	{/if}
	{#each authors ?? [] as a (a.id)}
		{@const art = coverArt(a.name)}
		<a href="/library?author={a.id}" class="author">
			<div class="portrait" style:--bg={art.bg} style:--ink={art.ink}>
				<span>{initials(a.name)}</span>
				{#if a.imagePath}
					<img
						src={session.api.authorImageUrl(a.id, { width: 240, updatedAt: a.updatedAt })}
						alt=""
						loading="lazy"
						decoding="async"
					/>
				{/if}
			</div>
			<strong>{a.name}</strong>
			{#if a.numBooks}<span class="muted">{t.books(a.numBooks)}</span>{/if}
		</a>
	{/each}
</div>

<style>
	h1 {
		margin-bottom: var(--sp-32);
	}

	.grid {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
		gap: var(--sp-32) var(--sp-24);
	}

	.author {
		display: grid;
		justify-items: center;
		gap: var(--sp-4);
		text-align: center;
	}

	.portrait {
		position: relative;
		display: grid;
		place-items: center;
		width: 100%;
		aspect-ratio: 1;
		margin-bottom: var(--sp-8);
		border-radius: 50%;
		overflow: hidden;
		background: var(--bg);
		color: var(--ink);
		font-family: var(--font-display);
		font-size: var(--fs-34);
		transition: scale var(--motion-base) var(--motion-ease);
	}

	.author:hover .portrait {
		scale: 1.04;
	}

	img {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		object-fit: cover;
	}

	strong {
		font-weight: var(--fw-semibold);
		font-size: var(--fs-14);
	}

	.muted {
		font-size: var(--fs-13);
	}
</style>
