// Average colour of a cover, for a subtle tinted glow. Computed locally from an 8x8 sample.
// Cross-origin covers without CORS cannot be read (tainted canvas): then there is no glow.
const cache = new Map<string, Promise<string | null>>();

export function coverColor(url: string): Promise<string | null> {
	let hit = cache.get(url);
	if (!hit) {
		hit = sample(url);
		cache.set(url, hit);
	}
	return hit;
}

async function sample(url: string): Promise<string | null> {
	try {
		const img = new Image();
		img.src = url;
		await img.decode();
		const canvas = document.createElement('canvas');
		canvas.width = canvas.height = 8;
		const ctx = canvas.getContext('2d', { willReadFrequently: true });
		if (!ctx) return null;
		ctx.drawImage(img, 0, 0, 8, 8);
		const d = ctx.getImageData(0, 0, 8, 8).data;
		let r = 0;
		let g = 0;
		let b = 0;
		let n = 0;
		for (let i = 0; i < d.length; i += 4) {
			const max = Math.max(d[i], d[i + 1], d[i + 2]);
			const min = Math.min(d[i], d[i + 1], d[i + 2]);
			// Prefer coloured pixels over near-black/white/grey ones.
			const weight = 1 + ((max - min) / 255) * 4;
			r += d[i] * weight;
			g += d[i + 1] * weight;
			b += d[i + 2] * weight;
			n += weight;
		}
		return `rgb(${Math.round(r / n)} ${Math.round(g / n)} ${Math.round(b / n)})`;
	} catch {
		return null;
	}
}
