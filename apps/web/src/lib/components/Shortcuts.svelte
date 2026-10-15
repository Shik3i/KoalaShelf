<script lang="ts">
	import { i18n } from '#lib/i18n/index.svelte.ts';

	let { open = $bindable(false) }: { open?: boolean } = $props();

	let dialog = $state<HTMLDialogElement>();
	const t = $derived(i18n.m.shortcuts);

	$effect(() => {
		if (!dialog) return;
		if (open && !dialog.open) dialog.showModal();
		if (!open && dialog.open) dialog.close();
	});

	const rows = $derived([
		[['Space'], t.playPause],
		[['←', '→'], t.skip],
		[['Shift', '←', '→'], t.chapter],
		[['[', ']'], t.speed],
		[['B'], t.bookmark],
		[['Ctrl', 'K'], t.palette],
		[['?'], t.help]
	] as const);
</script>

<!-- Overlay with all keyboard shortcuts (opened with "?"). -->
<dialog bind:this={dialog} onclose={() => (open = false)} aria-labelledby="shortcuts-title">
	<h2 id="shortcuts-title">{t.title}</h2>
	<dl>
		{#each rows as [keys, label] (label)}
			<dt>
				{#each keys as k (k)}<kbd>{k}</kbd>{/each}
			</dt>
			<dd>{label}</dd>
		{/each}
	</dl>
	<button class="btn primary" onclick={() => (open = false)}>{t.close}</button>
</dialog>

<style>
	dialog {
		width: min(92vw, 420px);
		padding: var(--sp-24);
		border: 0;
		border-radius: var(--r-card-lg);
		background: var(--c-card);
		color: var(--c-ink);
		box-shadow: 0 30px 60px -20px rgb(0 0 0 / 0.5);
	}

	dialog[open] {
		animation: pop var(--motion-base) var(--motion-spring);
	}

	dialog::backdrop {
		background: rgb(0 0 0 / 0.35);
		backdrop-filter: blur(2px);
	}

	@keyframes pop {
		from {
			opacity: 0;
			scale: 0.94;
		}
	}

	h2 {
		margin-bottom: var(--sp-16);
	}

	dl {
		display: grid;
		grid-template-columns: auto 1fr;
		gap: var(--sp-12) var(--sp-16);
		align-items: center;
		margin: 0 0 var(--sp-20);
	}

	dt {
		display: flex;
		gap: var(--sp-4);
	}

	dd {
		margin: 0;
		font-size: var(--fs-14);
	}

	kbd {
		min-width: 28px;
		padding: 2px 8px;
		border-radius: 6px;
		background: var(--c-bg);
		box-shadow: inset 0 -2px 0 var(--c-line);
		font: inherit;
		font-size: var(--fs-13);
		font-weight: var(--fw-bold);
		text-align: center;
	}

	.btn {
		width: 100%;
	}
</style>
