import { describe, expect, it } from 'vitest';
import { streak, toCsv } from './export.ts';

describe('toCsv', () => {
	it('quotes separators, quotes and line breaks', () => {
		expect(toCsv([['a', 'b,c', 'say "hi"', 'two\nlines', 3, null]])).toBe(
			'a,"b,c","say ""hi""","two\nlines",3,\r\n'
		);
	});

	it('defuses cells a spreadsheet would run as formulas', () => {
		expect(toCsv([['=SUM(A1)', '+1', '-x', '@me', 'fine']])).toBe(
			"'=SUM(A1),'+1,'-x,'@me,fine\r\n"
		);
		// Numbers stay numbers.
		expect(toCsv([[-5]])).toBe('-5\r\n');
	});
});

describe('streak', () => {
	const now = new Date(2026, 9, 15, 12);
	it('counts days in a row up to today', () => {
		expect(
			streak({ '2026-10-15': 60, '2026-10-14': 10, '2026-10-13': 5, '2026-10-11': 9 }, now)
		).toBe(3);
	});

	it('still counts when today has no listening yet', () => {
		expect(streak({ '2026-10-14': 10, '2026-10-13': 5 }, now)).toBe(2);
	});

	it('is zero after a gap', () => {
		expect(streak({ '2026-10-12': 10 }, now)).toBe(0);
	});
});
