<script lang="ts">
	import { downloads, supported } from '#lib/downloads.svelte.ts';
	import { session } from '#lib/session.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Download from '@lucide/svelte/icons/download';
	import Check from '@lucide/svelte/icons/check';
	import X from '@lucide/svelte/icons/x';
	import Trash2 from '@lucide/svelte/icons/trash-2';

	let { id, hasAudio = true }: { id: string; hasAudio?: boolean } = $props();

	const t = $derived(i18n.m.downloads);
	const job = $derived(downloads.jobs.get(id));
	const done = $derived(downloads.has(id));
	const pct = $derived(job && job.total ? Math.round((job.received / job.total) * 100) : 0);

	downloads.init();
</script>

{#if supported() && session.canDownload && hasAudio}
	{#if done}
		<div class="done">
			<span class="state"><Check size={16} /> {t.downloaded}</span>
			<button
				class="icon"
				onclick={() => downloads.remove(id)}
				title={t.remove}
				aria-label={t.remove}
			>
				<Trash2 size={16} />
			</button>
		</div>
	{:else if job?.state === 'running'}
		<div class="btn ghost progress" style:--p="{pct}%" role="status">
			<span>{t.downloading(pct)}</span>
			<button
				class="icon"
				onclick={() => downloads.cancel(id)}
				title={t.cancel}
				aria-label={t.cancel}
			>
				<X size={16} />
			</button>
		</div>
	{:else}
		<button class="btn ghost" onclick={() => downloads.download(id)}>
			<Download size={16} />
			{job?.state === 'failed' ? t.failed : t.download}
		</button>
	{/if}
{/if}

<style>
	.done {
		display: inline-flex;
		align-items: center;
		gap: var(--sp-4);
		min-height: var(--layout-touch);
		padding: 0 var(--sp-8) 0 var(--sp-16);
		border-radius: var(--r-pill);
		background: var(--c-card);
		font-size: var(--fs-14);
		font-weight: var(--fw-bold);
	}

	.state {
		display: inline-flex;
		align-items: center;
		gap: var(--sp-8);
		color: var(--c-ink);
	}

	.progress {
		position: relative;
		overflow: hidden;
		padding-right: var(--sp-8);
		background: linear-gradient(
			to right,
			color-mix(in oklab, var(--c-accent) 25%, transparent) var(--p),
			transparent 0
		);
	}

	.icon {
		display: grid;
		place-items: center;
		width: 36px;
		height: 36px;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: var(--c-muted);
	}

	.icon:hover {
		color: var(--c-ink);
		background: color-mix(in oklab, var(--c-ink) 8%, transparent);
	}
</style>
