import { expect, test } from '@playwright/test';
import { api, itemId, needsDevServer, player, stopPlayer } from './helpers.ts';

// The record-shop touches: they must land in the right place and never get in the way.

test('only the vinyl disc spins while playing — book covers and sleeves never rotate', async ({
	page
}) => {
	needsDevServer();
	const id = await itemId('Der lange Weg');
	await page.goto(`/book/${id}`);
	await page.locator('.cta .btn.primary').click();
	await expect.poll(() => player<boolean>(page, 'playing')).toBe(true);

	// 1. On the book detail page: the hero `.art` container and `.cover` sleeve must never animate.
	const heroAnimations = await page.evaluate(() => {
		const art = document.querySelector('.hero .art');
		const cover = document.querySelector('.hero .cover');
		const sleeve = document.querySelector('.hero .sleeve-card');
		return {
			art: art ? getComputedStyle(art).animationName : null,
			cover: cover ? getComputedStyle(cover).animationName : null,
			sleeve: sleeve ? getComputedStyle(sleeve).animationName : null
		};
	});
	expect(heroAnimations).toEqual({ art: 'none', cover: 'none', sleeve: 'none' });

	// 2. On the library overview page (client-side navigation so playback continues):
	//    the `.tile .cover` wrapper and `figure.cover` must never spin; only `.disc.spinning` rotates.
	await page
		.getByRole('navigation', { name: 'Main navigation' })
		.getByRole('link', { name: 'Library' })
		.click();
	await expect(page).toHaveURL(/\/library/);
	const activeTile = page.locator('.tile', { hasText: 'Der lange Weg' }).first();
	await expect(activeTile.locator('.disc.spinning')).toBeVisible();
	const tileAnimations = await activeTile.evaluate((tile) => {
		const coverWrapper = tile.querySelector(':scope > .cover');
		const coverInner = tile.querySelector(':scope > .cover > .cover');
		const disc = tile.querySelector('.disc');
		return {
			coverWrapper: coverWrapper ? getComputedStyle(coverWrapper).animationName : null,
			coverInner: coverInner ? getComputedStyle(coverInner).animationName : null,
			disc: disc ? getComputedStyle(disc).animationName : null
		};
	});
	expect(tileAnimations.coverWrapper).toBe('none');
	expect(tileAnimations.coverInner).toBe('none');
	expect(tileAnimations.disc).not.toBe('none');

	await stopPlayer(page);
});

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
	await api(`/api/me/progress/${id}`, {
		method: 'PATCH',
		body: JSON.stringify({ isFinished: false, currentTime: 0, progress: 0 })
	});
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
