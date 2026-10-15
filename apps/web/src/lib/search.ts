// Search across one or all libraries, recent searches and match highlighting.
import type { SearchResult } from '@koalashelf/abs-client';
import { session } from './session.svelte.ts';

export interface Found extends SearchResult {
	/** Library name per book id (only when searching several libraries). */
	libraryOf: Map<string, string>;
}

const empty = (): Found => ({
	book: [],
	series: [],
	authors: [],
	narrators: [],
	tags: [],
	genres: [],
	libraryOf: new Map()
});

/** Server search in the current library, or in every library at once (merged). */
export async function search(query: string, all: boolean, limit: number, signal?: AbortSignal) {
	const libs = all
		? session.libraries
		: session.libraries.filter((l) => l.id === session.libraryId);
	const results = await Promise.all(
		libs.map((l) => session.api.search(l.id, query, limit, signal).then((r) => ({ l, r })))
	);
	const out = empty();
	for (const { l, r } of results) {
		out.book.push(...r.book);
		out.series.push(...r.series);
		out.authors.push(...r.authors);
		out.narrators.push(...r.narrators);
		out.tags.push(...r.tags);
		out.genres.push(...r.genres);
		if (all) for (const b of r.book) out.libraryOf.set(b.libraryItem.id, l.name);
	}
	// The same narrator, genre or tag can come from two libraries: count them once.
	const merge = <T extends { name: string }>(list: T[], add: (a: T, b: T) => T) => [
		...list
			.reduce(
				(m, x) => m.set(x.name, m.has(x.name) ? add(m.get(x.name)!, x) : x),
				new Map<string, T>()
			)
			.values()
	];
	out.narrators = merge(out.narrators, (a, b) => ({ ...a, numBooks: a.numBooks + b.numBooks }));
	out.genres = merge(out.genres, (a, b) => ({ ...a, numItems: a.numItems + b.numItems }));
	out.tags = merge(out.tags, (a, b) => ({ ...a, numItems: a.numItems + b.numItems }));
	return out;
}

export const isEmpty = (r: SearchResult) =>
	!r.book.length &&
	!r.series.length &&
	!r.authors.length &&
	!r.narrators.length &&
	!r.genres.length &&
	!r.tags.length;

// --- Recent searches (this device only) -------------------------------------------

const RECENT_KEY = 'ks.recentSearches';

export function recentSearches(): string[] {
	try {
		const list = JSON.parse(localStorage.getItem(RECENT_KEY) ?? '[]');
		return Array.isArray(list) ? list.filter((x) => typeof x === 'string') : [];
	} catch {
		return [];
	}
}

export function rememberSearch(query: string) {
	const q = query.trim();
	if (q.length < 2) return recentSearches();
	const list = [q, ...recentSearches().filter((x) => x.toLowerCase() !== q.toLowerCase())].slice(
		0,
		8
	);
	try {
		localStorage.setItem(RECENT_KEY, JSON.stringify(list));
	} catch {
		/* storage blocked: nothing to remember */
	}
	return list;
}

export function clearRecentSearches() {
	try {
		localStorage.removeItem(RECENT_KEY);
	} catch {
		/* ignore */
	}
}

// --- Highlighting -----------------------------------------------------------------

/** Lowercase without accents, keeping one character per character (indexes stay valid). */
function fold(s: string) {
	let out = '';
	for (const ch of s) {
		const base = ch
			.normalize('NFD')
			.replace(/\p{Diacritic}/gu, '')
			.toLowerCase();
		// Characters that do not fold to exactly one unit keep their own place ("ß", emoji).
		out +=
			base.length === ch.length
				? base
				: ch.toLowerCase().length === ch.length
					? ch.toLowerCase()
					: ch;
	}
	return out;
}

/** Splits text into parts, marking every place where a word of the query occurs. */
export function highlight(text: string, query: string): { text: string; hit: boolean }[] {
	const words = fold(query)
		.split(/\s+/)
		.filter((w) => w.length >= 2);
	if (!words.length || !text) return [{ text, hit: false }];
	const hay = fold(text);
	const marks = new Array<boolean>(text.length).fill(false);
	for (const w of words) {
		for (let i = hay.indexOf(w); i >= 0; i = hay.indexOf(w, i + 1))
			marks.fill(true, i, i + w.length);
	}
	const parts: { text: string; hit: boolean }[] = [];
	for (let i = 0; i < text.length; i++) {
		const last = parts.at(-1);
		if (last && last.hit === marks[i]) last.text += text[i];
		else parts.push({ text: text[i], hit: marks[i] });
	}
	return parts;
}
