import {
	AbsClient,
	MIN_SERVER_VERSION,
	getServerStatus,
	isVersionAtLeast,
	login,
	normalizeBaseUrl,
	type Library,
	type FetchFn,
	type Tokens
} from '@koalashelf/abs-client';
import { apiFetch, isDesktop, vault } from './platform.ts';
import { invalidate } from './cache.ts';

/**
 * Saved accounts, one of them active. Privacy: only what is needed to resume a session is
 * stored, locally in this browser. Through the bundled proxy the first account keeps its
 * refresh token in an httpOnly cookie (never in storage); further accounts on the same
 * address, and accounts on other servers, keep theirs here. Signing out of an account
 * revokes its token on the server and forgets everything stored for it on this device.
 */
export interface SavedAccount {
	/** Server address + user id: one entry per account per server. */
	key: string;
	baseUrl: string;
	user: { id: string; username: string; type?: 'root' | 'admin' | 'user' | 'guest' };
	/** Null when the server ended the session: the account waits for its password. */
	tokens: Tokens | null;
	libraryId: string | null;
	/** Last known libraries, so the app works offline. */
	libraries?: Library[];
	lastUsed: number;
}

interface Stored {
	active: string | null;
	accounts: SavedAccount[];
}

const KEY = 'ks.accounts';
const LEGACY_KEY = 'ks.session';
/** Per-account data (queue, last book, recent searches, offline sessions), see scoped(). */
const SCOPED = ['ks.queue', 'ks.lastItem', 'ks.recentSearches', 'ks.localSessions'];

const keyOf = (baseUrl: string, userId: string) => `${baseUrl}|${userId}`;

function load(): Stored {
	try {
		const stored = JSON.parse(localStorage.getItem(KEY) ?? 'null') as Stored | null;
		if (stored && Array.isArray(stored.accounts)) return stored;
		// Earlier versions kept one session: it becomes the first saved account.
		const legacy = JSON.parse(localStorage.getItem(LEGACY_KEY) ?? 'null');
		if (legacy?.user?.id) {
			const account: SavedAccount = {
				key: keyOf(legacy.baseUrl, legacy.user.id),
				baseUrl: legacy.baseUrl,
				user: legacy.user,
				tokens: legacy.tokens,
				libraryId: legacy.libraryId ?? null,
				libraries: legacy.libraries,
				lastUsed: Date.now()
			};
			for (const k of SCOPED) {
				const v = localStorage.getItem(k);
				if (v !== null) localStorage.setItem(`${k}@${legacy.user.id}`, v);
				localStorage.removeItem(k);
			}
			localStorage.removeItem(LEGACY_KEY);
			return { active: account.key, accounts: [account] };
		}
	} catch {
		/* unreadable: start empty */
	}
	return { active: null, accounts: [] };
}

function save(s: Stored) {
	try {
		if (isDesktop) {
			const sanitized: Stored = {
				active: s.active,
				accounts: s.accounts.map((a) => ({
					...a,
					tokens: null
				}))
			};
			localStorage.setItem(KEY, JSON.stringify(sanitized));
			return;
		}
		localStorage.setItem(KEY, JSON.stringify(s));
	} catch {
		/* storage blocked: accounts live in memory only */
	}
}

/**
 * "audio.example.org". Accounts behind the bundled proxy show the Audiobookshelf server's
 * own address (from the container), never the app's: the proxy is an implementation detail.
 */
export function serverLabel(baseUrl: string) {
	try {
		// eslint-disable-next-line svelte/prefer-svelte-reactivity -- parsed once, not state
		return new URL(baseUrl || session.presetUrl || location.origin).host;
	} catch {
		return baseUrl;
	}
}

export class ServerTooOldError extends Error {
	constructor(
		readonly version: string,
		readonly min: string
	) {
		super(`Audiobookshelf ${version} < ${min}`);
	}
}

/**
 * The Docker image (and the dev server) proxies one preconfigured Audiobookshelf server on
 * the app's own origin. This returns that server's public URL (or, when none is configured,
 * the app's own address, which reaches the same server), or null when there is no proxy.
 * Signing in to exactly this server then goes through the proxy: no CORS, httpOnly cookie.
 */
