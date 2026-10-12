import { describe, expect, it, vi } from 'vitest';

vi.mock('./i18n/index.svelte.ts', () => ({
	i18n: { tag: 'de-DE', m: { format: { hours: 'Std.', minutes: 'Min.' } } }
}));

const { bytes, clock, duration, speed } = await import('./format.ts');

describe('format', () => {
	it('clock', () => {
		expect(clock(3725)).toBe('1:02:05');
		expect(clock(125)).toBe('2:05');
		expect(clock(-3)).toBe('0:00');
	});
	it('duration', () => {
		expect(duration(3725)).toBe('1 Std. 2 Min.');
		expect(duration(20)).toBe('1 Min.');
	});
	it('bytes and speed', () => {
		expect(bytes(1_500_000)).toBe('1,5 MB');
		expect(speed(1.2)).toBe('1,2×');
	});
});
