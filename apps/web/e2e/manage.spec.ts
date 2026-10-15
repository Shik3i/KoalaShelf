import { expect, test } from '@playwright/test';
import { api, itemId, resetListening } from './helpers.ts';

// Managing books: multi-select with bulk actions, book page extras (reset progress,
// earlier volumes, files, details), series actions and collections.
const dir = process.env.SCREENS_DIR;

interface Me {
	mediaProgress: { libraryItemId: string; isFinished: boolean; progress: number }[];
}

const finished = async (title: string) => {
	const id = await itemId(title);
	const me = await api<Me>('/api/me');
	return me.mediaProgress.find((p) => p.libraryItemId === id)?.isFinished ?? false;
};

test.describe('manage', () => {
	test.beforeEach(resetListening);
	test.afterAll(resetListening);

	test('library: select books, mark them finished and not finished, queue them', async ({
		page
	}) => {
		await page.goto('/library');
		await page.evaluate(() => localStorage.removeItem('ks.queue'));
		await page.getByRole('button', { name: 'Select', exact: true }).click();
		const bar = page.getByRole('region', { name: 'Selection' });
		await expect(bar.getByText('0 selected')).toBeVisible();
		await page.getByRole('button', { name: /^Glasfluss/ }).click();
		await page.getByRole('button', { name: /^Wintergrün/ }).click();
		await expect(bar.getByText('2 selected')).toBeVisible();
		if (dir) await page.screenshot({ path: `${dir}/library-select.png` });
		await bar.getByRole('button', { name: 'Mark finished' }).click();
		await expect(page.getByRole('status').getByText('2 books marked as finished')).toBeVisible();
		expect(await finished('Glasfluss')).toBe(true);
		expect(await finished('Wintergrün')).toBe(true);
		// Done: selection mode ends; the tiles are links again.
		await expect(bar).toHaveCount(0);
		await expect(page.getByRole('link', { name: /Glasfluss/ })).toBeVisible();

		// List view, select all, mark not finished, then queue two.
		await page.goto('/library?view=list');
		await page.getByRole('button', { name: 'Select', exact: true }).click();
		await bar.getByRole('button', { name: 'Select all' }).click();
		await expect(bar.getByText('7 selected')).toBeVisible();
		await bar.getByRole('button', { name: 'Mark not finished' }).click();
		await expect(
			page.getByRole('status').getByText('7 books marked as not finished')
		).toBeVisible();
		expect(await finished('Glasfluss')).toBe(false);
		await page.getByRole('button', { name: 'Select', exact: true }).click();
		await page.getByRole('button', { name: /^Salzwasser/ }).click();
		await page.getByRole('button', { name: /^Kleine Eulen/ }).click();
		await bar.getByRole('button', { name: 'Add to queue' }).click();
		expect(
			await page.evaluate(() => JSON.parse(localStorage.getItem('ks.queue') ?? '[]').length)
		).toBe(2);
		await page.evaluate(() => localStorage.removeItem('ks.queue'));
		// Escape leaves selection mode.
		await page.getByRole('button', { name: 'Select', exact: true }).click();
		await page.keyboard.press('Escape');
		await expect(bar).toHaveCount(0);
	});

	test('library: several books into a new playlist at once', async ({ page }) => {
		await page.goto('/library');
		await page.getByRole('button', { name: 'Select', exact: true }).click();
		await page.getByRole('button', { name: /^Glasfluss/ }).click();
		await page.getByRole('button', { name: /^Nachtzug/ }).click();
		await page
			.getByRole('region', { name: 'Selection' })
			.getByRole('button', { name: 'Playlist' })
			.click();
		const dialog = page.getByRole('dialog', { name: 'Add to playlist' });
		await expect(dialog.getByText('2 books')).toBeVisible();
		await dialog.getByRole('textbox', { name: 'New playlist' }).fill('Sammelprobe');
		await dialog.getByRole('button', { name: 'Create' }).click();
		const box = dialog.getByRole('checkbox', { name: /Sammelprobe/ });
		await expect(box).toHaveAttribute('aria-checked', 'true');
		await dialog.getByRole('button', { name: 'Done' }).click();
		const { libraries } = await api<{ libraries: { id: string }[] }>('/api/libraries');
		const lists = await api<{ results: { id: string; name: string; items: unknown[] }[] }>(
			`/api/libraries/${libraries[0].id}/playlists`
		);
		const made = lists.results.find((p) => p.name === 'Sammelprobe')!;
		expect(made.items).toHaveLength(2);
		await api(`/api/playlists/${made.id}`, { method: 'DELETE' });
	});

	test('book: more actions, reset progress with undo, earlier volumes, files, details', async ({
		page
	}) => {
		const id = await itemId('Salzwasser');
		await api(`/api/me/progress/${id}`, {
			method: 'PATCH',
			body: JSON.stringify({ currentTime: 50, progress: 0.25, duration: 180 })
		});
		await page.goto(`/book/${id}`);
		await page.getByRole('button', { name: 'More actions' }).click();
		if (dir) await page.screenshot({ path: `${dir}/book-more.png` });
		await page.getByRole('button', { name: 'Reset progress' }).click();
		const toast = page.getByRole('status').filter({ hasText: 'Progress reset' });
		await expect(toast).toBeVisible();
		await expect(page.locator('.cta .btn.primary')).toHaveText(/^\s*Play\s*$/);
		await toast.getByRole('button', { name: 'Undo' }).click();
		await expect(page.locator('.cta .btn.primary')).toHaveText(/Resume at 0:50/);

		// Second volume of a series: the first one counts as heard.
		await page.getByRole('button', { name: 'Mark earlier books as finished' }).click();
		await expect(page.getByRole('status').getByText('1 book marked as finished')).toBeVisible();
		expect(await finished('Der lange Weg')).toBe(true);

		// Files tab and linked details.
		await page.getByRole('tab', { name: 'Files' }).click();
		await expect(page.getByRole('tabpanel').getByText('Salzwasser.m4b')).toBeVisible();
		await page.getByRole('link', { name: 'Roman' }).click();
		await expect(page).toHaveURL(/\/library\?genre=Roman/);
	});

	test('series: mark all finished with undo', async ({ page }) => {
		await page.goto(`/book/${await itemId('Salzwasser')}`);
		await page
			.getByRole('link', { name: /Küstenlichter/ })
			.first()
			.click();
		await page.getByRole('button', { name: 'Mark series as finished' }).click();
		const toast = page.getByRole('status').filter({ hasText: '2 books marked as finished' });
		await expect(toast).toBeVisible();
		await expect(page.getByText('2 of 2 finished')).toBeVisible();
		await toast.getByRole('button', { name: 'Undo' }).click();
		await expect(page.getByText('0 of 2 finished')).toBeVisible();
	});

	test('collections: create, add books, arrange, rename, save as playlist, delete', async ({
		page
	}) => {
		// Leftovers from an interrupted run.
		const { libraries } = await api<{ libraries: { id: string }[] }>('/api/libraries');
		const old = await api<{ results: { id: string; name: string }[] }>(
			`/api/libraries/${libraries[0].id}/collections`
		);
		for (const c of old.results.filter((c) => c.name.startsWith('Probelauf')))
			await api(`/api/collections/${c.id}`, { method: 'DELETE' });

		await page.goto('/collections');
		await expect(page.getByText('New collections start with books')).toBeVisible();
		// A new collection starts from a book; the second one joins it.
		for (const [i, title] of ['Glasfluss', 'Nachtzug'].entries()) {
			await page.goto(`/book/${await itemId(title)}`);
			await page.getByRole('button', { name: 'More actions' }).click();
			await page.getByRole('button', { name: 'Add to collection' }).click();
			const dialog = page.getByRole('dialog', { name: 'Add to collection' });
			if (i === 0) {
				await dialog.getByRole('textbox', { name: 'New collection' }).fill('Probelauf');
				await dialog.getByRole('button', { name: 'Create' }).click();
			} else await dialog.getByRole('checkbox', { name: /Probelauf/ }).click();
			await expect(page.getByRole('status').getByText('Added to “Probelauf”')).toBeVisible();
			await dialog.getByRole('button', { name: 'Done' }).click();
		}

		await page.goto('/collections');
		await page.getByRole('link', { name: 'Probelauf' }).first().click();
		await expect(page.getByRole('heading', { name: 'Probelauf' })).toBeVisible();
		const url = page.url();
		await expect(page.getByText('2 books')).toBeVisible();
		await page.getByRole('button', { name: 'Arrange' }).click();
		await page.getByRole('button', { name: 'Move Nachtzug up' }).click();
		await expect(page.locator('.items li').first()).toContainText('Nachtzug');
		if (dir) await page.screenshot({ path: `${dir}/collection-arrange.png` });
		await page.getByRole('button', { name: 'Remove Glasfluss from the collection' }).click();
		await expect(page.locator('.items li')).toHaveCount(1);
		await page.getByRole('status').getByRole('button', { name: 'Undo' }).click();
		await expect(page.locator('.items li')).toHaveCount(2);

		await page.getByRole('button', { name: 'Edit' }).click();
		await page.getByRole('textbox', { name: 'Name' }).fill('Probelauf 2');
		await page.getByRole('textbox', { name: 'Description' }).fill('Zum Testen');
		await page.getByRole('button', { name: 'Save', exact: true }).click();
		await expect(page.getByRole('heading', { name: 'Probelauf 2' })).toBeVisible();
		await expect(page.getByText('Zum Testen')).toBeVisible();

		await page.getByRole('button', { name: 'Save as playlist' }).click();
		await expect(page).toHaveURL(/\/playlists\//);
		await expect(page.getByRole('heading', { name: 'Probelauf 2' })).toBeVisible();
		await page.getByRole('button', { name: 'Delete playlist' }).click();
		await page.getByRole('button', { name: 'Delete playlist' }).click();

		await page.goto(url);
		await page.getByRole('button', { name: 'Delete collection' }).click();
		await page.getByRole('button', { name: 'Delete collection' }).click();
		await expect(page).toHaveURL(/\/collections$/);
		await expect(page.getByText('Probelauf 2')).toHaveCount(0);
	});
});
