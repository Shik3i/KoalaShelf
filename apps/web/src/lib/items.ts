import type { LibraryItem, LibraryItemMinified } from '@koalashelf/abs-client';
import { session } from './session.svelte';

type AnyItem = LibraryItem | LibraryItemMinified;

/** Display fields for both expanded and minified items. */
export function view(item: AnyItem) {
	const m = item.media.metadata;
	const author = 'authorName' in m ? m.authorName : m.authors.map((a) => a.name).join(', ');
	const narrator = 'narratorName' in m ? m.narratorName : m.narrators.join(', ');
	const series =
		'seriesName' in m
			? m.seriesName
			: m.series.map((s) => (s.sequence ? `${s.name} #${s.sequence}` : s.name)).join(', ');
	return {
		id: item.id,
		title: m.title,
		subtitle: m.subtitle,
		author,
		narrator,
		series,
		duration: item.media.duration,
		cover: coverSrc(item)
	};
}

export function coverSrc(item: AnyItem) {
	if (!item.media.coverPath) return null;
	const api = session.api;
	return (width: number) => api.coverUrl(item.id, { width, updatedAt: item.updatedAt });
}
