import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { api, env, itemId, stopPlayer } from './helpers.ts';

async function noAxeViolations(page: Page, label: string) {
	await page.waitForTimeout(400);
	const r = await new AxeBuilder({ page })
		.withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa', 'best-practice'])
		.analyze();
	expect(
		r.violations.map((v) => `${v.id}: ${v.nodes[0]?.html}`),
		label
	).toEqual([]);
}

async function library() {
	const libs = await api<{ libraries: { id: string; name: string }[] }>('/api/libraries');
	return libs.libraries.find((l) => l.name === 'Hörbücher')!.id;
}

/** Removes playlists left by earlier runs, so names and counts are predictable. */
async function clearPlaylists() {
	const lib = await library();
	const r = await api<{ results: { id: string }[] }>(`/api/libraries/${lib}/playlists?limit=0`);
	for (const p of r.results) await api(`/api/playlists/${p.id}`, { method: 'DELETE' });
}

test.describe('browse', () => {
	test.afterEach(async ({ page }) => {
		await stopPlayer(page);
	});

	test('author page: portrait, series in order, other books, link to the library', async ({
		page
	}) => {
		await page.goto('/authors');
		await page.getByRole('link', { name: /Mara Lindqvist/ }).click();
		await expect(page).toHaveURL(/\/author\//);
		await expect(page.getByRole('heading', { level: 1, name: 'Mara Lindqvist' })).toBeVisible();
		const series = page.locator('section', {
			has: page.getByRole('heading', { name: 'Küstenlichter' })
		});
		await expect(series.locator('.tile > .title')).toHaveText(['Der lange Weg', 'Salzwasser']);
		await noAxeViolations(page, 'author page');
		await page.getByRole('link', { name: 'Show in library' }).click();
		await expect(page).toHaveURL(/\/library\?author=/);
	});

	test('narrators overview leads to their books', async ({ page }) => {
		await page.goto('/authors');
		await page
			.getByRole('group', { name: 'Show' })
			.getByRole('button', { name: 'Narrators' })
			.click();
		await expect(page).toHaveURL(/tab=narrators/);
		const paul = page.getByRole('link', { name: /Paul Dahm/ });
		await expect(paul).toContainText('2 books');
		await paul.click();
		await expect(page).toHaveURL(/narrator=Paul/);
		await expect(page.locator('.tile')).toHaveCount(2);
	});

	test('series page: progress, the next book, queue and "continue series"', async ({ page }) => {
		await page.goto('/series');
		await page.getByRole('link', { name: /Küstenlichter/ }).click();
		await expect(page).toHaveURL(/\/series\//);
		await expect(page.getByRole('heading', { level: 1, name: 'Küstenlichter' })).toBeVisible();
		await expect(page.getByText(/of 2 finished/)).toBeVisible();
		await expect(page.locator('.books li')).toHaveCount(2);
		await expect(page.locator('.books li').first()).toContainText('Der lange Weg');
		await noAxeViolations(page, 'series page');
		await page.getByRole('button', { name: /Remove from "Continue series"/ }).click();
		await expect(
			page.getByRole('status').getByText('Removed from "Continue series"')
		).toBeVisible();
		await page.getByRole('status').getByRole('button', { name: 'Undo' }).click();
		await page.locator('.cta .btn.primary').click();
		await expect(page.getByRole('region', { name: 'Player' })).toBeVisible();
	});

	test('playlists: create, add from a book, reorder, remove with undo, play, delete', async ({
		page
	}) => {
		await clearPlaylists();
		await page.goto('/playlists');
		await expect(page.getByText(/No playlists yet/)).toBeVisible();
		await page.getByRole('textbox', { name: 'New playlist' }).fill('Abendrunde');
		await page.getByRole('button', { name: 'Create' }).click();
		await expect(page).toHaveURL(/\/playlists\//);
		await expect(page.getByRole('heading', { level: 1, name: 'Abendrunde' })).toBeVisible();

		for (const title of ['Nachtzug', 'Salzwasser']) {
			await page.goto(`/book/${await itemId(title)}`);
			await page.getByRole('button', { name: 'Playlist', exact: true }).click();
			const dialog = page.getByRole('dialog', { name: 'Add to playlist' });
			await dialog.getByRole('checkbox', { name: /Abendrunde/ }).click();
			await expect(dialog.getByRole('checkbox', { name: /Abendrunde/ })).toHaveAttribute(
				'aria-checked',
				'true'
			);
			await expect(dialog.getByRole('status').or(page.getByRole('status')).first()).toBeVisible();
			await dialog.getByRole('button', { name: 'Done' }).click();
		}

		await page.goto('/playlists');
		await page.getByRole('link', { name: /Abendrunde/ }).click();
		const rows = page.locator('ol.items li');
		await expect(rows).toHaveText([/Nachtzug/, /Salzwasser/]);
		await noAxeViolations(page, 'playlist page');
		await page.getByRole('button', { name: 'Move Salzwasser up' }).click();
		await expect(rows).toHaveText([/Salzwasser/, /Nachtzug/]);
		await page.reload();
		await expect(rows).toHaveText([/Salzwasser/, /Nachtzug/]);

		await page.getByRole('button', { name: 'Remove Nachtzug from the playlist' }).click();
		await expect(rows).toHaveCount(1);
		await page.getByRole('status').getByRole('button', { name: 'Undo' }).click();
		await expect(rows).toHaveText([/Salzwasser/, /Nachtzug/]);

		await page.getByRole('button', { name: 'Play all' }).click();
		await expect(page.getByRole('region', { name: 'Player' })).toContainText('Salzwasser');

		await page.getByRole('button', { name: 'Delete playlist' }).click();
		await expect(page.getByRole('alert')).toContainText('Delete “Abendrunde”?');
		await page.getByRole('button', { name: 'Delete playlist' }).last().click();
		await expect(page).toHaveURL(/\/playlists$/);
		await expect(page.getByText(/No playlists yet/)).toBeVisible();
	});

	test('a new playlist can be made right from a book', async ({ page }) => {
		await clearPlaylists();
		await page.goto(`/book/${await itemId('Wintergrün')}`);
		await page.getByRole('button', { name: 'Playlist', exact: true }).click();
		const dialog = page.getByRole('dialog', { name: 'Add to playlist' });
		await dialog.getByRole('textbox', { name: 'New playlist' }).fill('Winter');
		await dialog.getByRole('button', { name: 'Create' }).click();
		await expect(dialog.getByRole('checkbox', { name: /Winter/ })).toHaveAttribute(
			'aria-checked',
			'true'
		);
		await noAxeViolations(page, 'playlist dialog');
		await clearPlaylists();
	});

	test('all bookmarks in one place: play from one, delete with undo', async ({ page }) => {
		const id = await itemId('Wintergrün');
		await api(`/api/me/item/${id}/bookmark`, {
			method: 'POST',
			body: JSON.stringify({ time: 20, title: 'Schneefall' })
		});
		await page.goto('/bookmarks');
		const book = page.locator('section', { hasText: 'Wintergrün' });
		await expect(book.getByRole('button', { name: /Schneefall/ })).toBeVisible();
		await noAxeViolations(page, 'bookmarks page');
		await book.getByRole('button', { name: 'Delete' }).click();
		await expect(book).toHaveCount(0);
		await page.getByRole('status').getByRole('button', { name: 'Undo' }).click();
		await expect(page.getByRole('button', { name: /Schneefall/ })).toBeVisible();
		await page.getByRole('button', { name: /Schneefall/ }).click();
		await expect(page.getByRole('region', { name: 'Player' })).toContainText('Wintergrün');
		await api(`/api/me/item/${id}/bookmark/20`, { method: 'DELETE' });
	});

	test('statistics: the year in review and the library in numbers', async ({ page }) => {
		await page.goto('/stats');
		const year = new Date().getFullYear();
		await expect(page.getByRole('heading', { name: `Your ${year}` })).toBeVisible();
		const facts = page.locator('section', { hasText: 'Library Hörbücher' });
		await expect(facts.getByText('Titles')).toBeVisible();
		await expect(facts.locator('dd').first()).toHaveText('7');
		await noAxeViolations(page, 'statistics');
	});

	test('settings: signed-in devices and password checks', async ({ page }) => {
		// Another signed-in device to sign out.
		await fetch(`${env.ABS_URL}/login`, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json', 'x-return-tokens': 'true' },
			body: JSON.stringify({ username: env.ABS_USER, password: env.ABS_PASSWORD })
		});
		await page.goto('/settings#account');
		const devices = page.locator('ul.devices li');
		await expect(devices.filter({ hasText: 'This device' })).toHaveCount(1);
		// This device comes first; the others can be signed out remotely.
		await expect(devices.first()).toContainText('This device');
		const [res] = await Promise.all([
			page.waitForResponse(
				(r) => r.url().includes('/api/me/sessions/') && r.request().method() === 'DELETE'
			),
			devices.nth(1).getByRole('button', { name: 'Sign out' }).click()
		]);
		expect(res.ok()).toBe(true);
		await expect(page.getByRole('button', { name: /Show all \d+ devices/ })).toBeVisible();

		await page.getByLabel('Current password').fill('definitely-wrong');
		await page.getByLabel('New password', { exact: true }).fill('new-secret-1');
		await page.getByLabel('Repeat new password').fill('new-secret-2');
		await page.getByRole('button', { name: 'Change password' }).click();
		await expect(page.getByRole('alert')).toHaveText('The new passwords do not match.');
		await page.getByLabel('Repeat new password').fill('new-secret-1');
		await page.getByRole('button', { name: 'Change password' }).click();
		await expect(page.getByRole('alert')).toHaveText('The current password is not right.');
	});

	test('quick search (Ctrl K): books, pages and actions by keyboard', async ({ page }) => {
		await page.goto('/library');
		await expect(page.locator('.tile').first()).toBeVisible();
		await page.keyboard.press('Control+k');
		const palette = page.getByRole('dialog', { name: 'Quick search' });
		await expect(palette).toBeVisible();
		await noAxeViolations(page, 'quick search');
		await page.keyboard.type('lange');
		await expect(palette.getByRole('option', { name: /Der lange Weg/ })).toBeVisible();
		await expect(palette.getByRole('option').first()).toHaveAttribute('aria-selected', 'true');
		await page.keyboard.press('Enter');
		await expect(page).toHaveURL(/\/book\//);
		await expect(palette).toBeHidden();

		await page.keyboard.press('/');
		await expect(palette).toBeVisible();
		await page.keyboard.type('statis');
		await expect(palette.getByRole('option', { name: 'Statistics' })).toBeVisible();
		// Arrow down until the selected option is the page we want.
		for (let i = 0; i < 20; i++) {
			if (await palette.getByRole('option', { name: 'Statistics', selected: true }).count()) break;
			await page.keyboard.press('ArrowDown');
		}

		await page.keyboard.press('Enter');
		await expect(page).toHaveURL(/\/stats$/);

		await page.keyboard.press('Control+k');
		await page.keyboard.press('Escape');
		await expect(palette).toBeHidden();
	});

	test('search page: genres, recent searches and all libraries', async ({ page }) => {
		await page.goto('/search');
		await page.getByPlaceholder(/Titles, authors/).fill('rom');
		const genres = page.locator('h2', { hasText: 'Genres' });
		await expect(genres).toBeVisible();
		await expect(page.getByRole('link', { name: /Roman/ })).toBeVisible();
		await page.getByPlaceholder(/Titles, authors/).fill('');
		await expect(page.getByRole('button', { name: 'rom' })).toBeVisible();

		// Stories live in the second library: found only when searching everywhere.
		await page.getByPlaceholder(/Titles, authors/).fill('Geschichte 0');
		await expect(page.getByText(/Nothing found/)).toBeVisible();
		await page
			.getByRole('group', { name: 'Search in' })
			.getByRole('button', { name: 'All libraries' })
			.click();
		await expect(page).toHaveURL(/in=all/);
		await expect(page.getByRole('link', { name: /Geschichte 01/ })).toContainText(
			'Kurzgeschichten'
		);
		await noAxeViolations(page, 'search page');
	});
});
