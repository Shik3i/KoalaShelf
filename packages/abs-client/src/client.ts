import type {
	Author,
	Bookmark,
	Collection,
	DeviceInfo,
	FilterData,
	Library,
	LibraryItem,
	LibraryItemMinified,
	LoginResponse,
	ListeningStats,
	MediaProgress,
	Paged,
	PlaybackSession,
	ProgressUpdate,
	Series,
	ServerStatus,
	SessionPage,
	SessionSync,
	Shelf,
	SearchResult,
	AuthorDetail,
	SeriesDetail,
	Narrator,
	Playlist,
	YearStats,
	LibraryStats,
	AuthSession,
	User
} from './types.ts';

export type FetchFn = typeof globalThis.fetch;

/** Expiry (seconds since epoch) of a JWT access token, or null when it has none or is opaque. */
export function tokenExpiry(token: string): number | null {
	try {
		const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
		const exp = JSON.parse(atob(payload)).exp;
		return typeof exp === 'number' ? exp : null;
	} catch {
		return null;
	}
}

export interface Tokens {
	accessToken: string;
	/** null in cookie mode (same origin): the server keeps the refresh token in an httpOnly cookie. */
	refreshToken: string | null;
}

export interface AbsClientOptions {
	/** Server root incl. optional sub path, e.g. https://abs.example.org or '' for same origin. */
	baseUrl: string;
	tokens: Tokens;
	/** Called after a token rotation so the caller can persist the new pair. */
	onTokens?: (tokens: Tokens) => void;
	/** Called once the session cannot be refreshed anymore. */
	onAuthLost?: () => void;
	fetch?: FetchFn;
}

export class AbsError extends Error {
	constructor(
		readonly status: number,
		message: string
	) {
		super(message);
		this.name = 'AbsError';
	}
}

type Query = Record<string, string | number | boolean | undefined | null>;

interface RequestOptions {
	method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
	/** Let the request outlive the page (final progress sync on tab close). */
	keepalive?: boolean;
	/** Per-request fetch (e.g. the one SvelteKit passes to load functions). */
	fetch?: FetchFn;
	query?: Query;
	body?: unknown;
	signal?: AbortSignal;
	/** Extra headers, e.g. the refresh token for calls that identify the current device. */
	headers?: Record<string, string>;
	/** Send cookies (same-origin cookie mode); off by default. */
	withCookies?: boolean;
}

/** Trims whitespace and trailing slashes (a loop: a regex here could backtrack on long input). */
export function normalizeBaseUrl(url: string) {
	let s = url.trim();
	while (s.endsWith('/')) s = s.slice(0, -1);
	return s;
}

function withQuery(path: string, query?: Query) {
	if (!query) return path;
	const params = new URLSearchParams();
	for (const [k, v] of Object.entries(query)) {
		if (v === undefined || v === null) continue;
		params.set(k, typeof v === 'boolean' ? (v ? '1' : '0') : String(v));
	}
	const qs = params.toString();
	return qs ? `${path}?${qs}` : path;
}

async function readError(res: Response) {
	const text = await res.text().catch(() => '');
	try {
		return (JSON.parse(text) as { error?: string }).error ?? text;
	} catch {
		return text || res.statusText;
	}
}

/** Unauthenticated: checks that a URL points to an Audiobookshelf server. */
export async function getServerStatus(baseUrl: string, fetchFn: FetchFn = fetch, signal?: AbortSignal) {
	const res = await fetchFn(`${normalizeBaseUrl(baseUrl)}/status`, { signal });
	if (!res.ok) throw new AbsError(res.status, await readError(res));
	const status = (await res.json()) as ServerStatus;
	if (status.app !== 'audiobookshelf') throw new AbsError(0, 'Not an Audiobookshelf server');
	return status;
}

/**
 * Logs in with username/password. In cookie mode the refresh token stays in an
 * httpOnly cookie (only works when the app is served from the same origin).
 */
export async function login(
	baseUrl: string,
	username: string,
	password: string,
	{ cookieMode = false, fetchFn = fetch }: { cookieMode?: boolean; fetchFn?: FetchFn } = {}
) {
	const res = await fetchFn(`${normalizeBaseUrl(baseUrl)}/login`, {
		method: 'POST',
		headers: {
			'Content-Type': 'application/json',
			...(cookieMode ? {} : { 'x-return-tokens': 'true' })
		},
		credentials: cookieMode ? 'same-origin' : 'omit',
		body: JSON.stringify({ username, password })
	});
	if (!res.ok) throw new AbsError(res.status, await readError(res));
	return (await res.json()) as LoginResponse;
}

