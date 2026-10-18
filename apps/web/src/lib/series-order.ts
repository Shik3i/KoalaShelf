// Books of a series in reading order. Servers list them by sequence when one is set, otherwise
// in no particular order (e.g. a radio play series numbered only in its titles: "001/…").
import type { LibraryItem, LibraryItemMinified } from '@koalashelf/abs-client';

type AnyItem = LibraryItem | LibraryItemMinified;

const collator = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' });

/** The book's number in the named series ("#3" or "#1.5"), null when it has none. */
export function sequenceIn(item: AnyItem, series: string): number | null {
	const m = item.media.metadata;
	let raw: string | null | undefined;
	if ('seriesName' in m) {
		// Minified: "Name #2" or several series joined: "A #1, B #3".
		const part = m.seriesName?.split(', ').find((p) => p === series || p.startsWith(`${series} #`));
		raw = part?.slice(series.length).replace(/^ #/, '');
	} else {
		raw = m.series.find((s) => s.name === series)?.sequence;
	}
	const n = raw ? Number.parseFloat(raw.replace(',', '.')) : NaN;
	return Number.isFinite(n) ? n : null;
}

/** By number in the series; books without one after them, in natural title order. */
export function inSeriesOrder<T extends AnyItem>(items: readonly T[], series: string): T[] {
	const keyed = items.map((item) => ({ item, seq: sequenceIn(item, series) }));
	keyed.sort((a, b) => {
		if (a.seq !== null && b.seq !== null && a.seq !== b.seq) return a.seq - b.seq;
		if (a.seq === null && b.seq !== null) return 1;
		if (a.seq !== null && b.seq === null) return -1;
		return collator.compare(a.item.media.metadata.title, b.item.media.metadata.title);
	});
	return keyed.map((k) => k.item);
}
