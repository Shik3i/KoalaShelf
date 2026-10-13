import { describe, expect, it } from 'vitest';
import { rampRate } from './motion.ts';

describe('rampRate', () => {
	it('eases from start to target speed', () => {
		expect(rampRate(0, 1, 0, 900)).toBe(0);
		expect(rampRate(0, 1, 450, 900)).toBeCloseTo(0.875);
		expect(rampRate(0, 1, 900, 900)).toBe(1);
		expect(rampRate(1, 0, 2000, 900)).toBe(0);
	});

	it('never turns negative when a frame timestamp precedes the start', () => {
		expect(rampRate(0, 1, -16, 900)).toBe(0);
		expect(rampRate(0.4, 0, -16, 900)).toBeGreaterThanOrEqual(0);
	});
});
