// Finds and validates an Audiobookshelf server from what the user typed.
import { AbsError, getServerStatus, normalizeBaseUrl } from '@koalashelf/abs-client';
import { apiFetch, isTauri, isDesktop } from './platform.ts';

export type ProbeFailure = 'not-abs' | 'cors' | 'unreachable' | 'local-disabled' | 'insecure-http';

export class ProbeError extends Error {
	constructor(readonly kind: ProbeFailure) {
		super(kind);
	}
}

const RECENT_KEY = 'ks.servers';

export function unwrapServerUrl(url: string): string {
	if (url.startsWith('/_abs/')) {
		const rest = url.slice('/_abs/'.length);
		const slash = rest.indexOf('/');
		const encoded = slash >= 0 ? rest.slice(0, slash) : rest;
		try {
			return decodeURIComponent(encoded);
		} catch {
			return url;
		}
	}
	return url;
}

export function isLoopback(urlOrHost: string): boolean {
	return /^(https?:\/\/)?(localhost|127\.0\.0\.1|\[::1\])(:\d+)?(\/|$)/i.test(urlOrHost);
}

export function normalizeServerInput(input: string): string {
	const trimmed = input.trim();
	if (!trimmed) return '';
	if (/^https?:\/\//i.test(trimmed)) return trimmed;
	return isLoopback(trimmed) ? `http://${trimmed}` : `https://${trimmed}`;
}

/** Prepares HTTPS candidate; rejects insecure HTTP for remote servers while allowing local loopback in dev/tests. */
function candidates(input: string) {
	const raw = normalizeBaseUrl(unwrapServerUrl(input.trim()));
	if (/^http:\/\//i.test(raw)) {
		if (isLoopback(raw)) return [raw];
		throw new ProbeError('insecure-http');
	}
	if (/^https:\/\//i.test(raw)) return [raw];
	if (isLoopback(raw)) {
		return [`http://${raw}`, `https://${raw}`];
	}
	return [`https://${raw}`];
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
			if (e instanceof AbsError) {
				failure = 'not-abs';
				continue;
			}
			// When direct browser fetch is blocked by CORS, try the container's dynamic ABS proxy.
			if (!isTauri && !isDesktop) {
				try {
					const proxied = `/_abs/${encodeURIComponent(url)}`;
					const status = await getServerStatus(proxied, fetchFn, signal);
					assertLocalLogin(status.authMethods);
					return { url: proxied, version: status.serverVersion };
				} catch (proxyErr) {
					if (signal?.aborted || proxyErr instanceof ProbeError) throw proxyErr;
				}
				if (await reachableWithoutCors(url, signal)) failure = 'cors';
			}
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
		normalizeBaseUrl(unwrapServerUrl(u))
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
	const clean = unwrapServerUrl(url);
	const list = [clean, ...recentServers().filter((s) => s !== clean)].slice(0, 5);
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
