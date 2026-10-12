// Library filter encoding: `<group>.<base64(value)>`. The server accepts one filter per request.

export type ProgressFilter = 'finished' | 'not-started' | 'in-progress' | 'not-finished';

export type FilterGroup = 'progress' | 'genres' | 'narrators' | 'tags' | 'languages' | 'authors' | 'series' | 'recent';

/** UTF-8 safe base64 (umlauts in genres and narrator names). */
export function base64(value: string) {
	const bytes = new TextEncoder().encode(value);
	let bin = '';
	for (const b of bytes) bin += String.fromCharCode(b);
	return btoa(bin);
}

export function encodeFilter(group: FilterGroup, value: string) {
	return `${group}.${base64(value)}`;
}
