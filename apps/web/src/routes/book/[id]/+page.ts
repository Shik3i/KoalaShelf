import type { PageLoad } from './$types';
import { session } from '#lib/session.svelte.ts';
import { downloads } from '#lib/downloads.svelte.ts';

export const load: PageLoad = async ({ params }) => {
	await session.whenReady();
	if (!session.client) return { item: null, offline: false };
	try {
		return { item: await session.api.item(params.id), offline: false };
	} catch {
		// Offline (or server down): a downloaded book still has its page.
		await downloads.init();
		return { item: downloads.books.get(params.id)?.item ?? null, offline: true };
	}
};
