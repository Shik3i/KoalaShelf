// Motion helpers. CSS animations are muted globally for prefers-reduced-motion (app.css);
// JS-driven ones (Svelte transitions, Web Animations) ask here.
import { cubicOut } from 'svelte/easing';

export const reducedMotion = () =>
	typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;

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
