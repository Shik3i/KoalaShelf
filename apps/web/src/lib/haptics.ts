/**
 * Haptic feedback using the Web Vibration API.
 * Provides subtle tactile feedback on mobile/touch devices; safely no-ops on unsupported platforms.
 */

export function vibrate(pattern: number | number[] = 10): boolean {
	if (typeof navigator === 'undefined') return false;
	try {
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
	/** Boundary hit (e.g. reaching the start or end of a series crate) */
	boundary: () => vibrate([10, 35, 10]),
	/** Success confirmation (e.g. bookmark added, item finished) */
	success: () => vibrate([12, 40, 18]),
	/** Custom vibration pattern */
	custom: (pattern: number | number[]) => vibrate(pattern)
};
