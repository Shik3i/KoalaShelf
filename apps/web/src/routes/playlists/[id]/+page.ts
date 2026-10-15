import type { PageLoad } from './$types';
import { session } from '#lib/session.svelte.ts';
import { isTauri } from '#lib/platform.ts';

export const load: PageLoad = async ({ params, fetch }) => {
	await session.whenReady();
	if (!session.client) return { playlist: null };
	try {
		return { playlist: await session.api.playlist(params.id, isTauri ? undefined : fetch) };
	} catch {
		return { playlist: null };
	}
};
