<script lang="ts">
	import { coverArt } from './cover-art';

	interface Props {
		title: string;
		author?: string;
		/** Cover URL builder (width -> url). Omit when the item has no cover. */
		src?: ((width: number) => string) | null;
		/** Rendered width hint for srcset, e.g. "(max-width: 800px) 40vw, 180px". */
		sizes?: string;
		/** Load immediately (above the fold). */
		eager?: boolean;
		/** Hide the title line (for crates: the ledge covers the bottom). */
		titleTop?: boolean;
		/** Motif only, no text (where a caption names the book, e.g. records in a crate). */
		bare?: boolean;
	}

	let {
		title,
		author = '',
		src = null,
		sizes = '200px',
		eager = false,
		titleTop = false,
		bare = false
	}: Props = $props();

	const art = $derived(coverArt(`${title}|${author}`));
	// Load state is tracked per URL, so a recycled list item never shows a stale state.
	const key = $derived(src?.(400) ?? null);
	let loadedFor = $state<string | null>(null);
	let failedFor = $state<string | null>(null);
	const loaded = $derived(key !== null && loadedFor === key);
	const failed = $derived(key !== null && failedFor === key);
</script>

<div
	class="cover"
	style:--bg={art.bg}
	style:--fg={art.fg}
	style:--ink={art.ink}
	style:--alt={art.alt}
>
	{#if !src || failed || !loaded}
		<div class="art" aria-hidden="true">
			<svg viewBox="0 0 100 100" preserveAspectRatio="xMidYMid slice">
				{#if art.motif === 'sun'}
					<circle cx="66" cy="34" r="12" fill="var(--fg)" />
					<path d="M0 74 Q30 58 55 70 T100 66 V100 H0Z" fill="var(--alt)" />
				{:else if art.motif === 'stripes'}
					<circle cx="24" cy="26" r="6" fill="var(--alt)" />
					<path d="M0 58 H100" stroke="var(--fg)" stroke-width="7" stroke-dasharray="4 3" />
				{:else if art.motif === 'rings'}
					{#each Array.from({ length: 11 }, (_, i) => i) as i (i)}
						<circle cx="50" cy="50" r={4 + i * 7} fill="none" stroke="var(--fg)" stroke-width="3" />
					{/each}
				{:else if art.motif === 'eyes'}
					<circle cx="36" cy="50" r="11" fill="var(--fg)" />
					<circle cx="64" cy="50" r="11" fill="var(--fg)" />
					<circle cx="38" cy="50" r="4.5" fill="var(--alt)" />
					<circle cx="66" cy="50" r="4.5" fill="var(--alt)" />
				{:else if art.motif === 'diagonal'}
					<path d="M100 0 V100 H0Z" fill="var(--fg)" />
					<circle cx="28" cy="32" r="6" fill="var(--alt)" />
				{:else if art.motif === 'arcs'}
					<circle cx="50" cy="104" r="46" fill="var(--alt)" />
					<circle cx="50" cy="104" r="30" fill="var(--fg)" />
				{:else}
					<circle cx="68" cy="34" r="13" fill="var(--fg)" />
					<circle cx="62" cy="30" r="12" fill="var(--bg)" />
				{/if}
			</svg>
			{#if !bare}<span class="author">{author}</span>{/if}
			{#if !titleTop && !bare}<span class="title">{title}</span>{/if}
		</div>
	{/if}
	{#if src && !failed}
		<img
			src={src(400)}
			srcset="{src(200)} 200w, {src(400)} 400w, {src(800)} 800w"
			{sizes}
			alt=""
			loading={eager ? 'eager' : 'lazy'}
			fetchpriority={eager ? 'high' : 'auto'}
			decoding="async"
			class:loaded
			onload={() => (loadedFor = key)}
			onerror={() => (failedFor = key)}
		/>
	{/if}
</div>

<style>
	.cover {
		container-type: inline-size;
		position: relative;
		aspect-ratio: 1;
		border-radius: var(--r-cover);
		overflow: hidden;
		background: var(--bg);
		box-shadow:
			0 1px 2px rgb(0 0 0 / 0.12),
			0 6px 16px -8px rgb(0 0 0 / 0.35);
	}

	.art {
		position: absolute;
		inset: 0;
		color: var(--ink);
	}

	svg {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
	}

	.author {
		position: absolute;
		top: 6cqw;
		left: 7cqw;
		right: 7cqw;
		font-size: var(--cover-author);
		font-weight: var(--fw-bold);
		letter-spacing: var(--tracking-cover-author);
		text-transform: uppercase;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.title {
		position: absolute;
		left: 7cqw;
		right: 7cqw;
		bottom: 6cqw;
		font-family: var(--font-display);
		font-size: var(--cover-title);
		line-height: 1;
		display: -webkit-box;
		-webkit-line-clamp: 3;
		line-clamp: 3;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}

	/* Thumbnails: text would be unreadable, the motif alone identifies the placeholder. */
	@container (max-width: 96px) {
		.author,
		.title {
			display: none;
		}
	}

	img {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		object-fit: cover;
		opacity: 0;
		transition: opacity var(--motion-base) var(--motion-ease);
	}

	img.loaded {
		opacity: 1;
	}
</style>
