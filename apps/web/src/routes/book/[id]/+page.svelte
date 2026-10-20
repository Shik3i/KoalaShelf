<script lang="ts">
	import { byId, unique } from '#lib/unique.ts';
	import {
		encodeFilter,
		type Bookmark,
		type LibraryItemMinified,
		type ListeningSession
	} from '@koalashelf/abs-client';
	import { queue } from '#lib/queue.svelte.ts';
	import SessionRow, { meaningful } from '#lib/components/SessionRow.svelte';
	import ListPlus from '@lucide/svelte/icons/list-plus';
	import ListChecks from '@lucide/svelte/icons/list-checks';
	import FileArchive from '@lucide/svelte/icons/file-archive';
	import ListMusic from '@lucide/svelte/icons/list-music';
	import ListPicker from '#lib/components/ListPicker.svelte';
	import MoreHorizontal from '@lucide/svelte/icons/ellipsis';
	import Library from '@lucide/svelte/icons/library';
	import RotateCcw from '@lucide/svelte/icons/rotate-ccw';
	import CheckCheck from '@lucide/svelte/icons/check-check';
	import { isTauri } from '#lib/platform.ts';
	import { session } from '#lib/session.svelte.ts';
	import { progress } from '#lib/progress.svelte.ts';
	import { player } from '#lib/player/player.svelte.ts';
	import { view } from '#lib/items.ts';
	import { bytes, clock, duration, htmlToText, parseClock } from '#lib/format.ts';
	import { page } from '$app/state';
	import { toasts } from '#lib/toast.svelte.ts';
	import { copyMomentLink, shareBook } from '#lib/share.ts';
	import Link from '@lucide/svelte/icons/link';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import LinerNotesSleeve from '#lib/components/LinerNotesSleeve.svelte';
	import Vinyl from '#lib/components/Vinyl.svelte';
	import BookTile from '#lib/components/BookTile.svelte';
	import Check from '@lucide/svelte/icons/check';
	import DownloadButton from '#lib/components/DownloadButton.svelte';
	import PlayPauseIcon from '#lib/components/PlayPauseIcon.svelte';
	import { downloads } from '#lib/downloads.svelte.ts';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	const t = $derived(i18n.m.book);
	const item = $derived(data.item);
	const v = $derived(item ? view(item) : null);

	// Offline: the cover comes from the download instead of the server.
	let localCover = $state<string | null>(null);
	$effect(() => {
		const id = item?.id;
		if (!data.offline || !id) return;
		let url: string | null = null;
		downloads.coverUrl(id).then((u) => (localCover = url = u));
		return () => {
			if (url) URL.revokeObjectURL(url);
		};
	});
	const coverSrc = $derived(
		data.offline ? (localCover ? () => localCover! : null) : (v?.cover ?? null)
	);
	const meta = $derived(item?.media.metadata);
	const p = $derived(item ? progress.get(item.id) : undefined);
	const effectiveProgress = $derived(p ?? item?.userMediaProgress);
	const isFinished = $derived(effectiveProgress?.isFinished ?? false);
	const isCurrent = $derived(!!item && player.item?.id === item.id && !isFinished);
	const position = $derived(isCurrent ? player.currentTime : (effectiveProgress?.currentTime ?? 0));
	const pct = $derived(
		item ? (isFinished ? 1 : isCurrent ? player.progress : (effectiveProgress?.progress ?? 0)) : 0
	);
	const marks = $derived(
		item ? item.media.chapters.slice(1).map((c) => c.start / item.media.duration) : []
	);

	type Tab = 'chapters' | 'bookmarks' | 'history' | 'files' | 'more';
	let tab = $state<Tab>('chapters');
	let tabsEl = $state<HTMLDivElement>();
	let ink = $state({ x: 0, w: 0 });

	// The underline glides to the selected tab (measured, tabs have different widths).
	$effect(() => {
		void tab;
		void i18n.locale;
		const el = tabsEl?.querySelector<HTMLElement>('[aria-selected="true"]');
		if (el) ink = { x: el.offsetLeft, w: el.offsetWidth };
	});
	let descOpen = $state(false);
	const description = $derived(htmlToText(meta?.description));

	let ownBookmarks = $state.raw<Bookmark[]>([]);
	const bookmarks = $derived(isCurrent ? player.bookmarks : ownBookmarks);
	let more = $state.raw<LibraryItemMinified[]>([]);

	$effect(() => {
		const it = item;
		if (!it) return;
		if (it.userMediaProgress) progress.merge(it.userMediaProgress);
		void progress.load();
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

	/** This book's listening sessions, loaded when the tab is opened. */
	let history = $state.raw<ListeningSession[] | null>(null);
	$effect(() => {
		const id = item?.id;
		if (tab !== 'history' || !id) return;
		history = null;
		session.api
			.itemListeningSessions(id, 0, 30)
			.then((r) => (history = r.sessions.filter(meaningful)))
			.catch(() => (history = []));
	});

	let picker = $state<'playlist' | 'collection' | null>(null);
	let moreOpen = $state(false);

	/** Forget the progress (as if never started); undo writes the old state back. */
	async function resetProgress() {
		if (!item || !p) return;
		const progressId = p.id;
		const before = { ...p };
		const beforeUpdate = {
			currentTime: p.currentTime,
			progress: p.progress,
			isFinished: p.isFinished
		};
		if (isCurrent) player.pause();
		if (item.userMediaProgress) item.userMediaProgress = undefined;
		progress.remove(item.id);
		await session.api.removeProgress(progressId);
		if (isCurrent) player.seek(0);
		await progress.load(true);
		toasts.show(t.progressReset, {
			label: i18n.m.toast.undo,
			run: () => {
				if (item) item.userMediaProgress = before;
				void session.api
					.updateProgress(item.id, beforeUpdate)
					.then(() => progress.load(true))
					.then(() => isCurrent && player.seek(before.currentTime));
			}
		});
	}

	/** Starting a series in the middle: the volumes before this one count as heard. */
	async function finishEarlier() {
		const s = meta?.series[0];
		if (!item || !s) return;
		const { results } = await session.api.libraryItems(item.libraryId, {
			filter: encodeFilter('series', s.id),
			sort: 'sequence',
			limit: 0
		});
		const before = results.slice(
			0,
			results.findIndex((r) => r.id === item.id)
		);
		const open = before.filter((r) => !progress.get(r.id)?.isFinished);
		if (open.length)
			await session.api.updateProgressMany(
				open.map((r) => ({ libraryItemId: r.id, isFinished: true }))
			);
		await progress.load(true);
		toasts.show(i18n.m.select.finished(open.length));
	}

	const sequence = $derived(Number.parseFloat(meta?.series[0]?.sequence ?? ''));

	function toggleQueue() {
		if (!item) return;
		if (queue.has(item.id)) return queue.remove(item.id);
		queue.add(item);
		toasts.show(i18n.m.toast.queued(item.media.metadata.title), {
			label: i18n.m.toast.undo,
			run: () => queue.remove(item.id)
		});
	}

	/** The book's files to keep (ZIP of its folder): needs the download permission and a connection. */
	const canSaveFiles = $derived(!!item && !data.offline && !isTauri && session.canDownload);

	async function saveFiles() {
		if (!item) return;
		await session.api.ensureFresh();
		// The server answers with an attachment: the browser saves it without leaving the page.
		const a = document.createElement('a');
		a.href = session.api.downloadUrl(item.id);
		a.download = '';
		a.rel = 'noopener';
		document.body.append(a);
		a.click();
		a.remove();
		toasts.show(t.saveStarted);
	}

	/** A shared link to a moment (?t=754 or ?t=12:34) starts playback there. */
	const linkedTime = $derived(parseClock(page.url.searchParams.get('t')));

	function playAt(time?: number) {
		if (!item) return;
		if (time === undefined && linkedTime !== null && !(isCurrent && player.playing)) {
			return void player.play(item.id, linkedTime);
		}
		if (isCurrent && time === undefined) return player.toggle();
		void player.play(item.id, time);
	}

	async function toggleFinished() {
		if (!item) return;
		const done = !p?.isFinished;
		const cur = progress.get(item.id);
		if (cur) {
			cur.isFinished = done;
			if (!done) {
				cur.currentTime = 0;
				cur.progress = 0;
			}
		}
		if (item.userMediaProgress) {
			item.userMediaProgress.isFinished = done;
			if (!done) {
				item.userMediaProgress.currentTime = 0;
				item.userMediaProgress.progress = 0;
			}
		}
		await session.api.updateProgress(item.id, {
			isFinished: done,
			...(done ? {} : { currentTime: 0, progress: 0 })
		});
		toasts.show(done ? i18n.m.toast.finished : i18n.m.toast.unfinished);
		await progress.load(true);
		if (done) progress.celebrate(item.id);
	}

	const formats = $derived(
		item
			? [
					...new Set(
						item.media.audioFiles.map(
							(f) =>
								// File type and codec read better than the container name ("QuickTime / MOV").
								[
									...new Set([
										f.metadata.ext.slice(1).toUpperCase(),
										(f.codec || f.format).toUpperCase()
									])
								].join(' · ') + ` · ${Math.round(f.bitRate / 1000)} kbit/s`
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
						src={coverSrc}
						playing={isCurrent && player.playing}
						speed={player.rate}
						gold={!!p?.isFinished}
						shine={progress.shine(item.id)}
						{marks}
					/>
				</div>
				<div class="cover" style:view-transition-name="cover-{item.id}">
					<LinerNotesSleeve
						title={v.title}
						author={v.author}
						src={coverSrc}
						{description}
						chapters={item.media.chapters}
						narrators={meta.narrators}
						publisher={meta.publisher}
						year={meta.publishedYear}
						catalogId={item.id}
						durationSec={item.media.duration}
						format={formats[0] ?? ''}
						onPlayChapter={(start) => playAt(start)}
					/>
				</div>
			</div>

			<div class="info">
				{#if meta.series[0]}
					<a class="label series" href="/series/{meta.series[0].id}"
						>{t.seriesBook(meta.series[0].name, meta.series[0].sequence)}</a
					>
				{/if}
				<h1>{v.title}</h1>
				{#if meta.subtitle}<p class="subtitle">{meta.subtitle}</p>{/if}
				<p class="people">
					{#each unique(meta.authors, byId) as a, i (a.id)}{i ? ', ' : ''}<a href="/author/{a.id}"
							>{a.name}</a
						>{/each}
				</p>
				{#if meta.narrators.length}
					<p class="muted">
						{t.readBy}
						{#each unique(meta.narrators) as n, i (n)}{i ? ', ' : ''}<a
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
						{Math.round(pct * 100)} % · {p?.isFinished && !isCurrent
							? i18n.m.library.done
							: t.remaining(duration(Math.max(0, item.media.duration - position)))}
					</span>
				</div>

				{#if !item.media.audioFiles.length}<p class="muted">{t.noAudio}</p>{/if}
				<div class="cta">
					<button
						disabled={!item.media.audioFiles.length}
						class="btn primary big"
						class:is-loading={player.loadingId === item.id}
						onclick={() => playAt()}
					>
						<PlayPauseIcon playing={isCurrent && player.playing} size={18} />
						{#if isCurrent && player.playing}
							{i18n.m.player.pause}
						{:else}
							{linkedTime !== null
								? t.playFrom(clock(linkedTime))
								: p?.isFinished
									? t.listenAgain
									: position > 5
										? t.resume(clock(position))
										: t.play}
						{/if}
					</button>
					{#if !data.offline}
						<button class="btn ghost" onclick={toggleFinished}>
							<Check size={16} />
							{p?.isFinished ? t.markUnfinished : t.markFinished}
						</button>
					{/if}
					<DownloadButton id={item.id} hasAudio={item.media.audioFiles.length > 0} />
					{#if item.media.audioFiles.length && !isCurrent}
						{@const queued = queue.has(item.id)}
						<button class="btn ghost queue" class:queued onclick={toggleQueue}>
							{#key queued}
								<span class="qi">
									{#if queued}<ListChecks size={16} />{:else}<ListPlus size={16} />{/if}
								</span>
							{/key}
							{queued ? t.inQueue : t.addToQueue}
						</button>
					{/if}
					{#if item.media.audioFiles.length && !data.offline}
						<button class="btn ghost" onclick={() => (picker = 'playlist')}>
							<ListMusic size={16} aria-hidden="true" />
							{t.playlist}
						</button>
					{/if}
					{#if !data.offline}
						<button
							class="btn ghost"
							aria-expanded={moreOpen}
							aria-controls="book-more"
							onclick={() => (moreOpen = !moreOpen)}
						>
							<MoreHorizontal size={16} aria-hidden="true" />
							{t.moreActions}
						</button>
					{/if}
				</div>
				{#if moreOpen && !data.offline}
					<div class="more-actions" id="book-more">
						{#if session.canUpdate}
							<button class="btn ghost sm" onclick={() => (picker = 'collection')}>
								<Library size={16} aria-hidden="true" />
								{t.addToCollection}
							</button>
						{/if}
						{#if p && (p.progress > 0 || p.isFinished)}
							<button class="btn ghost sm" onclick={resetProgress}>
								<RotateCcw size={16} aria-hidden="true" />
								{t.resetProgress}
							</button>
						{/if}
						{#if sequence > 1}
							<button class="btn ghost sm" onclick={finishEarlier}>
								<CheckCheck size={16} aria-hidden="true" />
								{t.finishEarlier}
							</button>
						{/if}
						<button class="btn ghost sm" onclick={() => shareBook(item.id, v.title)}>
							<Link size={16} aria-hidden="true" />
							{t.copyLink}
						</button>
					</div>
				{/if}
			</div>
		</section>

		<div class="lower">
			<section class="tabs-wrap">
				<div
					class="tabs"
					role="tablist"
					bind:this={tabsEl}
					style:--ink-x="{ink.x}px"
					style:--ink-w="{ink.w}px"
				>
					<span class="ink" aria-hidden="true"></span>
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
					{#if !data.offline}
						<button role="tab" aria-selected={tab === 'history'} onclick={() => (tab = 'history')}
							>{t.history}</button
						>
					{/if}
					{#if item.media.audioFiles.length}
						<button role="tab" aria-selected={tab === 'files'} onclick={() => (tab = 'files')}
							>{t.filesTab}</button
						>
					{/if}
					{#if more.length}
						<button role="tab" aria-selected={tab === 'more'} onclick={() => (tab = 'more')}
							>{t.moreByAuthor}</button
						>
					{/if}
				</div>

				<div role="tabpanel">
					{#if tab === 'chapters' && !item.media.chapters.length}
						<p class="muted">{t.noChapters}</p>
					{:else if tab === 'chapters'}
						<ol class="rows">
							{#each item.media.chapters as c, i (i)}
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
							{#each unique(bookmarks, (b) => b.time) as b (b.time)}
								<li>
									<button onclick={() => playAt(b.time)}>
										<span class="pin tabular">{clock(b.time)}</span>
										<span class="ttl">{b.title}</span>
									</button>
									<button
										class="link-btn"
										onclick={() => copyMomentLink(item.id, b.time)}
										aria-label={i18n.m.player.copyLink}
										title={i18n.m.player.copyLink}
									>
										<Link size={16} />
									</button>
								</li>
							{:else}
								<li class="muted empty">{t.noBookmarks}</li>
							{/each}
						</ul>
					{:else if tab === 'history'}
						{#if history === null}
							<div class="skeleton" style="height: 110px; border-radius: 18px"></div>
						{:else}
							<ul class="sessions">
								{#each unique(history, byId) as s (s.id)}
									<SessionRow {s} />
								{:else}
									<li class="muted empty">{t.noHistory}</li>
								{/each}
							</ul>
						{/if}
					{:else if tab === 'files'}
						<ol class="rows files">
							{#each unique(item.media.audioFiles, (f) => f.ino).sort((a, b) => a.index - b.index) as f, i (f.ino)}
								<li>
									<span class="num display">{i + 1}</span>
									<span class="ttl">{f.metadata.filename}</span>
									<span class="muted tabular">{clock(f.duration)} · {bytes(f.metadata.size)}</span>
								</li>
							{/each}
						</ol>
					{:else}
						<div class="shelf">
							{#each unique(more, byId) as m (m.id)}<BookTile item={m} />{/each}
						</div>
					{/if}
				</div>
			</section>

			<aside class="facts">
				{#if meta.description}
					<h2 class="label">{t.description}</h2>
					<p class="desc" class:open={descOpen}>{description}</p>
					{#if description.length > 420}
						<button class="more" onclick={() => (descOpen = !descOpen)}>
							{descOpen ? t.showLess : t.showMore}
						</button>
					{/if}
				{/if}
				<h2 class="label">{t.details}</h2>
				<dl>
					<dt>{t.format}</dt>
					<dd>{formats.join(', ')} · {t.files(item.media.audioFiles.length)}</dd>
					<dt>{t.size}</dt>
					<dd>{bytes(item.media.size)}</dd>
					{#if meta.genres.length}<dt>{t.genre}</dt>
						<dd>
							{#each unique(meta.genres) as g, i (g)}{i ? ', ' : ''}<a
									href="/library?genre={encodeURIComponent(g)}">{g}</a
								>{/each}
						</dd>{/if}
					{#if item.media.tags?.length}<dt>{t.tags}</dt>
						<dd>
							{#each unique(item.media.tags) as g, i (g)}{i ? ', ' : ''}<a
									href="/library?tag={encodeURIComponent(g)}">{g}</a
								>{/each}
						</dd>{/if}
					{#if meta.publishedYear}<dt>{t.published}</dt>
						<dd>{meta.publishedYear}</dd>{/if}
					{#if meta.publisher}<dt>{t.publisher}</dt>
						<dd>
							<a href="/library?publisher={encodeURIComponent(meta.publisher)}">{meta.publisher}</a>
						</dd>{/if}
					{#if meta.language}<dt>{t.language}</dt>
						<dd>
							<a href="/library?language={encodeURIComponent(meta.language)}">{meta.language}</a>
						</dd>{/if}
					{#if meta.explicit || meta.abridged}<dt>{t.edition}</dt>
						<dd>
							{[meta.abridged ? t.abridged : '', meta.explicit ? t.explicit : '']
								.filter(Boolean)
								.join(' · ')}
						</dd>{/if}
					{#if meta.isbn}<dt>ISBN</dt>
						<dd class="tabular">{meta.isbn}</dd>{/if}
					{#if meta.asin}<dt>ASIN</dt>
						<dd class="tabular">{meta.asin}</dd>{/if}
					{#if item.addedAt}<dt>{t.added}</dt>
						<dd>{new Date(item.addedAt).toLocaleDateString(i18n.tag)}</dd>{/if}
					{#if session.library}<dt>{t.library}</dt>
						<dd>{session.library.name}</dd>{/if}
				</dl>
				{#if canSaveFiles}
					<button class="btn ghost save" onclick={saveFiles}>
						<FileArchive size={16} aria-hidden="true" />
						{item.isFile ? t.saveFile : t.saveZip}
					</button>
				{/if}
			</aside>
		</div>
		{#if picker}
			<ListPicker
				kind={picker}
				items={[item]}
				bind:open={() => picker !== null, (o) => !o && (picker = null)}
			/>
		{/if}
	</article>
{/if}

<style>
	.hero {
		display: grid;
		grid-template-columns: auto minmax(0, 1fr);
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
		/* Long audiobook titles: scale with the available width, never overflow. */
		font-size: clamp(var(--fs-34), 5.5cqi, var(--fs-56));
		line-height: 0.98;
		overflow-wrap: break-word;
		hyphens: auto;
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

	.queue.queued {
		box-shadow: inset 0 0 0 1.5px var(--c-accent);
	}

	/* The icon pops when the book joins or leaves the queue. */
	.qi {
		display: grid;
		animation: q-pop 360ms var(--motion-spring);
	}

	@keyframes q-pop {
		from {
			scale: 0.4;
			rotate: -20deg;
		}
	}

	.save {
		justify-self: start;
		margin-top: var(--sp-8);
	}

	.sessions {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--sp-8);
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
		position: relative;
		display: flex;
		gap: var(--sp-24);
		border-bottom: 1px solid var(--c-line);
		margin-bottom: var(--sp-16);
		overflow-x: auto;
		scrollbar-width: none;
		/* Hints that more tabs are reachable by scrolling on narrow screens. */
		mask-image: linear-gradient(to right, #000 calc(100% - 32px), transparent);
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
	}

	.ink {
		position: absolute;
		left: 0;
		bottom: 0;
		width: var(--ink-w);
		height: 2px;
		translate: var(--ink-x) 0;
		border-radius: 2px;
		background: var(--c-accent);
		transition:
			translate var(--motion-base) var(--motion-out),
			width var(--motion-base) var(--motion-out);
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

	.rows li {
		display: flex;
		align-items: center;
	}

	.rows li > button:first-child {
		flex: 1;
		min-width: 0;
	}

	.rows .link-btn {
		display: grid;
		place-items: center;
		width: var(--layout-touch);
		height: var(--layout-touch);
		flex: none;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: var(--c-muted);
	}

	.rows .link-btn:hover {
		color: var(--c-ink);
		background: var(--c-card);
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
		grid-template-columns: repeat(
			auto-fill,
			minmax(min(calc(140px * var(--tile-scale)), 42%), 1fr)
		);
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
		display: -webkit-box;
		-webkit-line-clamp: 10;
		line-clamp: 10;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}

	.desc.open {
		display: block;
	}

	.more {
		justify-self: start;
		padding: var(--sp-8) 0;
		border: 0;
		background: transparent;
		color: var(--c-accent);
		font-weight: var(--fw-bold);
		font-size: var(--fs-13);
	}

	.more-actions {
		display: flex;
		flex-wrap: wrap;
		gap: var(--sp-8);
		margin-top: var(--sp-12);
		animation: drop var(--motion-base) var(--motion-out);
	}

	@keyframes drop {
		from {
			translate: 0 -6px;
			opacity: 0;
		}
	}

	.btn.sm {
		min-height: 40px;
		font-size: var(--fs-13);
	}

	.files li {
		display: grid;
		grid-template-columns: 2em 1fr auto;
		align-items: center;
		gap: var(--sp-12);
		min-height: var(--layout-touch);
		padding: 0 var(--sp-12);
		font-size: var(--fs-14);
	}

	dd a {
		text-decoration: underline;
		text-decoration-color: color-mix(in oklab, currentColor 35%, transparent);
		text-underline-offset: 3px;
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

	@container page (max-width: 900px) {
		.lower {
			grid-template-columns: minmax(0, 1fr);
		}
	}

	@container page (max-width: 760px) {
		.hero {
			grid-template-columns: 1fr;
			gap: var(--sp-24);
			margin-bottom: var(--sp-32);
		}

		.art {
			--size: min(62cqi, 280px);
			justify-self: center;
		}

		.big {
			flex: 1;
		}
	}
</style>
