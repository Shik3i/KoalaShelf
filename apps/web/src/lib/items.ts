import type { LibraryItem, LibraryItemMinified } from '@koalashelf/abs-client';
import { session } from './session.svelte.ts';

type AnyItem = LibraryItem | LibraryItemMinified;

/** Display fields for both expanded and minified items (resilient to sparse server payloads). */
export function view(item: AnyItem) {
	const m = item.media?.metadata;
	const author = !m
		? ''
		: 'authorName' in m
			? (m.authorName ?? '')
			: Array.isArray(m.authors)
				? m.authors
						.map((a) => (typeof a === 'string' ? a : (a?.name ?? '')))
						.filter(Boolean)
						.join(', ')
				: '';
	const narrator = !m
		? ''
		: 'narratorName' in m
			? (m.narratorName ?? '')
			: Array.isArray(m.narrators)
				? m.narrators.filter(Boolean).join(', ')
				: typeof m.narrators === 'string'
					? m.narrators
					: '';
	const series = !m
		? ''
		: 'seriesName' in m
			? (m.seriesName ?? '')
			: Array.isArray(m.series)
				? m.series
						.map((s) => (s?.sequence ? `${s.name} #${s.sequence}` : (s?.name ?? '')))
						.filter(Boolean)
						.join(', ')
				: '';
	return {
		id: item.id ?? '',
		title: m?.title || 'Untitled',
		subtitle: m?.subtitle ?? null,
		author,
		narrator,
		series,
		duration: item.media?.duration ?? 0,
		cover: coverSrc(item)
	};
}

export function coverSrc(item: AnyItem) {
	if (!item.media?.coverPath) return null;
	const api = session.api;
	return (width: number) => api.coverUrl(item.id, { width, updatedAt: item.updatedAt });
}
