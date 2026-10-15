// Readable device names from user agents ("Edge on Windows"), for the signed-in devices list.

const BROWSERS: [RegExp, string][] = [
	[/\bEdg(e|A|iOS)?\//, 'Edge'],
	[/\bOPR\//, 'Opera'],
	[/\bFirefox\//, 'Firefox'],
	[/\bSamsungBrowser\//, 'Samsung Internet'],
	[/\bChrome\//, 'Chrome'],
	[/\bVersion\/[\d.]+ .*Safari\//, 'Safari']
];

const SYSTEMS: [RegExp, string][] = [
	[/\bWindows\b/, 'Windows'],
	[/\b(iPhone|iPad|iPod)\b/, 'iOS'],
	[/\bAndroid\b/, 'Android'],
	[/\bMac OS X\b|\bMacintosh\b/, 'macOS'],
	[/\bCrOS\b/, 'ChromeOS'],
	[/\bLinux\b/, 'Linux']
];

/** { app, system }: a browser or app name and the operating system, when recognizable. */
export function describeAgent(ua: string | null | undefined): { app: string; system: string } {
	if (!ua) return { app: '', system: '' };
	const system = SYSTEMS.find(([re]) => re.test(ua))?.[1] ?? '';
	// Native Audiobookshelf apps and tools announce themselves plainly.
	if (/okhttp|Dalvik/i.test(ua)) return { app: 'Audiobookshelf app', system: system || 'Android' };
	if (/CFNetwork|Darwin/i.test(ua) && !/Mozilla/.test(ua))
		return { app: 'Audiobookshelf app', system: system || 'iOS' };
	const browser = BROWSERS.find(([re]) => re.test(ua))?.[1];
	if (browser) return { app: browser, system };
	return { app: ua.split(/[\s/]/)[0].slice(0, 32), system };
}
