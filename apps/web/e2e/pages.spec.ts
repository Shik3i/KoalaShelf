import { expect, test } from '@playwright/test';
import { api, itemId, resetListening } from './helpers.ts';

test.describe('pages', () => {
	test.beforeAll(resetListening);

	test('home shows crates and, after listening, the continue card', async ({ page }) => {
		const id = await itemId('Salzwasser');
		await api(`/api/me/progress/${id}`, {
			method: 'PATCH',
			body: JSON.stringify({ currentTime: 30, progress: 0.15 })
		});
		await page.goto('/');
		await expect(page.getByText('Continue listening', { exact: true })).toBeVisible();
		await expect(page.getByRole('heading', { level: 2, name: 'Salzwasser' })).toBeVisible();
		await expect(page.getByRole('button', { name: /Resume at 0:30/ })).toBeVisible();
		await expect(page.getByRole('region', { name: 'Recently added' })).toBeVisible();
		// Hide it again: the card disappears, progress stays on the server.
		await page.getByRole('button', { name: 'Remove from continue listening' }).click();
		await expect(page.getByRole('heading', { level: 2, name: 'Salzwasser' })).toBeHidden();
		const p = await api<{ currentTime: number }>(`/api/me/progress/${id}`);
		expect(p.currentTime).toBe(30);
	});

	test('book page: details, description without HTML, bookmarks from the server', async ({
		page
	}) => {
		const id = await itemId('Der lange Weg');
		await api(`/api/me/item/${id}/bookmark`, {
			method: 'POST',
			body: JSON.stringify({ time: 12, title: 'Vom Server' })
		});
		await page.goto(`/book/${id}`);
		await expect(page.getByText('Küstenlichter · Book 1')).toBeVisible();
		await expect(page.getByText('Narrated by')).toBeVisible();
		await expect(page.getByText('6 chapters')).toBeVisible();
		await expect(page.locator('.desc')).not.toContainText('<');
		await page.getByRole('tab', { name: /Bookmarks/ }).click();
		await expect(page.getByText('Vom Server')).toBeVisible();
		await api(`/api/me/item/${id}/bookmark/12`, { method: 'DELETE' });
	});

	test('unknown book id shows a not-found message', async ({ page }) => {
		await page.goto('/book/does-not-exist');
		await expect(page.getByText('This title does not exist (anymore).')).toBeVisible();
	});

	test('series overview: a crate to flip through every book, by pointer, wheel and keys', async ({
		page
	}) => {
		await page.goto('/series');
		const crate = page.getByRole('group', { name: 'Küstenlichter' });
		await expect(crate).toContainText('2 books');
		// In series order, whatever order the server lists them in.
		const record = crate.getByRole('link', { name: /^Der lange Weg – Küstenlichter, 1 of 2$/ });
		await expect(record).toBeVisible();
		// Over the right half of the crate: the second record is pulled up, the link follows it.
		const box = (await record.boundingBox())!;
		await page.mouse.move(box.x + box.width * 0.8, box.y + box.height / 2);
		await expect(crate).toContainText('2/2 · Salzwasser');
		await expect(crate.locator('.rec.cur')).toHaveCount(1);
		const second = crate.getByRole('link', { name: /^Salzwasser – Küstenlichter, 2 of 2$/ });
		await expect(second).toHaveAttribute('href', `/book/${await itemId('Salzwasser')}`);
		// Sideways scrolling flips back.
		await page.mouse.wheel(-200, 0);
		await expect(crate).toContainText('1/2 · Der lange Weg');
		await page.mouse.move(0, 0);
		await expect(crate).toContainText('2 books');
		// Keyboard: arrows and End flip, the hint explains it, Enter opens the book in front.
		await record.focus();
		await page.keyboard.press('End');
		await expect(crate).toContainText('2/2 · Salzwasser');
		await expect(second).toHaveAccessibleDescription(/arrow keys flip/i);
		await page.keyboard.press('Enter');
		await expect(page.getByRole('heading', { level: 1, name: 'Salzwasser' })).toBeVisible();
	});

	test('series overview lists the series as a stack and opens its page', async ({ page }) => {
		await page.goto('/series');
		await expect(page.getByText('1 series')).toBeVisible();
		await page.getByRole('link', { name: 'Küstenlichter', exact: true }).click();
		await expect(page).toHaveURL(/\/series\/[\w-]+$/);
	});

	test('authors overview opens the author page with their books', async ({ page }) => {
		await page.goto('/authors');
		await expect(page.locator('.person')).toHaveCount(6);
		await page.getByRole('link', { name: /Mara Lindqvist/ }).click();
		await expect(page.locator('.tile')).toHaveCount(2);
	});

	test('collections page and detail', async ({ page }) => {
		await page.goto('/collections');
		await expect(page.getByRole('region', { name: 'Lieblinge' })).toBeVisible();
		await page.getByRole('link', { name: /Lieblinge/ }).click();
		await expect(page.getByRole('heading', { name: 'Lieblinge' })).toBeVisible();
		await expect(page.locator('.tile')).toHaveCount(2);
		await expect(page.getByText('Fiktive Testsammlung.')).toBeVisible();
	});

	test('statistics show listening time after a session', async ({ page }) => {
		await page.goto(`/book/${await itemId('Kleine Eulen')}`);
		await page.locator('.cta .btn.primary').click();
		await page.waitForTimeout(3000);
		// Pause through the UI (works on production builds too); pausing syncs the time listened.
		await page.locator('.cta .btn.primary').click();
		await expect(page.locator('.cta .btn.primary')).not.toContainText('Pause');
		await page.goto('/stats');
		await expect(page.getByText('Listened in total')).toBeVisible();
		await expect(
			page.locator('.heat .cell.l1, .heat .cell.l2, .heat .cell.l3, .heat .cell.l4')
		).not.toHaveCount(0);
		await expect(page.locator('.top li')).not.toHaveCount(0);
	});

	test('search: results, no results, query kept in the URL', async ({ page }) => {
		await page.goto('/search');
		await page.getByPlaceholder(/Titles, authors/).fill('lange');
		await expect(page.getByRole('link', { name: /Der lange Weg/ })).toBeVisible();
		await expect(page).toHaveURL(/q=lange/);
		// Author names are found as authors.
		await page.getByPlaceholder(/Titles, authors/).fill('lindq');
		await expect(page.getByRole('link', { name: 'Mara Lindqvist' })).toBeVisible();
		await page.getByPlaceholder(/Titles, authors/).fill('zzzzqq');
		await expect(page.getByText('Nothing found for “zzzzqq”.')).toBeVisible();
	});

	test('settings: theme, language, skip steps persist', async ({ page }) => {
		await page.goto('/settings');
		await page.getByRole('button', { name: 'Light', exact: true }).click();
		await expect(page.locator('html')).toHaveClass(/th-light/);
		await page.getByRole('button', { name: '15 s' }).first().click();
		await page.getByRole('button', { name: 'DE', exact: true }).click();
		await expect(page.getByRole('heading', { name: 'Einstellungen' })).toBeVisible();
		await expect(page.locator('html')).toHaveAttribute('lang', 'de');
		await page.reload();
		await expect(page.getByRole('heading', { name: 'Einstellungen' })).toBeVisible();
		await expect(page.locator('html')).toHaveClass(/th-light/);
		expect(await page.evaluate(() => JSON.parse(localStorage.getItem('ks.prefs')!).skipBack)).toBe(
			15
		);
		// Back to defaults for the following tests.
		await page.getByRole('button', { name: 'EN', exact: true }).click();
		await page.evaluate(() => {
			localStorage.removeItem('ks.locale');
			localStorage.removeItem('ks.prefs');
		});
		await page
			.getByRole('group', { name: 'Theme' })
			.getByRole('button', { name: 'System', exact: true })
			.click();
		await page.getByRole('button', { name: '30 s' }).first().click();
		await expect(page.locator('html')).not.toHaveClass(/th-light/);
	});
});
