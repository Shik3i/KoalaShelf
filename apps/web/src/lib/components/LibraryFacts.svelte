<script lang="ts">
	import type { LibraryStats } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { bytes, duration } from '#lib/format.ts';

	const t = $derived(i18n.m.libraryFacts);
	let stats = $state.raw<LibraryStats | null>(null);

	$effect(() => {
		const lib = session.libraryId;
		if (!lib) return;
		session.api
			.libraryStats(lib)
			.then((s) => (stats = s))
			.catch(() => (stats = null));
	});

	const genres = $derived(
		[...(stats?.genresWithCount ?? [])].sort((a, b) => b.count - a.count).slice(0, 8)
	);
	const maxGenre = $derived(Math.max(1, ...genres.map((g) => g.count)));
</script>

{#if stats}
	<section class="card facts" aria-labelledby="lf-title">
		<h2 class="label" id="lf-title">{t.title(session.library?.name ?? '')}</h2>
		<dl class="numbers">
			<div>
				<dt>{t.titles}</dt>
				<dd class="display tabular">{stats.totalItems}</dd>
			</div>
			<div>
				<dt>{t.duration}</dt>
				<dd class="display">{duration(stats.totalDuration)}</dd>
			</div>
			<div>
				<dt>{t.authors}</dt>
				<dd class="display tabular">{stats.totalAuthors}</dd>
			</div>
			<div>
				<dt>{t.size}</dt>
				<dd class="display">{bytes(stats.totalSize)}</dd>
			</div>
		</dl>
		<div class="split">
			{#if genres.length}
				<div>
					<h3 class="label">{t.genres}</h3>
					<ol class="ranks">
						{#each genres as g (g.genre)}
							<li style:--w={g.count / maxGenre}>
								<a href="/library?genre={encodeURIComponent(g.genre)}">{g.genre}</a>
								<span class="muted tabular">{g.count}</span>
							</li>
						{/each}
					</ol>
				</div>
			{/if}
			{#if stats.longestItems.length}
				<div>
					<h3 class="label">{t.longest}</h3>
					<ol class="ranks plain">
						{#each stats.longestItems.slice(0, 6) as b (b.id)}
							<li>
								<a href="/book/{b.id}">{b.title}</a>
								<span class="muted tabular">{duration(b.duration)}</span>
							</li>
						{/each}
					</ol>
				</div>
			{/if}
		</div>
	</section>
{/if}

<style>
	.facts {
		display: grid;
		gap: var(--sp-20);
		padding: var(--sp-24);
		border-radius: var(--r-card-lg);
		margin-top: var(--sp-16);
	}

	.numbers {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
		gap: var(--sp-16);
		margin: 0;
	}

	dt {
		font-size: var(--fs-13);
		color: var(--c-muted);
	}

	dd {
		margin: 0;
		font-size: var(--fs-24);
	}

	.split {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(min(100%, 260px), 1fr));
		gap: var(--sp-24);
	}

	h3 {
		margin-bottom: var(--sp-8);
	}

	.ranks {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--sp-8);
	}

	.ranks li {
		position: relative;
		display: flex;
		justify-content: space-between;
		gap: var(--sp-12);
		padding: var(--sp-8) var(--sp-12);
		border-radius: var(--r-card);
		font-size: var(--fs-14);
		isolation: isolate;
	}

	.ranks li::before {
		content: '';
		position: absolute;
		inset: 0 auto 0 0;
		width: calc(var(--w, 0) * 100%);
		z-index: -1;
		border-radius: inherit;
		background: color-mix(in oklab, var(--c-accent) 14%, transparent);
	}

	.ranks.plain li {
		background: var(--c-bg);
	}

	.ranks a {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.ranks a:hover {
		text-decoration: underline;
		text-underline-offset: 3px;
	}
</style>
