import { describe, it, expect, vi } from 'vitest';
import { AbsClient } from './client.ts';
import type { ServerUser, ListeningStats, SessionPage } from './types.ts';

describe('Admin user stats and sessions in AbsClient', () => {
	const dummyTokens = { accessToken: 'dummy.jwt.token', refreshToken: 'dummy-refresh' };

	const makeResponse = (data: unknown) => ({
		ok: true,
		status: 200,
		headers: new Headers({ 'content-type': 'application/json' }),
		json: async () => data
	});

	it('routes listeningStats() to /api/me/listening-stats without userId', async () => {
		const mockFetch = vi.fn().mockResolvedValue(
			makeResponse({ totalTime: 120, items: {}, days: {}, dayOfWeek: {}, today: 120, recentSessions: [] })
		);

		const client = new AbsClient({
			baseUrl: 'http://localhost:13378',
			tokens: dummyTokens,
			fetch: mockFetch as any
		});

		const stats = await client.listeningStats();
		expect(mockFetch).toHaveBeenCalledWith(
			'http://localhost:13378/api/me/listening-stats',
			expect.objectContaining({ method: 'GET' })
		);
		expect(stats.totalTime).toBe(120);
	});

	it('routes listeningStats(userId) to /api/users/:userId/listening-stats for admin', async () => {
		const mockFetch = vi.fn().mockResolvedValue(
			makeResponse({ totalTime: 4500, items: {}, days: {}, dayOfWeek: {}, today: 0, recentSessions: [] })
		);

		const client = new AbsClient({
			baseUrl: 'http://localhost:13378',
			tokens: dummyTokens,
			fetch: mockFetch as any
		});

		const targetUserId = 'gast-user-uuid-1234';
		const stats = await client.listeningStats(targetUserId);
		expect(mockFetch).toHaveBeenCalledWith(
			'http://localhost:13378/api/users/gast-user-uuid-1234/listening-stats',
			expect.objectContaining({ method: 'GET' })
		);
		expect(stats.totalTime).toBe(4500);
	});

	it('routes listeningSessions(userId) to /api/users/:userId/listening-sessions for admin', async () => {
		const mockFetch = vi.fn().mockResolvedValue(
			makeResponse({ total: 10, numPages: 1, page: 0, itemsPerPage: 50, sessions: [] })
		);

		const client = new AbsClient({
			baseUrl: 'http://localhost:13378',
			tokens: dummyTokens,
			fetch: mockFetch as any
		});

		const targetUserId = 'gast-user-uuid-1234';
		const page = await client.listeningSessions(0, 50, targetUserId);
		expect(mockFetch).toHaveBeenCalledWith(
			'http://localhost:13378/api/users/gast-user-uuid-1234/listening-sessions?page=0&itemsPerPage=50',
			expect.objectContaining({ method: 'GET' })
		);
		expect(page.total).toBe(10);
	});

	it('fetches server users via /api/users for admin', async () => {
		const mockUsers: ServerUser[] = [
			{ id: '1', username: 'koala', type: 'root', isActive: true },
			{ id: '2', username: 'gast', type: 'user', isActive: true }
		];
		const mockFetch = vi.fn().mockResolvedValue(
			makeResponse({ users: mockUsers })
		);

		const client = new AbsClient({
			baseUrl: 'http://localhost:13378',
			tokens: dummyTokens,
			fetch: mockFetch as any
		});

		const users = await client.users();
		expect(mockFetch).toHaveBeenCalledWith(
			'http://localhost:13378/api/users',
			expect.objectContaining({ method: 'GET' })
		);
		expect(users).toHaveLength(2);
		expect(users[0].username).toBe('koala');
		expect(users[1].username).toBe('gast');
	});
});
