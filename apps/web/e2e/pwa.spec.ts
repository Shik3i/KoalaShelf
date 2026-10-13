import { expect, test } from '@playwright/test';
import { itemId, resetListening } from './helpers.ts';

// Production build only (E2E_BASE_URL, e.g. the Docker image): the dev server has no service worker.
test.describe('pwa (production build)', () => {
	test.skip(!process.env.E2E_BASE_URL, 'needs E2E_BASE_URL pointing to a production build');

	test('no console errors or CSP violations on any page', async ({ page }) => {
		const errors: string[] = [];
		page.on('console', (m) => m.type() === 'error' && errors.push(m.text()));
		page.on('pageerror', (e) => errors.push(e.message));
		for (const path of [
			'/',
			'/library',
			'/series',
			'/authors',
			'/collections',
			'/stats',
			'/search',
			'/settings',
			'/downloads'
		]) {
			await page.goto(path);
			await page.waitForTimeout(500);
		}
		expect(errors).toEqual([]);
	});

	test('manifest and icons are served', async ({ request }) => {
		const manifest = await (await request.get('/manifest.webmanifest')).json();
		expect(manifest.name).toBe('KoalaShelf');
		for (const icon of manifest.icons) expect((await request.get(icon.src)).ok()).toBe(true);
	});

	test('service worker serves the app shell offline', async ({ page, context }) => {
		await page.goto('/library');
		await page.evaluate(() => navigator.serviceWorker.ready.then(() => true));
		// Let the worker take control of this page, then cut the network.
		await page.reload();
		await expect.poll(() => page.evaluate(() => !!navigator.serviceWorker.controller)).toBe(true);
		await context.setOffline(true);
		await page.goto('/downloads');
		await expect(page.getByRole('heading', { name: 'Downloads' })).toBeVisible();
		await expect(page.getByRole('status').filter({ hasText: 'You are offline' })).toBeVisible();
		await context.setOffline(false);
	});

	test('offline after a full reload: downloaded book still plays', async ({ page, context }) => {
		await resetListening();
		const id = await itemId('Wintergrün');
		await page.goto(`/book/${id}`);
		await page.evaluate(() => navigator.serviceWorker.ready.then(() => true));
		await page.getByRole('button', { name: 'Download' }).click();
		await expect(page.getByText('Downloaded', { exact: true })).toBeVisible({ timeout: 30_000 });
		await page.goto('/downloads');
		await expect.poll(() => page.evaluate(() => !!navigator.serviceWorker.controller)).toBe(true);

		await context.setOffline(true);
		await page.reload();
		const row = page.locator('.list li', { hasText: 'Wintergrün' });
		await expect(row).toBeVisible();
		await row.getByRole('button', { name: 'Play' }).click();
		const mini = page.getByRole('region', { name: 'Player' });
		await expect(mini.getByRole('button', { name: 'Pause' })).toBeVisible();
		await page.waitForTimeout(2000);
		await mini.getByRole('button', { name: 'Pause' }).click();
		await context.setOffline(false);
		await page.goto('/downloads');
		await page
			.locator('.list li', { hasText: 'Wintergrün' })
			.getByRole('button', { name: 'Remove download' })
			.click();
	});
});
