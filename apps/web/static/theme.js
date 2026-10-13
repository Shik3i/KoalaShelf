// Applies theme and appearance settings before first paint (no flash, no layout jump).
// Kept external for a strict CSP; the app keeps these attributes in sync afterwards.
try {
	const root = document.documentElement;
	const t = localStorage.getItem('ks.theme');
	if (t === 'light' || t === 'dark') root.classList.add('th-' + t);
	const p = JSON.parse(localStorage.getItem('ks.prefs') || '{}');
	const set = (name, value) => typeof value === 'string' && (root.dataset[name] = value);
	set('accent', p.accent);
	set('text', p.textSize);
	set('motion', p.motion);
	set('cover', p.coverSize);
} catch {
	// Storage blocked or unreadable: defaults (system theme, standard sizes).
}
