<script lang="ts">
	import { encodeFilter, type Bookmark, type LibraryItemMinified } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { progress } from '#lib/progress.svelte.ts';
	import { player } from '#lib/player/player.svelte.ts';
	import { view } from '#lib/items.ts';
	import { bytes, clock, duration } from '#lib/format.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Cover from '#lib/components/Cover.svelte';
	import Vinyl from '#lib/components/Vinyl.svelte';
	import BookTile from '#lib/components/BookTile.svelte';
	import Play from '@lucide/svelte/icons/play';
	import Pause from '@lucide/svelte/icons/pause';
	import Check from '@lucide/svelte/icons/check';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	const t = $derived(i18n.m.book);
	const item = $derived(data.item);
	const v = $derived(item ? view(item) : null);
	const meta = $derived(item?.media.metadata);
	const p = $derived(item ? progress.get(item.id) : undefined);
	const isCurrent = $derived(!!item && player.item?.id === item.id);
	const position = $derived(isCurrent ? player.currentTime : (p?.currentTime ?? 0));
	const pct = $derived(
		item ? (isCurrent ? player.progress : p?.isFinished ? 1 : (p?.progress ?? 0)) : 0
	);
	const marks = $derived(
		item ? item.media.chapters.slice(1).map((c) => c.start / item.media.duration) : []
	);

	type Tab = 'chapters' | 'bookmarks' | 'more';
	let tab = $state<Tab>('chapters');

	let ownBookmarks = $state.raw<Bookmark[]>([]);
	const bookmarks = $derived(isCurrent ? player.bookmarks : ownBookmarks);
	let more = $state.raw<LibraryItemMinified[]>([]);

	$effect(() => {
		const it = item;
		if (!it) return;
		progress.load();
		session.api
			.bookmarks(it.id)
			.then((b) => (ownBookmarks = b.sort((a, c) => a.time - c.time)))
			.catch(() => (ownBookmarks = []));
		const author = it.media.metadata.authors[0];
		more = [];
		if (author && session.libraryId)
			session.api
				.libraryItems(session.libraryId, {
					filter: encodeFilter('authors', author.id),
					limit: 13,
					sort: 'media.metadata.title'
				})
				.then((r) => (more = r.results.filter((x) => x.id !== it.id).slice(0, 12)))
				.catch(() => undefined);
	});

	function playAt(time?: number) {
		if (!item) return;
		if (isCurrent && time === undefined) return player.toggle();
		void player.play(item.id, time);
	}

	async function toggleFinished() {
		if (!item) return;
		const done = !p?.isFinished;
		await session.api.updateProgress(item.id, {
			isFinished: done,
			...(done ? {} : { currentTime: 0, progress: 0 })
		});
		await progress.load(true);
	}

	const formats = $derived(
		item
			? [
					...new Set(
						item.media.audioFiles.map(
							(f) =>
								`${f.format || f.metadata.ext.slice(1).toUpperCase()} · ${Math.round(f.bitRate / 1000)} kbit/s`
						)
					)
				]
			: []
	);
</script>

<svelte:head><title>{v?.title ?? t.notFound} · KoalaShelf</title></svelte:head>

