import { describe, expect, it, vi } from 'vitest';
import { clearBadge, setBadge } from './badge.ts';

describe('PWA badge helper', () => {
	it('calls setAppBadge with rounded number when supported', () => {
		const setAppBadge = vi.fn().mockResolvedValue(undefined);
		const clearAppBadge = vi.fn().mockResolvedValue(undefined);
		vi.stubGlobal('navigator', { setAppBadge, clearAppBadge });

		setBadge(4.2);
		expect(setAppBadge).toHaveBeenCalledWith(4);

		setBadge(0);
		expect(clearAppBadge).toHaveBeenCalled();

		clearBadge();
		expect(clearAppBadge).toHaveBeenCalledTimes(2);

		vi.unstubAllGlobals();
	});

	it('safely handles missing navigator or unsupported API', () => {
		vi.stubGlobal('navigator', {});
		expect(() => setBadge(5)).not.toThrow();
		expect(() => clearBadge()).not.toThrow();
		vi.unstubAllGlobals();
	});
});
