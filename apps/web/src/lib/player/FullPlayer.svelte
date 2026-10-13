<script lang="ts">
	import { player, RATES } from './player.svelte.ts';
	import { prefs } from '#lib/prefs.svelte.ts';
	import { view } from '#lib/items.ts';
	import { clock, speed } from '#lib/format.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Turntable from '#lib/components/Turntable.svelte';
	import Timeline from './Timeline.svelte';
	import RotateCcw from '@lucide/svelte/icons/rotate-ccw';
	import PlayPauseIcon from '#lib/components/PlayPauseIcon.svelte';
	import Segmented from '#lib/components/Segmented.svelte';
	import { dur, flyUp } from '#lib/motion.ts';
	import { toasts } from '#lib/toast.svelte.ts';
	import { copyMomentLink } from '#lib/share.ts';
	import { coverColor } from '#lib/cover-color.ts';
	import Toaster from '#lib/components/Toaster.svelte';
	import { fly } from 'svelte/transition';
	import Link from '@lucide/svelte/icons/link';
	import RotateCw from '@lucide/svelte/icons/rotate-cw';
	import SkipBack from '@lucide/svelte/icons/skip-back';
	import SkipForward from '@lucide/svelte/icons/skip-forward';
	import ChevronDown from '@lucide/svelte/icons/chevron-down';
	import Timer from '@lucide/svelte/icons/timer';
	import ListMusic from '@lucide/svelte/icons/list-music';
	import Bookmark from '@lucide/svelte/icons/bookmark';
	import Trash2 from '@lucide/svelte/icons/trash-2';
	import Pencil from '@lucide/svelte/icons/pencil';
	import ListOrdered from '@lucide/svelte/icons/list-ordered';
	import { scale } from 'svelte/transition';
	import SleepPanel from './SleepPanel.svelte';
	import QueuePanel from './QueuePanel.svelte';
	import { queue } from '#lib/queue.svelte.ts';

	type Panel = 'chapters' | 'bookmarks' | 'timer' | 'queue' | null;

	const t = $derived(i18n.m.player);
	const v = $derived(player.item ? view(player.item) : null);
	let panel = $state<Panel>(null);
	let dialog = $state<HTMLDialogElement>();
	let closing = $state(false);
	/** Tint taken from the cover for a soft background glow. */
	let glow = $state<string | null>(null);
	$effect(() => {
		const url = v?.cover?.(64);
		glow = null;
		if (url) coverColor(url).then((c) => (glow = c));
	});
	let note = $state('');
	let noteInput = $state<HTMLInputElement>();
	/** Click counters: re-keying the icon replays its little "jump" animation. */
	let backN = $state(0);
	let fwdN = $state(0);
	let bookmarkError = $state(false);
	/** Bookmark being renamed (its time) and the draft note. */
	let editing = $state<number | null>(null);
	let draft = $state('');

	async function saveEdit(e: SubmitEvent) {
		e.preventDefault();
		if (editing === null) return;
		await player.renameBookmark(editing, draft.trim() || clock(editing));
		editing = null;
	}

	// Live clock for the sleep timer countdown.
	let now = $state(Date.now());
	$effect(() => {
		if (player.sleep.kind !== 'minutes') return;
		const id = setInterval(() => (now = Date.now()), 1000);
		return () => clearInterval(id);
	});

	$effect(() => {
		if (!dialog) return;
		if (player.expanded && !dialog.open) {
			closing = false;
			dialog.showModal();
		}
		if (!player.expanded && dialog.open && !closing) {
			// Slide out first, then close for real.
			closing = true;
			const d = dialog;
			setTimeout(() => {
				if (!player.expanded) d.close();
				closing = false;
			}, dur(220));
		}
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
		if (s.kind === 'minutes')
			return t.timerLeft(clock((player.sleepPausedLeft ?? s.endsAt - now) / 1000));
		if (s.kind === 'window' && player.sleepForecast)
			return t.timerSmartLeft(clock(player.sleepForecast.seconds));
		return t.timer;
	});

	/** Delete with an undo: the bookmark is recreated with the same note and time. */
	async function removeBookmark(time: number, title: string) {
		await player.deleteBookmark(time);
		toasts.show(i18n.m.toast.bookmarkDeleted, {
			label: i18n.m.toast.undo,
			run: () => void player.addBookmark(title, time)
		});
	}

	async function saveBookmark(e: SubmitEvent) {
		e.preventDefault();
		bookmarkError = false;
		try {
			await player.addBookmark(note.trim() || clock(player.currentTime));
			note = '';
			toasts.show(i18n.m.toast.bookmarkSaved);
		} catch {
			bookmarkError = true;
		}
	}
</script>

