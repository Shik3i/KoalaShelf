// Deterministic placeholder art for items without a cover, in the style of the design samples.

export interface CoverArt {
	bg: string;
	fg: string;
	ink: string;
	motif: 'sun' | 'rings' | 'eyes' | 'diagonal' | 'arcs' | 'moon' | 'stripes';
	alt: string;
}

const PALETTES: CoverArt[] = [
	{ bg: '#2B4A7A', fg: '#F2B544', ink: '#F1E8DC', motif: 'sun', alt: '#13203F' },
	{ bg: '#11183A', fg: '#F2C14E', ink: '#F2C14E', motif: 'stripes', alt: '#F1E8DC' },
	{ bg: '#8FB3C4', fg: '#6F98AB', ink: '#F1E8DC', motif: 'rings', alt: '#B9D0DA' },
	{ bg: '#C9563E', fg: '#F7EFE6', ink: '#F7EFE6', motif: 'eyes', alt: '#1E222B' },
	{ bg: '#3E4A2E', fg: '#E2C25A', ink: '#F7EFE6', motif: 'rings', alt: '#3E4A2E' },
	{ bg: '#22533A', fg: '#6E9A80', ink: '#F1E8DC', motif: 'diagonal', alt: '#F1E8DC' },
	{ bg: '#F3E3CF', fg: '#EF6F4F', ink: '#5A2A1A', motif: 'arcs', alt: '#F7A88A' },
	{ bg: '#4B3FB8', fg: '#8C7CF0', ink: '#F1E8DC', motif: 'rings', alt: '#4B3FB8' },
	{ bg: '#DCE8F2', fg: '#3C6FA8', ink: '#1E3A5A', motif: 'diagonal', alt: '#1E222B' },
	{ bg: '#1E3A4A', fg: '#F1E8DC', ink: '#F1E8DC', motif: 'moon', alt: '#1E3A4A' }
];

/** FNV-1a: tiny, stable string hash. */
function hash(s: string) {
	let h = 0x811c9dc5;
	for (let i = 0; i < s.length; i++) {
		h ^= s.charCodeAt(i);
		h = Math.imul(h, 0x01000193);
	}
	return h >>> 0;
}

export function coverArt(seed: string): CoverArt {
	return PALETTES[hash(seed) % PALETTES.length];
}
