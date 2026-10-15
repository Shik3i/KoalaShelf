<script lang="ts">
	import { session } from '#lib/session.svelte.ts';
	import { progress } from '#lib/progress.svelte.ts';
	import { player } from '#lib/player/player.svelte.ts';
	import { queue } from '#lib/queue.svelte.ts';
	import { toasts } from '#lib/toast.svelte.ts';
	import { view } from '#lib/items.ts';
	import { duration, htmlToText } from '#lib/format.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Cover from '#lib/components/Cover.svelte';
	import EmptyState from '#lib/components/EmptyState.svelte';
	import PlayPauseIcon from '#lib/components/PlayPauseIcon.svelte';
	import Check from '@lucide/svelte/icons/check';
	import ListPlus from '@lucide/svelte/icons/list-plus';
	import EyeOff from '@lucide/svelte/icons/eye-off';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	const t = $derived(i18n.m.seriesPage);
	const series = $derived(data.series);
	const items = $derived(data.items);

	$effect(() => void progress.load());

	/** "Küstenlichter #2" → "2" (minified items carry the volume in the series name). */
	const volume = (seriesName: string) => seriesName.match(/#([\d.]+)\s*$/)?.[1] ?? '';

	const finished = $derived(items.filter((i) => progress.status(i.id) === 'done').length);
	const total = $derived(items.reduce((s, i) => s + i.media.duration, 0));
	/** The book to listen to next: one in progress, else the first unfinished one. */
	const next = $derived(
		items.find((i) => progress.status(i.id) === 'running') ??
			items.find((i) => progress.status(i.id) !== 'done')
	);
	const nextLabel = $derived(
		!next
			? t.again
			: progress.status(next.id) === 'running'
				? t.continue(view(next).title)
				: t.start(view(next).title)
	);
	const playing = $derived(!!next && player.item?.id === next.id && player.playing);

	function listen() {
		const target = next ?? items[0];
		if (!target) return;
		if (player.item?.id === target.id) player.toggle();
		else void player.play(target.id);
	}

	function queueRest() {
		const rest = items.filter(
			(i) => progress.status(i.id) !== 'done' && i.id !== player.item?.id && !queue.has(i.id)
		);
		for (const i of rest) queue.add(i);
		toasts.show(t.queued(rest.length));
	}

	async function hideFromContinue() {
		if (!series) return;
		const id = series.id;
		await session.api.setSeriesInContinue(id, false);
		toasts.show(t.hidden, {
			label: i18n.m.toast.undo,
			run: () => void session.api.setSeriesInContinue(id, true)
		});
	}
</script>

<svelte:head><title>{series?.name ?? t.notFound} · KoalaShelf</title></svelte:head>

{#if !series}
	<EmptyState text={t.notFound} />
{:else}
	<header>
		<div class="covers" aria-hidden="true">
			{#each items.slice(0, 3) as item, i (item.id)}
				{@const v = view(item)}
				<div class="c" style:--i={i}>
					<Cover title={v.title} author={v.author} src={v.cover} sizes="160px" eager />
				</div>
			{/each}
		</div>
		<div class="info">
			<span class="label">{t.label}</span>
			<h1>{series.name}</h1>
			<p class="muted">
				{i18n.m.authors.books(items.length)} · {duration(total)}
			</p>
			<div class="progress">
				<div class="bar" style:--p={items.length ? finished / items.length : 0}></div>
				<span class="muted tabular">{t.finishedOf(finished, items.length)}</span>
			</div>
			{#if series.description}<p class="desc">{htmlToText(series.description)}</p>{/if}
			<div class="cta">
				<button class="btn primary" onclick={listen} disabled={!items.length}>
					<PlayPauseIcon {playing} size={18} />
					{playing ? i18n.m.player.pause : nextLabel}
				</button>
				{#if items.some((i) => progress.status(i.id) !== 'done')}
					<button class="btn ghost" onclick={queueRest}>
						<ListPlus size={16} aria-hidden="true" />
						{t.queueAll}
					</button>
					<button class="btn ghost" onclick={hideFromContinue}>
						<EyeOff size={16} aria-hidden="true" />
						{t.hide}
					</button>
				{/if}
			</div>
		</div>
	</header>

	<ol class="books">
		{#each items as item (item.id)}
			{@const v = view(item)}
			{@const status = progress.status(item.id)}
			{@const p = progress.get(item.id)}
			<li>
				<a href="/book/{item.id}" class:current={item.id === next?.id}>
					<span class="vol display">{volume(item.media.metadata.seriesName) || '·'}</span>
					<span class="mini"
						><Cover title={v.title} author={v.author} src={v.cover} sizes="56px" /></span
					>
					<span class="meta">
						<strong>{v.title}</strong>
						<span class="muted">{v.author} · {duration(v.duration)}</span>
					</span>
					{#if status === 'done'}
						<span class="done" title={i18n.m.library.done}
							><Check size={16} aria-label={i18n.m.library.done} /></span
						>
					{:else if status === 'running'}
						<span class="pct tabular">{Math.round((p?.progress ?? 0) * 100)} %</span>
					{/if}
				</a>
			</li>
		{/each}
	</ol>
{/if}

<style>
	header {
		display: grid;
		grid-template-columns: minmax(0, 280px) 1fr;
		gap: var(--sp-40);
		align-items: center;
		margin-bottom: var(--sp-40);
	}

	/* The first three covers fanned out like records in a crate. */
	.covers {
		position: relative;
		aspect-ratio: 1.25;
	}

	.c {
		position: absolute;
		top: calc(var(--i) * 6%);
		left: calc(var(--i) * 18%);
		width: 64%;
		z-index: calc(3 - var(--i));
		box-shadow: 0 16px 30px -18px rgb(0 0 0 / 0.6);
		rotate: calc((var(--i) - 1) * 3deg);
	}

	.info {
		display: grid;
		gap: var(--sp-8);
		justify-items: start;
	}

	h1 {
		font-size: var(--fs-56);
	}

	.progress {
		display: grid;
		gap: var(--sp-4);
		width: min(100%, 420px);
	}

	.bar {
		height: 6px;
		border-radius: 3px;
		background: linear-gradient(to right, var(--c-accent) calc(var(--p) * 100%), var(--c-track) 0);
	}

	.progress span {
		font-size: var(--fs-13);
	}

	.desc {
		max-width: 68ch;
		line-height: 1.6;
	}

	.cta {
		display: flex;
		flex-wrap: wrap;
		gap: var(--sp-12);
		margin-top: var(--sp-8);
	}

	.books {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--sp-4);
		max-width: 860px;
	}

	.books a {
		display: grid;
		grid-template-columns: 2.5em 56px 1fr auto;
		align-items: center;
		gap: var(--sp-16);
		padding: var(--sp-8) var(--sp-12);
		border-radius: var(--r-card);
	}

	.books a:hover {
		background: var(--c-card);
	}

	.books a.current {
		background: var(--c-card);
		box-shadow: inset 3px 0 0 var(--c-accent);
	}

	.vol {
		font-size: var(--fs-24);
		text-align: center;
	}

	.meta {
		display: grid;
		min-width: 0;
	}

	.meta > * {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.meta .muted,
	.pct {
		font-size: var(--fs-13);
	}

	.done {
		display: grid;
		place-items: center;
		width: 28px;
		height: 28px;
		border-radius: 50%;
		background: var(--c-accent);
		color: var(--c-on-accent);
	}

	.pct {
		font-weight: var(--fw-bold);
		color: var(--c-accent);
	}

	@container page (max-width: 700px) {
		header {
			grid-template-columns: 1fr;
			gap: var(--sp-24);
		}

		.covers {
			width: min(280px, 80%);
		}

		h1 {
			font-size: var(--fs-34);
		}

		.books a {
			grid-template-columns: 2em 48px 1fr auto;
			gap: var(--sp-12);
		}
	}
</style>
