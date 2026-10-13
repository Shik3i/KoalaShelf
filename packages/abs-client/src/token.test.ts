import { describe, expect, it } from 'vitest';
import { tokenExpiry } from './client.ts';

const jwt = (payload: object) =>
	['e30', btoa(JSON.stringify(payload)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, ''), 'sig'].join('.');

describe('tokenExpiry', () => {
	it('reads exp from a JWT', () => {
		expect(tokenExpiry(jwt({ userId: 'x', exp: 1_800_000_000 }))).toBe(1_800_000_000);
	});

	it('is null for tokens without expiry (older servers) or opaque ones', () => {
		expect(tokenExpiry(jwt({ userId: 'x' }))).toBeNull();
		expect(tokenExpiry('not-a-jwt')).toBeNull();
		expect(tokenExpiry('')).toBeNull();
	});
});
