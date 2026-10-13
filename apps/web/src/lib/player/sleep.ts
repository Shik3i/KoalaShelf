// Sleep timer modes and the "smart" window forecast (pure, unit-tested).
import type { Chapter } from '@koalashelf/abs-client';

export type SleepTimer =
	| { kind: 'off' }
	| { kind: 'minutes'; endsAt: number; minutes: number }
	| { kind: 'chapter' }
	/** Stops at the first chapter end between minAt and maxAt; at maxAt at the latest. */
	| { kind: 'window'; minAt: number; maxAt: number; min: number; max: number };

export interface SleepForecast {
	/** Listening seconds until playback stops. */
	seconds: number;
	/** Index of the chapter whose end stops playback, or null when the window runs out first. */
	chapter: number | null;
}

/**
 * When a smart timer would stop: the first chapter end that is reached after `minLeft`
 * seconds and no later than `maxLeft` seconds of listening (book time runs `rate` times
 * faster than the clock). Without such a chapter end it stops after `maxLeft`.
 */
export function forecast(
	chapters: readonly Chapter[],
	currentTime: number,
	rate: number,
	minLeft: number,
	maxLeft: number
): SleepForecast {
	for (let i = 0; i < chapters.length; i++) {
		const until = (chapters[i].end - currentTime) / rate;
		if (until < 0) continue;
		if (until > maxLeft) break;
		if (until >= minLeft) return { seconds: until, chapter: i };
	}
	return { seconds: Math.max(0, maxLeft), chapter: null };
}

export const SMART_STEP = 5;
export const SMART_MIN = 5;
export const SMART_MAX = 180;

/** Keeps a window valid: both bounds in range, the latest at least one step after the earliest. */
export function clampWindow(min: number, max: number, changed: 'min' | 'max') {
	min = Math.min(Math.max(min, SMART_MIN), SMART_MAX - SMART_STEP);
	max = Math.min(Math.max(max, SMART_MIN + SMART_STEP), SMART_MAX);
	if (max - min < SMART_STEP) {
		if (changed === 'min') max = min + SMART_STEP;
		else min = max - SMART_STEP;
	}
	return { min, max };
}
