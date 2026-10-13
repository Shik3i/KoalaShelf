import { expect, test, type Page } from '@playwright/test';
import { itemId } from './helpers.ts';

// Every page at every common size: phones (incl. 320 px = WCAG reflow at 400 % zoom),
// landscape phones, tablets, laptops and large screens.
const VIEWPORTS = [
	['phone-320', 320, 640],
	['phone-360', 360, 740],
	['phone-390', 390, 844],
	['phone-414', 414, 896],
	['phone-landscape', 844, 390],
	['tablet', 768, 1024],
	['tablet-landscape', 1024, 768],
	['laptop', 1280, 800],
	['desktop', 1440, 900],
	['full-hd', 1920, 1080],
	['wide', 2560, 1440]
] as const;

const dir = process.env.SCREENS_DIR;

async function pages() {
	const book = await itemId('Nachtzug');
	return [
		'/',
		'/library',
		'/library?view=list',
		'/series',
		'/authors',
		'/collections',
		'/history',
		'/stats',
		'/search?q=a',
		'/settings',
		'/downloads',
		`/book/${book}`
	];
}

/** Page wider than the viewport = sideways scrolling (measured against the set width). */
async function overflow(page: Page, width: number) {
	return page.evaluate((w) => document.documentElement.scrollWidth - w, width);
}

test.describe('responsive', () => {
	for (const [name, width, height] of VIEWPORTS) {
		test(`${name} (${width}×${height}): no sideways scrolling, player fits`, async ({ page }) => {
			await page.setViewportSize({ width, height });
			for (const path of await pages()) {
				await page.goto(path);
				await page.waitForTimeout(dir ? 900 : 300);
				expect(await overflow(page, width), `${path} at ${name}`).toBeLessThanOrEqual(0);
				if (dir)
					await page.screenshot({ path: `${dir}/rs-${name}-${path.replace(/\W+/g, '_')}.png` });
			}
			// Player: opens, plays, and its main controls are on screen without sideways scrolling.
			await page.goto(`/book/${await itemId('Nachtzug')}`);
			await page.locator('.cta .btn.primary').click();
			await page.getByRole('button', { name: 'Open player' }).click();
			const dialog = page.getByRole('dialog', { name: 'Player' });
			await page.waitForTimeout(600);
			for (const control of ['Pause', 'Sleep timer', 'Chapters', 'Bookmarks']) {
				const b = dialog.getByRole('button', { name: control, exact: true });
				await b.scrollIntoViewIfNeeded();
				const box = (await b.boundingBox())!;
				expect(box.x, `${control} at ${name}`).toBeGreaterThanOrEqual(0);
				expect(box.x + box.width, `${control} at ${name}`).toBeLessThanOrEqual(width);
			}
			expect(await overflow(page, width), `player at ${name}`).toBeLessThanOrEqual(0);
			if (dir) {
				await dialog.getByRole('button', { name: 'Pause', exact: true }).scrollIntoViewIfNeeded();
				await page.screenshot({ path: `${dir}/rs-${name}-player.png` });
			}
			await dialog.getByRole('button', { name: 'Pause', exact: true }).click();
		});
	}

	test('largest text size on a small phone: still no sideways scrolling', async ({ page }) => {
		await page.setViewportSize({ width: 360, height: 740 });
		await page.addInitScript(() =>
			localStorage.setItem('ks.prefs', JSON.stringify({ textSize: 'xl' }))
		);
		for (const path of await pages()) {
			await page.goto(path);
			await page.waitForTimeout(dir ? 900 : 300);
			expect(await overflow(page, 360), `${path} at 125 %`).toBeLessThanOrEqual(0);
			if (dir)
				await page.screenshot({ path: `${dir}/rs-text-xl-${path.replace(/\W+/g, '_')}.png` });
		}
	});
});
