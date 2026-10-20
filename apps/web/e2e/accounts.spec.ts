import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { env, itemId, scopedKey, settle } from './helpers.ts';

// Several accounts and servers with quick switching, plus the own accent color and fonts.
const dir = process.env.SCREENS_DIR;

const accounts = (page: Page) =>
	page.evaluate(() => JSON.parse(localStorage.getItem('ks.accounts') ?? '{"accounts":[]}'));

async function addGuest(page: Page) {
	await page.goto('/');
	await page.getByRole('button', { name: /, switch accounts$/ }).click();
	const switcher = page.getByRole('dialog', { name: 'Accounts' });
	await switcher.getByRole('button', { name: 'Add account' }).click();
	await expect(page.getByRole('heading', { name: 'Add an account' })).toBeVisible();
	await expect(page.getByText(/Audiobookshelf .* found/)).toBeVisible();
	await page.getByLabel('Username').fill(env.ABS_GUEST_USER);
	await page.getByLabel('Password').fill(env.ABS_GUEST_PASSWORD);
	await page.getByRole('button', { name: 'Add account' }).click();
	await expect(page.getByRole('heading', { level: 1 })).toContainText(env.ABS_GUEST_USER);
}

test.describe('accounts', () => {
	test('add a second account, switch back and forth, each with its own queue', async ({ page }) => {
		await page.goto(`/book/${await itemId('Glasfluss')}`);
		await page.evaluate((k) => localStorage.removeItem(k), await scopedKey(page, 'ks.queue'));
		await page.reload();
		await page.getByRole('button', { name: 'Add to queue' }).click();

		await addGuest(page);
		const stored = await accounts(page);
		expect(stored.accounts).toHaveLength(2);
		// The first account keeps its refresh token in the httpOnly cookie; the added one,
		// on the same proxy address, keeps its own (the cookie can only belong to one).
		const [guest, owner] = [env.ABS_GUEST_USER, env.ABS_USER].map((n) =>
			stored.accounts.find((a: { user: { username: string } }) => a.user.username === n)
		);
		expect(owner.tokens.refreshToken).toBeNull();
		expect(typeof guest.tokens.refreshToken).toBe('string');
		// The guest has its own, empty queue.
		await page.goto(`/book/${await itemId('Glasfluss')}`);
		await expect(page.getByRole('button', { name: 'Add to queue' })).toBeVisible();

		// Switch back from the sidebar: the owner's queue is still there.
		await page.getByRole('button', { name: /, switch accounts$/ }).click();
		const switcher = page.getByRole('dialog', { name: 'Accounts' });
		await expect(switcher.getByRole('region')).toHaveCount(1); // one server
		if (dir) await page.screenshot({ path: `${dir}/accounts-switcher.png` });
		await switcher.getByRole('button', { name: new RegExp(`^.?${env.ABS_USER}`) }).click();
		await expect(
			page.getByRole('status').getByText(`Now listening as ${env.ABS_USER}`)
		).toBeVisible();
		await expect(page.getByRole('heading', { level: 1 })).toContainText(env.ABS_USER);
		await page.goto(`/book/${await itemId('Glasfluss')}`);
		await expect(page.getByRole('button', { name: 'In queue' })).toBeVisible();

		// Quick search switches too.
		await page.keyboard.press('Control+k');
		await page
			.locator('dialog[open]')
			.getByRole('combobox')
			.fill(`switch to ${env.ABS_GUEST_USER}`);
		await page.keyboard.press('Enter');
		await expect(page.getByRole('heading', { level: 1 })).toContainText(env.ABS_GUEST_USER);

		// Sign the guest out from the settings: it is gone, the owner remains saved.
		await page.goto('/settings#account');
		await page
			.getByRole('button', { name: new RegExp(`^Sign out ${env.ABS_GUEST_USER} on`) })
			.click();
		if (dir) await page.screenshot({ path: `${dir}/accounts-settings.png` });
		await page.getByRole('alert').getByText(`Sign out ${env.ABS_GUEST_USER}?`).waitFor();
		await page
			.locator('li')
			.filter({ hasText: 'Sign out ' + env.ABS_GUEST_USER + '?' })
			.getByRole('button', { name: 'Sign out', exact: true })
			.click();
		await expect(page).toHaveURL(/\/signin/);
		// The remaining account is offered with one tap.
		await page.getByRole('button', { name: new RegExp(env.ABS_USER) }).click();
		await expect(page.getByRole('heading', { level: 1 })).toContainText(env.ABS_USER);
		expect((await accounts(page)).accounts).toHaveLength(1);
	});

	test('an account whose session ended asks for its password, prefilled', async ({ page }) => {
		await addGuest(page);
		// Break the guest's tokens, then switch to the owner and back to the guest.
		await page.evaluate((guest) => {
			const s = JSON.parse(localStorage.getItem('ks.accounts')!);
			for (const a of s.accounts)
				if (a.user.username === guest) a.tokens = { accessToken: 'x', refreshToken: 'y' };
			localStorage.setItem('ks.accounts', JSON.stringify(s));
		}, env.ABS_GUEST_USER);
		await page.goto('/');
		await expect(page).toHaveURL(/\/signin/);
		await expect(page.getByText('Your session has expired. Please sign in again.')).toBeVisible();
		const row = page.getByRole('button', { name: new RegExp(env.ABS_GUEST_USER) });
		await expect(row).toContainText('Sign in again');
		await row.click();
		await expect(page.getByLabel('Username')).toHaveValue(env.ABS_GUEST_USER);
		await expect(page.getByLabel('Password')).toBeFocused();
		await page.getByLabel('Password').fill(env.ABS_GUEST_PASSWORD);
		await page.getByRole('button', { name: 'Sign in', exact: true }).click();
		await expect(page.getByRole('heading', { level: 1 })).toContainText(env.ABS_GUEST_USER);
		// Clean up: back to the owner, guest signed out.
		await page.goto('/settings#account');
		const owner = page
			.getByRole('region', { name: /^Accounts on/ })
			.getByRole('listitem')
			.filter({ has: page.getByText(env.ABS_USER, { exact: true }) });
		await owner.getByRole('button', { name: 'Switch' }).click();
		await expect(page.getByRole('heading', { level: 1 })).toContainText(env.ABS_USER);
		await page.goto('/settings#account');
		await page
			.getByRole('button', { name: new RegExp(`^Sign out ${env.ABS_GUEST_USER} on`) })
			.click();
		await page
			.locator('li')
			.filter({ hasText: 'Sign out ' + env.ABS_GUEST_USER + '?' })
			.getByRole('button', { name: 'Sign out', exact: true })
			.click();
		await expect(page.getByText(`${env.ABS_GUEST_USER} signed out`)).toBeVisible();
	});
});

