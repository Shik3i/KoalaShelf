import { expect, test, type Page } from '@playwright/test';
import { api, itemId, player, playerCall, resetListening } from './helpers.ts';

const playing = (page: Page) => player<boolean>(page, 'playing');
const time = (page: Page) => player<number>(page, 'currentTime');

test.describe('quality of life', () => {
	test.beforeAll(resetListening);
	test.afterEach(async ({ page }) => {
		await playerCall(page, 'stop').catch(() => undefined);
	});

	test('a link to a moment (?t=) starts playback there', async ({ page }) => {
		await page.goto(`/book/${await itemId('Der lange Weg')}?t=1:30`);
		await page.getByRole('button', { name: 'Play from 1:30' }).click();
		await expect.poll(() => playing(page)).toBe(true);
		expect(await time(page)).toBeGreaterThanOrEqual(89);
		await expect.poll(() => player<number>(page, 'chapterIndex')).toBe(2);
	});

	test('bookmark delete can be undone; links to moments are copied', async ({ page, context }) => {
		await context.grantPermissions(['clipboard-read', 'clipboard-write']);
		const id = await itemId('Salzwasser');
		await page.goto(`/book/${id}`);
		await page.locator('.cta .btn.primary').click();
		await expect.poll(() => playing(page)).toBe(true);
		await playerCall(page, 'seek', 33);
		await page.getByRole('button', { name: 'Open player' }).click();
		await page.keyboard.press('b');
		const dialog = page.getByRole('dialog', { name: 'Player' });
		await dialog.getByPlaceholder('What happens here?').fill('Wendepunkt');
		await dialog.getByRole('button', { name: /Add bookmark/ }).click();
		await expect(page.getByRole('status').getByText('Bookmark saved')).toBeVisible();

		await dialog.getByRole('button', { name: 'Copy link to this moment' }).click();
		await expect(page.getByRole('status').getByText('Link copied')).toBeVisible();
		expect(await page.evaluate(() => navigator.clipboard.readText())).toMatch(
			// Playback keeps running while the bookmark is added: 33 s or a moment later.
			new RegExp(`/book/${id}\\?t=3\\d$`)
		);

		await dialog.getByRole('button', { name: 'Delete' }).click();
		await expect(dialog.locator('.list li', { hasText: 'Wendepunkt' })).toHaveCount(0);
		await page.getByRole('status').getByRole('button', { name: 'Undo' }).click();
		await expect(dialog.locator('.list li', { hasText: 'Wendepunkt' })).toBeVisible();
		await expect
			.poll(
				async () =>
					(await api<{ bookmarks: { title: string }[] }>(`/api/me/bookmarks/${id}`)).bookmarks
						.length
			)
			.toBe(1);
	});

	test('speed is remembered per book', async ({ page }) => {
		await page.goto(`/book/${await itemId('Glasfluss')}`);
		await page.locator('.cta .btn.primary').click();
		await expect.poll(() => playing(page)).toBe(true);
		await page.locator('body').click({ position: { x: 5, y: 5 } });
		await page.keyboard.press(']');
		await page.keyboard.press(']');
		expect(await player<number>(page, 'rate')).toBe(1.5);
		await page.goto(`/book/${await itemId('Wintergrün')}`);
		await page.locator('.cta .btn.primary').click();
		await expect.poll(() => player<string>(page, 'item.id')).toBe(await itemId('Wintergrün'));
		// A new book starts at the speed used last; then it keeps its own.
		await expect.poll(() => player<number>(page, 'rate')).toBe(1.5);
		await page.locator('body').click({ position: { x: 5, y: 5 } });
		await page.keyboard.press('[');
		await page.keyboard.press('[');
		await expect.poll(() => player<number>(page, 'rate')).toBe(1);
		await page.goto(`/book/${await itemId('Glasfluss')}`);
		await page.locator('.cta .btn.primary').click();
		await expect.poll(() => player<number>(page, 'rate')).toBe(1.5);
		await page.keyboard.press('[');
		await page.keyboard.press('[');
	});

	test('clock toggles between elapsed and remaining time', async ({ page }) => {
		await page.goto(`/book/${await itemId('Nachtzug')}`);
		await page.locator('.cta .btn.primary').click();
		await page.getByRole('button', { name: 'Open player' }).click();
		const clock = page.getByRole('button', { name: 'Switch between elapsed and remaining time' });
		await expect(clock).not.toContainText('−');
		await clock.click();
		await expect(clock).toContainText('−');
		await clock.click();
		await expect(clock).not.toContainText('−');
	});

	test('"?" opens the shortcut overview', async ({ page }) => {
		await page.goto('/library');
		await expect(page.locator('.tile').first()).toBeVisible();
		await page.keyboard.press('?');
		await expect(page.getByRole('dialog', { name: 'Keyboard shortcuts' })).toBeVisible();
		await page.keyboard.press('Escape');
		await expect(page.getByRole('dialog', { name: 'Keyboard shortcuts' })).toBeHidden();
	});

	test('library remembers view and sort, not filters', async ({ page }) => {
		await page.goto('/library?view=list&sort=author&genre=Krimi');
		await expect(page.locator('.list li')).toHaveCount(1);
		await page.goto('/');
		await page.goto('/library');
		await expect(page).toHaveURL(/view=list/);
		await expect(page).toHaveURL(/sort=author/);
		await expect(page).not.toHaveURL(/genre=/);
		await expect(page.locator('.list li')).toHaveCount(7);
		// Back to the default view for other tests.
		await page.getByRole('button', { name: 'Shelf' }).click();
		await page.getByRole('combobox', { name: /Sort/ }).selectOption({ label: 'Title' });
	});

	test('removing from continue listening can be undone', async ({ page }) => {
		const id = await itemId('Kleine Eulen');
		await api(`/api/me/progress/${id}`, {
			method: 'PATCH',
			body: JSON.stringify({ currentTime: 20, progress: 0.15 })
		});
		await page.goto('/');
		await expect(page.getByRole('heading', { level: 2, name: 'Kleine Eulen' })).toBeVisible();
		await page.getByRole('button', { name: 'Remove from continue listening' }).click();
		await expect(page.getByRole('heading', { level: 2, name: 'Kleine Eulen' })).toBeHidden();
		await page.getByRole('status').getByRole('button', { name: 'Undo' }).click();
		await expect(page.getByRole('heading', { level: 2, name: 'Kleine Eulen' })).toBeVisible();
	});
});
