// Library queries: any combination of filters, a text filter and every server sort.
//
// The server takes one filter per request. Simple queries page through the server directly.
// Everything else becomes an exact set: each filter value is one server request (cached), values
// of the same kind are OR'ed, different kinds AND'ed. Status and duration are checked locally, so
// status reflects the current listening progress rather than a cached server answer.
import {
	encodeFilter,
	type AbsClient,
	type FilterGroup,
	type LibraryItemMinified
} from '@koalashelf/abs-client';
import { cached } from './cache.ts';

export type Status = '' | 'in-progress' | 'not-started' | 'finished';

/** Duration ranges in hours. */
export const LENGTHS = {
	lt2: [0, 2],
	lt4: [0, 4],
	'2-6': [2, 6],
	'4-8': [4, 8],
	lt8: [0, 8],
	'6-12': [6, 12],
	'8-16': [8, 16],
	'12-24': [12, 24],
	gt24: [24, Infinity]
} as const;
export type Length = keyof typeof LENGTHS;

export interface DurationFilter {
	minSeconds?: number;
	maxSeconds?: number;
	cleanQuery: string;
}

/** Parses natural duration expressions like "unter 8 stunden", "<4h", ">10h", "4-8h" from search queries. */
export function parseDurationQuery(raw: string): DurationFilter {
	let text = raw.trim();
	let minHours: number | undefined;
	let maxHours: number | undefined;

	// Range: "4-8h", "4 bis 8 stunden", "4 to 8 hours"
	const rangeMatch = text.match(
		/(?:^|\s)(\d+(?:[.,]\d+)?)\s*(?:-|–|bis|to)\s*(\d+(?:[.,]\d+)?)\s*(?:h|std|stunden|hours|hrs)?(?:\s|$)/i
	);
	if (rangeMatch) {
		minHours = parseFloat(rangeMatch[1].replace(',', '.'));
		maxHours = parseFloat(rangeMatch[2].replace(',', '.'));
		text = text.replace(rangeMatch[0], ' ').trim();
	}

	// Under / less than: "<8h", "< 8", "unter 8 stunden", "unter 4", "nur unter 8h", "under 8 hours"
	const underMatch = text.match(
		/(?:^|\s)(?:(?:nur|only)\s+)?(?:<|unter|under|weniger\s+als|less\s+than)\s*(\d+(?:[.,]\d+)?)\s*(?:h|std|stunden|hours|hrs)?(?:\s|$)/i
	);
	if (underMatch) {
		maxHours = parseFloat(underMatch[1].replace(',', '.'));
		text = text.replace(underMatch[0], ' ').trim();
	}

	// Over / more than: ">12h", "> 10", "über 10 stunden", "mehr als 8 stunden", "over 10 hours"
	const overMatch = text.match(
		/(?:^|\s)(?:(?:nur|only)\s+)?(?:>|über|ueber|over|mehr\s+als|more\s+than)\s*(\d+(?:[.,]\d+)?)\s*(?:h|std|stunden|hours|hrs)?(?:\s|$)/i
	);
	if (overMatch) {
		minHours = parseFloat(overMatch[1].replace(',', '.'));
		text = text.replace(overMatch[0], ' ').trim();
	}

	return {
		minSeconds: minHours !== undefined ? minHours * 3600 : undefined,
		maxSeconds: maxHours !== undefined ? maxHours * 3600 : undefined,
		cleanQuery: text.replace(/\s+/g, ' ').trim()
	};
}

export const SORTS = {
	title: { key: 'media.metadata.title', desc: false },
	author: { key: 'media.metadata.authorNameLF', desc: false },
	added: { key: 'addedAt', desc: true },
	played: { key: 'progress', desc: true },
	started: { key: 'progress.createdAt', desc: true },
	finished: { key: 'progress.finishedAt', desc: true },
	duration: { key: 'media.duration', desc: false },
	published: { key: 'media.metadata.publishedYear', desc: true },
	random: { key: 'random', desc: false }
} as const;
export type SortId = keyof typeof SORTS | 'sequence';

/** Multi-value filters (any of the chosen values) and single-value ones. */
export const MULTI = ['genre', 'tag', 'narrator', 'language'] as const;
export const SINGLE = ['author', 'series', 'publisher', 'decade'] as const;
type Multi = (typeof MULTI)[number];
type Single = (typeof SINGLE)[number];

/** Server filter group per URL parameter. */
const GROUP: Record<Multi | Single, FilterGroup> = {
	genre: 'genres',
	tag: 'tags',
	narrator: 'narrators',
	language: 'languages',
	author: 'authors',
	series: 'series',
	publisher: 'publishers',
	decade: 'publishedDecades'
};

/** Most selective first: the first single-value filter decides the base list. */
const SELECTIVITY: (Multi | Single)[] = [
	'series',
	'author',
	'narrator',
	'publisher',
	'tag',
	'decade',
	'genre',
	'language'
];

export interface LibraryQuery {
	status: Status;
	values: Record<Multi, string[]> & Record<Single, string>;
	length: Length | '';
	q: string;
	sort: SortId;
	desc: boolean;
	group: boolean;
	/** Changes to reshuffle a random order. */
	shuffle: string;
}

export function parseQuery(p: Pick<URLSearchParams, 'get' | 'getAll' | 'has'>): LibraryQuery {
	const values = {} as LibraryQuery['values'];
	for (const k of MULTI) values[k] = p.getAll(k).filter(Boolean);
	for (const k of SINGLE) values[k] = p.get(k) ?? '';
	const sortParam = p.get('sort') ?? '';
	const sort: SortId =
		sortParam in SORTS ? (sortParam as SortId) : values.series ? 'sequence' : 'title';
	const status = (p.get('status') ?? '') as Status;
	const length = (p.get('length') ?? '') as Length | '';
	return {
		status: ['in-progress', 'not-started', 'finished'].includes(status) ? status : '',
		values,
		length: length in LENGTHS ? length : '',
		q: (p.get('q') ?? '').trim(),
		sort,
		desc: p.has('desc') ? p.get('desc') === '1' : sort === 'sequence' ? false : SORTS[sort].desc,
		group: p.get('group') === '1' && !values.series,
		shuffle: p.get('shuffle') ?? ''
	};
}

