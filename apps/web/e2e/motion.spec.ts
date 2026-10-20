import { expect, test } from '@playwright/test';
import { itemId } from './helpers.ts';

// The record-shop touches: they must land in the right place and never get in the way.

test('the navigation highlight glides to the current page and sits under it', async ({ page }) => {
	await page.goto('/library');
	const nav = page.getByRole('navigation', { name: 'Main navigation' });
	await nav.getByRole('link', { name: 'Settings' }).click();
	await expect(page).toHaveURL(/\/settings/);
	const current = nav.locator('[aria-current="page"]');
	await expect
		.poll(async () => {
			const [pill, cur] = await Promise.all([
				nav.locator('.pill').boundingBox(),
				current.boundingBox()
			]);
			return pill && cur ? Math.abs(pill.y - cur.y) + Math.abs(pill.height - cur.height) : 99;
		})
		.toBeLessThan(1);
});

test('finishing a book turns its record gold, once with a shine', async ({ page }) => {
	const id = await itemId('Salzwasser');
	await page.goto(`/book/${id}`);
	const disc = page.locator('.hero .disc');
	await expect(disc).not.toHaveClass(/gold/);
	await page.getByRole('button', { name: 'Mark as finished' }).click();
	await expect(disc).toHaveClass(/gold/);
	await expect(disc).toHaveClass(/shine/);
	await expect(page.getByText('100 % · Finished')).toBeVisible();
	await expect(disc).not.toHaveClass(/shine/, { timeout: 4000 });
	// Coming back later: still gold, no second shine.
	await page.reload();
	await expect(disc).toHaveClass(/gold/);
	await expect(disc).not.toHaveClass(/shine/);
	await page.getByRole('button', { name: 'Mark as not finished' }).click();
	await expect(disc).not.toHaveClass(/gold/);
});

test('switching the theme spreads from the click and leaves nothing behind', async ({ page }) => {
	await page.goto('/');
	const html = page.locator('html');
	const button = page.getByRole('button', { name: /^Theme: / }).first();
	await button.click();
	await expect(html).toHaveClass(/th-light/);
	await expect(html).not.toHaveClass(/theme-vt/);
	await button.click();
	await expect(html).toHaveClass(/th-dark/);
	await button.click();
	await expect(html).not.toHaveClass(/th-(light|dark)/);
	await expect(html).not.toHaveClass(/theme-vt/);
});