export async function presetServer(): Promise<string | null> {
	if (isDesktop) return null;
	try {
		const res = await fetch('/koalashelf.json', { signal: AbortSignal.timeout(2500) });
		if (!res.ok || !res.headers.get('content-type')?.includes('json')) return null;
		const { server } = (await res.json()) as { server?: string };
		if (typeof server !== 'string') return null;
		return normalizeBaseUrl(server || location.origin);
	} catch {
		return null;
	}
}

class Session {
	client = $state.raw<AbsClient | null>(null);
	user = $state<SavedAccount['user'] | null>(null);
	libraries = $state.raw<Library[]>([]);
	libraryId = $state<string | null>(null);
	ready = $state(false);
	/** True once the server answered the library list (not just the offline copy). */
	librariesLoaded = $state(false);
	/** Set when the server rejected the refresh token (shown on the sign-in page). */
	expired = $state(false);
	/** Server permission to download files (false hides download buttons). */
	canDownload = $state(true);
	canUpdate = $state(false);
	canDelete = $state(false);

	get isAdmin(): boolean {
		return this.user?.type === 'root' || this.user?.type === 'admin';
	}
	/** Every saved account, most recently used first; the active one is activeKey. */
	accounts = $state.raw<SavedAccount[]>([]);
	activeKey = $state<string | null>(null);
	/** Switching accounts right now (the UI waits). */
	switching = $state(false);
	/** The Audiobookshelf address the bundled proxy serves (null without one). */
	presetUrl = $state<string | null>(null);

	#readyPromise: Promise<void>;
	#resolveReady!: () => void;

	constructor() {
		this.#readyPromise = new Promise((r) => (this.#resolveReady = r));
	}

	/** Resolves once the stored session was restored (or found missing). */
	whenReady() {
		return this.#readyPromise;
	}

	get library() {
		return this.libraries.find((l) => l.id === this.libraryId) ?? null;
	}

	get active() {
		return this.accounts.find((a) => a.key === this.activeKey) ?? null;
	}

	/** Non-null client for code paths that run behind the auth guard. */
	get api(): AbsClient {
		if (!this.client) throw new Error('Not signed in');
		return this.client;
	}