test.describe('appearance', () => {
	test.afterEach(async ({ page }) => {
		await page.evaluate(() => {
			localStorage.removeItem('ks.prefs');
			localStorage.removeItem('ks.accentCustom');
		});
	});

	test('own accent color, kept readable, applied before the first paint', async ({ page }) => {
		await page.goto('/settings');
		await page.getByRole('radio', { name: 'Your own' }).check();
		// A bright yellow is far too light on light surfaces: it is darkened, same hue.
		await page.getByLabel('Pick your color').fill('#ffe600');
		await expect(page.getByText('Made a little lighter or darker where needed')).toBeVisible();
		await expect(page.locator('html')).toHaveAttribute('data-accent', 'custom');
		const accent = () =>
			page.evaluate(() =>
				getComputedStyle(document.documentElement).getPropertyValue('--c-accent').trim()
			);
		const light = await accent();
		expect(light).not.toBe('#FFE600');
		if (dir) await page.screenshot({ path: `${dir}/settings-accent.png` });
		// After a reload the color is there from the start (static/theme.js).
		await page.reload();
		expect(await page.evaluate(() => !!document.getElementById('ks-custom-accent'))).toBe(true);
		expect(await accent()).toBe(light);
	});

	test('easy-to-read and system fonts', async ({ page }) => {
		await page.goto('/settings');
		await page.locator('input[name="font"][value="readable"]').click();
		await expect(page.locator('html')).toHaveAttribute('data-font', 'readable');
		const family = () => page.evaluate(() => getComputedStyle(document.body).fontFamily);
		expect(await family()).toContain('Atkinson Hyperlegible Next');
		await page.waitForFunction(() =>
			document.fonts.check('16px "Atkinson Hyperlegible Next Variable"')
		);
		if (dir) await page.screenshot({ path: `${dir}/settings-font-readable.png` });
		await page.locator('input[name="font"][value="system"]').click();
		expect(await family()).toContain('system-ui');
		await page.reload();
		await expect(page.locator('html')).toHaveAttribute('data-font', 'system');
	});
});

test.describe('accounts and appearance pass axe', () => {
	for (const scheme of ['light', 'dark'] as const)
		test(`switcher, saved accounts, own color (${scheme})`, async ({ page }) => {
			await page.emulateMedia({ colorScheme: scheme });
			const scan = async (label: string) => {
				await page.waitForTimeout(400);
				await settle(page);
				const r = await new AxeBuilder({ page })
					.withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa', 'best-practice'])
					.analyze();
				expect(
					r.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`),
					label
				).toEqual([]);
			};
			await page.goto('/');
			await page.getByRole('button', { name: /, switch accounts$/ }).click();
			await expect(page.getByRole('dialog', { name: 'Accounts' })).toBeVisible();
			await scan('switcher');
			await page.keyboard.press('Escape');
			await page.goto('/settings');
			await page.getByRole('radio', { name: 'Your own' }).check();
			await page.getByLabel('Pick your color').fill('#ffe600');
			await scan('settings with own color');
			await page.goto('/library');
			await scan('library in own color');
			await page.evaluate(() => {
				localStorage.removeItem('ks.prefs');
				localStorage.removeItem('ks.accentCustom');
			});
		});
});
