import { describe, expect, it } from 'vitest';
import { colors } from '@koalashelf/design-tokens';
import { accentCss, contrast, customAccent } from './accent.ts';

const surfaces = {
	light: [colors.light.bg, colors.light.card, colors.light.panel],
	dark: [
		colors.dark.bg,
		colors.dark.card,
		colors.dark.panel,
		colors.dark.sel,
		colors.dark['cur-bg']
	]
};

describe('custom accent', () => {
	it.each(['#FFE600', '#00FF00', '#FF00FF', '#000000', '#FFFFFF', '#1D63B8', '#808080', '#E8A33D'])(
		'%s is readable in both themes',
		(hex) => {
			const a = customAccent(hex);
			for (const theme of ['light', 'dark'] as const) {
				for (const s of surfaces[theme])
					expect(contrast(a[theme].accent, s)).toBeGreaterThanOrEqual(4.5);
				expect(contrast(a[theme].onAccent, a[theme].accent)).toBeGreaterThanOrEqual(4.5);
			}
		}
	);

	it('keeps a color that is already readable', () => {
		// The ocean preset's light variant passes in light mode as it is.
		const a = customAccent('#1D63B8');
		expect(a.light.accent).toBe('#1D63B8');
	});

	it('keeps the hue when it has to adjust', () => {
		const a = customAccent('#FFE600'); // bright yellow: far too light for the light theme
		expect(a.adjusted).toBe(true);
		const [r, g, b] = [1, 3, 5].map((i) => parseInt(a.light.accent.slice(i, i + 2), 16));
		expect(r).toBeGreaterThan(b); // still a yellow-brown, not a grey or blue
		expect(g).toBeGreaterThan(b);
	});

	it('writes CSS only for valid colors', () => {
		const ok = accentCss({
			light: { accent: '#112233', onAccent: '#FFFFFF' },
			dark: { accent: '#AABBCC', onAccent: '#000000' }
		});
		expect(ok).toContain(":root[data-accent='custom']{--c-accent:#112233");
		expect(
			accentCss({
				light: { accent: 'red;}body{display:none', onAccent: '#FFFFFF' },
				dark: { accent: '#AABBCC', onAccent: '#000000' }
			})
		).toBe('');
	});
});