<dialog
	bind:this={dialog}
	class:closing
	style:--glow={glow ?? 'transparent'}
	onclose={() => (player.expanded = false)}
	oncancel={(e) => {
		// Escape: animate out instead of the browser closing instantly.
		e.preventDefault();
		player.expanded = false;
	}}
	aria-label={t.player}
>
	{#if v && player.item}
		<div class="sheet">
			<header>
				<button class="icon" onclick={() => (player.expanded = false)} aria-label={t.close}>
					<ChevronDown size={26} />
				</button>
				<span class="label chapter-label">
					{#key player.chapterIndex}
						<span in:fly={flyUp(8)}
							>{player.chapter
								? t.chapterOf(player.chapterIndex + 1, player.chapters.length)
								: ''}</span
						>
					{/key}
				</span>
				<button
					class="icon queue-btn"
					aria-expanded={panel === 'queue'}
					onclick={() => toggle('queue')}
					aria-label={t.queueOpen(queue.items.length)}
					title={t.queue}
				>
					<ListOrdered size={22} />
					{#if queue.items.length}
						{#key queue.items.length}
							<span class="badge tabular" in:scale={{ start: 0.4, duration: dur(260) }}
								>{queue.items.length}</span
							>
						{/key}
					{/if}
				</button>
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
							{v.narrator ? `${v.author} · ${v.narrator}` : v.author}
						</p>
					</div>

					<div class="time">
						<button
							class="big tabular display"
							title={t.timeMode}
							aria-label={t.timeMode}
							onclick={() => prefs.set('remainingTime', !prefs.remainingTime)}
						>
							{prefs.remainingTime
								? `−${clock((player.duration - player.currentTime) / player.rate)}`
								: clock(player.currentTime)}
						</button>
						<span class="muted tabular">
							{player.chapter
								? t.remainingChapter(clock((player.chapter.end - player.currentTime) / player.rate))
								: t.remainingBook(clock((player.duration - player.currentTime) / player.rate))}
						</span>
					</div>

					<Timeline />

					<div class="transport">
						<button class="ctl" onclick={() => player.prevChapter()} aria-label={t.prevChapter}
							><SkipBack size={22} /></button
						>
						<button
							class="ctl"
							onclick={() => (player.back(), backN++)}
							aria-label={t.back(prefs.skipBack)}
						>
							{#key backN}<span class="jump back" class:go={backN > 0}><RotateCcw size={26} /></span
								>{/key}
							<span class="n">{prefs.skipBack}</span>
						</button>
						<button
							class="ctl play"
							class:is-loading={player.loadingId !== null}
							onclick={() => player.toggle()}
							aria-label={player.playing ? t.pause : t.play}
						>
							<PlayPauseIcon playing={player.playing} size={30} />
						</button>
						<button
							class="ctl"
							onclick={() => (player.forward(), fwdN++)}
							aria-label={t.forward(prefs.skipForward)}
						>
							{#key fwdN}<span class="jump fwd" class:go={fwdN > 0}><RotateCw size={26} /></span
								>{/key}
							<span class="n">{prefs.skipForward}</span>
						</button>
						<button class="ctl" onclick={() => player.nextChapter()} aria-label={t.nextChapter}
							><SkipForward size={22} /></button
						>
					</div>

					<Segmented
						label={t.speed}
						value={player.rate}
						options={RATES.map((r) => ({ value: r, label: speed(r) }))}
						onchange={(r) => player.setRate(r)}
					/>

					<div class="actions">
						<button
							class="action"
							class:on={player.sleep.kind !== 'off'}
							aria-expanded={panel === 'timer'}
							onclick={() => toggle('timer')}
						>
							<Timer size={20} /><span>{timerLabel}</span>
						</button>
						<button
							class="action"
							aria-expanded={panel === 'chapters'}
							onclick={() => toggle('chapters')}
						>
							<ListMusic size={20} /><span>{t.chapters}</span>
						</button>
						<button
							class="action"
							aria-expanded={panel === 'bookmarks'}
							onclick={() => toggle('bookmarks')}
						>
							<Bookmark size={20} /><span>{t.bookmarks}</span>
						</button>
					</div>
				</div>

				{#if panel}
					<aside class="panel">
						{#if panel === 'timer'}
							<SleepPanel />
						{:else if panel === 'queue'}
							<QueuePanel />
						{:else if panel === 'chapters'}
							<h3 class="label">{t.chapters}</h3>
							<ol class="list">
								{#each player.chapters as c, i (c.id)}
									<li>
										<button
											aria-current={player.chapter === c}
											onclick={() => player.seek(c.start)}
										>
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
								<button class="btn primary" type="submit"
									>{t.addBookmark} · {clock(player.currentTime)}</button
								>
							</form>
							{#if bookmarkError}<p class="err">{t.bookmarkFailed}</p>{/if}
							<ul class="list">
								{#each player.bookmarks as b (b.time)}
									<li>
										{#if editing === b.time}
											<form class="edit" onsubmit={saveEdit}>
												<span class="tabular pin">{clock(b.time)}</span>
												<!-- svelte-ignore a11y_autofocus -->
												<input
													bind:value={draft}
													aria-label={t.bookmarkTitle}
													maxlength="200"
													autofocus
													onkeydown={(e) =>
														e.key === 'Escape' && (e.stopPropagation(), (editing = null))}
												/>
												<button class="btn primary sm" type="submit">{t.save}</button>
											</form>
										{:else}
											<button onclick={() => player.seek(b.time)}>
												<span class="tabular pin">{clock(b.time)}</span>
												<span class="ttl">{b.title}</span>
											</button>
											<button
												class="icon"
												onclick={() => ((editing = b.time), (draft = b.title))}
												aria-label={t.edit}
											>
												<Pencil size={16} />
											</button>
											<button
												class="icon"
												onclick={() => player.item && copyMomentLink(player.item.id, b.time)}
												aria-label={t.copyLink}
												title={t.copyLink}
											>
												<Link size={16} />
											</button>
										{/if}
										<button
											class="icon"
											onclick={() => removeBookmark(b.time, b.title)}
											aria-label={t.delete}
										>
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
			{#if player.error}
				<p class="err" role="status">{player.error === 'fatal' ? t.errorFatal : t.error}</p>
			{/if}
			<p class="keys muted">{t.shortcuts}</p>
			<Toaster />
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
		background:
			radial-gradient(
				110% 70% at 15% 0%,
				color-mix(in oklab, var(--glow) 28%, transparent),
				transparent 70%
			),
			var(--c-bg);
		color: var(--c-ink);
		transition: background-color var(--motion-slow) var(--motion-ease);
	}

	dialog[open] {
		animation: rise var(--motion-slow) var(--motion-out);
	}

	dialog.closing {
		animation: sink 220ms var(--motion-ease) forwards;
	}

	@keyframes sink {
		to {
			translate: 0 8%;
			opacity: 0;
		}
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

	.queue-btn {
		position: relative;
	}

	.queue-btn[aria-expanded='true'] {
		background: var(--c-card);
	}

	.badge {
		position: absolute;
		top: 4px;
		right: 2px;
		min-width: 18px;
		height: 18px;
		padding: 0 5px;
		border-radius: var(--r-pill);
		background: var(--c-accent);
		color: var(--c-on-accent);
		font-size: 11px;
		font-weight: var(--fw-bold);
		line-height: 18px;
		text-align: center;
	}

	.icon {
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

	button.big {
		padding: 0;
		border: 0;
		background: none;
		color: inherit;
		cursor: pointer;
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

	.jump {
		display: grid;
	}

	/* Skip buttons spin a little in their direction. */
	.jump.back.go {
		animation: jump-back 460ms var(--motion-spring);
	}

	.jump.fwd.go {
		animation: jump-fwd 460ms var(--motion-spring);
	}

	@keyframes jump-back {
		40% {
			rotate: -55deg;
		}
	}

	@keyframes jump-fwd {
		40% {
			rotate: 55deg;
		}
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

	.controls > :global(.seg) {
		display: grid;
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

	.edit {
		flex: 1;
		display: flex;
		align-items: center;
		gap: var(--sp-8);
		padding-left: var(--sp-12);
	}

	.edit input {
		flex: 1;
		min-width: 0;
		min-height: 40px;
		padding: 0 var(--sp-12);
		border: 1.5px solid var(--c-accent);
		border-radius: var(--r-card);
		background: var(--c-bg);
	}

	.sm {
		min-height: 40px;
		padding: 0 var(--sp-16);
	}

	.err {
		text-align: center;
		color: var(--c-accent);
		font-weight: var(--fw-bold);
	}

	.chapter-label {
		display: grid;
	}

	.chapter-label > span {
		grid-area: 1 / 1;
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
			gap: var(--sp-16);
			padding: var(--sp-4) 0;
		}

		.controls {
			gap: var(--sp-12);
		}

		/* Keep transport, speed and actions on one phone screen. */
		.deck {
			width: min(100%, 360px, 34dvh);
			justify-self: center;
		}

		.meta h2 {
			font-size: var(--fs-24);
			display: -webkit-box;
			-webkit-line-clamp: 2;
			line-clamp: 2;
			-webkit-box-orient: vertical;
			overflow: hidden;
		}

		.meta p {
			margin-top: var(--sp-4);
		}

		.action {
			min-height: 56px;
		}

		.keys {
			display: none;
		}
	}
</style>
