// Settings backup: everything a listener tailored on this device, as one file, and back.
// Sessions, device ids and server lists are never part of it.
import { sanitize } from './prefs.svelte.ts';
import { saveFile, today } from './export.ts';
import { isHex } from './accent.ts';

const MARK = 'ks.imported';

const json = (check: (v: unknown) => boolean) => (raw: string) => {
	try {
		return check(JSON.parse(raw));
	} catch {
		return false;
	}
};

/** Stored values exactly as the app keeps them, each with a check before it is written back. */
const KEYS: Record<string, (raw: string) => boolean> = {
	'ks.prefs': json((v) => typeof v === 'object' && v !== null),
	'ks.theme': (raw) => raw === 'light' || raw === 'dark',
	'ks.locale': (raw) => /^[a-z]{2}$/.test(raw),
	'ks.bookRates': json((v) => typeof v === 'object' && v !== null && !Array.isArray(v)),
	'ks.rate': json((v) => typeof v === 'number' && v > 0),
	// Light and dark variants of a custom accent color (hex values only).
	'ks.accentCustom': json((v) => {
		const c = v as { light?: Record<string, unknown>; dark?: Record<string, unknown> } | null;
		return [c?.light?.accent, c?.light?.onAccent, c?.dark?.accent, c?.dark?.onAccent].every(isHex);
	}),
	// View, sort and grouping as a query string ("view=list&sort=added").
	'ks.libraryView': (raw) => /^[\w=&%.+-]*$/.test(raw)
};

export function exportSettings() {
	const data: Record<string, string> = {};
	for (const k of Object.keys(KEYS)) {
		const v = localStorage.getItem(k);
		if (v !== null) data[k] = v;
	}
	const file = { app: 'KoalaShelf', kind: 'settings', version: 1, exportedAt: Date.now(), data };
	saveFile(
		`koalashelf-settings-${today()}.json`,
		JSON.stringify(file, null, 2),
		'application/json'
	);
}

/** Reads a backup; false when the file is not one. Reload afterwards to apply everything. */
export async function importSettings(file: File) {
	let parsed: { app?: string; kind?: string; data?: Record<string, unknown> };
	try {
		parsed = JSON.parse(await file.text());
	} catch {
		return false;
	}
	const data = parsed?.data;
	if (
		parsed?.app !== 'KoalaShelf' ||
		parsed.kind !== 'settings' ||
		typeof data !== 'object' ||
		!data
	)
		return false;
	for (const [k, ok] of Object.entries(KEYS)) {
		const raw = data[k];
		if (typeof raw !== 'string' || !ok(raw)) continue;
		// Preferences go through the same checks as on every start.
		const value = k === 'ks.prefs' ? JSON.stringify(sanitize(JSON.parse(raw))) : raw;
		localStorage.setItem(k, value);
	}
	sessionStorage.setItem(MARK, '1');
	return true;
}

/** True once after an import (to confirm it after the reload). */
export function justImported() {
	const yes = sessionStorage.getItem(MARK) === '1';
	sessionStorage.removeItem(MARK);
	return yes;
}
