<script lang="ts">
	import { Tween } from 'svelte/motion';
	import { cubicOut } from 'svelte/easing';
	import { dur } from '#lib/motion.ts';

	/** A number that counts up from zero when it first appears (instant with reduced motion). */
	let {
		value,
		format = (n: number) => String(Math.round(n))
	}: {
		value: number;
		format?: (n: number) => string;
	} = $props();

	const tween = new Tween(0, { duration: dur(900), easing: cubicOut });
	$effect(() => {
		tween.target = value;
	});
</script>

<!-- The final value is exposed to assistive tech right away; only the visual counts. -->
<span aria-hidden="true">{format(tween.current)}</span><span class="sr-only">{format(value)}</span>
