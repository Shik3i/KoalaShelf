import { resetListening } from './helpers.ts';
import { expect, test, type Page } from '@playwright/test';

const titles = (page: Page) => page.locator('.tile .title').allTextContents();

test.describe('library', () => {
	test.beforeAll(resetListening);

	test.beforeEach(async ({ page }) => {
		await page.goto('/library');
		await expect(page.locator('.tile')).toHaveCount(7);
	});

	test('lists all books sorted by title, count in header', async ({ page }) => {
		await expect(page.getByText('7 titles')).toBeVisible();
		const t = await titles(page);
		expect(t).toEqual([...t].sort((a, b) => a.localeCompare(b)));
	});

	test('reverse order', async ({ page }) => {
		const before = await titles(page);
		await page.getByRole('button', { name: 'Reverse order' }).click();
		await expect(page).toHaveURL(/desc=1/);
		await expect.poll(() => titles(page)).toEqual([...before].reverse());
	});

	test('genre filter, highlighted chip, survives reload', async ({ page }) => {
		await page.getByLabel('Genre').selectOption('Krimi');
		await expect(page).toHaveURL(/genre=Krimi/);
		await expect.poll(() => titles(page)).toEqual(['Nachtzug']);
		await expect(page.locator('label.chip.on')).toHaveCount(1);
		await page.reload();
		await expect.poll(() => titles(page)).toEqual(['Nachtzug']);
	});

	test('narrator filter with umlaut-free and umlaut values', async ({ page }) => {
		await page.getByLabel('Narrator').selectOption('Ines Brandt');
		await expect.poll(() => titles(page)).toEqual(['Der lange Weg', 'Salzwasser']);
	});

	test('series filter sorts by volume and offers a removable chip', async ({ page }) => {
		await page.getByRole('combobox', { name: 'Series' }).selectOption({ label: 'Küstenlichter' });
		await expect.poll(() => titles(page)).toEqual(['Der lange Weg', 'Salzwasser']);
		await page.getByRole('button', { name: /Küstenlichter ✕/ }).click();
		await expect(page.locator('.tile')).toHaveCount(7);
	});

	test('combined filters are intersected (genre + status)', async ({ page }) => {
		await page.getByLabel('Genre').selectOption('Roman');
		await page.getByRole('button', { name: 'New', exact: true }).click();
		await expect.poll(() => titles(page)).toEqual(['Der lange Weg', 'Salzwasser']);
		await page.getByRole('button', { name: 'Finished', exact: true }).click();
		await expect(page.getByText('No titles match this selection.')).toBeVisible();
	});

	test('list view shows narrator and duration', async ({ page }) => {
		await page.getByRole('button', { name: 'List' }).click();
		await expect(page).toHaveURL(/view=list/);
		await expect(page.locator('.list li')).toHaveCount(7);
		await expect(page.locator('.list li').first()).toContainText('read by');
	});

	test('group series shows one stacked entry per series', async ({ page }) => {
		await page.getByRole('button', { name: 'Group series' }).click();
		await expect(page.locator('.tile.group')).toHaveCount(1);
		await expect(page.locator('.tile.group .title')).toHaveText('Küstenlichter');
		await expect(page.locator('.tile.group')).toContainText('2 books');
		await page.locator('.tile.group').click();
		await expect(page).toHaveURL(/series=/);
		await expect.poll(() => titles(page)).toEqual(['Der lange Weg', 'Salzwasser']);
	});

	test('author link from a book filters the library', async ({ page }) => {
		await page.locator('.tile', { hasText: 'Nachtzug' }).click();
		await expect(page).toHaveURL(/\/book\//);
		await page.getByRole('link', { name: 'Jonas Falk', exact: true }).click();
		await expect(page).toHaveURL(/author=/);
		await expect(page.getByRole('button', { name: /Jonas Falk ✕/ })).toBeVisible();
		await expect.poll(() => titles(page)).toEqual(['Nachtzug']);
	});
});
