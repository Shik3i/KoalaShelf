import { expect, test } from '@playwright/test';
import { api, env, itemId, player, playerCall, signIn, resetListening } from './helpers.ts';

test.describe('live and offline', () => {
	test.beforeAll(resetListening);

	test('progress from another device appears without reload', async ({ page }) => {
		const id = await itemId('Wintergrün');
		await api(`/api/me/progress/${id}`, { method: 'DELETE' }).catch(() => undefined);
		await page.goto(`/book/${id}`);
		await expect(page.getByRole('button', { name: /^Play$/ })).toBeVisible();
		// "Another device": a session opened and synced through the API.
		const s = await api<{ id: string }>(`/api/items/${id}/play`, {
			method: 'POST',
			body: JSON.stringify({
				deviceInfo: { deviceId: 'e2e-phone', clientName: 'Phone' },
				supportedMimeTypes: ['audio/mp4']
			})
		});
		await api(`/api/session/${s.id}/sync`, {
			method: 'POST',
			body: JSON.stringify({ currentTime: 100, timeListened: 5, duration: 135 })
		});
		await expect(page.getByRole('button', { name: 'Resume at 1:40' })).toBeVisible();
		await api(`/api/session/${s.id}/close`, { method: 'POST', body: '{}' });
	});

	test('download, play offline, sync when back online, remove', async ({ page, context }) => {
		const id = await itemId('Kleine Eulen');
		await api(`/api/me/progress/${id}`, { method: 'DELETE' }).catch(() => undefined);
		await page.goto(`/book/${id}`);
		await page.getByRole('button', { name: 'Download' }).click();
		await expect(page.getByText('Downloaded')).toBeVisible({ timeout: 30_000 });

		await page.goto('/downloads');
		const row = page.locator('.list li', { hasText: 'Kleine Eulen' });
		await expect(row).toBeVisible();
		await expect(row).toContainText(/KB|MB/);

		await context.setOffline(true);
		await expect(page.getByRole('status').filter({ hasText: 'You are offline' })).toBeVisible();
		await row.getByRole('button', { name: 'Play' }).click();
		await expect.poll(() => player<boolean>(page, 'playing')).toBe(true);
		expect(await player<string>(page, 'audio.src')).toMatch(/^blob:/);
		await page.waitForTimeout(3000);
		await playerCall(page, 'pause');
		await expect
			.poll(() =>
				page.evaluate(() => JSON.parse(localStorage.getItem('ks.localSessions') ?? '[]').length)
			)
			.toBe(1);

		await context.setOffline(false);
		// Coming back online uploads the queued offline session.
		await expect
			.poll(
				() =>
					page.evaluate(() => JSON.parse(localStorage.getItem('ks.localSessions') ?? '[]').length),
				{
					timeout: 15_000
				}
			)
			.toBe(0);
		await expect
			.poll(
				async () => (await api<{ currentTime?: number }>(`/api/me/progress/${id}`)).currentTime ?? 0
			)
			.toBeGreaterThan(1);

		await playerCall(page, 'stop');
		await row.getByRole('button', { name: 'Remove download' }).click();
		await expect(page.getByText(/Nothing downloaded yet/)).toBeVisible();
	});
});

test.describe('guest without download permission', () => {
	test.use({ storageState: { cookies: [], origins: [] } });

	test('sees no download button', async ({ page }) => {
		await signIn(page, env.ABS_GUEST_USER, env.ABS_GUEST_PASSWORD);
		await page.goto(`/book/${await itemId('Nachtzug')}`);
		await expect(page.getByRole('heading', { level: 1, name: 'Nachtzug' })).toBeVisible();
		await expect(page.getByRole('button', { name: /^Play/ })).toBeVisible();
		await expect(page.getByRole('button', { name: 'Download' })).toHaveCount(0);
	});
});

test.describe('mobile', () => {
	test.use({ viewport: { width: 375, height: 812 }, hasTouch: true, isMobile: true });

	test('tab bar, mini player above it, full player fits the screen', async ({ page }) => {
		await page.goto('/');
		const tabs = page.getByRole('navigation', { name: 'Main navigation' }).last();
		await expect(tabs.getByRole('link', { name: 'Downloads' })).toBeVisible();
		await expect(page.locator('aside.side')).toBeHidden();

		await page.goto(`/book/${await itemId('Salzwasser')}`);
		await page.locator('.cta .btn.primary').click();
		const mini = page.getByRole('region', { name: 'Player' });
		await expect(mini).toBeVisible();
		const miniBox = (await mini.boundingBox())!;
		const tabBox = (await tabs.boundingBox())!;
		expect(miniBox.y + miniBox.height).toBeLessThanOrEqual(tabBox.y + 1);

		await mini.getByRole('button', { name: 'Open player' }).click();
		const dialog = page.getByRole('dialog', { name: 'Player' });
		for (const name of ['Sleep timer', 'Chapters', 'Bookmarks']) {
			const box = (await dialog.getByRole('button', { name }).boundingBox())!;
			expect(box.y + box.height).toBeLessThanOrEqual(812);
		}
		await page.keyboard.press('Escape');
		await playerCall(page, 'stop');
	});

	test('no horizontal overflow on any page', async ({ page }) => {
		const id = await itemId('Der lange Weg');
		for (const path of [
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
		]) {
			await page.goto(path);
			await page.waitForTimeout(400);
			const overflow = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
			expect(overflow, path).toBeLessThanOrEqual(0);
		}
	});
});