export const sortKey = (q: LibraryQuery) =>
	q.sort === 'sequence' ? 'sequence' : SORTS[q.sort].key;

/** Active filters (kind, server filter strings) in selectivity order. */
function facets(q: LibraryQuery) {
	const out: { kind: Multi | Single; filters: string[] }[] = [];
	for (const kind of SELECTIVITY) {
		const v = q.values[kind];
		const list = Array.isArray(v) ? v : v ? [v] : [];
		if (list.length) out.push({ kind, filters: list.map((x) => encodeFilter(GROUP[kind], x)) });
	}
	return out;
}

/** How many filters are active (for the "Filters" button badge). */
export function activeCount(q: LibraryQuery) {
	return (
		facets(q).reduce((n, f) => n + f.filters.length, 0) +
		(q.status ? 1 : 0) +
		(q.length ? 1 : 0) +
		(q.q ? 1 : 0)
	);
}

export type Plan =
	/** One server filter (or none): page through the server. */
	| { mode: 'paged'; filter?: string; sort: string; desc: boolean; group: boolean }
	/** Several conditions: compute the exact set once. */
	| { mode: 'set' };

export function plan(q: LibraryQuery): Plan {
	const f = facets(q);
	const serverConditions = f.length + (q.status ? 1 : 0);
	const simple =
		!q.q &&
		!q.length &&
		q.sort !== 'random' &&
		serverConditions <= 1 &&
		(f.length === 0 || f[0].filters.length === 1);
	if (!simple) return { mode: 'set' };
	const filter = f[0]?.filters[0] ?? (q.status ? encodeFilter('progress', q.status) : undefined);
	return { mode: 'paged', filter, sort: sortKey(q), desc: q.desc, group: q.group };
}

/** Local status of an item: 'new' | 'running' | 'done' (from the progress store). */
type StatusOf = (itemId: string) => 'new' | 'running' | 'done';
const STATUS_LOCAL = { 'not-started': 'new', 'in-progress': 'running', finished: 'done' } as const;

const ID_TTL = 120_000;

/** Ids of every item matching one server filter (cached for repeated combinations). */
function idsFor(api: AbsClient, libraryId: string, filter: string, signal?: AbortSignal) {
	return cached(
		`ids:${libraryId}:${filter}`,
		() =>
			api
				.libraryItems(libraryId, { limit: 0, filter, sort: 'addedAt' }, signal)
				.then((r) => new Set(r.results.map((i) => i.id))),
		ID_TTL
	);
}

/**
 * Text filter: everything the server search finds for the words (titles, and the books of
 * matching authors, series, narrators, genres and tags).
 */
async function textIds(api: AbsClient, libraryId: string, text: string, signal?: AbortSignal) {
	const r = await api.search(libraryId, text, 200, signal);
	const ids = new Set<string>([
		...r.book.map((b) => b.libraryItem.id),
		...r.series.flatMap((s) => s.books.map((b) => b.id))
	]);
	const related = [
		...r.authors.slice(0, 5).map((a) => encodeFilter('authors', a.id)),
		...r.narrators.slice(0, 5).map((n) => encodeFilter('narrators', n.name)),
		...r.genres.slice(0, 3).map((g) => encodeFilter('genres', g.name)),
		...r.tags.slice(0, 3).map((t) => encodeFilter('tags', t.name))
	];
	for (const set of await Promise.all(related.map((f) => idsFor(api, libraryId, f, signal))))
		for (const id of set) ids.add(id);
	return ids;
}

/** The exact result for a combined query, in the chosen order. */
export async function runSet(
	api: AbsClient,
	libraryId: string,
	q: LibraryQuery,
	statusOf: StatusOf,
	signal?: AbortSignal
): Promise<LibraryItemMinified[]> {
	const f = facets(q);
	// Base list in the requested order: the most selective single-value filter, else everything.
	const baseFacet = f.find((x) => x.filters.length === 1);
	const rest = f.filter((x) => x !== baseFacet);
	const dur = parseDurationQuery(q.q);
	const textSearch = dur.cleanQuery;
	const [base, restSets, text] = await Promise.all([
		api
			.libraryItems(
				libraryId,
				{ limit: 0, filter: baseFacet?.filters[0], sort: sortKey(q), desc: q.desc },
				signal
			)
			.then((r) => r.results),
		Promise.all(
			rest.map(async (x) => {
				const sets = await Promise.all(x.filters.map((flt) => idsFor(api, libraryId, flt, signal)));
				return new Set(sets.flatMap((s) => [...s]));
			})
		),
		textSearch ? textIds(api, libraryId, textSearch, signal) : Promise.resolve(null)
	]);
	const [loPreset, hiPreset] = q.length ? LENGTHS[q.length] : [0, Infinity];
	const minSec = Math.max(loPreset * 3600, dur.minSeconds ?? 0);
	const maxSec = Math.min(hiPreset * 3600, dur.maxSeconds ?? Infinity);
	const status = q.status ? STATUS_LOCAL[q.status] : null;
	return base.filter(
		(i) =>
			restSets.every((s) => s.has(i.id)) &&
			(!text || text.has(i.id)) &&
			(!status || statusOf(i.id) === status) &&
			i.media.duration >= minSec &&
			i.media.duration < maxSec
	);
}