/** Semver-ish compare: returns true if `version` >= `min`. */
export function isVersionAtLeast(version: string, min: string) {
	const a = version.replace(/^v/, '').split('.').map(Number);
	const b = min.split('.').map(Number);
	for (let i = 0; i < 3; i++) {
		if ((a[i] ?? 0) !== (b[i] ?? 0)) return (a[i] ?? 0) > (b[i] ?? 0);
	}
	return true;
}

/** Access/refresh tokens exist since ABS 2.26. */
export const MIN_SERVER_VERSION = '2.26.0';

export class AbsClient {
	readonly baseUrl: string;
	private tokens: Tokens;
	private refreshing: Promise<boolean> | null = null;
	private readonly fetchFn: FetchFn;

	constructor(private readonly opts: AbsClientOptions) {
		this.baseUrl = normalizeBaseUrl(opts.baseUrl);
		this.tokens = opts.tokens;
		this.fetchFn = opts.fetch ?? globalThis.fetch.bind(globalThis);
	}

	get accessToken() {
		return this.tokens.accessToken;
	}

	/**
	 * Renews the access token if it expires within `marginS` seconds. For URLs handed to the
	 * browser (downloads, media), which cannot refresh on a 401 by themselves.
	 */
	async ensureFresh(marginS = 60) {
		const exp = tokenExpiry(this.tokens.accessToken);
		if (exp !== null && exp - Date.now() / 1000 < marginS) await this.refresh();
	}

	/** Rotates tokens. Concurrent callers share one in-flight refresh. */
	refresh(): Promise<boolean> {
		this.refreshing ??= (async () => {
			try {
				const res = await this.fetchFn(`${this.baseUrl}/auth/refresh`, {
					method: 'POST',
					credentials: this.tokens.refreshToken ? 'omit' : 'same-origin',
					headers: this.tokens.refreshToken ? { 'x-refresh-token': this.tokens.refreshToken } : {}
				});
				if (!res.ok) return false;
				const { user } = (await res.json()) as { user: User };
				this.tokens = {
					accessToken: user.accessToken,
					refreshToken: user.refreshToken ?? this.tokens.refreshToken
				};
				this.opts.onTokens?.(this.tokens);
				return true;
			} catch {
				return false;
			} finally {
				this.refreshing = null;
			}
		})();
		return this.refreshing;
	}

	async request<T>(
		path: string,
		{ method = 'GET', query, body, signal, keepalive, fetch: fetchFn, headers, withCookies }: RequestOptions = {}
	): Promise<T> {
		const send = () =>
			(fetchFn ?? this.fetchFn)(`${this.baseUrl}${withQuery(path, query)}`, {
				method,
				signal,
				keepalive,
				credentials: withCookies ? 'same-origin' : 'omit',
				headers: {
					Authorization: `Bearer ${this.tokens.accessToken}`,
					...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
					...headers
				},
				body: body !== undefined ? JSON.stringify(body) : undefined
			});

		let res = await send();
		if (res.status === 401) {
			if (await this.refresh()) res = await send();
			if (res.status === 401) {
				this.opts.onAuthLost?.();
				throw new AbsError(401, 'Session expired');
			}
		}
		if (!res.ok) throw new AbsError(res.status, await readError(res));
		const type = res.headers.get('content-type') ?? '';
		return (type.includes('application/json') ? await res.json() : await res.text()) as T;
	}

	async logout() {
		await this.fetchFn(`${this.baseUrl}/logout`, {
			method: 'POST',
			credentials: this.tokens.refreshToken ? 'omit' : 'same-origin',
			headers: this.tokens.refreshToken ? { 'x-refresh-token': this.tokens.refreshToken } : {}
		}).catch(() => undefined);
	}

	// --- URLs ---------------------------------------------------------------

	/** Covers are public on the server, so no token ends up in image URLs. */
	coverUrl(itemId: string, { width = 400, updatedAt }: { width?: number; updatedAt?: number } = {}) {
		return withQuery(`${this.baseUrl}/api/items/${itemId}/cover`, { width, format: 'webp', ts: updatedAt });
	}

	authorImageUrl(authorId: string, { width = 200, updatedAt }: { width?: number; updatedAt?: number } = {}) {
		return withQuery(`${this.baseUrl}/api/authors/${authorId}/image`, { width, format: 'webp', ts: updatedAt });
	}

