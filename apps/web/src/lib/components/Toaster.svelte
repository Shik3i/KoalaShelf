<script lang="ts">
	import { fly } from 'svelte/transition';
	import { flip } from 'svelte/animate';
	import { toasts } from '#lib/toast.svelte.ts';
	import { flyUp, dur } from '#lib/motion.ts';

	/**
	 * A modal dialog makes everything outside it inert, so the full player renders its own
	 * toaster inside; the page-level one steps aside meanwhile (\`hidden\`).
	 */
	let { hidden = false }: { hidden?: boolean } = $props();
</script>

{#if !hidden}
	<div class="toaster" role="status" aria-live="polite">
		{#each toasts.list as t (t.id)}
			<div
				class="toast"
				in:fly={flyUp(16)}
				out:fly={flyUp(8, 180)}
				animate:flip={{ duration: dur(220) }}
			>
				<span>{t.text}</span>
				{#if t.action}
					<button
						type="button"
						onclick={() => {
							t.action?.run();
							toasts.dismiss(t.id);
						}}>{t.action.label}</button
					>
				{/if}
			</div>
		{/each}
	</div>
{/if}

<style>
	.toaster {
		position: fixed;
		z-index: 40;
		left: 50%;
		bottom: calc(var(--sp-16) + 88px);
		translate: -50% 0;
		display: grid;
		justify-items: center;
		gap: var(--sp-8);
		width: min(92vw, 420px);
		pointer-events: none;
	}

	.toast {
		display: flex;
		align-items: center;
		gap: var(--sp-16);
		min-height: var(--layout-touch);
		padding: var(--sp-8) var(--sp-8) var(--sp-8) var(--sp-20);
		border-radius: var(--r-pill);
		background: var(--c-ctrl);
		color: var(--c-ctrl-ink);
		font-size: var(--fs-14);
		font-weight: var(--fw-semibold);
		box-shadow: 0 14px 30px -14px rgb(0 0 0 / 0.5);
		pointer-events: auto;
	}

	.toast:not(:has(button)) {
		padding-right: var(--sp-20);
	}

	button {
		min-height: 36px;
		padding: 0 var(--sp-16);
		border: 0;
		border-radius: var(--r-pill);
		background: var(--c-accent);
		color: var(--c-on-accent);
		font-weight: var(--fw-bold);
		font-size: var(--fs-13);
	}

	@media (max-width: 800px) {
		.toaster {
			bottom: calc(150px + env(safe-area-inset-bottom));
		}
	}
</style>
