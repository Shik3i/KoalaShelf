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
import { apiFetch, isTauri } from './platform.ts';
import { invalidate } from './cache.ts';

/**
 * Persisted login. Privacy: only what is needed to resume the session is stored,
 * locally in this browser. In cookie mode (same origin) no refresh token is stored
 * at all: the server keeps it in an httpOnly cookie.
 */
interface Stored {
	baseUrl: string;
	tokens: Tokens;
	user: { id: string; username: string };
	libraryId: string | null;
}

const KEY = 'ks.session';

function load(): Stored | null {
	try {
		return JSON.parse(localStorage.getItem(KEY) ?? 'null');
	} catch {
		return null;
	}
}

function save(s: Stored | null) {
	try {
		if (s) localStorage.setItem(KEY, JSON.stringify(s));
		else localStorage.removeItem(KEY);
	} catch {
		/* storage blocked: session lives in memory only */
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
 * the app's own origin. This returns that server's public URL, or null when there is none.
 * Signing in to exactly this server then goes through the proxy: no CORS, httpOnly cookie.
 */
export async function presetServer(): Promise<string | null> {
	if (isTauri) return null;
	try {
		const res = await fetch('/koalashelf.json', { signal: AbortSignal.timeout(2500) });
		if (!res.ok || !res.headers.get('content-type')?.includes('json')) return null;
		const { server } = (await res.json()) as { server?: string };
		return server ? normalizeBaseUrl(server) : null;
	} catch {
		return null;
	}
}

class Session {
	client = $state.raw<AbsClient | null>(null);
	user = $state<Stored['user'] | null>(null);
	libraries = $state.raw<Library[]>([]);
	libraryId = $state<string | null>(null);
	ready = $state(false);
	/** Set when the server rejected the refresh token (shown on the sign-in page). */
	expired = $state(false);

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

	/** Non-null client for code paths that run behind the auth guard. */
	get api(): AbsClient {
		if (!this.client) throw new Error('Not signed in');
		return this.client;
	}

	#persist() {
		if (!this.client || !this.user) return save(null);
		save({
			baseUrl: this.client.baseUrl,
			tokens: { accessToken: this.client.accessToken, refreshToken: this.#refreshToken },
			user: this.user,
			libraryId: this.libraryId
		});
	}

	#refreshToken: string | null = null;

	#fetch: FetchFn = globalThis.fetch.bind(globalThis);

	#makeClient(baseUrl: string, tokens: Tokens) {
		this.#refreshToken = tokens.refreshToken;
		return new AbsClient({
			baseUrl,
			fetch: this.#fetch,
			tokens,
			onTokens: (t) => {
				this.#refreshToken = t.refreshToken;
				this.#persist();
			},
			onAuthLost: () => {
				this.expired = true;
				this.#clear();
			}
		});
	}

	#restoring: Promise<void> | null = null;

	/** Restores the stored session once; safe to call from every load function. */
	restore() {
		this.#restoring ??= this.#restore();
		return this.#restoring;
	}

	async #restore() {
		this.#fetch = await apiFetch();
		const stored = load();
		if (stored) {
			this.client = this.#makeClient(stored.baseUrl, stored.tokens);
			this.user = stored.user;
			this.libraryId = stored.libraryId;
			try {
				await this.#loadLibraries();
			} catch {
				// Offline or server down: keep the session, views show their own errors.
			}
		}
		this.ready = true;
		this.#resolveReady();
	}

	async signIn(serverUrl: string, username: string, password: string) {
		const baseUrl = normalizeBaseUrl(serverUrl);
		this.#fetch = await apiFetch();
		const status = await getServerStatus(baseUrl, this.#fetch);
		if (!isVersionAtLeast(status.serverVersion, MIN_SERVER_VERSION)) {
			throw new ServerTooOldError(status.serverVersion, MIN_SERVER_VERSION);
		}
		const cookieMode = baseUrl === '';
		const res = await login(baseUrl, username, password, { cookieMode, fetchFn: this.#fetch });
		this.expired = false;
		this.client = this.#makeClient(baseUrl, {
			accessToken: res.user.accessToken,
			refreshToken: cookieMode ? null : res.user.refreshToken
		});
		this.user = { id: res.user.id, username: res.user.username };
		this.libraryId = res.userDefaultLibraryId ?? null;
		await this.#loadLibraries();
		this.#persist();
	}

	async signOut() {
		await this.client?.logout();
		this.#clear();
	}

	selectLibrary(id: string) {
		this.libraryId = id;
		this.#persist();
	}

	async #loadLibraries() {
		const libs = (await this.api.libraries()).filter((l) => l.mediaType === 'book');
		this.libraries = libs.sort((a, b) => a.displayOrder - b.displayOrder);
		if (!libs.some((l) => l.id === this.libraryId)) this.libraryId = libs[0]?.id ?? null;
	}

	#signOutHooks = new Set<() => void>();

	/** Runs before the session is cleared (sign-out or lost auth), e.g. to stop playback. */
	onSignOut(fn: () => void) {
		this.#signOutHooks.add(fn);
	}

	#clear() {
		for (const fn of this.#signOutHooks) {
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
		save(null);
	}
}

export const session = new Session();
