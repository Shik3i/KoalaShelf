import { expect, test } from '@playwright/test';
import { env, setActiveTokens, signIn } from './helpers.ts';

test.describe('sign-in', () => {
	test('preset server is prefilled and checked', async ({ page }) => {
		await page.goto('/signin');
		await expect(page.getByLabel('Audiobookshelf server')).toHaveValue(/localhost:13378/);
		await expect(page.getByText(/Audiobookshelf 2\.\d+\.\d+ found/)).toBeVisible();
	});

	test('wrong password shows an error and stays on sign-in', async ({ page }) => {
		await page.goto('/signin');
		await expect(page.getByText(/found/)).toBeVisible();
		await page.getByLabel('Username').fill(env.ABS_USER);
		await page.getByLabel('Password').fill('definitely-wrong');
		await page.getByRole('button', { name: 'Sign in' }).click();
		await expect(page.getByRole('alert')).toHaveText('Wrong username or password.');
		await expect(page).toHaveURL(/\/signin/);
	});

	test('a changed server address is checked again before signing in', async ({ page }) => {
		await page.goto('/signin');
		await expect(page.getByText(/Audiobookshelf .* found/)).toBeVisible();
		await page.getByLabel('Username').fill(env.ABS_USER);
		await page.getByLabel('Password').fill(env.ABS_PASSWORD);
		// New address and an immediate submit: the earlier check must not be reused.
		await page.getByLabel('Audiobookshelf server').fill('localhost:1');
		await page.getByRole('button', { name: 'Sign in' }).click();
		await expect(page.getByRole('alert')).toBeVisible();
		await expect(page).toHaveURL(/\/signin/);
	});

	test('unreachable server is reported while typing', async ({ page }) => {
		await page.goto('/signin');
		await page.getByLabel('Audiobookshelf server').fill('localhost:1');
		await expect(page.getByText(/Server not reachable|does not allow this app/)).toBeVisible();
	});

	test('a server without CORS gets a precise hint', async ({ page }) => {
		await page.goto('/signin');
		// Same server, but addressed directly (not through the proxy) from a foreign origin.
		await page.getByLabel('Audiobookshelf server').fill('127.0.0.1:13378');
		await expect(page.getByText(/Allowed origins/)).toBeVisible();
	});

	test('sign in, survive a reload, sign out', async ({ page }) => {
		await signIn(page);
		await expect(page.getByRole('heading', { level: 1 })).toContainText(env.ABS_USER);
		await page.reload();
		await expect(page.getByRole('heading', { level: 1 })).toContainText(env.ABS_USER);
		// Cookie mode behind the proxy: no refresh token in browser storage.
		const stored = await page.evaluate(() => JSON.parse(localStorage.getItem('ks.accounts')!));
		expect(stored.accounts).toHaveLength(1);
		expect(stored.accounts[0].tokens.refreshToken).toBeNull();
		await page.getByRole('button', { name: 'Sign out' }).click();
		await expect(page).toHaveURL(/\/signin/);
		// Signed out: the account and its tokens are gone from this device.
		const after = await page.evaluate(() => JSON.parse(localStorage.getItem('ks.accounts')!));
		expect(after.accounts).toHaveLength(0);
	});

	test('deep link while signed out returns there after sign-in', async ({ page }) => {
		await page.goto('/library?view=list');
		await expect(page).toHaveURL(/\/signin\?next=/);
		await expect(page.getByText(/found/)).toBeVisible();
		await page.getByLabel('Username').fill(env.ABS_USER);
		await page.getByLabel('Password').fill(env.ABS_PASSWORD);
		await page.getByRole('button', { name: 'Sign in' }).click();
		await expect(page).toHaveURL(/\/library/);
	});

	test('expired session sends back to sign-in with a message', async ({ page, context }) => {
		await signIn(page);
		// Break both tokens: the access token is invalid and the refresh cookie is gone.
		await context.clearCookies();
		await setActiveTokens(page, { accessToken: 'broken' });
		await page.goto('/library');
		await expect(page).toHaveURL(/\/signin/);
		await expect(page.getByText('Your session has expired. Please sign in again.')).toBeVisible();
	});

	test('an expired access token is refreshed silently', async ({ page }) => {
		await signIn(page);
		await setActiveTokens(page, { accessToken: 'broken' });
		await page.goto('/library');
		await expect(page.getByRole('heading', { name: 'Library' })).toBeVisible();
		await expect(page.locator('.tile')).toHaveCount(7);
	});
});
