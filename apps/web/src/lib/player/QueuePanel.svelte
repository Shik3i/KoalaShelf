<script lang="ts">
	import { flip } from 'svelte/animate';
	import { fly, fade } from 'svelte/transition';
	import { player } from './player.svelte.ts';
	import { queue } from '#lib/queue.svelte.ts';
	import { view } from '#lib/items.ts';
	import { duration } from '#lib/format.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { toasts } from '#lib/toast.svelte.ts';
	import { dur, flyUp } from '#lib/motion.ts';
	import Cover from '#lib/components/Cover.svelte';
	import ChevronUp from '@lucide/svelte/icons/chevron-up';
	import X from '@lucide/svelte/icons/x';

	const t = $derived(i18n.m.player);

	function remove(id: string) {
		const index = queue.items.findIndex((i) => i.id === id);
		const item = queue.items[index];
		queue.remove(id);
		toasts.show(i18n.m.toast.unqueued, {
			label: i18n.m.toast.undo,
			run: () => queue.insert(item, index)
		});
	}

	function clear() {
		const before = queue.items;
		queue.clear();
		toasts.show(i18n.m.toast.queueCleared, {
			label: i18n.m.toast.undo,
			run: () => before.forEach((item, i) => queue.insert(item, i))
		});
	}
</script>

<div class="head">
	<h3 class="label">{t.queue}</h3>
	{#if queue.items.length}
		<button class="clear" onclick={clear}>{t.queueClear}</button>
	{/if}
</div>

<ol class="queue">
	{#each queue.items as item, i (item.id)}
		{@const v = view(item)}
		<li animate:flip={{ duration: dur(260) }} in:fly={flyUp(10)} out:fade={{ duration: dur(160) }}>
			<button class="row" onclick={() => player.play(item.id)} aria-label={t.queuePlay(v.title)}>
				<span class="cover"
					><Cover title={v.title} author={v.author} src={v.cover} sizes="44px" /></span
				>
				<span class="text">
					<strong>{v.title}</strong>
					<small class="muted">{v.author} · {duration(v.duration)}</small>
				</span>
			</button>
			<button
				class="icon"
				disabled={i === 0}
				onclick={() => queue.move(item.id, -1)}
				aria-label={t.queueUp}
				title={t.queueUp}><ChevronUp size={18} /></button
			>
			<button
				class="icon"
				onclick={() => remove(item.id)}
				aria-label={t.queueRemove}
				title={t.queueRemove}><X size={18} /></button
			>
		</li>
	{:else}
		<li class="muted empty">{t.queueEmpty}</li>
	{/each}
</ol>

<style>
	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}

	.clear {
		min-height: 36px;
		padding: 0 var(--sp-12);
		border: 0;
		border-radius: var(--r-pill);
		background: transparent;
		color: var(--c-muted);
		font-weight: var(--fw-bold);
		font-size: var(--fs-13);
	}

	.clear:hover {
		color: var(--c-ink);
		background: var(--c-bg);
	}

	.queue {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: 2px;
	}

	li {
		display: flex;
		align-items: center;
		gap: 2px;
	}

	.row {
		flex: 1;
		min-width: 0;
		display: grid;
		grid-template-columns: 44px 1fr;
		align-items: center;
		gap: var(--sp-12);
		min-height: 60px;
		padding: var(--sp-8);
		border: 0;
		border-radius: var(--r-card);
		background: transparent;
		color: inherit;
		text-align: left;
	}

	.row:hover {
		background: var(--c-bg);
	}

	.cover {
		width: 44px;
		border-radius: 6px;
		overflow: hidden;
	}

	.text {
		display: grid;
		min-width: 0;
	}

	.text strong,
	.text small {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.text strong {
		font-size: var(--fs-14);
	}

	.text small {
		font-size: var(--fs-12);
	}

	.icon {
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

	.icon:hover:not(:disabled) {
		color: var(--c-ink);
		background: var(--c-bg);
	}

	.icon:disabled {
		opacity: 0.35;
	}

	.empty {
		padding: var(--sp-12);
		font-size: var(--fs-13);
	}
</style>
