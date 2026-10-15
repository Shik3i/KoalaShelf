<script lang="ts">
	import { coverArt } from './cover-art.ts';

	/** Round portrait: the photo when there is one, otherwise initials on the name's colors. */
	let {
		name,
		src = null,
		size = '100%'
	}: { name: string; src?: string | null; size?: string } = $props();

	const art = $derived(coverArt(name));
	const initials = $derived(
		name
			.split(/\s+/)
			.map((p) => p[0])
			.slice(0, 2)
			.join('')
			.toUpperCase()
	);
	let failed = $state(false);
</script>

<div class="portrait" style:--bg={art.bg} style:--ink={art.ink} style:width={size}>
	<span aria-hidden="true">{initials}</span>
	{#if src && !failed}
		<img {src} alt="" loading="lazy" decoding="async" onerror={() => (failed = true)} />
	{/if}
</div>

<style>
	.portrait {
		position: relative;
		display: grid;
		place-items: center;
		aspect-ratio: 1;
		border-radius: 50%;
		overflow: hidden;
		background: var(--bg);
		color: var(--ink);
		font-family: var(--font-display);
		font-size: var(--fs-34);
		container-type: inline-size;
	}

	/* Initials scale with the circle. */
	span {
		font-size: clamp(0.9rem, 30cqw, 3.5rem);
	}

	img {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
</style>