{#if !item || !v || !meta}
	<p class="muted">{t.notFound}</p>
{:else}
	<article>
		<section class="hero">
			<div class="art" class:spinning={isCurrent && player.playing}>
				<div class="vinyl">
					<Vinyl
						title={v.title}
						author={v.author}
						src={v.cover}
						playing={isCurrent && player.playing}
						{marks}
					/>
				</div>
				<div class="cover" style:view-transition-name="cover-{item.id}">
					<Cover
						title={v.title}
						author={v.author}
						src={v.cover}
						sizes="(max-width: 800px) 70vw, 300px"
						eager
					/>
				</div>
			</div>

			<div class="info">
				{#if meta.series[0]}
					<a class="label series" href="/library?series={meta.series[0].id}"
						>{t.seriesBook(meta.series[0].name, meta.series[0].sequence)}</a
					>
				{/if}
				<h1>{v.title}</h1>
				{#if meta.subtitle}<p class="subtitle">{meta.subtitle}</p>{/if}
				<p class="people">
					{#each meta.authors as a, i (a.id)}{i ? ', ' : ''}<a href="/library?author={a.id}"
							>{a.name}</a
						>{/each}
				</p>
				{#if meta.narrators.length}
					<p class="muted">
						{t.readBy}
						{#each meta.narrators as n, i (n)}{i ? ', ' : ''}<a
								href="/library?narrator={encodeURIComponent(n)}">{n}</a
							>{/each}
					</p>
				{/if}

				<div class="chips">
					<span class="chip outline">{duration(item.media.duration)}</span>
					{#if item.media.chapters.length}<span class="chip outline"
							>{t.chapterCount(item.media.chapters.length)}</span
						>{/if}
					{#if meta.language}<span class="chip outline">{meta.language}</span>{/if}
					{#if meta.publishedYear}<span class="chip outline">{meta.publishedYear}</span>{/if}
				</div>

				<div class="progress">
					<div class="bar" style:--p={pct}></div>
					<span class="muted tabular">
						{Math.round(pct * 100)} % · {t.remaining(
							duration(Math.max(0, item.media.duration - position))
						)}
					</span>
				</div>

				<div class="cta">
					<button class="btn primary big" onclick={() => playAt()}>
						{#if isCurrent && player.playing}
							<Pause size={18} fill="currentColor" /> {i18n.m.player.pause}
						{:else}
							<Play size={18} fill="currentColor" />
							{p?.isFinished ? t.listenAgain : position > 5 ? t.resume(clock(position)) : t.play}
						{/if}
					</button>
					<button class="btn ghost" onclick={toggleFinished}>
						<Check size={16} />
						{p?.isFinished ? t.markUnfinished : t.markFinished}
					</button>
				</div>
			</div>
		</section>

		<div class="lower">
			<section class="tabs-wrap">
				<div class="tabs" role="tablist">
					<button role="tab" aria-selected={tab === 'chapters'} onclick={() => (tab = 'chapters')}
						>{t.chapters}</button
					>
					<button
						role="tab"
						aria-selected={tab === 'bookmarks'}
						onclick={() => (tab = 'bookmarks')}
					>
						{t.bookmarks}{bookmarks.length ? ` · ${bookmarks.length}` : ''}
					</button>
					{#if more.length}
						<button role="tab" aria-selected={tab === 'more'} onclick={() => (tab = 'more')}
							>{t.moreByAuthor}</button
						>
					{/if}
				</div>

				<div role="tabpanel">
					{#if tab === 'chapters'}
						<ol class="rows">
							{#each item.media.chapters as c, i (c.id)}
								{@const current = isCurrent && player.chapter?.id === c.id}
								<li>
									<button class:current onclick={() => playAt(c.start)}>
										<span class="num display">{i + 1}</span>
										<span class="ttl">{c.title}</span>
										<span class="muted tabular">{clock(c.end - c.start)}</span>
									</button>
								</li>
							{/each}
						</ol>
					{:else if tab === 'bookmarks'}
						<ul class="rows">
							{#each bookmarks as b (b.time)}
								<li>
									<button onclick={() => playAt(b.time)}>
										<span class="pin tabular">{clock(b.time)}</span>
										<span class="ttl">{b.title}</span>
									</button>
								</li>
							{:else}
								<li class="muted empty">{t.noBookmarks}</li>
							{/each}
						</ul>
					{:else}
						<div class="shelf">
							{#each more as m (m.id)}<BookTile item={m} />{/each}
						</div>
					{/if}
				</div>
			</section>

			<aside class="facts">
				{#if meta.description}
					<h2 class="label">{t.description}</h2>
					<p class="desc">{meta.description}</p>
				{/if}
				<h2 class="label">{t.details}</h2>
				<dl>
					<dt>{t.format}</dt>
					<dd>{formats.join(', ')} · {t.files(item.media.audioFiles.length)}</dd>
					<dt>{t.size}</dt>
					<dd>{bytes(item.media.size)}</dd>
					{#if meta.genres.length}<dt>{t.genre}</dt>
						<dd>{meta.genres.join(', ')}</dd>{/if}
					{#if meta.publishedYear}<dt>{t.published}</dt>
						<dd>{meta.publishedYear}</dd>{/if}
					{#if meta.publisher}<dt>{t.publisher}</dt>
						<dd>{meta.publisher}</dd>{/if}
					{#if meta.language}<dt>{t.language}</dt>
						<dd>{meta.language}</dd>{/if}
					{#if session.library}<dt>{t.library}</dt>
						<dd>{session.library.name}</dd>{/if}
				</dl>
			</aside>
		</div>
	</article>
{/if}

<style>
	.hero {
		display: grid;
		grid-template-columns: auto 1fr;
		gap: var(--sp-48);
		align-items: center;
		margin-bottom: var(--sp-48);
	}

	.art {
		--size: 300px;
		position: relative;
		width: calc(var(--size) * 1.42);
		height: var(--size);
	}

	.cover {
		position: absolute;
		inset: 0 auto 0 0;
		width: var(--size);
		z-index: 1;
	}

	.vinyl {
		position: absolute;
		top: 4%;
		left: calc(var(--size) * 0.46);
		width: calc(var(--size) * 0.92);
		transition: left 600ms var(--motion-ease);
	}

	.art:hover .vinyl,
	.art.spinning .vinyl {
		left: calc(var(--size) * 0.5);
	}

	.info {
		display: grid;
		gap: var(--sp-8);
		align-content: center;
		min-width: 0;
	}

	.series {
		color: var(--c-accent);
	}

	h1 {
		font-size: var(--fs-56);
		line-height: 0.95;
	}

	.subtitle {
		font-size: var(--fs-18);
	}

	.people {
		font-size: var(--fs-18);
		font-weight: var(--fw-semibold);
	}

	.info a:hover {
		text-decoration: underline;
		text-underline-offset: 3px;
	}

	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: var(--sp-8);
		margin-top: var(--sp-8);
	}

	.progress {
		display: grid;
		gap: var(--sp-8);
		max-width: 420px;
		margin-top: var(--sp-12);
	}

	.progress .muted {
		font-size: var(--fs-13);
	}

	.bar {
		height: 6px;
		border-radius: var(--r-pill);
		background: linear-gradient(to right, var(--c-accent) calc(var(--p) * 100%), var(--c-track) 0);
	}

	.cta {
		display: flex;
		flex-wrap: wrap;
		gap: var(--sp-12);
		margin-top: var(--sp-12);
	}

	.big {
		min-height: 52px;
		padding: 0 var(--sp-24);
		font-size: var(--fs-15);
	}

	.lower {
		display: grid;
		grid-template-columns: minmax(0, 1fr) 320px;
		gap: var(--sp-48);
		align-items: start;
	}

	.tabs {
		display: flex;
		gap: var(--sp-24);
		border-bottom: 1px solid var(--c-line);
		margin-bottom: var(--sp-16);
		overflow-x: auto;
	}

	.tabs button {
		min-height: var(--layout-touch);
		padding: 0;
		border: 0;
		border-bottom: 2px solid transparent;
		background: transparent;
		color: var(--c-muted);
		font-weight: var(--fw-bold);
		white-space: nowrap;
	}

	.tabs button[aria-selected='true'] {
		color: var(--c-ink);
		border-color: var(--c-accent);
	}

	.rows {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: 2px;
	}

	.rows button {
		width: 100%;
		display: grid;
		grid-template-columns: 2.5em 1fr auto;
		align-items: center;
		gap: var(--sp-12);
		min-height: var(--layout-touch);
		padding: 0 var(--sp-12);
		border: 0;
		border-radius: var(--r-card);
		background: transparent;
		text-align: left;
	}

	.rows button:hover {
		background: var(--c-card);
	}

	.rows button.current {
		background: var(--c-cur-bg);
		color: var(--c-cur-ink);
	}

	.rows button.current .muted {
		color: inherit;
	}

	.num {
		font-size: var(--fs-18);
	}

	.ttl {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.pin {
		color: var(--c-accent);
		font-weight: var(--fw-bold);
		font-size: var(--fs-13);
	}

	.empty {
		padding: var(--sp-16) var(--sp-12);
	}

	.shelf {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
		row-gap: var(--sp-24);
		margin: 0 calc(-1 * var(--sp-12));
	}

	.facts {
		display: grid;
		gap: var(--sp-12);
		padding: var(--sp-24);
		border-radius: var(--r-card-lg);
		background: var(--c-card);
	}

	.facts h2 + * {
		margin-bottom: var(--sp-8);
	}

	.desc {
		font-size: var(--fs-14);
		white-space: pre-line;
	}

	dl {
		display: grid;
		grid-template-columns: auto 1fr;
		gap: var(--sp-8) var(--sp-16);
		margin: 0;
		font-size: var(--fs-14);
	}

	dt {
		color: var(--c-muted);
	}

	dd {
		margin: 0;
		overflow-wrap: anywhere;
	}

	@media (max-width: 1100px) {
		.lower {
			grid-template-columns: 1fr;
		}
	}

	@media (max-width: 800px) {
		.hero {
			grid-template-columns: 1fr;
			gap: var(--sp-24);
			margin-bottom: var(--sp-32);
		}

		.art {
			--size: min(62vw, 280px);
			justify-self: center;
		}

		h1 {
			font-size: var(--fs-34);
		}

		.big {
			flex: 1;
		}
	}
</style>
