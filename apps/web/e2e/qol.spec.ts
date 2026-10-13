import { expect, test, type Page } from '@playwright/test';
import { api, itemId, player, playerCall, resetListening, stopPlayer } from './helpers.ts';

const playing = (page: Page) => player<boolean>(page, 'playing');
const time = (page: Page) => player<number>(page, 'currentTime');

test.describe('quality of life', () => {
	test.beforeAll(resetListening);
	test.afterEach(async ({ page }) => {
		await stopPlayer(page);
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

	test('finishing a book offers the next one in its series', async ({ page }) => {
		const first = await itemId('Der lange Weg');
		await page.goto(`/book/${first}`);
		await page.locator('.cta .btn.primary').click();
		await expect.poll(() => playing(page)).toBe(true);
		await playerCall(page, 'seek', (await player<number>(page, 'duration')) - 1.5);
		const toast = page.getByRole('status').filter({ hasText: 'Next in the series: Salzwasser' });
		await expect(toast).toBeVisible({ timeout: 15_000 });
		await toast.getByRole('button', { name: 'Play' }).click();
		await expect.poll(() => player<string>(page, 'item.id')).toBe(await itemId('Salzwasser'));
		await expect.poll(() => playing(page)).toBe(true);
		await api(`/api/me/progress/${first}`, {
			method: 'PATCH',
			body: JSON.stringify({ isFinished: false, currentTime: 0, progress: 0 })
		});
	});

	test('timeline previews the time under the mouse; mini player shows the sleep timer', async ({
		page
	}) => {
		await page.goto(`/book/${await itemId('Wintergrün')}`);
		await page.locator('.cta .btn.primary').click();
		await expect.poll(() => playing(page)).toBe(true);
		await page.getByRole('button', { name: 'Open player' }).click();
		const dialog = page.getByRole('dialog', { name: 'Player' });
		const timeline = dialog.locator('.timeline');
		const box = (await timeline.boundingBox())!;
		await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
		const duration = await player<number>(page, 'duration');
		const half = Math.round(duration / 2);
		const label = `${Math.floor(half / 60)}:${String(half % 60).padStart(2, '0')}`;
		await expect(dialog.locator('.bubble.hover')).toHaveText(new RegExp(label.slice(0, -1)));
		await page.mouse.move(box.x + box.width / 2, box.y - 200);
		await expect(dialog.locator('.bubble.hover')).toHaveCount(0);

		await dialog.getByRole('button', { name: 'Sleep timer' }).click();
		await dialog.getByRole('button', { name: '15 min' }).click();
		await page.keyboard.press('Escape');
		const badge = page.locator('.mini .sleep');
		await expect(badge).toHaveText(/1[45]:\d\d/);
		await playerCall(page, 'setSleep', { kind: 'chapter' });
		await expect(badge).toHaveText('End of chapter');
		await playerCall(page, 'setSleep', { kind: 'off' });
		await expect(badge).toHaveCount(0);
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
