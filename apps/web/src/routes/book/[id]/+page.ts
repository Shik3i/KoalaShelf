import type { PageLoad } from './$types';
import { session } from '#lib/session.svelte.ts';

export const load: PageLoad = async ({ params }) => {
	await session.whenReady();
	if (!session.client) return { item: null };
	try {
		return { item: await session.api.item(params.id) };
	} catch {
		return { item: null };
	}
};
