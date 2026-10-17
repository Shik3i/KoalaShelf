import { describe, expect, it } from 'vitest';
import { AbsClient } from './client.ts';

// A reverse proxy in front of a real server sent "Cache-Control: public, max-age=15" for the
// API: progress looked unchanged after marking a book finished, and the browser cache does not
// tell accounts apart (it ignores the Authorization header).
describe('API requests', () => {
	it('never come from the browser cache', async () => {
		const seen: RequestInit[] = [];
		const client = new AbsClient({
			baseUrl: 'https://abs.example',
			tokens: { accessToken: 'a', refreshToken: null },
			fetch: async (_url, init) => {
				seen.push(init ?? {});
				return new Response('{}', { headers: { 'content-type': 'application/json' } });
			}
		});
		await client.request('/api/me');
		await client.request('/api/me/progress/x', { method: 'PATCH', body: { isFinished: true } });
		expect(seen.map((i) => i.cache)).toEqual(['no-store', 'no-store']);
	});
});
