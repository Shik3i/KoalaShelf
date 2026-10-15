import { expect, test } from '@playwright/test';
import { itemId, playerCall, resetListening } from './helpers.ts';

// Visual review: renders every page and key state to screenshots (not an assertion test).
// Run with: SCREENS_DIR=<folder> pnpm exec playwright test screens
const dir = process.env.SCREENS_DIR;

test.describe('screens', () => {
	test.skip(!dir, 'set SCREENS_DIR to render screenshots');
	test.beforeAll(resetListening);

	for (const scheme of ['light', 'dark'] as const) {
		for (const [device, viewport] of [
			['desktop', { width: 1280, height: 800 }],
			['mobile', { width: 390, height: 844 }]
		] as const) {
			test(`${device} ${scheme}`, async ({ page }) => {
				await page.emulateMedia({ colorScheme: scheme });
				await page.setViewportSize(viewport);
				const shot = async (name: string) => {
					await page.waitForTimeout(700);
					await page.screenshot({ path: `${dir}/${device}-${scheme}-${name}.png` });
				};
				const book = await itemId('Der lange Weg');
				for (const [name, path] of [
					['home', '/'],
					['library', '/library'],
					['library-grouped', '/library?group=1&genre=Roman'],
					['list', '/library?view=list'],
					['book', `/book/${book}`],
					['series', '/series'],
					['authors', '/authors'],
					['collections', '/collections'],
					['history', '/history'],
					['stats', '/stats'],
					['search', '/search?q=lange'],
					['settings', '/settings'],
					['downloads', '/downloads']
				]) {
					await page.goto(path);
					await shot(name);
				}
				// Managing: selection bar, book actions, daily goal.
				await page.goto('/library');
				await page.getByRole('button', { name: 'Select', exact: true }).click();
				await page.getByRole('button', { name: /^Glasfluss/ }).click();
				await shot('library-select');
				await page.keyboard.press('Escape');
				await page.goto(`/book/${await itemId('Salzwasser')}`);
				await page.getByRole('button', { name: 'More actions' }).click();
				await shot('book-more');
				await page.evaluate(() =>
					localStorage.setItem('ks.prefs', JSON.stringify({ dailyGoal: 30 }))
				);
				await page.goto('/stats');
				await shot('stats-goal');
				await page.goto('/');
				await shot('home-goal');
				await page.evaluate(() => localStorage.removeItem('ks.prefs'));
				// Two books queued (as added from their pages).
				for (const title of ['Salzwasser', 'Wintergrün']) {
					await page.goto(`/book/${await itemId(title)}`);
					await page.getByRole('button', { name: 'Add to queue' }).click();
				}
				await page.goto(`/book/${book}`);
				await shot('book-queued');
				await page.locator('.cta .btn.primary').click();
				await page.getByRole('button', { name: 'Open player' }).click();
				await expect(page.getByRole('dialog', { name: 'Player' })).toBeVisible();
				await shot('player');
				const dialog = page.getByRole('dialog');
				await dialog.getByRole('button', { name: 'Chapters' }).click();
				await shot('player-chapters');
				await playerCall(page, 'addBookmark', 'Leuchtturm', 40);
				const box = (await dialog.locator('.timeline').boundingBox())!;
				await page.mouse.move(box.x + box.width * 0.7, box.y + box.height / 2);
				await shot('player-hover');
				await playerCall(page, 'deleteBookmark', 40);
				await page.mouse.move(0, 0);
				await dialog.getByRole('button', { name: 'Sleep timer' }).click();
				await dialog.getByRole('button', { name: 'Smart' }).click();
				await shot('player-smart-timer');
				await dialog.getByRole('button', { name: 'Start timer' }).click();
				await shot('player-smart-running');
				await dialog.getByRole('button', { name: /^Up next/ }).click();
				await shot('player-queue');
				await dialog.getByRole('button', { name: /^Speed:/ }).click();
				await shot('player-speed');
				await dialog.getByRole('button', { name: 'Sound' }).click();
				await shot('player-sound');
				await dialog.getByRole('button', { name: 'Sound' }).click();
				await shot('player-plain');
				await page.evaluate(() => localStorage.removeItem('ks.queue'));
				await playerCall(page, 'setSleep', {
					kind: 'minutes',
					minutes: 15,
					endsAt: Date.now() + 9e5
				});
				await page.keyboard.press('Escape');
				await shot('mini');
				if (device === 'mobile') {
					await page.getByRole('button', { name: 'More', exact: true }).click();
					await shot('more');
					await page.keyboard.press('Escape');
				}
				await playerCall(page, 'stop');
			});
		}
	}
});
