import type { PageLoad } from './$types';
import { session } from '#lib/session.svelte.ts';
import { isTauri, isDesktop } from '#lib/platform.ts';

export const load: PageLoad = async ({ params, fetch }) => {
	const kit = isDesktop || isTauri ? undefined : fetch;
	await session.whenReady();
	const lib = session.libraryId;
	if (!session.client || !lib) return { author: null };
	try {
		const author = await session.api.author(params.id, lib, kit);
		// An author from another library (search across libraries): load their books from there.
		return {
			author:
				author.libraryId !== lib
					? await session.api.author(params.id, author.libraryId, kit)
					: author
		};
	} catch {
		return { author: null };
	}
};
