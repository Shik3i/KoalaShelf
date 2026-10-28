import { readFileSync } from 'node:fs';
import { expect, test, type Page } from '@playwright/test';
import { itemId, needsDevServer, player, playerCall, stopPlayer } from './helpers.ts';

const root = (page: Page, prop: string) =>
	page.evaluate((p) => getComputedStyle(document.documentElement).getPropertyValue(p).trim(), prop);

async function openSettings(page: Page) {
	await page.goto('/settings');
	await expect(page.getByRole('heading', { level: 1, name: 'Settings' })).toBeVisible();
}

test.describe('customization', () => {
	test.afterEach(async ({ page }) => {
		await stopPlayer(page);
	});

	test('appearance: accent, text size, motion and cover size apply at once and on reload', async ({
		page
	}) => {
		await openSettings(page);
		await page.getByRole('radio', { name: 'Ocean' }).check();
		// Lowercase: the production build minifies colors.
		await expect.poll(async () => (await root(page, '--c-accent')).toLowerCase()).toBe('#1d63b8');
		await page
			.getByRole('group', { name: 'Text size' })
			.getByRole('button', { name: 'Larger' })
			.click();
		await expect.poll(() => root(page, 'font-size')).toBe('20px');
		await page
			.getByRole('group', { name: 'Animations' })
			.getByRole('button', { name: 'Reduced' })
			.click();
		await expect
			.poll(() =>
				page.evaluate(() => getComputedStyle(document.querySelector('.btn')!).transitionDuration)
			)
			.toMatch(/^(1e-05s|0\.00001s)/);

		// Applied before the app starts (no flash of the default look).
		await page.reload({ waitUntil: 'domcontentloaded' });
		expect(await page.evaluate(() => ({ ...document.documentElement.dataset }))).toMatchObject({
			accent: 'ocean',
			text: 'xl',
			motion: 'reduce'
		});

		// Cover size changes the tile grid.
		const tileWidth = async () => {
			await page.goto('/library');
			return (await page.locator('.tile').first().boundingBox())!.width;
		};
		const medium = await tileWidth();
		await openSettings(page);
		await page
			.getByRole('group', { name: 'Cover size' })
			.getByRole('button', { name: 'Large' })
			.click();
		expect(await tileWidth()).toBeGreaterThan(medium * 1.15);

		// Reset brings everything back (with a confirmation step).
		await openSettings(page);
		await page.getByRole('button', { name: 'Reset settings' }).click();
		await expect(page.getByRole('alert')).toHaveText('Reset all settings?');
		await page.getByRole('button', { name: 'Reset settings' }).last().click();
		await expect(page.getByRole('radio', { name: 'Vinyl', exact: true })).toBeChecked();
		await expect.poll(() => root(page, 'font-size')).toBe('16px');
		expect(await page.evaluate(() => document.documentElement.dataset.accent)).toBe('vinyl');
	});

	test('appearance: font choices apply immediately and update dataset.font', async ({ page }) => {
		await openSettings(page);
		await page.getByRole('radio', { name: 'Literary' }).check();
		await expect
			.poll(() => page.evaluate(() => document.documentElement.dataset.font))
			.toBe('literary');
		await page.getByRole('radio', { name: 'Vintage Vinyl' }).check();
		await expect
			.poll(() => page.evaluate(() => document.documentElement.dataset.font))
			.toBe('vintage');
		await page.getByRole('radio', { name: 'Standard' }).check();
		await expect
			.poll(() => page.evaluate(() => document.documentElement.dataset.font))
			.toBe('standard');
	});

	test('accent presets keep text contrast in both themes', async ({ page }) => {
		await page.goto('/');
		// Styles are in place once the app has rendered (dev mode injects CSS from JS).
		await expect(page.getByRole('navigation', { name: 'Main navigation' }).first()).toBeVisible();
		const ratios = await page.evaluate(() => {
			const lum = (rgb: string) => {
				const [r, g, b] = rgb
					.match(/\d+/g)!
					.slice(0, 3)
					.map((v) => {
						const c = +v / 255;
						return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
					});
				return 0.2126 * r + 0.7152 * g + 0.0722 * b;
			};
			const ratio = (a: string, b: string) => {
				const [hi, lo] = [lum(a), lum(b)].sort((x, y) => y - x);
				return (hi + 0.05) / (lo + 0.05);
			};
			const probe = document.createElement('div');
			document.body.append(probe);
			const color = (v: string) => {
				probe.style.color = `var(${v})`;
				return getComputedStyle(probe).color;
			};
			const out: Record<string, number> = {};
			const el = document.documentElement;
			for (const theme of ['th-light', 'th-dark']) {
				el.classList.remove('th-light', 'th-dark');
				el.classList.add(theme);
				for (const accent of ['vinyl', 'ocean', 'teal', 'forest', 'indigo', 'plum']) {
					el.dataset.accent = accent;
					out[`${theme} ${accent} on-accent`] = ratio(color('--c-on-accent'), color('--c-accent'));
					out[`${theme} ${accent} on panel`] = ratio(color('--c-accent'), color('--c-panel'));
				}
			}
			return out;
		});
		for (const [name, r] of Object.entries(ratios)) expect(r, name).toBeGreaterThanOrEqual(4.5);
	});

	test('home shelves can be hidden and reordered; start page', async ({ page }) => {
		await openSettings(page);
		const shelves = page.locator('ol.shelves li');
		await shelves
			.filter({ hasText: 'Recently added' })
			.getByRole('switch', { name: 'Recently added' })
			.click();
		await page.getByRole('button', { name: 'Move Recent series up' }).click();
		await page.getByRole('button', { name: 'Move Recent series up' }).click();
		await expect(shelves.first()).toContainText('Recent series');
		await page.goto('/');
		await expect(page.getByText('Recent series')).toBeVisible();
		await expect(page.getByText('Recently added', { exact: true })).toHaveCount(0);

		await openSettings(page);
		await page
			.getByRole('group', { name: 'Start page' })
			.getByRole('button', { name: 'Library' })
			.click();
		await page.goto('/');
		await expect(page).toHaveURL(/\/library/);
	});

	test('keyboard shortcuts can be switched off', async ({ page }) => {
		await openSettings(page);
		await page.getByRole('switch', { name: 'Keyboard shortcuts' }).click();
		await page.goto('/library');
		await expect(page.locator('.tile').first()).toBeVisible();
		await page.keyboard.press('?');
		await expect(page.getByRole('dialog', { name: 'Keyboard shortcuts' })).toBeHidden();
	});

	test('save the book files as a ZIP', async ({ page }) => {
		await page.goto(`/book/${await itemId('Nachtzug')}`);
		const [download] = await Promise.all([
			page.waitForEvent('download'),
			page.getByRole('button', { name: 'Save files (ZIP)' }).click()
		]);
		expect(download.suggestedFilename()).toBe('Nachtzug.zip');
		const zip = readFileSync((await download.path())!);
		expect(zip.subarray(0, 2).toString()).toBe('PK');
		// Both audio files of the multi-file book are inside.
		expect(zip.toString('latin1').match(/\.mp3/g)?.length ?? 0).toBeGreaterThanOrEqual(2);
		await expect(page.getByRole('status').getByText('Download started')).toBeVisible();
	});

	test('timeline for the current chapter: clock, range and lock screen follow it', async ({
		page
	}) => {
		needsDevServer(); // reads player internals: skip before any playback starts
		await page.goto(`/book/${await itemId('Nachtzug')}`);
		await page.locator('.cta .btn.primary').click();
		await expect.poll(() => player<boolean>(page, 'playing')).toBe(true);
		await playerCall(page, 'seek', 52);
		await page.getByRole('button', { name: 'Open player' }).click();
		const dialog = page.getByRole('dialog', { name: 'Player' });
		const range = dialog.getByRole('slider', { name: 'Position' });
		await expect(range).toHaveAttribute('min', '0');

		await dialog.getByRole('button', { name: /Timeline shows: Book/ }).click();
		const chapter = await player<{ start: number; end: number }>(page, 'chapter');
		await expect(range).toHaveAttribute('min', String(chapter.start));
		await expect(range).toHaveAttribute('max', String(chapter.end));
		// The clock counts from the start of the chapter.
		await expect(dialog.locator('button.big')).toHaveText(/^0:0\d$/);
		await expect(dialog.getByText(/left in chapter/)).toBeVisible();
		expect(await player<boolean>(page, 'scope.chapter')).toBe(true);

		await dialog.getByRole('button', { name: /Timeline shows: Chapter/ }).click();
		await expect(range).toHaveAttribute('min', '0');
	});

	test('speed: fixed speed for new books, not remembered per book', async ({ page }) => {
		needsDevServer(); // reads player internals: skip before any playback starts
		await openSettings(page);
		await page.getByRole('switch', { name: 'Remember speed per book' }).click();
		const speed = page.getByRole('group', { name: 'Speed for new books' });
		await speed.getByRole('button', { name: 'Fixed speed' }).click();
		await page
			.getByRole('group', { name: 'Fixed speed' })
			.getByRole('button', { name: '1.2×' })
			.click();

		await page.goto(`/book/${await itemId('Glasfluss')}`);
		await page.locator('.cta .btn.primary').click();
		await expect.poll(() => player<number>(page, 'rate')).toBe(1.2);
		await playerCall(page, 'setRate', 2);
		await page.goto(`/book/${await itemId('Wintergrün')}`);
		await page.locator('.cta .btn.primary').click();
		await expect.poll(() => player<string>(page, 'item.id')).toBe(await itemId('Wintergrün'));
		await expect.poll(() => player<number>(page, 'rate')).toBe(1.2);
		await page.goto(`/book/${await itemId('Glasfluss')}`);
		await page.locator('.cta .btn.primary').click();
		await expect.poll(() => player<number>(page, 'rate')).toBe(1.2);
	});

	test('sleep timer without rewind stops exactly where it ends', async ({ page }) => {
		needsDevServer(); // reads player internals: skip before any playback starts
		await openSettings(page);
		await page
			.getByRole('group', { name: 'Go back when it stops' })
			.getByRole('button', { name: 'Off' })
			.click();
		await page.goto(`/book/${await itemId('Wintergrün')}`);
		await page.locator('.cta .btn.primary').click();
		await expect.poll(() => player<boolean>(page, 'playing')).toBe(true);
		await playerCall(page, 'seek', 10);
		await playerCall(page, 'setSleep', { kind: 'minutes', minutes: 1, endsAt: Date.now() + 2000 });
		await expect.poll(() => player<boolean>(page, 'playing'), { timeout: 10_000 }).toBe(false);
		expect(await player<number>(page, 'currentTime')).toBeGreaterThan(11);
	});
});
