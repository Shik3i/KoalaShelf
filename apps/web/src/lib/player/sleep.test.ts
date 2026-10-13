import { describe, expect, it } from 'vitest';
import type { Chapter } from '@koalashelf/abs-client';
import { clampWindow, forecast } from './sleep.ts';

// Chapters of 10 minutes each: ends at 600, 1200, 1800, ...
const chapters: Chapter[] = Array.from({ length: 6 }, (_, i) => ({
	id: i,
	start: i * 600,
	end: (i + 1) * 600,
	title: `Chapter ${i + 1}`
}));

describe('smart sleep forecast', () => {
	it('stops at the first chapter end inside the window', () => {
		// At 5:00 with a 12–25 min window: the end of chapter 2 (20:00) is 15 min away.
		expect(forecast(chapters, 300, 1, 12 * 60, 25 * 60)).toEqual({ seconds: 900, chapter: 1 });
	});

	it('skips chapter ends that come too early', () => {
		// Chapter 1 ends in 5 min (before the 8 min minimum): wait for chapter 2.
		expect(forecast(chapters, 300, 1, 8 * 60, 20 * 60)).toEqual({ seconds: 900, chapter: 1 });
	});

	it('falls back to the latest time when no chapter ends inside the window', () => {
		expect(forecast(chapters, 300, 1, 6 * 60, 14 * 60)).toEqual({ seconds: 840, chapter: null });
		expect(forecast([], 0, 1, 60, 120)).toEqual({ seconds: 120, chapter: null });
	});

	it('accounts for playback speed', () => {
		// At 2x, the end of chapter 1 (10 min of book) is 5 min of listening away.
		expect(forecast(chapters, 0, 2, 4 * 60, 6 * 60)).toEqual({ seconds: 300, chapter: 0 });
	});

	it('includes chapter ends right at the window bounds', () => {
		expect(forecast(chapters, 0, 1, 600, 900).chapter).toBe(0);
		expect(forecast(chapters, 0, 1, 300, 600).chapter).toBe(0);
	});
});

describe('clampWindow', () => {
	it('keeps the latest time after the earliest', () => {
		expect(clampWindow(40, 40, 'min')).toEqual({ min: 40, max: 45 });
		expect(clampWindow(40, 40, 'max')).toEqual({ min: 35, max: 40 });
	});

	it('stays within bounds', () => {
		expect(clampWindow(0, 400, 'min')).toEqual({ min: 5, max: 180 });
		expect(clampWindow(180, 180, 'min')).toEqual({ min: 175, max: 180 });
	});
});
