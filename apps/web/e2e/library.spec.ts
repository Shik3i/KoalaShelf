import { api, resetListening } from './helpers.ts';
import { expect, test, type Page } from '@playwright/test';

// The tile's own title (placeholder covers print the title as well).
const titles = (page: Page) => page.locator('.tile > .title').allTextContents();
const sorted = (list: string[]) => [...list].sort((a, b) => a.localeCompare(b));

/** Opens the filter panel and toggles a value in one facet. */
async function pick(page: Page, facet: string, value: string) {
	const panel = page.locator('#filter-panel');
	if (!(await panel.isVisible())) await page.getByRole('button', { name: /^Filters/ }).click();
	await panel
		.getByRole('group', { name: facet })
		.getByRole('button', { name: value, exact: true })
		.click();
}

async function authorId(name: string) {
	const libs = await api<{ libraries: { id: string; name: string }[] }>('/api/libraries');
	const lib = libs.libraries.find((l) => l.name === 'Hörbücher')!;
	const fd = await api<{
		authors: { id: string; name: string }[];
		series: { id: string; name: string }[];
	}>(`/api/libraries/${lib.id}/filterdata`);
	return { author: fd.authors.find((a) => a.name === name)!.id, series: fd.series[0].id };
}

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

	test('genre filter, active chip, survives reload', async ({ page }) => {
		await pick(page, 'Genre', 'Krimi');
		await expect(page).toHaveURL(/genre=Krimi/);
		await expect.poll(() => titles(page)).toEqual(['Nachtzug']);
		await expect(page.getByRole('button', { name: 'Filters (1)' })).toBeVisible();
		await expect(page.getByRole('button', { name: 'Krimi ✕' })).toBeVisible();
		await page.reload();
		await expect.poll(() => titles(page)).toEqual(['Nachtzug']);
	});

	test('narrator filter', async ({ page }) => {
		await pick(page, 'Narrator', 'Ines Brandt');
		await expect.poll(() => titles(page)).toEqual(['Der lange Weg', 'Salzwasser']);
	});

	test('series filter sorts by volume and offers a removable chip', async ({ page }) => {
		await pick(page, 'Series', 'Küstenlichter');
		await expect.poll(() => titles(page)).toEqual(['Der lange Weg', 'Salzwasser']);
		await page.getByRole('button', { name: /Küstenlichter ✕/ }).click();
		await expect(page.locator('.tile')).toHaveCount(7);
	});

	test('combined filters are intersected (genre + status)', async ({ page }) => {
		await pick(page, 'Genre', 'Roman');
		await page.getByRole('button', { name: 'New', exact: true }).click();
		await expect.poll(() => titles(page)).toEqual(['Der lange Weg', 'Salzwasser']);
		await page.getByRole('button', { name: 'Finished', exact: true }).click();
		await expect(page.getByText('No titles match this selection.')).toBeVisible();
	});

	test('values of one kind widen, kinds narrow (genre OR genre, AND narrator)', async ({
		page
	}) => {
		await pick(page, 'Genre', 'Krimi');
		await pick(page, 'Genre', 'Roman');
		await expect
			.poll(async () => sorted(await titles(page)))
			.toEqual(['Der lange Weg', 'Nachtzug', 'Salzwasser']);
		await pick(page, 'Narrator', 'Paul Dahm');
		await expect.poll(() => titles(page)).toEqual(['Nachtzug']);
		await page.getByRole('button', { name: 'Clear all' }).click();
		await expect(page.locator('.tile')).toHaveCount(7);
	});

	test('author and series together are both applied', async ({ page }) => {
		// Regression: the author used to be dropped silently when a series was chosen too.
		const mara = await authorId('Mara Lindqvist');
		const jonas = await authorId('Jonas Falk');
		await page.goto(`/library?series=${mara.series}&author=${mara.author}`);
		await expect.poll(() => titles(page)).toEqual(['Der lange Weg', 'Salzwasser']);
		await page.goto(`/library?series=${mara.series}&author=${jonas.author}`);
		await expect(page.getByText('No titles match this selection.')).toBeVisible();
	});

	test('length filter', async ({ page }) => {
		await pick(page, 'Length', 'Under 2 h');
		await expect(page.locator('.tile')).toHaveCount(7);
		await pick(page, 'Length', '2–6 h');
		await expect(page.getByText('No titles match this selection.')).toBeVisible();
	});

	test('text filter finds titles and the books of matching authors and narrators', async ({
		page
	}) => {
		const find = page.getByRole('searchbox', { name: 'Filter this library' });
		await find.fill('lindq');
		await expect(page).toHaveURL(/q=lindq/);
		await expect
			.poll(async () => sorted(await titles(page)))
			.toEqual(['Der lange Weg', 'Salzwasser']);
		await find.fill('Hanna');
		// Hanna Voss wrote "Wintergrün" and narrates "Kleine Eulen".
		await expect
			.poll(async () => sorted(await titles(page)))
			.toEqual(['Kleine Eulen', 'Wintergrün']);
		await find.fill('');
		await expect(page.locator('.tile')).toHaveCount(7);
	});

	test('random order can be reshuffled', async ({ page }) => {
		await page.getByRole('combobox', { name: 'Sort' }).selectOption({ label: 'Random' });
		await expect(page.locator('.tile')).toHaveCount(7);
		await page.getByRole('button', { name: 'Shuffle again' }).click();
		await expect(page).toHaveURL(/shuffle=/);
		await expect(page.locator('.tile')).toHaveCount(7);
		await page.getByRole('combobox', { name: 'Sort' }).selectOption({ label: 'Title' });
	});

	test('a filter combination can be saved as a view and reopened', async ({ page }) => {
		await pick(page, 'Genre', 'Roman');
		await page.getByRole('button', { name: 'Save view' }).click();
		await page.getByRole('textbox', { name: 'Name for this view' }).fill('Romane');
		await page.getByRole('button', { name: 'Save', exact: true }).click();
		await expect(page.getByRole('status').getByText('View saved: Romane')).toBeVisible();
		await page.getByRole('button', { name: 'Clear all' }).click();
		await expect(page.locator('.tile')).toHaveCount(7);
		const views = page.getByRole('group', { name: 'Your views' });
		await views.getByRole('button', { name: 'Romane', exact: true }).click();
		await expect.poll(() => titles(page)).toEqual(['Der lange Weg', 'Salzwasser']);
		await expect(views.getByRole('button', { name: 'Romane', exact: true })).toHaveAttribute(
			'aria-pressed',
			'true'
		);
		await views.getByRole('button', { name: 'Delete view Romane' }).click();
		await expect(views).toHaveCount(0);
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

	test('author link from a book opens the author, then the filtered library', async ({ page }) => {
		await page.locator('.tile', { hasText: 'Nachtzug' }).click();
		await expect(page).toHaveURL(/\/book\//);
		await page.getByRole('link', { name: 'Jonas Falk', exact: true }).click();
		await expect(page).toHaveURL(/\/author\//);
		await expect(page.getByRole('heading', { level: 1, name: 'Jonas Falk' })).toBeVisible();
		await page.getByRole('link', { name: 'Show in library' }).click();
		await expect(page).toHaveURL(/author=/);
		await expect(page.getByRole('button', { name: /Jonas Falk ✕/ })).toBeVisible();
		await expect.poll(() => titles(page)).toEqual(['Nachtzug']);
	});

	test('switching library and paging through 70 titles (infinite scroll)', async ({ page }) => {
		await page.locator('.lib-trigger').click();
		await page.locator('.lib-opt', { hasText: 'Kurzgeschichten' }).click();
		await expect(page.getByText('70 titles')).toBeVisible();
		await expect(page.locator('.tile')).toHaveCount(60);
		await page.locator('.more').scrollIntoViewIfNeeded();
		await expect(page.locator('.tile')).toHaveCount(70);
		await expect(page.locator('.more')).toHaveCount(0);
		// The choice is remembered across reloads.
		await page.reload();
		await expect(page.getByText('70 titles')).toBeVisible();
	});
});
