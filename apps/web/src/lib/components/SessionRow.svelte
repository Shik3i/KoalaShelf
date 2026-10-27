<script lang="ts" module>
	import type { ListeningSession } from '@koalashelf/abs-client';

	/** "Windows · Edge" for browsers, "Google Pixel 8" for the mobile apps. */
	export function deviceLabel(d: ListeningSession['deviceInfo']) {
		if (!d) return '';
		if (d.osName && d.browserName) return `${d.osName} · ${d.browserName}`;
		if (d.model) return [d.manufacturer, d.model].filter(Boolean).join(' ');
		return d.clientName ?? '';
	}

	/** Sessions that were opened but barely played are noise in a history. */
	export const meaningful = (s: ListeningSession) =>
		(s?.timeListening ?? 0) >= 5 || Math.abs((s?.currentTime ?? 0) - (s?.startTime ?? 0)) >= 5;
</script>

<script lang="ts">
	import type { LibraryItem } from '@koalashelf/abs-client';
	import { player } from '#lib/player/player.svelte.ts';
	import { view } from '#lib/items.ts';
	import { clock, span } from '#lib/format.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Cover from './Cover.svelte';
	import Play from '@lucide/svelte/icons/play';

	interface Props {
		s: ListeningSession;
		/** The book (for its cover); omitted on the book's own page. */
		item?: LibraryItem | null;
		/** Show cover and title (history page) or only the session (book page). */
		withBook?: boolean;
		/** The book is gone from the library: nothing to jump to. */
		missing?: boolean;
	}

	let { s, item = null, withBook = false, missing = false }: Props = $props();

	const t = $derived(i18n.m.history);
	const cover = $derived(item ? view(item).cover : null);
	const safeStartedAt = $derived(
		typeof s?.startedAt === 'number'
			? s.startedAt
			: typeof s?.startedAt === 'string'
				? Date.parse(s.startedAt) || Date.now()
				: Date.now()
	);
	const time = $derived.by(() => {
		try {
			return new Intl.DateTimeFormat(i18n.locale, {
				hour: 'numeric',
				minute: '2-digit'
			}).format(safeStartedAt);
		} catch {
			return '';
		}
	});
	const device = $derived(deviceLabel(s?.deviceInfo));
	const at = (sec: number) => (s?.duration ? Math.min(1, Math.max(0, sec / s.duration)) : 0);
	const [lo, hi] = $derived([s?.startTime ?? 0, s?.currentTime ?? 0].sort((a, b) => a - b));
</script>

<li class="session" class:with-book={withBook}>
	{#if withBook}
		<a class="cover" href="/book/{s.libraryItemId}" tabindex="-1" aria-hidden="true">
			<Cover title={s.displayTitle} author={s.displayAuthor} src={cover} sizes="56px" />
		</a>
	{/if}
	<div class="body">
		{#if withBook}
			<a class="title" href="/book/{s.libraryItemId}">{s.displayTitle}</a>
			<span class="muted author">{s.displayAuthor}</span>
		{/if}
		<span class="meta muted tabular">
			{time} · {t.listened(span(s.timeListening))}{device ? ` · ${device}` : ''}
		</span>
		<!-- Where in the book this session took place. -->
		<div class="span" style:--a={at(lo)} style:--b={at(hi)} aria-hidden="true">
			<span></span>
		</div>
		{#if missing}
			<span class="muted note">{t.removed}</span>
		{:else}
			<div class="jumps">
				<button
					class="jump"
					onclick={() => player.play(s.libraryItemId, s.startTime)}
					aria-label="{t.sessionStart}: {t.playFrom(clock(s.startTime))}"
					title={t.sessionStart}
				>
					<Play size={12} fill="currentColor" />{clock(s.startTime)}
				</button>
				<span class="arrow" aria-hidden="true">→</span>
				<button
					class="jump"
					onclick={() => player.play(s.libraryItemId, s.currentTime)}
					aria-label="{t.sessionEnd}: {t.playFrom(clock(s.currentTime))}"
					title={t.sessionEnd}
				>
					<Play size={12} fill="currentColor" />{clock(s.currentTime)}
				</button>
			</div>
		{/if}
	</div>
</li>

<style>
	.session {
		display: grid;
		grid-template-columns: 1fr;
		gap: var(--sp-16);
		padding: var(--sp-12) var(--sp-16);
		border-radius: var(--r-card);
		background: var(--c-card);
	}

	.session.with-book {
		grid-template-columns: 56px 1fr;
	}

	.cover {
		align-self: start;
		width: 56px;
		border-radius: 8px;
		overflow: hidden;
	}

	.body {
		display: grid;
		gap: var(--sp-4);
		min-width: 0;
	}

	.title {
		font-weight: var(--fw-bold);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.title:hover {
		text-decoration: underline;
		text-underline-offset: 3px;
	}

	.author,
	.meta,
	.note {
		font-size: var(--fs-13);
	}

	.author {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.span {
		position: relative;
		height: 4px;
		margin: var(--sp-4) 0;
		border-radius: 2px;
		background: var(--c-line);
	}

	.span span {
		position: absolute;
		inset: 0 auto 0 calc(var(--a) * 100%);
		/* At least a visible sliver, even for a short session in a long book. */
		width: max(4px, calc((var(--b) - var(--a)) * 100%));
		border-radius: 2px;
		background: var(--c-accent);
	}

	.jumps {
		display: flex;
		align-items: center;
		gap: 6px;
	}

	.jump {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		min-height: 36px;
		padding: 0 10px;
		border: 0;
		border-radius: var(--r-pill);
		background: var(--c-bg);
		color: var(--c-ink);
		font-weight: var(--fw-bold);
		font-size: var(--fs-13);
		font-variant-numeric: tabular-nums;
		position: relative;
	}

	/* 44px touch target around the compact pill. */
	.jump::after {
		content: '';
		position: absolute;
		inset: -4px 0;
	}

	.jump:hover {
		background: color-mix(in oklab, var(--c-accent) 20%, var(--c-bg));
	}

	.arrow {
		color: var(--c-muted);
	}
</style>
