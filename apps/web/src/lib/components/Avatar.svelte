<script lang="ts">
	/** Initial on a calm color derived from the name (tells accounts apart at a glance). */
	let { name, size = 36 }: { name: string; size?: number } = $props();

	const HUES = [12, 38, 145, 175, 205, 250, 285, 330];
	const hue = $derived(
		HUES[[...name].reduce((h, c) => (h * 31 + c.charCodeAt(0)) >>> 0, 7) % HUES.length]
	);
</script>

<span
	class="avatar"
	style:--h={hue}
	style:width="{size}px"
	style:height="{size}px"
	style:font-size="{Math.round(size * 0.42)}px"
	aria-hidden="true">{name.slice(0, 1).toUpperCase()}</span
>

<style>
	/* Dark text on a light tint, or light text on a deep one: 4.5:1 either way. */
	.avatar {
		display: grid;
		place-items: center;
		flex: none;
		border-radius: 50%;
		background: oklch(0.86 0.07 var(--h));
		color: oklch(0.3 0.09 var(--h));
		font-weight: var(--fw-bold);
		line-height: 1;
	}

	@media (prefers-color-scheme: dark) {
		:global(:root:not(.th-light)) .avatar {
			background: oklch(0.42 0.09 var(--h));
			color: oklch(0.95 0.03 var(--h));
		}
	}

	:global(.th-dark) .avatar {
		background: oklch(0.42 0.09 var(--h));
		color: oklch(0.95 0.03 var(--h));
	}

	@media (forced-colors: active) {
		.avatar {
			border: 1px solid CanvasText;
		}
	}
</style>
