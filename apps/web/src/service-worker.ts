/// <reference lib="webworker" />
// Offline app shell + cover image cache. Privacy: API responses and audio are never cached.
import { self } from '$app/service-worker';
import { assets, immutable } from '$app/manifest';
import { version } from '$app/env';

const SHELL = `shell-${version}`;
const COVERS = 'covers-v1';
const MAX_COVERS = 600;

// Skip font subsets the UI languages never need (they still load on demand via unicode-range).
const SHELL_FILES = [...immutable, ...assets]
	.map((f) => (f.path.startsWith('/') ? f.path : `/${f.path}`))
	.filter((p) => !/(cyrillic|greek|vietnamese)[^/]*\.woff2?$/.test(p))
	.concat(['/']);

self.addEventListener('install', (event) => {
	event.waitUntil(
		caches
			.open(SHELL)
			.then((c) => c.addAll(SHELL_FILES))
			.then(() => self.skipWaiting())
	);
});

self.addEventListener('activate', (event) => {
	event.waitUntil(
		caches
			.keys()
			.then((keys) =>
				Promise.all(
					keys.filter((k) => k.startsWith('shell-') && k !== SHELL).map((k) => caches.delete(k))
				)
			)
			.then(() => self.clients.claim())
	);
});

const isCover = (url: URL) =>
	/\/api\/(items\/[^/]+\/cover|authors\/[^/]+\/image)$/.test(url.pathname);

async function trimCovers() {
	const cache = await caches.open(COVERS);
	const keys = await cache.keys();
	for (const k of keys.slice(0, Math.max(0, keys.length - MAX_COVERS))) await cache.delete(k);
}

/** Stale-while-revalidate for covers; opaque cross-origin responses are skipped (quota padding). */
async function cover(request: Request) {
	const cache = await caches.open(COVERS);
	const hit = await cache.match(request);
	const fresh = fetch(request)
		.then(async (res) => {
			if (res.ok && res.type !== 'opaque') {
				await cache.put(request, res.clone());
				void trimCovers();
			}
			return res;
		})
		.catch(() => hit ?? Response.error());
	return hit ?? fresh;
}

/** SPA navigations: network first, cached shell when offline. */
async function navigate(request: Request) {
	try {
		return await fetch(request);
	} catch {
		const shell = await caches.open(SHELL);
		return (await shell.match('/')) ?? Response.error();
	}
}

self.addEventListener('fetch', (event) => {
	const { request } = event;
	if (request.method !== 'GET') return;
	const url = new URL(request.url);

	if (isCover(url)) return event.respondWith(cover(request));
	if (url.origin !== location.origin) return;
	if (request.mode === 'navigate') return event.respondWith(navigate(request));
	if (/^\/(api|auth|hls|socket\.io|status)\b/.test(url.pathname)) return;

	if (SHELL_FILES.includes(url.pathname)) {
		event.respondWith(caches.match(request).then((hit) => hit ?? fetch(request)));
	}
});