	#store() {
		save({ active: this.activeKey, accounts: this.accounts });
	}

	#update(key: string, change: Partial<SavedAccount>) {
		this.accounts = this.accounts.map((a) => (a.key === key ? { ...a, ...change } : a));
		this.#store();
	}

	#persist() {
		const key = this.activeKey;
		if (!key || !this.client || !this.user) return;
		const tokens: Tokens = {
			accessToken: this.client.accessToken,
			refreshToken: this.#refreshToken
		};
		if (isDesktop) {
			void vault.set(`ks.tokens.${key}`, JSON.stringify(tokens));
		}
		this.#update(key, {
			tokens,
			libraryId: this.libraryId,
			libraries: this.libraries
		});
	}

	#refreshToken: string | null = null;

	#fetch: FetchFn = globalThis.fetch.bind(globalThis);

	#makeClient(baseUrl: string, tokens: Tokens) {
		this.#refreshToken = tokens.refreshToken;
		const client: AbsClient = new AbsClient({
			baseUrl,
			fetch: this.#fetch,
			tokens,
			onTokens: (t) => {
				if (this.client !== client) return;
				this.#refreshToken = t.refreshToken;
				this.#persist();
			},
			onAuthLost: () => {
				if (this.client !== client) return;
				// The account stays listed and waits for its password.
				const key = this.activeKey;
				this.expired = true;
				this.#leave();
				if (key) {
					if (isDesktop) {
						void vault.delete(`ks.tokens.${key}`);
					}
					this.#update(key, { tokens: null });
				}
			}
		});
		return client;
	}

	#restoring: Promise<void> | null = null;

	/** Restores the stored session once; safe to call from every load function. */
	restore() {
		this.#restoring ??= this.#restore();
		return this.#restoring;
	}

	async #restore() {
		this.#fetch = await apiFetch();
		void presetServer().then((p) => (this.presetUrl = p));
		const stored = load();
		this.accounts = stored.accounts;
		this.activeKey = stored.active;

		if (isDesktop) {
			for (const acc of this.accounts) {
				// Migrate any legacy tokens from localStorage into secure storage
				if (acc.tokens) {
					await vault.set(`ks.tokens.${acc.key}`, JSON.stringify(acc.tokens));
				}
				// Retrieve decrypted tokens from safeStorage
				const secret = await vault.get(`ks.tokens.${acc.key}`);
				if (secret) {
					try {
						acc.tokens = JSON.parse(secret) as Tokens;
					} catch {
						acc.tokens = null;
					}
				} else if (!acc.tokens) {
					acc.tokens = null;
				}
			}
			// Persist sanitized state to ensure localStorage has no plaintext tokens
			this.#store();
		}

		const account = this.active;
		if (account?.tokens) {
			this.#enter(account);
			try {
				await this.#loadLibraries();
			} catch {
				// Offline or server down: keep the session, views show their own errors.
			}
		} else this.activeKey = null;
		this.ready = true;
		this.#resolveReady();
	}

	/** The cookie belongs to one account per proxy address; others keep their own token. */
	#cookieTaken(key: string) {
		return this.accounts.some(
			(a) => a.baseUrl === '' && a.key !== key && a.tokens && !a.tokens.refreshToken
		);
	}

	/**
	 * Signs in (or adds another account, or renews one whose session ended). The account
	 * becomes the active one; other saved accounts stay signed in.
	 */
	async signIn(serverUrl: string, username: string, password: string) {
		const baseUrl = normalizeBaseUrl(serverUrl);
		this.#fetch = await apiFetch();
		const status = await getServerStatus(baseUrl, this.#fetch);
		if (!isVersionAtLeast(status.serverVersion, MIN_SERVER_VERSION)) {
			throw new ServerTooOldError(status.serverVersion, MIN_SERVER_VERSION);
		}
		const probeKey = (id: string) => keyOf(baseUrl, id);
		// Which user this is, is only known after signing in: try the cookie only when free.
		const known = this.accounts.find(
			(a) => a.baseUrl === baseUrl && a.user.username.toLowerCase() === username.toLowerCase()
		);
		const cookieMode = baseUrl === '' && !this.#cookieTaken(known?.key ?? '');
		const res = await login(baseUrl, username, password, { cookieMode, fetchFn: this.#fetch });
		const key = probeKey(res.user.id);
		const tokens: Tokens = {
			accessToken: res.user.accessToken,
			refreshToken: cookieMode ? null : res.user.refreshToken
		};
		if (isDesktop) {
			await vault.set(`ks.tokens.${key}`, JSON.stringify(tokens));
		}
		const existingAccount = this.accounts.find((a) => a.key === key);
		const initialLibraryId = existingAccount?.libraryId ?? res.userDefaultLibraryId ?? null;
		const prefetchClient = new AbsClient({
			baseUrl,
			fetch: this.#fetch,
			tokens
		});
		let prefetchedLibs: Library[] | undefined;
		try {
			const rawLibs = await prefetchClient.libraries();
			prefetchedLibs = rawLibs
				.filter((l) => l.mediaType === 'book')
				.sort((a, b) => a.displayOrder - b.displayOrder);
		} catch {
			prefetchedLibs = existingAccount?.libraries;
		}
		const resolvedLibraryId = prefetchedLibs
			? prefetchedLibs.some((l) => l.id === initialLibraryId)
				? initialLibraryId
				: (prefetchedLibs[0]?.id ?? null)
			: initialLibraryId;

		const account: SavedAccount = {
			key,
			baseUrl,
			user: { id: res.user.id, username: res.user.username, type: res.user.type },
			tokens,
			libraryId: resolvedLibraryId,
			libraries: prefetchedLibs,
			lastUsed: Date.now()
		};
		this.#leave();
		this.accounts = [account, ...this.accounts.filter((a) => a.key !== key)];
		this.activeKey = key;
		this.expired = false;
		this.#enter(account);
		if (prefetchedLibs !== undefined) {
			this.librariesLoaded = true;
		} else {
			try {
				await this.#loadLibraries();
			} catch {
				/* offline fallback */
			}
		}
		this.#persist();
	}

	/** Makes another saved account the active one. False when it needs its password again. */
	async switchTo(key: string) {
		if (key === this.activeKey) return true;
		const target = this.accounts.find((a) => a.key === key);
		if (!target) return false;
		if (isDesktop && !target.tokens) {
			const secret = await vault.get(`ks.tokens.${key}`);
			if (secret) {
				try {
					target.tokens = JSON.parse(secret) as Tokens;
				} catch {
					target.tokens = null;
				}
			}
		}
		if (!target.tokens) return false;
		this.switching = true;
		try {
			this.#leave();
			this.activeKey = key;
			this.#update(key, { lastUsed: Date.now() });
			this.#enter(target);
			try {
				await this.#loadLibraries();
			} catch {
				// Offline: the account still opens with its last known libraries.
			}
			return this.client !== null;
		} finally {
			this.switching = false;
		}
	}

	/** Signs the active account out (revoked on the server) and forgets it on this device. */
	async signOut() {
		const key = this.activeKey;
		await this.client?.logout();
		this.#leave();
		if (key) {
			if (isDesktop) {
				void vault.delete(`ks.tokens.${key}`);
			}
			this.#forgetLocal(key);
		}
	}

	/** Signs a saved account out and forgets it (the active one or any other). */
	async forget(key: string) {
		if (key === this.activeKey) return this.signOut();
		const account = this.accounts.find((a) => a.key === key);
		if (account?.tokens) {
			const client = new AbsClient({
				baseUrl: account.baseUrl,
				fetch: this.#fetch,
				tokens: account.tokens
			});
			await client.logout();
		}
		if (isDesktop) {
			void vault.delete(`ks.tokens.${key}`);
		}
		this.#forgetLocal(key);
	}

	#forgetLocal(key: string) {
		const account = this.accounts.find((a) => a.key === key);
		this.accounts = this.accounts.filter((a) => a.key !== key);
		if (this.activeKey === key) this.activeKey = null;
		this.#store();
		// Its queue, last book, recent searches and offline sessions go too (unless the same
		// user is also saved under another address).
		const id = account?.user.id;
		if (id && !this.accounts.some((a) => a.user.id === id)) {
			for (const k of SCOPED) localStorage.removeItem(`${k}@${id}`);
			for (const fn of this.#forgetHooks) void Promise.resolve(fn(id)).catch(() => undefined);
		}
	}

	selectLibrary(id: string) {
		this.libraryId = id;
		this.#persist();
	}

	async #loadLibraries() {
		const libs = (await this.api.libraries()).filter((l) => l.mediaType === 'book');
		this.libraries = libs.sort((a, b) => a.displayOrder - b.displayOrder);
		this.librariesLoaded = true;
		if (!libs.some((l) => l.id === this.libraryId)) this.libraryId = libs[0]?.id ?? null;
		this.#persist();
	}

	#leaveHooks = new Set<() => void>();
	#forgetHooks = new Set<(userId: string) => unknown>();

	/** Runs when an account is forgotten on this device (e.g. to delete its downloads). */
	onForget(fn: (userId: string) => unknown) {
		this.#forgetHooks.add(fn);
	}
	#enterHooks = new Set<() => void>();

	/** Runs before the active account changes or signs out (stop playback, close sockets). */
	onSignOut(fn: () => void) {
		this.#leaveHooks.add(fn);
	}

	/** Runs after an account became the active one (reload per-account data). */
	onEnter(fn: () => void) {
		this.#enterHooks.add(fn);
	}

	#enter(account: SavedAccount) {
		this.client = this.#makeClient(account.baseUrl, account.tokens!);
		this.user = account.user;
		this.libraryId = account.libraryId;
		this.libraries = account.libraries ?? [];
		this.librariesLoaded = false;
		if (!account.user.type && this.client) {
			void this.client
				.me()
				.then((me) => {
					if (this.activeKey === account.key) {
						this.user = { id: me.id, username: me.username, type: me.type };
						this.#update(account.key, { user: this.user });
					}
				})
				.catch(() => undefined);
		}
		for (const fn of this.#enterHooks) {
			try {
				fn();
			} catch {
				/* keep entering */
			}
		}
	}

	#leave() {
		if (!this.client) return;
		for (const fn of this.#leaveHooks) {
			try {
				fn();
			} catch {
				/* keep clearing */
			}
		}
		invalidate();
		this.client = null;
		this.user = null;
		this.libraries = [];
		this.libraryId = null;
		this.canUpdate = false;
		this.canDelete = false;
		this.canDownload = true;
	}
}

export const session = new Session();

/** A storage key for the active account's own data, e.g. its queue. */
export function scoped(key: string) {
	return `${key}@${session.user?.id ?? 'none'}`;
}
