<script lang="ts">
	/** A settings row: label (and hint) with an on/off switch. */
	let {
		label,
		hint = '',
		checked,
		onchange
	}: { label: string; hint?: string; checked: boolean; onchange: (on: boolean) => void } = $props();
</script>

<div class="toggle">
	<span class="text">
		<span class="name">{label}</span>
		{#if hint}<small class="muted">{hint}</small>{/if}
	</span>
	<button
		class="switch"
		role="switch"
		aria-checked={checked}
		aria-label={label}
		onclick={() => onchange(!checked)}
	>
		<span></span>
	</button>
</div>

<style>
	.toggle {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--sp-16);
	}

	.text {
		display: grid;
		gap: var(--sp-4);
		min-width: 0;
	}

	.name {
		font-weight: var(--fw-semibold);
	}

	small {
		font-size: var(--fs-13);
	}

	.switch {
		position: relative;
		width: 52px;
		height: 32px;
		flex: none;
		padding: 0;
		border: 0;
		border-radius: var(--r-pill);
		/* Off state still reads as a control: 3:1 against the card (WCAG 1.4.11). */
		background: color-mix(in oklab, var(--c-muted) 75%, var(--c-card));
		transition: background var(--motion-fast) var(--motion-ease);
	}

	.switch span {
		position: absolute;
		top: 4px;
		left: 4px;
		width: 24px;
		height: 24px;
		border-radius: 50%;
		background: var(--c-card);
		box-shadow: 0 1px 3px rgb(0 0 0 / 0.3);
		transition: translate var(--motion-base) var(--motion-spring);
	}

	.switch[aria-checked='true'] {
		background: var(--c-accent);
	}

	.switch[aria-checked='true'] span {
		translate: 20px 0;
		background: var(--c-on-accent);
	}

	/* 44px touch target. */
	.switch::after {
		content: '';
		position: absolute;
		inset: -6px;
	}
</style>
