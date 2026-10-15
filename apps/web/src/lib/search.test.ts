import { describe, expect, it, vi } from 'vitest';

// The search module reads the session only when searching; highlight is pure.
vi.mock('./session.svelte.ts', () => ({ session: {} }));
const { highlight } = await import('./search.ts');

const hits = (text: string, q: string) =>
	highlight(text, q)
		.filter((p) => p.hit)
		.map((p) => p.text);

describe('highlight', () => {
	it('marks every word of the query, ignoring case and accents', () => {
		expect(hits('Der lange Weg', 'lange')).toEqual(['lange']);
		expect(hits('Wintergrün', 'grun')).toEqual(['grün']);
		expect(hits('Mara Lindqvist', 'lind mar')).toEqual(['Mar', 'Lind']);
	});

	it('keeps the text intact', () => {
		const parts = highlight('Kleine Eulen', 'eul');
		expect(parts.map((p) => p.text).join('')).toBe('Kleine Eulen');
	});

	it('ignores single letters and empty queries', () => {
		expect(hits('Salzwasser', 'a')).toEqual([]);
		expect(highlight('Salzwasser', '')).toEqual([{ text: 'Salzwasser', hit: false }]);
	});
});
