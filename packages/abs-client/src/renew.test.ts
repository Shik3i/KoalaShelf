import { describe, expect, it } from 'vitest';
import { AbsClient } from './client.ts';

const jwt = (exp: number) =>
	['e30', btoa(JSON.stringify({ userId: 'u', exp })).replace(/=+$/, ''), 'sig'].join('.');
const now = () => Math.floor(Date.now() / 1000);

/** A client whose server hands out `nextExp` tokens on refresh; records the paths it calls. */
function setup(accessExp: number, nextExp: number) {
	const calls: string[] = [];
	const client = new AbsClient({
		baseUrl: 'https://abs.example',
		tokens: { accessToken: jwt(accessExp), refreshToken: 'r' },
		fetch: async (url) => {
			const path = new URL(String(url)).pathname;
			calls.push(path);
			const body =
				path === '/auth/refresh'
					? { user: { accessToken: jwt(nextExp), refreshToken: 'r2' } }
					: { ok: true };
			return new Response(JSON.stringify(body), { headers: { 'content-type': 'application/json' } });
		}
	});
	return { client, calls };
}

describe('access token renewal', () => {
	it('renews a token about to expire before the request instead of after a 401', async () => {
		const { client, calls } = setup(now() + 5, now() + 3600);
		await client.request('/api/me');
		await client.request('/api/me');
		expect(calls).toEqual(['/auth/refresh', '/api/me', '/api/me']);
	});

	it('leaves a fresh token alone', async () => {
		const { client, calls } = setup(now() + 3600, now() + 7200);
		await client.request('/api/me');
		expect(calls).toEqual(['/api/me']);
	});

	it('stops renewing ahead when the device clock runs ahead (every token looks expired)', async () => {
		const { client, calls } = setup(now() - 600, now() - 300);
		await client.request('/api/me');
		await client.request('/api/me');
		await client.request('/api/me');
		expect(calls.filter((c) => c === '/auth/refresh')).toHaveLength(1);
	});
});