	/** Audio elements cannot send headers, so streams carry the (short-lived) access token. */
	/** The book's files: a ZIP of its folder, or the file itself for single-file items. */
	downloadUrl(itemId: string) {
		return `${this.baseUrl}/api/items/${itemId}/download?token=${encodeURIComponent(this.tokens.accessToken)}`;
	}

	trackUrl(contentUrl: string) {
		const sep = contentUrl.includes('?') ? '&' : '?';
		return `${this.baseUrl}${contentUrl}${sep}token=${encodeURIComponent(this.tokens.accessToken)}`;
	}

	// --- Me -----------------------------------------------------------------

	me() {
		return this.request<User>('/api/me');
	}

	itemsInProgress() {
		return this.request<{ libraryItems: LibraryItem[] }>('/api/me/items-in-progress').then((r) => r.libraryItems);
	}

	progress(itemId: string) {
		return this.request<MediaProgress>(`/api/me/progress/${itemId}`);
	}

	updateProgress(itemId: string, update: ProgressUpdate) {
		return this.request<void>(`/api/me/progress/${itemId}`, { method: 'PATCH', body: update });
	}

	/** Progress of several books in one request (e.g. mark a selection finished). */
	updateProgressMany(updates: (ProgressUpdate & { libraryItemId: string })[]) {
		return this.request<void>('/api/me/progress/batch/update', { method: 'PATCH', body: updates });
	}

	/** Forgets a book's progress entirely, as if never started (progress id, not item id). */
	removeProgress(progressId: string) {
		return this.request<void>(`/api/me/progress/${progressId}`, { method: 'DELETE' });
	}

	/** Hides an item from "continue listening" without touching its progress. */
	hideFromContinueListening(progressId: string) {
		return this.request<void>(`/api/me/progress/${progressId}/remove-from-continue-listening`);
	}

	/** The user's listening history, newest first. */
	listeningSessions(page = 0, itemsPerPage = 50) {
		return this.request<SessionPage>('/api/me/listening-sessions', { query: { page, itemsPerPage } });
	}

	/** Listening history of one book, newest first. */
	itemListeningSessions(itemId: string, page = 0, itemsPerPage = 20) {
		return this.request<SessionPage>(`/api/me/item/listening-sessions/${itemId}`, { query: { page, itemsPerPage } });
	}

	listeningStats() {
		return this.request<ListeningStats>('/api/me/listening-stats');
	}

	collections(libraryId: string) {
		return this.request<Paged<Collection>>(`/api/libraries/${libraryId}/collections`).then((r) => r.results);
	}

	collection(id: string) {
		return this.request<Collection>(`/api/collections/${id}`);
	}

	bookmarks(itemId: string) {
		return this.request<{ bookmarks: Bookmark[] }>(`/api/me/bookmarks/${itemId}`).then((r) => r.bookmarks);
	}

	createBookmark(itemId: string, time: number, title: string) {
		return this.request<Bookmark>(`/api/me/item/${itemId}/bookmark`, { method: 'POST', body: { time, title } });
	}

	updateBookmark(itemId: string, time: number, title: string) {
		return this.request<Bookmark>(`/api/me/item/${itemId}/bookmark`, { method: 'PATCH', body: { time, title } });
	}

	deleteBookmark(itemId: string, time: number) {
		return this.request<void>(`/api/me/item/${itemId}/bookmark/${time}`, { method: 'DELETE' });
	}

	// --- Libraries ----------------------------------------------------------

	libraries() {
		return this.request<{ libraries: Library[] }>('/api/libraries').then((r) => r.libraries);
	}

	libraryItems(
		libraryId: string,
		q: { page?: number; limit?: number; sort?: string; desc?: boolean; filter?: string; collapseSeries?: boolean } = {},
		signal?: AbortSignal,
		fetchFn?: FetchFn
	) {
		return this.request<Paged<LibraryItemMinified>>(`/api/libraries/${libraryId}/items`, {
			signal,
			fetch: fetchFn,
			query: {
				limit: q.limit ?? 60,
				page: q.page ?? 0,
				sort: q.sort,
				desc: q.desc,
				filter: q.filter,
				collapseseries: q.collapseSeries,
				minified: true
			}
		});
	}

	filterData(libraryId: string) {
		return this.request<FilterData>(`/api/libraries/${libraryId}/filterdata`);
	}

	series(libraryId: string, q: { page?: number; limit?: number; sort?: string; desc?: boolean } = {}, signal?: AbortSignal) {
		return this.request<Paged<Series>>(`/api/libraries/${libraryId}/series`, {
			signal,
			query: { limit: q.limit ?? 40, page: q.page ?? 0, sort: q.sort, desc: q.desc, minified: true }
		});
	}

