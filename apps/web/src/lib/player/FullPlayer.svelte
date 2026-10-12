<script lang="ts">
	import { player, RATES } from './player.svelte.ts';
	import { view } from '#lib/items.ts';
	import { clock, speed } from '#lib/format.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Turntable from '#lib/components/Turntable.svelte';
	import Timeline from './Timeline.svelte';
	import Play from '@lucide/svelte/icons/play';
	import Pause from '@lucide/svelte/icons/pause';
	import RotateCcw from '@lucide/svelte/icons/rotate-ccw';
	import RotateCw from '@lucide/svelte/icons/rotate-cw';
	import SkipBack from '@lucide/svelte/icons/skip-back';
	import SkipForward from '@lucide/svelte/icons/skip-forward';
	import ChevronDown from '@lucide/svelte/icons/chevron-down';
	import Timer from '@lucide/svelte/icons/timer';
	import ListMusic from '@lucide/svelte/icons/list-music';
	import Bookmark from '@lucide/svelte/icons/bookmark';
	import Trash2 from '@lucide/svelte/icons/trash-2';

	type Panel = 'chapters' | 'bookmarks' | 'timer' | null;

	const t = $derived(i18n.m.player);
	const v = $derived(player.item ? view(player.item) : null);
	let panel = $state<Panel>(null);
	let dialog = $state<HTMLDialogElement>();
	let note = $state('');
	let noteInput = $state<HTMLInputElement>();

	// Live clock for the sleep timer countdown.
	let now = $state(Date.now());
	$effect(() => {
		if (player.sleep.kind !== 'minutes') return;
		const id = setInterval(() => (now = Date.now()), 1000);
		return () => clearInterval(id);
	});

	$effect(() => {
		if (!dialog) return;
		if (player.expanded && !dialog.open) dialog.showModal();
		if (!player.expanded && dialog.open) dialog.close();
	});

	export function openBookmarks() {
		player.expanded = true;
		panel = 'bookmarks';
		queueMicrotask(() => noteInput?.focus());
	}

	const toggle = (p: Panel) => (panel = panel === p ? null : p);

	const timerLabel = $derived.by(() => {
		const s = player.sleep;
		if (s.kind === 'chapter') return t.timerAtChapterEnd;
		if (s.kind === 'minutes') return t.timerLeft(clock((s.endsAt - now) / 1000));
		return t.timer;
	});

	async function saveBookmark(e: SubmitEvent) {
		e.preventDefault();
		await player.addBookmark(note.trim() || clock(player.currentTime));
		note = '';
	}
</script>

