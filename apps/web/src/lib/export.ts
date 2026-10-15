// Your data as files: bookmarks, listening history and settings. Built on this device and
// saved directly; nothing is uploaded anywhere.

/** Saves text as a file (the browser's normal download). */
export function saveFile(name: string, text: string | Blob, type = 'text/plain') {
	const blob =
		typeof text === 'string' ? new Blob([text], { type: `${type};charset=utf-8` }) : text;
	const url = URL.createObjectURL(blob);
	const a = document.createElement('a');
	a.href = url;
	a.download = name;
	document.body.append(a);
	a.click();
	a.remove();
	setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

/** RFC 4180 CSV; cells that spreadsheets would run as formulas are defused. */
export function toCsv(rows: (string | number | null | undefined)[][]) {
	const cell = (v: string | number | null | undefined) => {
		let s = v === null || v === undefined ? '' : String(v);
		if (/^[=+\-@\t\r]/.test(s) && typeof v === 'string') s = `'${s}`;
		return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
	};
	return rows.map((r) => r.map(cell).join(',')).join('\r\n') + '\r\n';
}

/** "2026-10-15" in local time, for file names. */
export function today() {
	const d = new Date();
	return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Days in a row with listening, ending today (or yesterday, if today has none yet). */
export function streak(days: Record<string, number>, now = new Date()) {
	const key = (d: Date) =>
		`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
	const d = new Date(now);
	if (!(days[key(d)] > 0)) d.setDate(d.getDate() - 1);
	let n = 0;
	while (days[key(d)] > 0) {
		n++;
		d.setDate(d.getDate() - 1);
	}
	return n;
}
