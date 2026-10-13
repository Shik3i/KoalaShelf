<script lang="ts" generics="T extends string | number">
	import type { Component } from 'svelte';

	interface Option {
		value: T;
		label: string;
		/** Icon-only option: the label becomes the accessible name. */
		icon?: Component<{ size?: number }>;
		lang?: string;
	}

	let {
		options,
		value,
		onchange,
		label,
		size = 'md'
	}: {
		options: Option[];
		value: T;
		onchange: (value: T) => void;
		label: string;
		size?: 'sm' | 'md';
	} = $props();

	const index = $derived(
		Math.max(
			0,
			options.findIndex((o) => o.value === value)
		)
	);
</script>

<!-- Equal-width segments with a pill that glides to the selected one. -->
<div
	class="seg {size}"
	role="group"
	aria-label={label}
	style:--n={options.length}
	style:--i={index}
>
	<span class="pill" aria-hidden="true"></span>
	{#each options as o (o.value)}
		<button
			type="button"
			aria-pressed={o.value === value}
			aria-label={o.icon ? o.label : undefined}
			title={o.icon ? o.label : undefined}
			lang={o.lang}
			onclick={() => onchange(o.value)}
		>
			{#if o.icon}<o.icon size={16} />{:else}{o.label}{/if}
		</button>
	{/each}
</div>

<style>
	.seg {
		position: relative;
		display: inline-grid;
		grid-template-columns: repeat(var(--n), minmax(0, 1fr));
		padding: 3px;
		border-radius: var(--r-pill);
		background: var(--c-card);
		isolation: isolate;
	}

	.pill {
		position: absolute;
		z-index: -1;
		top: 3px;
		bottom: 3px;
		left: 3px;
		width: calc((100% - 6px) / var(--n));
		translate: calc(var(--i) * 100%) 0;
		border-radius: var(--r-pill);
		background: var(--c-sel);
		transition: translate var(--motion-base) var(--motion-out);
	}

	button {
		position: relative;
		display: grid;
		place-items: center;
		min-width: 44px;
		min-height: 40px;
		padding: 0 var(--sp-12);
		border: 0;
		border-radius: var(--r-pill);
		background: transparent;
		color: var(--c-muted);
		font-size: var(--fs-13);
		font-weight: var(--fw-bold);
		white-space: nowrap;
		transition: color var(--motion-base) var(--motion-ease);
	}

	button[aria-pressed='true'] {
		color: var(--c-sel-ink);
	}

	.sm button {
		min-height: 30px;
		min-width: 40px;
		padding: 0 var(--sp-8);
		font-size: var(--fs-12);
	}

	.sm button::after {
		content: '';
		position: absolute;
		inset: -7px 0;
	}
</style>
