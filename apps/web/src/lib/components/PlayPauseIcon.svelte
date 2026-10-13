<script lang="ts">
	import Play from '@lucide/svelte/icons/play';
	import Pause from '@lucide/svelte/icons/pause';

	let { playing, size = 22 }: { playing: boolean; size?: number } = $props();
</script>

<!-- Both glyphs stay mounted and cross-fade with a small turn, instead of swapping abruptly. -->
<span class="pp" class:playing aria-hidden="true">
	<span class="play"><Play {size} fill="currentColor" /></span>
	<span class="pause"><Pause {size} fill="currentColor" /></span>
</span>

<style>
	.pp {
		display: inline-grid;
		place-items: center;
	}

	.pp > span {
		grid-area: 1 / 1;
		display: grid;
		transition:
			opacity var(--motion-fast) var(--motion-ease),
			scale var(--motion-base) var(--motion-spring),
			rotate var(--motion-base) var(--motion-spring);
	}

	/* The play triangle is optically centred a touch to the right. */
	.play {
		translate: 1px 0;
	}

	.pause {
		opacity: 0;
		scale: 0.4;
		rotate: -90deg;
	}

	.playing .play {
		opacity: 0;
		scale: 0.4;
		rotate: 90deg;
	}

	.playing .pause {
		opacity: 1;
		scale: 1;
		rotate: 0deg;
	}
</style>
