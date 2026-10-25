import type { PageLoad } from './$types';
import { session } from '#lib/session.svelte.ts';
import { downloads } from '#lib/downloads.svelte.ts';
import { isTauri, isDesktop } from '#lib/platform.ts';

export const load: PageLoad = async ({ params, fetch }) => {
	await session.whenReady();
	if (!session.client) return { item: null, offline: false };
	try {
		// In the desktop app requests go through native netFetch, so keep the client's own fetch there.
		const viaKit = isDesktop || isTauri ? undefined : fetch;
		return { item: await session.api.item(params.id, undefined, viaKit), offline: false };
	} catch {
		// Offline (or server down): a downloaded book still has its page.
		await downloads.init();
		return { item: downloads.books.get(params.id)?.item ?? null, offline: true };
	}
};
