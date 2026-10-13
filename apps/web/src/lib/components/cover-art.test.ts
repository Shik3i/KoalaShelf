import { describe, expect, it } from 'vitest';
import { coverArt, PALETTES } from './cover-art.ts';

const lum = (hex: string) => {
	const [r, g, b] = [1, 3, 5].map((i) => {
		const c = parseInt(hex.slice(i, i + 2), 16) / 255;
		return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
	});
	return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};
const contrast = (a: string, b: string) => {
	const [hi, lo] = [lum(a), lum(b)].sort((x, y) => y - x);
	return (hi + 0.05) / (lo + 0.05);
};

describe('cover placeholder art', () => {
	it('text on every palette meets WCAG AA (4.5:1)', () => {
		for (const p of PALETTES)
			expect(contrast(p.ink, p.bg), `${p.bg}/${p.ink}`).toBeGreaterThanOrEqual(4.5);
	});

	it('is deterministic per title and author', () => {
		expect(coverArt('Der lange Weg|Mara')).toBe(coverArt('Der lange Weg|Mara'));
	});
});
