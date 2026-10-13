import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { itemId, playerCall } from './helpers.ts';

// Automated accessibility scan (WCAG 2.2 AA) of every page in both themes.
for (const scheme of ['light', 'dark'] as const) {
	test.describe(`a11y ${scheme}`, () => {
		test.use({ colorScheme: scheme });

		test('all pages pass axe', async ({ page }) => {
			const id = await itemId('Der lange Weg');
			const pages = [
				'/',
				'/library',
				'/library?view=list',
				'/series',
				'/authors',
				'/collections',
				'/stats',
				'/search',
				'/settings',
				'/downloads',
				`/book/${id}`
			];
			for (const path of pages) {
				await page.goto(path);
				await page.waitForTimeout(600);
				const result = await new AxeBuilder({ page })
					.withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
					.analyze();
				const issues = result.violations.map(
					(v) =>
						`${v.id}: ${v.nodes
							.map((n) => n.target.join(' '))
							.slice(0, 3)
							.join(' | ')}`
				);
				expect(issues, `${path} (${scheme})`).toEqual([]);
			}
		});

		test('mini player and full player with every panel pass axe', async ({ page }) => {
			await page.goto(`/book/${await itemId('Salzwasser')}`);
			await page.locator('.cta .btn.primary').click();
			const scan = async (label: string) => {
				const r = await new AxeBuilder({ page })
					.withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
					.analyze();
				expect(
					r.violations.map((v) => `${v.id}: ${v.nodes[0]?.target.join(' ')}`),
					label
				).toEqual([]);
			};
			await scan('mini player');
			await page.getByRole('button', { name: 'Open player' }).click();
			const dialog = page.getByRole('dialog', { name: 'Player' });
			await page.waitForTimeout(500); // let the open animation finish (opacity)
			await scan('full player');
			for (const panel of ['Sleep timer', 'Chapters', 'Bookmarks']) {
				await dialog.getByRole('button', { name: panel }).click();
				await scan(`panel ${panel}`);
			}
			await page.keyboard.press('Escape');
			await playerCall(page, 'stop');
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
