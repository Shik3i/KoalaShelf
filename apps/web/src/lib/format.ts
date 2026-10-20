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

/** Like duration(), but short spans in seconds ("6 s" instead of "1 min"). */
export function span(seconds: number) {
	return seconds < 60 ? `${Math.round(seconds)} ${i18n.m.format.seconds}` : duration(seconds);
}

/**
 * Formats a timestamp into a friendly, relative time string:
 * "gerade eben", "vor 5 Min.", "vor 2 Std.", "gestern", "vor 3 Tagen" or medium date.
 */
export function relativeTime(timestamp: number | null | undefined): string {
	if (!timestamp) return '';
	const diffMs = timestamp - Date.now();
	const diffSec = Math.round(diffMs / 1000);
	const diffMin = Math.round(diffSec / 60);
	const diffHour = Math.round(diffMin / 60);
	const diffDay = Math.round(diffHour / 24);

	if (Math.abs(diffSec) < 60) return i18n.m.common.justNow;
	const rtf = new Intl.RelativeTimeFormat(i18n.tag, { numeric: 'auto' });
	if (Math.abs(diffMin) < 60) return rtf.format(diffMin, 'minute');
	if (Math.abs(diffHour) < 24) return rtf.format(diffHour, 'hour');
	if (Math.abs(diffDay) < 30) return rtf.format(diffDay, 'day');
	return new Intl.DateTimeFormat(i18n.tag, { dateStyle: 'medium' }).format(new Date(timestamp));
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

/** "1:02:05", "2:05" or "125" -> seconds; null when it is not a time. */
export function parseClock(text: string | null | undefined): number | null {
	if (!text) return null;
	if (!/^\d+(:\d{1,2}){0,2}$/.test(text)) return null;
	return text.split(':').reduce((s, part) => s * 60 + Number(part), 0);
}
