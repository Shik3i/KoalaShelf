// Custom accent color: the listener picks any color. A light-theme and a dark-theme variant are
// derived with the same hue, moved in lightness (OKLCH) only as far as needed to meet the same
// contrast rules as the built-in presets: 4.5:1 against every surface the accent is text on,
// and 4.5:1 for the text on top of it.
import { colors } from '@koalashelf/design-tokens';

type Theme = 'light' | 'dark';
type RGB = [number, number, number];

export interface AccentVariant {
	accent: string;
	onAccent: string;
}

export interface CustomAccent {
	light: AccentVariant;
	dark: AccentVariant;
	/** The picked color had to change somewhere to stay readable. */
	adjusted: boolean;
}

const MIN = 4.5;
/** Surfaces the accent appears on as text (see the design-token contrast pairs). */
const SURFACES: Record<Theme, (keyof typeof colors.light)[]> = {
	light: ['bg', 'card', 'panel'],
	dark: ['bg', 'card', 'panel', 'sel', 'cur-bg']
};
/** Text on the accent: the theme's usual choices. */
const ON: Record<Theme, string[]> = {
	light: ['#FFFFFF', colors.light.ink],
	dark: [colors.dark['on-accent'], '#FFFFFF']
};

export const isHex = (s: unknown): s is string =>
	typeof s === 'string' && /^#[0-9a-f]{6}$/i.test(s);

function hexToRgb(hex: string): RGB {
	const n = parseInt(hex.slice(1), 16);
	return [(n >> 16) / 255, ((n >> 8) & 255) / 255, (n & 255) / 255];
}

function rgbToHex(rgb: RGB) {
	return `#${rgb
		.map((c) =>
			Math.round(Math.min(1, Math.max(0, c)) * 255)
				.toString(16)
				.padStart(2, '0')
		)
		.join('')
		.toUpperCase()}`;
}

const toLinear = (c: number) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
const toGamma = (c: number) => (c <= 0.0031308 ? 12.92 * c : 1.055 * c ** (1 / 2.4) - 0.055);

function luminance(hex: string) {
	const [r, g, b] = hexToRgb(hex).map(toLinear);
	return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

export function contrast(a: string, b: string) {
	const [x, y] = [luminance(a), luminance(b)].sort((m, n) => n - m);
	return (x + 0.05) / (y + 0.05);
}

/** sRGB -> OKLCH (L 0..1, C, H in radians). */
function toOklch(hex: string): [number, number, number] {
	const [r, g, b] = hexToRgb(hex).map(toLinear);
	const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
	const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
	const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
	const L = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s;
	const A = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s;
	const B = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s;
	return [L, Math.hypot(A, B), Math.atan2(B, A)];
}

/** OKLCH -> sRGB (may lie outside 0..1 when out of gamut). */
function fromOklch(L: number, C: number, H: number): RGB {
	const A = C * Math.cos(H);
	const B = C * Math.sin(H);
	const l = (L + 0.3963377774 * A + 0.2158037573 * B) ** 3;
	const m = (L - 0.1055613458 * A - 0.0638541728 * B) ** 3;
	const s = (L - 0.0894841775 * A - 1.291485548 * B) ** 3;
	return [
		toGamma(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
		toGamma(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
		toGamma(-0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s)
	];
}

const inGamut = (rgb: RGB) => rgb.every((c) => c >= -1e-4 && c <= 1 + 1e-4);

/** The color at lightness L with the most chroma (up to C) that sRGB can show. */
function mapped(L: number, C: number, H: number) {
	let lo = 0;
	let hi = C;
	if (inGamut(fromOklch(L, C, H))) return rgbToHex(fromOklch(L, C, H));
	for (let i = 0; i < 20; i++) {
		const mid = (lo + hi) / 2;
		if (inGamut(fromOklch(L, mid, H))) lo = mid;
		else hi = mid;
	}
	return rgbToHex(fromOklch(L, lo, H));
}

function bestOn(accent: string, theme: Theme) {
	return ON[theme]
		.map((on) => ({ on, ratio: contrast(on, accent) }))
		.sort((a, b) => b.ratio - a.ratio)[0];
}

function readable(accent: string, theme: Theme) {
	return (
		SURFACES[theme].every((k) => contrast(accent, colors[theme][k]) >= MIN) &&
		bestOn(accent, theme).ratio >= MIN
	);
}

function variant(hex: string, theme: Theme): AccentVariant & { adjusted: boolean } {
	if (readable(hex, theme))
		return { accent: hex, onAccent: bestOn(hex, theme).on, adjusted: false };
	const [L0, C, H] = toOklch(hex);
	// Light theme: darker until readable on light surfaces; dark theme: lighter.
	const dir = theme === 'light' ? -1 : 1;
	for (let L = L0; L >= 0 && L <= 1; L += dir * 0.005) {
		const candidate = mapped(L, C, H);
		if (readable(candidate, theme))
			return { accent: candidate, onAccent: bestOn(candidate, theme).on, adjusted: true };
	}
	// Nothing in that direction works (cannot happen with these surfaces): the preset default.
	return { accent: colors[theme].accent, onAccent: colors[theme]['on-accent'], adjusted: true };
}

export function customAccent(hex: string): CustomAccent {
	const color = isHex(hex) ? hex.toUpperCase() : colors.light.accent;
	const light = variant(color, 'light');
	const dark = variant(color, 'dark');
	return {
		light: { accent: light.accent, onAccent: light.onAccent },
		dark: { accent: dark.accent, onAccent: dark.onAccent },
		adjusted: light.adjusted || dark.adjusted
	};
}

const STYLE_ID = 'ks-custom-accent';
export const CUSTOM_KEY = 'ks.accentCustom';

/** The same rules as the presets in tokens.css, for data-accent="custom". */
export function accentCss({ light, dark }: Pick<CustomAccent, 'light' | 'dark'>) {
	if (![light.accent, light.onAccent, dark.accent, dark.onAccent].every(isHex)) return '';
	const d = `--c-accent:${dark.accent};--c-on-accent:${dark.onAccent};--c-sel-ink:${dark.accent};--c-cur-ink:${dark.accent}`;
	return (
		`:root[data-accent='custom']{--c-accent:${light.accent};--c-on-accent:${light.onAccent}}` +
		`:root[data-accent='custom'].th-dark{${d}}` +
		`@media (prefers-color-scheme: dark){:root[data-accent='custom']:not(.th-light){${d}}}`
	);
}

/** Applies the variants now and remembers them for the first paint (static/theme.js). */
export function applyCustomAccent(accent: CustomAccent) {
	const css = accentCss(accent);
	let el = document.getElementById(STYLE_ID);
	if (!el) {
		el = document.createElement('style');
		el.id = STYLE_ID;
		document.head.append(el);
	}
	el.textContent = css;
	try {
		localStorage.setItem(CUSTOM_KEY, JSON.stringify({ light: accent.light, dark: accent.dark }));
	} catch {
		/* storage blocked: applies for this visit */
	}
}
