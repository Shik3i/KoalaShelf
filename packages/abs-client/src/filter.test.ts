import { describe, expect, it } from 'vitest';
import { encodeFilter } from './filter.ts';
import { isVersionAtLeast } from './client.ts';

describe('encodeFilter', () => {
	it('encodes utf-8 values as base64', () => {
		expect(encodeFilter('genres', 'Hörspiel')).toBe('genres.SMO2cnNwaWVs');
		expect(encodeFilter('progress', 'in-progress')).toBe('progress.aW4tcHJvZ3Jlc3M=');
	});
});

describe('isVersionAtLeast', () => {
	it('compares versions', () => {
		expect(isVersionAtLeast('2.37.1', '2.26.0')).toBe(true);
		expect(isVersionAtLeast('v2.26.0', '2.26.0')).toBe(true);
		expect(isVersionAtLeast('2.25.9', '2.26.0')).toBe(false);
	});
});
