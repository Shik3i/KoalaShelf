import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import { itemId } from './helpers.ts';

/** Fly-ins and fades finished (infinite ones like the spinning record excepted). */
const settle = (page: Page) =>
	page.waitForFunction(() =>
		document
			.getAnimations()
			.every(
				(x) =>
					x instanceof CSSTransition ||
					x.playState !== 'running' ||
					x.effect?.getTiming().iterations === Infinity
			)
	);

// Automated accessibility scan (WCAG 2.2 AA plus axe best practices) of every page,
// in both themes, on a laptop and on a phone.
for (const scheme of ['light', 'dark'] as const) {
	test.describe(`a11y ${scheme}`, () => {
		test.use({ colorScheme: scheme });

		for (const [device, viewport] of [
			['laptop', { width: 1280, height: 800 }],
			['phone', { width: 390, height: 844 }]
		] as const)
			test(`all pages pass axe (${device})`, async ({ page }) => {
				await page.setViewportSize(viewport);
				const id = await itemId('Der lange Weg');
				const pages = [
					'/',
					'/library',
					'/library?view=list',
					'/series',
					'/authors',
					'/collections',
					'/history',
					'/playlists',
					'/bookmarks',
					'/authors?tab=narrators',
					'/stats',
					'/search',
					'/settings',
					'/downloads',
					`/book/${id}`
				];
				for (const path of pages) {
					await page.goto(path);
					await page.waitForTimeout(600);
					await settle(page);
					const result = await new AxeBuilder({ page })
						.withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa', 'best-practice'])
						.analyze();
					const issues = result.violations.map(
						(v) =>
							`${v.id}: ${v.nodes
								.map((n) => n.target.join(' '))
								.slice(0, 3)
								.join(' | ')}`
					);
					expect(issues, `${path} (${scheme}, ${device})`).toEqual([]);
				}
				if (device === 'phone') {
					await page.getByRole('button', { name: 'More', exact: true }).click();
					await expect(page.getByRole('dialog', { name: 'More' })).toBeVisible();
					await page.waitForTimeout(400);
					const sheet = await new AxeBuilder({ page })
						.withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa', 'best-practice'])
						.analyze();
					expect(
						sheet.violations.map((v) => v.id),
						`More sheet (${scheme})`
					).toEqual([]);
				}
			});

		test('selection, book actions, collections and the daily goal pass axe', async ({ page }) => {
			const scan = async (label: string) => {
				await page.waitForTimeout(500);
				await settle(page);
				const result = await new AxeBuilder({ page })
					.withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa', 'best-practice'])
					.analyze();
				expect(
					result.violations.map(
						(v) =>
							`${v.id}: ${v.nodes
								.map((n) => n.target.join(' '))
								.slice(0, 3)
								.join(' | ')}`
					),
					`${label} (${scheme})`
				).toEqual([]);
			};
			for (const path of ['/library', '/library?view=list']) {
				await page.goto(path);
				await page.getByRole('button', { name: 'Select', exact: true }).click();
				await page.getByRole('button', { name: /^Glasfluss/ }).click();
				await scan(`selection ${path}`);
			}
			await page.goto(`/book/${await itemId('Salzwasser')}`);
			await page.getByRole('button', { name: 'More actions' }).click();
			await scan('book: more actions');
			await page.getByRole('button', { name: 'Add to collection' }).click();
			await expect(page.getByRole('dialog', { name: 'Add to collection' })).toBeVisible();
			await scan('add to collection');
			await page.keyboard.press('Escape');
			await page.getByRole('tab', { name: 'Files' }).click();
			await scan('book: files');
			await page.goto('/collections');
			await page.getByRole('link', { name: 'Lieblinge' }).first().click();
			await expect(page.getByRole('heading', { name: 'Lieblinge' })).toBeVisible();
			await scan('collection');
			await page.getByRole('button', { name: 'Arrange' }).click();
			await scan('collection: arrange');
			await page.goto('/stats');
			await page
				.getByRole('group', { name: 'Daily goal' })
				.getByRole('button', { name: '30 min' })
				.click();
			await scan('daily goal');
			await page.goto('/');
			await scan('home with goal ring');
			await page.evaluate(() => localStorage.removeItem('ks.prefs'));
		});

		test('mini player and full player with every panel pass axe', async ({ page }) => {
			await page.goto(`/book/${await itemId('Salzwasser')}`);
			await page.locator('.cta .btn.primary').click();
			const scan = async (label: string) => {
				// Fly-ins and fades must settle first. The spinning record is infinite; CSS
				// transitions (progress line) restart with every time update.
				await page.waitForFunction(() =>
					document
						.getAnimations()
						.every(
							(x) =>
								x instanceof CSSTransition ||
								x.playState !== 'running' ||
								x.effect?.getTiming().iterations === Infinity
						)
				);
				// ...and CSS transitions such as a gliding segment pill (motion-base is 280 ms).
				await page.waitForTimeout(350);
				const r = await new AxeBuilder({ page })
					.withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
					.analyze();
				expect(
					r.violations.map((v) => `${v.id}: ${v.nodes[0]?.html}`),
					label
				).toEqual([]);
			};
			await scan('mini player');
			await page.getByRole('button', { name: 'Open player' }).click();
			const dialog = page.getByRole('dialog', { name: 'Player' });
			await page.waitForTimeout(500); // let the open animation finish (opacity)
			await scan('full player');
			for (const panel of ['Sleep timer', 'Chapters', 'Bookmarks', 'Speed:', 'Sound']) {
				await dialog.getByRole('button', { name: panel }).click();
				await scan(`panel ${panel}`);
			}
			await dialog.getByRole('button', { name: 'Sleep timer' }).click();
			await dialog.getByRole('button', { name: 'Smart' }).click();
			await scan('panel smart sleep timer');
			await dialog.getByRole('button', { name: /^Up next/ }).click();
			await scan('panel up next');
			await page.keyboard.press('Escape');
			// Pause through the UI: works on production builds as well.
			await page
				.getByRole('region', { name: 'Player' })
				.getByRole('button', { name: 'Pause' })
				.click();
		});
	});
}

test.describe('a11y sign-in', () => {
	test.use({ storageState: { cookies: [], origins: [] } });

	test('sign-in page passes axe', async ({ page }) => {
		await page.goto('/signin');
		await expect(page.getByText(/found/)).toBeVisible();
		const result = await new AxeBuilder({ page })
			.withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
			.analyze();
		expect(result.violations.map((v) => v.id)).toEqual([]);
	});
});
