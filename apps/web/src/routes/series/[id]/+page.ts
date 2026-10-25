import { encodeFilter } from '@koalashelf/abs-client';
import type { PageLoad } from './$types';
import { session } from '#lib/session.svelte.ts';
import { isTauri, isDesktop } from '#lib/platform.ts';

export const load: PageLoad = async ({ params, fetch }) => {
	const kit = isDesktop || isTauri ? undefined : fetch;
	await session.whenReady();
	if (!session.client) return { series: null, items: [] };
	// The current library first; a series found by searching every library may live elsewhere.
	const libs = [session.libraryId, ...session.libraries.map((l) => l.id)].filter(
		(id, i, all): id is string => !!id && all.indexOf(id) === i
	);
	for (const lib of libs) {
		try {
			const [series, list] = await Promise.all([
				session.api.seriesDetail(lib, params.id, kit),
				session.api.libraryItems(
					lib,
					{
						filter: encodeFilter('series', params.id),
						sort: 'sequence',
						limit: 0
					},
					undefined,
					kit
				)
			]);
			return { series, items: list.results };
		} catch {
			/* not in this library */
		}
	}
	return { series: null, items: [] };
};
