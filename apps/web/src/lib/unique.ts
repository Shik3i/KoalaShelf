// Real libraries carry duplicates: a book in two series shows up twice on a shelf, metadata
// lists a narrator or genre twice, pages overlap while books are added. Keyed lists must never
// see the same key twice (Svelte stops rendering the page), so server lists pass through here.

/** The list without later duplicates (by key, default: the value itself). */
export function unique<T>(
	list: readonly T[] | null | undefined,
	key: (x: T) => unknown = (x) => x
) {
	const seen = new Set<unknown>();
	const out: T[] = [];
	for (const x of list ?? []) {
		const k = key(x);
		if (seen.has(k)) continue;
		seen.add(k);
		out.push(x);
	}
	return out;
}

/** By id: unique(list, byId). */
export const byId = (x: { id: string }) => x.id;
