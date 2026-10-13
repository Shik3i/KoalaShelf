import { expect, test } from '@playwright/test';
import { api, itemId, playerCall } from './helpers.ts';

test.describe('pages', () => {
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

	test('series page lists the series as a stack', async ({ page }) => {
		await page.goto('/series');
		await expect(page.getByText('1 series')).toBeVisible();
		await page.getByRole('link', { name: /Küstenlichter/ }).click();
		await expect(page).toHaveURL(/library\?series=/);
	});

	test('authors page links to the filtered library', async ({ page }) => {
		await page.goto('/authors');
		await expect(page.locator('.author')).toHaveCount(6);
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
		await playerCall(page, 'stop');
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
		await page.getByRole('button', { name: 'DE' }).first().click();
		await expect(page.getByRole('heading', { name: 'Einstellungen' })).toBeVisible();
		await expect(page.locator('html')).toHaveAttribute('lang', 'de');
		await page.reload();
		await expect(page.getByRole('heading', { name: 'Einstellungen' })).toBeVisible();
		await expect(page.locator('html')).toHaveClass(/th-light/);
		expect(await page.evaluate(() => JSON.parse(localStorage.getItem('ks.prefs')!).skipBack)).toBe(
			15
		);
		// Back to defaults for the following tests.
		await page.getByRole('button', { name: 'EN' }).first().click();
		await page.getByRole('button', { name: 'System', exact: true }).click();
		await page.getByRole('button', { name: '30 s' }).first().click();
		await expect(page.locator('html')).not.toHaveClass(/th-light/);
	});
});
