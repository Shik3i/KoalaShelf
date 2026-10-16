// Applies theme and appearance settings before first paint (no flash, no layout jump).
// Kept external for a strict CSP; the app keeps these attributes in sync afterwards.
try {
	const root = document.documentElement;
	const t = localStorage.getItem('ks.theme');
	if (t === 'light' || t === 'dark') root.classList.add('th-' + t);
	const p = JSON.parse(localStorage.getItem('ks.prefs') || '{}');
	const set = (name, value) => typeof value === 'string' && (root.dataset[name] = value);
	set('accent', p.accent);
	set('font', p.font);
	set('text', p.textSize);
	set('motion', p.motion);
	set('cover', p.coverSize);
	// The listener's own accent (variants computed by src/lib/accent.ts; same rules as there).
	if (p.accent === 'custom') {
		const c = JSON.parse(localStorage.getItem('ks.accentCustom') || 'null');
		const hex = (s) => typeof s === 'string' && /^#[0-9a-f]{6}$/i.test(s);
		if (c && [c.light?.accent, c.light?.onAccent, c.dark?.accent, c.dark?.onAccent].every(hex)) {
			const d = `--c-accent:${c.dark.accent};--c-on-accent:${c.dark.onAccent};--c-sel-ink:${c.dark.accent};--c-cur-ink:${c.dark.accent}`;
			const style = document.createElement('style');
			style.id = 'ks-custom-accent';
			style.textContent =
				`:root[data-accent='custom']{--c-accent:${c.light.accent};--c-on-accent:${c.light.onAccent}}` +
				`:root[data-accent='custom'].th-dark{${d}}` +
				`@media (prefers-color-scheme: dark){:root[data-accent='custom']:not(.th-light){${d}}}`;
			document.head.append(style);
		}
	}
} catch {
	// Storage blocked or unreadable: defaults (system theme, standard sizes).
}