<dialog bind:this={dialog} onclose={() => (player.expanded = false)} aria-label={t.player}>
	{#if v && player.item}
		<div class="sheet">
			<header>
				<button class="icon" onclick={() => (player.expanded = false)} aria-label={t.close}>
					<ChevronDown size={26} />
				</button>
				<span class="label">{player.chapter ? t.chapterOf(player.chapterIndex + 1, player.chapters.length) : ''}</span>
				<span class="icon-spacer"></span>
			</header>

			<div class="body" class:with-panel={panel}>
				<div class="deck">
					<Turntable
						title={v.title}
						author={v.author}
						src={v.cover}
						playing={player.playing}
						progress={player.progress}
						marks={player.chapters.slice(1).map((c) => c.start / player.duration)}
					/>
				</div>

				<div class="controls">
					<div class="meta">
						<h2>{v.title}</h2>
						<p class="muted">
							{v.author}{#if v.narrator}<span> · {v.narrator}</span>{/if}
						</p>
					</div>

					<div class="time">
						<span class="big tabular display">{clock(player.currentTime)}</span>
						<span class="muted tabular">
							{player.chapter
								? t.remainingChapter(clock((player.chapter.end - player.currentTime) / player.rate))
								: t.remainingBook(clock((player.duration - player.currentTime) / player.rate))}
						</span>
					</div>

					<Timeline />

					<div class="transport">
						<button class="ctl" onclick={() => player.prevChapter()} aria-label={t.prevChapter}><SkipBack size={22} /></button>
						<button class="ctl" onclick={() => player.skip(-30)} aria-label={t.back30}>
							<RotateCcw size={26} /><span class="n">30</span>
						</button>
						<button class="ctl play" onclick={() => player.toggle()} aria-label={player.playing ? t.pause : t.play}>
							{#if player.playing}<Pause size={30} fill="currentColor" />{:else}<Play size={30} fill="currentColor" />{/if}
						</button>
						<button class="ctl" onclick={() => player.skip(30)} aria-label={t.forward30}>
							<RotateCw size={26} /><span class="n">30</span>
						</button>
						<button class="ctl" onclick={() => player.nextChapter()} aria-label={t.nextChapter}><SkipForward size={22} /></button>
					</div>

					<div class="rates" role="group" aria-label={t.speed}>
						{#each RATES as r (r)}
							<button aria-pressed={player.rate === r} onclick={() => player.setRate(r)}>{speed(r)}</button>
						{/each}
					</div>

					<div class="actions">
						<button class="action" class:on={player.sleep.kind !== 'off'} aria-expanded={panel === 'timer'} onclick={() => toggle('timer')}>
							<Timer size={20} /><span>{timerLabel}</span>
						</button>
						<button class="action" aria-expanded={panel === 'chapters'} onclick={() => toggle('chapters')}>
							<ListMusic size={20} /><span>{t.chapters}</span>
						</button>
						<button class="action" aria-expanded={panel === 'bookmarks'} onclick={() => toggle('bookmarks')}>
							<Bookmark size={20} /><span>{t.bookmarks}</span>
						</button>
					</div>
				</div>

				{#if panel}
					<aside class="panel">
						{#if panel === 'timer'}
							<h3 class="label">{t.timer}</h3>
							<div class="opts">
								<button class="chip" aria-pressed={player.sleep.kind === 'off'} onclick={() => player.setSleep({ kind: 'off' })}>{t.timerOff}</button>
								<button class="chip" aria-pressed={player.sleep.kind === 'chapter'} onclick={() => player.setSleep({ kind: 'chapter' })}>
									{t.timerEndOfChapter}
								</button>
								{#each [5, 10, 15, 30, 45, 60] as m (m)}
									<button
										class="chip"
										aria-pressed={player.sleep.kind === 'minutes' && player.sleep.minutes === m}
										onclick={() => player.setSleep({ kind: 'minutes', minutes: m, endsAt: Date.now() + m * 60_000 })}
									>
										{t.timerMinutes(m)}
									</button>
								{/each}
							</div>
						{:else if panel === 'chapters'}
							<h3 class="label">{t.chapters}</h3>
							<ol class="list">
								{#each player.chapters as c, i (c.id)}
									<li>
										<button aria-current={player.chapter === c} onclick={() => player.seek(c.start)}>
											<span class="num display">{i + 1}</span>
											<span class="ttl">{c.title}</span>
											<span class="tabular muted">{clock(c.end - c.start)}</span>
										</button>
									</li>
								{/each}
							</ol>
						{:else}
							<h3 class="label">{t.bookmarks}</h3>
							<form class="add" onsubmit={saveBookmark}>
								<input
									bind:this={noteInput}
									bind:value={note}
									placeholder={t.bookmarkPlaceholder}
									aria-label={t.bookmarkTitle}
									maxlength="200"
								/>
								<button class="btn primary" type="submit">{t.addBookmark} · {clock(player.currentTime)}</button>
							</form>
							<ul class="list">
								{#each player.bookmarks as b (b.time)}
									<li>
										<button onclick={() => player.seek(b.time)}>
											<span class="tabular pin">{clock(b.time)}</span>
											<span class="ttl">{b.title}</span>
										</button>
										<button class="icon" onclick={() => player.deleteBookmark(b.time)} aria-label={t.delete}>
											<Trash2 size={16} />
										</button>
									</li>
								{:else}
									<li class="muted empty">{i18n.m.book.noBookmarks}</li>
								{/each}
							</ul>
						{/if}
					</aside>
				{/if}
			</div>
			<p class="keys muted">{t.shortcuts}</p>
		</div>
	{/if}
</dialog>

<style>
	dialog {
		width: 100%;
		max-width: none;
		height: 100%;
		max-height: none;
		margin: 0;
		padding: 0;
		border: 0;
		background: var(--c-bg);
		color: var(--c-ink);
	}

	dialog[open] {
		animation: rise var(--motion-base) var(--motion-ease);
	}

	dialog::backdrop {
		background: transparent;
	}

	@keyframes rise {
		from {
			translate: 0 6%;
			opacity: 0;
		}
	}

	.sheet {
		min-height: 100%;
		display: grid;
		grid-template-rows: auto 1fr auto;
		padding: var(--sp-16) var(--sp-24) var(--sp-24);
	}

	header {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}

	.icon,
	.icon-spacer {
		display: grid;
		place-items: center;
		width: var(--layout-touch);
		height: var(--layout-touch);
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: var(--c-ink);
	}

	.body {
		display: grid;
		grid-template-columns: minmax(0, 440px) minmax(0, 420px);
		justify-content: center;
		align-items: center;
		gap: var(--sp-48);
		padding: var(--sp-24) 0;
	}

	.body.with-panel {
		grid-template-columns: minmax(0, 360px) minmax(0, 400px) minmax(0, 340px);
		gap: var(--sp-32);
	}

	.controls {
		display: grid;
		gap: var(--sp-20);
	}

	.meta h2 {
		font-size: var(--fs-34);
	}

	.meta p {
		margin-top: var(--sp-8);
	}

	.time {
		display: flex;
		align-items: baseline;
		justify-content: space-between;
		gap: var(--sp-12);
	}

	.big {
		font-size: var(--fs-time);
		line-height: 1;
	}

	.time .muted {
		font-size: var(--fs-13);
	}

	.transport {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}

	.ctl {
		position: relative;
		display: grid;
		place-items: center;
		width: 52px;
		height: 52px;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: var(--c-ink);
		transition: background var(--motion-fast) var(--motion-ease);
	}

	.ctl:hover {
		background: color-mix(in oklab, var(--c-ink) 8%, transparent);
	}

	.ctl .n {
		position: absolute;
		font-size: 9px;
		font-weight: var(--fw-bold);
		padding-top: 2px;
	}

	.play {
		width: 76px;
		height: 76px;
		background: var(--c-accent);
		color: var(--c-on-accent);
		box-shadow: 0 12px 24px -12px var(--c-accent);
	}

	.play:hover {
		background: var(--c-accent);
		filter: brightness(1.06);
	}

	.rates {
		display: flex;
		justify-content: space-between;
		padding: 4px;
		border-radius: var(--r-pill);
		background: var(--c-card);
	}

	.rates button {
		flex: 1;
		min-height: 40px;
		border: 0;
		border-radius: var(--r-pill);
		background: transparent;
		color: var(--c-muted);
		font-size: var(--fs-13);
		font-weight: var(--fw-bold);
	}

	.rates button[aria-pressed='true'] {
		background: var(--c-sel);
		color: var(--c-sel-ink);
	}

	.actions {
		display: grid;
		grid-template-columns: repeat(3, 1fr);
		gap: var(--sp-8);
	}

	.action {
		display: grid;
		justify-items: center;
		gap: var(--sp-4);
		min-height: 64px;
		align-content: center;
		padding: var(--sp-8);
		border: 0;
		border-radius: var(--r-card);
		background: var(--c-card);
		color: var(--c-ink);
		font-size: var(--fs-12);
		font-weight: var(--fw-semibold);
	}

	.action[aria-expanded='true'],
	.action.on {
		box-shadow: inset 0 0 0 2px var(--c-accent);
	}

	.action.on :global(svg) {
		color: var(--c-accent);
	}

	.panel {
		align-self: stretch;
		display: grid;
		align-content: start;
		gap: var(--sp-12);
		max-height: min(640px, 70dvh);
		overflow: auto;
		padding: var(--sp-16);
		border-radius: var(--r-card-lg);
		background: var(--c-card);
	}

	.opts {
		display: flex;
		flex-wrap: wrap;
		gap: var(--sp-8);
	}

	.opts .chip {
		background: var(--c-bg);
	}

	.opts .chip[aria-pressed='true'] {
		background: var(--c-accent);
	}

	.list {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: 2px;
	}

	.list li {
		display: flex;
		align-items: center;
	}

	.list li > button:first-child {
		flex: 1;
		min-width: 0;
		display: grid;
		grid-template-columns: auto 1fr auto;
		align-items: center;
		gap: var(--sp-12);
		min-height: var(--layout-touch);
		padding: 0 var(--sp-12);
		border: 0;
		border-radius: var(--r-card);
		background: transparent;
		color: inherit;
		text-align: left;
		font-size: var(--fs-14);
	}

	.list button[aria-current='true'] {
		background: var(--c-cur-bg);
		color: var(--c-cur-ink);
	}

	.list button[aria-current='true'] .muted {
		color: inherit;
	}

	.num {
		font-size: var(--fs-18);
		min-width: 1.5em;
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

	.add {
		display: grid;
		gap: var(--sp-8);
	}

	.add input {
		min-height: var(--layout-touch);
		padding: 0 var(--sp-16);
		border: 1.5px solid var(--c-line);
		border-radius: var(--r-card);
		background: var(--c-bg);
	}

	.empty {
		padding: var(--sp-12);
		font-size: var(--fs-13);
	}

	.keys {
		text-align: center;
		font-size: var(--fs-12);
	}

	@media (max-width: 1100px) {
		.body.with-panel {
			grid-template-columns: minmax(0, 400px) minmax(0, 360px);
		}

		.body.with-panel .deck {
			display: none;
		}
	}

	@media (max-width: 800px) {
		.sheet {
			padding: var(--sp-8) var(--sp-16) calc(var(--sp-16) + env(safe-area-inset-bottom));
		}

		.body,
		.body.with-panel {
			grid-template-columns: 1fr;
			align-content: start;
			gap: var(--sp-20);
			padding: var(--sp-8) 0;
		}

		.deck {
			width: min(100%, 360px);
			justify-self: center;
		}

		.meta h2 {
			font-size: var(--fs-24);
		}

		.keys {
			display: none;
		}
	}
</style>
