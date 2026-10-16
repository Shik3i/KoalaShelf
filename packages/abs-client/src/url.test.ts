import { describe, expect, it } from 'vitest';
import { normalizeBaseUrl } from './client.ts';

describe('normalizeBaseUrl', () => {
	it('trims whitespace and trailing slashes', () => {
		expect(normalizeBaseUrl('  https://abs.example.org///  ')).toBe('https://abs.example.org');
		expect(normalizeBaseUrl('https://abs.example.org/sub/')).toBe('https://abs.example.org/sub');
		expect(normalizeBaseUrl('')).toBe('');
	});

	it('stays fast on pathological input', () => {
		const start = performance.now();
		normalizeBaseUrl('/'.repeat(100_000) + 'a');
		expect(performance.now() - start).toBeLessThan(50);
	});
});
