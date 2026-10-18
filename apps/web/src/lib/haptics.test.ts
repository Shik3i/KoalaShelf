import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { vibrate, haptic } from './haptics.ts';

describe('haptics', () => {
	const originalVibrate = navigator.vibrate;

	beforeEach(() => {
		navigator.vibrate = vi.fn().mockReturnValue(true);
	});

	afterEach(() => {
		navigator.vibrate = originalVibrate;
	});

	it('triggers vibrate with given pattern', () => {
		expect(vibrate(10)).toBe(true);
		expect(navigator.vibrate).toHaveBeenCalledWith(10);
	});

	it('provides semantic haptic helpers', () => {
		haptic.tick();
		expect(navigator.vibrate).toHaveBeenCalledWith(10);

		haptic.tap();
		expect(navigator.vibrate).toHaveBeenCalledWith(16);

		haptic.boundary();
		expect(navigator.vibrate).toHaveBeenCalledWith([10, 35, 10]);

		haptic.success();
		expect(navigator.vibrate).toHaveBeenCalledWith([12, 40, 18]);

		haptic.custom([20, 20]);
		expect(navigator.vibrate).toHaveBeenCalledWith([20, 20]);
	});

	it('gracefully handles missing vibrate or exceptions', () => {
		// @ts-expect-error test missing property
		delete navigator.vibrate;
		expect(vibrate(10)).toBe(false);

		navigator.vibrate = vi.fn().mockImplementation(() => {
			throw new Error('Not allowed');
		});
		expect(vibrate(10)).toBe(false);
	});
});
