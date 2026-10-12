import { session } from '#lib/session.svelte.ts';
import { player } from '#lib/player/player.svelte.ts';

// Pure client-side app: the server URL is only known at runtime.
export const ssr = false;
export const prerender = false;

// Restore the session before any page load runs (pages await session.whenReady()).
export async function load() {
	await session.restore();
	if (session.client) void player.restoreLast();
	return {};
}
