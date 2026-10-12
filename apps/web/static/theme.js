// Applies a manual theme choice before first paint (no flash). Kept external for a strict CSP.
try {
	const t = localStorage.getItem('ks.theme');
	if (t === 'light' || t === 'dark') document.documentElement.classList.add('th-' + t);
} catch {}
