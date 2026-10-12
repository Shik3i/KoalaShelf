// Renders the PWA icons from SVG sources (run: pnpm icons). Output is committed.
import sharp from 'sharp';
import { fileURLToPath } from 'node:url';

const disc = (cx, cy, r) => `
  <circle cx="${cx}" cy="${cy}" r="${r}" fill="#161820"/>
  <g fill="none" stroke="#2c303b" stroke-width="${r * 0.04}">
    ${[0.83, 0.7, 0.57].map((f) => `<circle cx="${cx}" cy="${cy}" r="${r * f}"/>`).join('')}
  </g>
  <circle cx="${cx}" cy="${cy}" r="${r * 0.37}" fill="#C23D20"/>
  <circle cx="${cx}" cy="${cy}" r="${r * 0.07}" fill="#EEF1F5"/>`;

const arm = (
	s
) => `<path d="M${s * 0.8} ${s * 0.12} L${s * 0.6} ${s * 0.46}" stroke="#E8A33D" stroke-width="${s * 0.045}" stroke-linecap="round"/>
  <circle cx="${s * 0.8}" cy="${s * 0.12}" r="${s * 0.035}" fill="#E8A33D"/>`;

// "any": rounded tile. "maskable": full bleed, artwork inside the 80% safe zone.
const any = (
	s
) => `<svg xmlns="http://www.w3.org/2000/svg" width="${s}" height="${s}" viewBox="0 0 ${s} ${s}">
  <rect width="${s}" height="${s}" rx="${s * 0.22}" fill="#17130F"/>${disc(s * 0.46, s * 0.54, s * 0.36)}${arm(s)}</svg>`;
const maskable = (
	s
) => `<svg xmlns="http://www.w3.org/2000/svg" width="${s}" height="${s}" viewBox="0 0 ${s} ${s}">
  <rect width="${s}" height="${s}" fill="#17130F"/>${disc(s * 0.48, s * 0.52, s * 0.28)}
  <path d="M${s * 0.72} ${s * 0.22} L${s * 0.58} ${s * 0.46}" stroke="#E8A33D" stroke-width="${s * 0.035}" stroke-linecap="round"/></svg>`;

const out = new URL('../static/icons/', import.meta.url);
const jobs = [
	['icon-192.png', any(192)],
	['icon-512.png', any(512)],
	['maskable-512.png', maskable(512)],
	['apple-touch-icon.png', maskable(180)]
];
const toPath = (url) => fileURLToPath(url);
await Promise.all(
	jobs.map(([name, svg]) =>
		sharp(Buffer.from(svg))
			.png({ compressionLevel: 9 })
			.toFile(toPath(new URL(name, out)))
	)
);
// Source for the desktop icon set: pnpm --filter @koalashelf/desktop tauri icon src-tauri/app-icon.png
await sharp(Buffer.from(any(1024)))
	.png()
	.toFile(toPath(new URL('../../desktop/src-tauri/app-icon.png', import.meta.url)));
console.log('icons written');