	authors(libraryId: string) {
		return this.request<{ authors: Author[] }>(`/api/libraries/${libraryId}/authors`).then((r) => r.authors);
	}

	personalized(libraryId: string, limit = 12) {
		return this.request<Shelf[]>(`/api/libraries/${libraryId}/personalized`, { query: { limit, minified: true } });
	}

	search(libraryId: string, q: string, limit = 12, signal?: AbortSignal) {
		return this.request<SearchResult>(`/api/libraries/${libraryId}/search`, { query: { q, limit }, signal });
	}

	// --- Browse -------------------------------------------------------------

	author(authorId: string, libraryId: string, fetchFn?: FetchFn) {
		return this.request<AuthorDetail>(`/api/authors/${authorId}`, {
			query: { include: 'items,series', library: libraryId },
			fetch: fetchFn
		});
	}

	seriesDetail(libraryId: string, seriesId: string, fetchFn?: FetchFn) {
		return this.request<SeriesDetail>(`/api/libraries/${libraryId}/series/${seriesId}`, {
			query: { include: 'progress' },
			fetch: fetchFn
		});
	}

	/** Takes a series off (or back onto) the "continue series" shelf. */
	setSeriesInContinue(seriesId: string, shown: boolean) {
		return this.request<unknown>(
			`/api/me/series/${seriesId}/${shown ? 'readd-to-continue-listening' : 'remove-from-continue-listening'}`
		);
	}

	narrators(libraryId: string) {
		return this.request<{ narrators: Narrator[] }>(`/api/libraries/${libraryId}/narrators`).then((r) => r.narrators);
	}

	libraryStats(libraryId: string) {
		return this.request<LibraryStats>(`/api/libraries/${libraryId}/stats`);
	}

	yearStats(year: number) {
		return this.request<YearStats>(`/api/me/stats/year/${year}`);
	}

	/** Every bookmark of the user, across books. */
	allBookmarks() {
		return this.request<{ bookmarks: Bookmark[] }>('/api/me/bookmarks').then((r) => r.bookmarks);
	}

	// --- Playlists ------------------------------------------------------------

	playlists(libraryId: string) {
		return this.request<Paged<Playlist>>(`/api/libraries/${libraryId}/playlists`, { query: { limit: 0 } }).then(
			(r) => r.results
		);
	}

	playlist(id: string, fetchFn?: FetchFn) {
		return this.request<Playlist>(`/api/playlists/${id}`, { fetch: fetchFn });
	}

	createPlaylist(libraryId: string, name: string, itemIds: string[] = []) {
		return this.request<Playlist>('/api/playlists', {
			method: 'POST',
			body: { libraryId, name, items: itemIds.map((libraryItemId) => ({ libraryItemId })) }
		});
	}

	/** Rename and/or reorder (the item list replaces the current one). */
	updatePlaylist(id: string, changes: { name?: string; description?: string; itemIds?: string[] }) {
		const { itemIds, ...rest } = changes;
		return this.request<Playlist>(`/api/playlists/${id}`, {
			method: 'PATCH',
			body: { ...rest, ...(itemIds ? { items: itemIds.map((libraryItemId) => ({ libraryItemId })) } : {}) }
		});
	}

	deletePlaylist(id: string) {
		return this.request<unknown>(`/api/playlists/${id}`, { method: 'DELETE' });
	}

	addToPlaylist(id: string, libraryItemId: string) {
		return this.request<Playlist>(`/api/playlists/${id}/item`, { method: 'POST', body: { libraryItemId } });
	}

	removeFromPlaylist(id: string, libraryItemId: string) {
		return this.request<Playlist>(`/api/playlists/${id}/item/${libraryItemId}`, { method: 'DELETE' });
	}

	addManyToPlaylist(id: string, itemIds: string[]) {
		return this.request<Playlist>(`/api/playlists/${id}/batch/add`, {
			method: 'POST',
			body: { items: itemIds.map((libraryItemId) => ({ libraryItemId })) }
		});
	}

	removeManyFromPlaylist(id: string, itemIds: string[]) {
		return this.request<Playlist>(`/api/playlists/${id}/batch/remove`, {
			method: 'POST',
			body: { items: itemIds.map((libraryItemId) => ({ libraryItemId })) }
		});
	}

	/** A new playlist with a collection's books (in its order). */
	playlistFromCollection(collectionId: string) {
		return this.request<Playlist>(`/api/playlists/collection/${collectionId}`, { method: 'POST' });
	}

