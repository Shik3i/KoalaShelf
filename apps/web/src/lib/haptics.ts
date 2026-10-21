/**
 * Haptic feedback using the Web Vibration API with Koala tactile vocabulary.
 * Provides subtle tactile feedback on mobile/touch devices; safely no-ops on unsupported platforms
 * or when disabled in user preferences.
 */
import { prefs } from './prefs.svelte';

let lastGrooveTickMs = 0;

export function vibrate(pattern: number | number[] = 10): boolean {
	if (typeof navigator === 'undefined') return false;
	try {
		if (prefs.haptics === false) return false;
		if ('vibrate' in navigator && typeof navigator.vibrate === 'function') {
			return navigator.vibrate(pattern);
		}
	} catch {
		// Ignore any browser security restrictions or lack of permission
	}
	return false;
}

export const haptic = {
	/** Crisp, very light tick (e.g. flipping one record in a crate, snapping to a notch) */
	tick: () => vibrate(10),
	/** Standard tactile tap (e.g. play/pause, button press) */
	tap: () => vibrate(16),
	/** Needle drop: distinct double tactile pulse mimicking stylus contact */
	needleDrop: () => vibrate([22, 35, 12]),
	/** Ultra-light groove tick with 35ms throttle for scratching / scrubbing */
	grooveTick: () => {
		const now = typeof performance !== 'undefined' ? performance.now() : Date.now();
		if (now - lastGrooveTickMs < 35) return false;
		lastGrooveTickMs = now;
		return vibrate(8);
	},
	/** Crisp chapter snap pulse */
	chapterSnap: () => vibrate([15, 25, 20]),
	/** Sleep timer armed acknowledgment */
	sleepArmed: () => vibrate([15, 50, 10, 30, 8]),
	/** Boundary hit (e.g. reaching the start or end of a series crate or track) */
	boundary: () => vibrate([10, 35, 10]),
	/** Success confirmation (e.g. bookmark added, item finished) */
	success: () => vibrate([12, 40, 18]),
	/** Custom vibration pattern */
	custom: (pattern: number | number[]) => vibrate(pattern)
};
