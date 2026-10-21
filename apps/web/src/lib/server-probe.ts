// Finds and validates an Audiobookshelf server from what the user typed.
import { AbsError, getServerStatus, normalizeBaseUrl } from '@koalashelf/abs-client';
import { apiFetch, isTauri, isDesktop } from './platform.ts';

export type ProbeFailure = 'not-abs' | 'cors' | 'unreachable' | 'local-disabled';

export class ProbeError extends Error {
	constructor(readonly kind: ProbeFailure) {
		super(kind);
	}
}

const RECENT_KEY = 'ks.servers';

/** "nas:13378" -> try https first, then http (home servers often run without TLS). */
function candidates(input: string) {
	const raw = normalizeBaseUrl(input);
	if (/^https?:\/\//i.test(raw)) return [raw];
	return [`https://${raw}`, `http://${raw}`];
}

/**
 * A failed fetch hides the reason. If a no-cors request still gets through, the server is
 * reachable and only CORS blocks us: worth a precise hint instead of "unreachable".
 */
async function reachableWithoutCors(url: string, signal?: AbortSignal) {
	try {
		await fetch(`${url}/status`, { mode: 'no-cors', signal });
		return true;
	} catch {
		return false;
	}
}

export async function probeServer(input: string, signal?: AbortSignal) {
	const fetchFn = await apiFetch();
	let failure: ProbeFailure = 'unreachable';
	for (const url of candidates(input)) {
		try {
			const status = await getServerStatus(url, fetchFn, signal);
			assertLocalLogin(status.authMethods);
			return { url, version: status.serverVersion };
		} catch (e) {
			if (signal?.aborted || e instanceof ProbeError) throw e;
			if (e instanceof AbsError) failure = 'not-abs';
			else if (!isTauri && !isDesktop && (await reachableWithoutCors(url, signal))) failure = 'cors';
		}
	}
	throw new ProbeError(failure);
}

/** KoalaShelf signs in with username/password; SSO-only servers get a clear message. */
function assertLocalLogin(methods: string[] | undefined) {
	if (methods && !methods.includes('local')) throw new ProbeError('local-disabled');
}

/** The preconfigured server, reached through the app's own origin (proxy). */
export async function probeProxy(signal?: AbortSignal) {
	const status = await getServerStatus('', fetch, signal);
	assertLocalLogin(status.authMethods);
	return { url: '', version: status.serverVersion };
}

/** Same server regardless of scheme, case or trailing slash ("nas:13378" == "http://NAS:13378/"). */
export function sameServer(a: string, b: string) {
	const key = (u: string) =>
		normalizeBaseUrl(u)
			.replace(/^https?:\/\//i, '')
			.toLowerCase();
	return key(a) === key(b);
}

export function recentServers(): string[] {
	try {
		const list = JSON.parse(localStorage.getItem(RECENT_KEY) ?? '[]');
		const legacy = localStorage.getItem('ks.lastServer');
		return Array.isArray(list) ? list : legacy ? [legacy] : [];
	} catch {
		return [];
	}
}

export function rememberServer(url: string) {
	const list = [url, ...recentServers().filter((s) => s !== url)].slice(0, 5);
	try {
		localStorage.setItem(RECENT_KEY, JSON.stringify(list));
	} catch {
		/* ignore */
	}
	return list;
}

export function forgetServer(url: string) {
	const list = recentServers().filter((s) => s !== url);
	try {
		localStorage.setItem(RECENT_KEY, JSON.stringify(list));
	} catch {
		/* ignore */
	}
	return list;
}
