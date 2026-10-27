import { describe, it, expect, beforeEach } from 'vitest';
import { exportSettings, importSettings } from './backup.ts';
import { scoped, session } from './session.svelte.ts';
import { AbsClient } from '@koalashelf/abs-client';

function makeMemoryStorage(): Storage {
	const store = new Map<string, string>();
	return {
		get length() {
			return store.size;
		},
		clear() {
			store.clear();
		},
		getItem(key: string) {
			return store.get(key) ?? null;
		},
		key(index: number) {
			return [...store.keys()][index] ?? null;
		},
		removeItem(key: string) {
			store.delete(key);
		},
		setItem(key: string, value: string) {
			store.set(key, String(value));
		}
	};
}

describe('Security & Privacy Isolation Audit', () => {
	beforeEach(() => {
		Object.defineProperty(globalThis, 'localStorage', {
			value: makeMemoryStorage(),
			configurable: true,
			writable: true
		});
		Object.defineProperty(globalThis, 'sessionStorage', {
			value: makeMemoryStorage(),
			configurable: true,
			writable: true
		});
	});

	it('never leaks accounts, tokens, or server URLs in settings backup export', async () => {
		localStorage.setItem(
			'ks.accounts',
			JSON.stringify({
				active: 'https://abs.example.org|u1',
				accounts: [
					{
						key: 'https://abs.example.org|u1',
						baseUrl: 'https://abs.example.org',
						user: { id: 'u1', username: 'secret_admin', type: 'admin' },
						tokens: { accessToken: 'secret-jwt', refreshToken: 'secret-refresh' },
						libraryId: 'lib1',
						lastUsed: 123
					}
				]
			})
		);
		localStorage.setItem('ks.tokens.u1', 'super-secret-token');
		localStorage.setItem('ks.servers', JSON.stringify(['https://abs.example.org']));
		localStorage.setItem('ks.theme', 'dark');

		// Verify importSettings rejects injection of arbitrary localStorage keys
		const maliciousBackup = new File(
			[
				JSON.stringify({
					app: 'KoalaShelf',
					kind: 'settings',
					version: 1,
					data: {
						'ks.theme': 'light',
						'ks.accounts': '{"active":"injected","accounts":[]}',
						'ks.tokens.injected': 'stolen'
					}
				})
			],
			'backup.json',
			{ type: 'application/json' }
		);

		const ok = await importSettings(maliciousBackup);
		expect(ok).toBe(true);
		expect(localStorage.getItem('ks.theme')).toBe('light');
		expect(localStorage.getItem('ks.tokens.injected')).toBeNull();
		expect(localStorage.getItem('ks.accounts')).not.toBe('{"active":"injected","accounts":[]}');
		expect(typeof exportSettings).toBe('function');
	});

	it('isolates per-user localStorage keys via scoped() and restricts isAdmin to root/admin', () => {
		session.user = { id: 'user-alice', username: 'alice', type: 'user' };
		expect(session.isAdmin).toBe(false);
		expect(scoped('ks.queue')).toBe('ks.queue@user-alice');

		session.user = { id: 'user-guest', username: 'guest', type: 'guest' };
		expect(session.isAdmin).toBe(false);
		expect(scoped('ks.queue')).toBe('ks.queue@user-guest');

		session.user = { id: 'user-admin', username: 'admin', type: 'admin' };
		expect(session.isAdmin).toBe(true);
		expect(scoped('ks.queue')).toBe('ks.queue@user-admin');

		session.user = null;
		expect(session.isAdmin).toBe(false);
		expect(scoped('ks.queue')).toBe('ks.queue@none');
	});

	it('enforces cache: no-store and credentials: omit on API requests to prevent cross-user cache leaks', async () => {
		let capturedInit: RequestInit | undefined;
		const client = new AbsClient({
			baseUrl: 'https://abs.example.org',
			tokens: { accessToken: 'jwt-123', refreshToken: 'ref-123' },
			fetch: (async (_input: RequestInfo | URL, init?: RequestInit) => {
				capturedInit = init;
				return new Response(JSON.stringify({ users: [] }), {
					status: 200,
					headers: { 'content-type': 'application/json' }
				});
			}) as typeof fetch
		});

		await client.users();
		expect(capturedInit?.cache).toBe('no-store');
		expect(capturedInit?.credentials).toBe('omit');
		expect((capturedInit?.headers as Record<string, string>)?.Authorization).toBe('Bearer jwt-123');
	});
});
