// Locale-aware formatting helpers.
import { i18n } from './i18n/index.svelte.ts';

/** 3725 -> "1:02:05", 125 -> "2:05". */
export function clock(seconds: number) {
	const s = Math.max(0, Math.floor(seconds));
	const h = Math.floor(s / 3600);
	const m = Math.floor((s % 3600) / 60);
	const sec = String(s % 60).padStart(2, '0');
	return h ? `${h}:${String(m).padStart(2, '0')}:${sec}` : `${m}:${sec}`;
}

/** 3725 -> "1 h 2 min" / "1 Std. 2 Min." */
export function duration(seconds: number) {
	const { hours, minutes } = i18n.m.format;
	const total = Math.round(seconds / 60);
	const h = Math.floor(total / 60);
	const m = total % 60;
	if (!h) return `${Math.max(1, m)} ${minutes}`;
	return m ? `${h} ${hours} ${m} ${minutes}` : `${h} ${hours}`;
}

export function bytes(n: number) {
	const units = ['B', 'KB', 'MB', 'GB', 'TB'];
	let i = 0;
	while (n >= 1000 && i < units.length - 1) {
		n /= 1000;
		i++;
	}
	return `${n.toLocaleString(i18n.tag, { maximumFractionDigits: 1 })} ${units[i]}`;
}

/** Playback rate as shown on the toggle, e.g. 1.2 -> "1.2×" / "1,2×". */
export const speed = (r: number) => `${r.toLocaleString(i18n.tag, { minimumFractionDigits: 1 })}×`;

/**
 * Audiobookshelf descriptions are HTML. Render them as plain text with paragraph breaks:
 * DOMParser builds an inert document (no scripts, no network), so this is XSS-safe.
 */
export function htmlToText(html: string | null | undefined) {
	if (!html) return '';
	const marked = html
		.replace(/<br\s*\/?>/gi, '\n')
		.replace(/<\/(p|div|li|h[1-6])>/gi, '\n\n')
		.replace(/<li[^>]*>/gi, '• ');
	const text = new DOMParser().parseFromString(marked, 'text/html').body.textContent ?? '';
	return text
		.replace(/[ \t]+\n/g, '\n')
		.replace(/\n{3,}/g, '\n\n')
		.trim();
}