	// --- Collections (need the "update" permission; deleting needs "delete") -----

	createCollection(libraryId: string, name: string, bookIds: string[] = [], description?: string) {
		return this.request<Collection>('/api/collections', {
			method: 'POST',
			body: { libraryId, name, description, books: bookIds }
		});
	}

	/** Rename, describe and/or reorder (the book list replaces the current order). */
	updateCollection(id: string, changes: { name?: string; description?: string; bookIds?: string[] }) {
		const { bookIds, ...rest } = changes;
		return this.request<Collection>(`/api/collections/${id}`, {
			method: 'PATCH',
			body: { ...rest, ...(bookIds ? { books: bookIds } : {}) }
		});
	}

	deleteCollection(id: string) {
		return this.request<unknown>(`/api/collections/${id}`, { method: 'DELETE' });
	}

	addToCollection(id: string, libraryItemId: string) {
		return this.request<Collection>(`/api/collections/${id}/book`, { method: 'POST', body: { id: libraryItemId } });
	}

	removeFromCollection(id: string, libraryItemId: string) {
		return this.request<Collection>(`/api/collections/${id}/book/${libraryItemId}`, { method: 'DELETE' });
	}

	addManyToCollection(id: string, itemIds: string[]) {
		return this.request<Collection>(`/api/collections/${id}/batch/add`, { method: 'POST', body: { books: itemIds } });
	}

	removeManyFromCollection(id: string, itemIds: string[]) {
		return this.request<Collection>(`/api/collections/${id}/batch/remove`, {
			method: 'POST',
			body: { books: itemIds }
		});
	}

	// --- Account ----------------------------------------------------------------

	changePassword(password: string, newPassword: string) {
		return this.request<unknown>('/api/me/password', { method: 'PATCH', body: { password, newPassword } });
	}

	/** Signed-in devices of this user, most recently used first. */
	authSessions() {
		// The refresh token lets the server mark which session is this device's.
		const refresh = this.tokens.refreshToken;
		return this.request<{ sessions: AuthSession[] }>('/api/me/sessions', {
			query: { itemsPerPage: 50 },
			headers: refresh ? { 'x-refresh-token': refresh } : undefined,
			withCookies: !refresh
		}).then((r) => r.sessions);
	}

	signOutDevice(sessionId: string) {
		return this.request<unknown>(`/api/me/sessions/${sessionId}`, { method: 'DELETE' });
	}

	// --- Items & playback ---------------------------------------------------

	item(itemId: string, signal?: AbortSignal, fetchFn?: FetchFn) {
		return this.request<LibraryItem>(`/api/items/${itemId}`, {
			signal,
			fetch: fetchFn,
			query: { expanded: 1, include: 'progress' }
		});
	}

	/** Several expanded items in one request. */
	items(ids: string[]) {
		return this.request<{ libraryItems: LibraryItem[] }>('/api/items/batch/get', {
			method: 'POST',
			body: { libraryItemIds: ids }
		}).then((r) => r.libraryItems);
	}

	play(itemId: string, deviceInfo: DeviceInfo, supportedMimeTypes: string[]) {
		return this.request<PlaybackSession>(`/api/items/${itemId}/play`, {
			method: 'POST',
			body: { deviceInfo, supportedMimeTypes, forceDirectPlay: false, forceTranscode: false, mediaPlayer: 'html5' }
		});
	}

	syncSession(sessionId: string, sync: SessionSync, keepalive = false) {
		return this.request<void>(`/api/session/${sessionId}/sync`, { method: 'POST', body: sync, keepalive });
	}

	/** Uploads a playback session recorded offline (server keeps the newer progress). */
	syncLocalSession(local: Record<string, unknown>) {
		return this.request<void>('/api/session/local', { method: 'POST', body: local });
	}

	/** Raw authorized fetch for binary downloads (audio files, covers). */
	async download(path: string, signal?: AbortSignal) {
		const send = () =>
			this.fetchFn(`${this.baseUrl}${path}`, {
				signal,
				credentials: 'omit',
				headers: { Authorization: `Bearer ${this.tokens.accessToken}` }
			});
		let res = await send();
		if (res.status === 401 && (await this.refresh())) res = await send();
		if (!res.ok) throw new AbsError(res.status, res.statusText);
		return res;
	}

	closeSession(sessionId: string, sync?: SessionSync) {
		return this.request<void>(`/api/session/${sessionId}/close`, { method: 'POST', body: sync ?? {} });
	}
}
