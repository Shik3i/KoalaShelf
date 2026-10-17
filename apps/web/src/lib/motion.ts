// Motion helpers. CSS animations are muted globally for prefers-reduced-motion (app.css);
// JS-driven ones (Svelte transitions, Web Animations) ask here.
import { backOut, cubicOut } from 'svelte/easing';
import { prefs } from './prefs.svelte.ts';

/** Settings > Motion wins; "system" follows prefers-reduced-motion. */
export const reducedMotion = () =>
	prefs.motion === 'reduce' ||
	(prefs.motion === 'system' &&
		typeof matchMedia !== 'undefined' &&
		matchMedia('(prefers-reduced-motion: reduce)').matches);

/** Duration in ms, or 0 when the user prefers reduced motion. */
export const dur = (ms: number) => (reducedMotion() ? 0 : ms);

/** Parameters for Svelte's fly/fade transitions that respect reduced motion. */
export const flyUp = (y = 16, ms = 280) => ({ y, duration: dur(ms), easing: cubicOut });

/**
 * Turntable motor: playback rate `elapsed` ms into a ramp from `from` to `to` (ease-out).
 * Never negative: a reversed rate makes play() throw on an endless animation.
 */
export function rampRate(from: number, to: number, elapsed: number, ms: number) {
	const t = Math.min(1, Math.max(0, elapsed / ms));
	return Math.max(0, from + (to - from) * (1 - (1 - t) ** 3));
}

/** Svelte transition: a new record is put on the platter, turning as it settles. */
export function placeRecord(_node: Element, { duration = 560 } = {}) {
	return {
		duration: dur(duration),
		easing: backOut,
		css: (t: number, u: number) =>
			`transform: rotate(${-100 * u}deg) scale(${0.65 + 0.35 * t}); opacity: ${Math.min(1, t * 2)}`
	};
}
