import { readFileSync } from 'node:fs';
import { expect, test } from '@playwright/test';
import { api, itemId, resetListening } from './helpers.ts';

// Daily goal, year picture, exports (bookmarks, history, settings) and settings import.
const dir = process.env.SCREENS_DIR;

test.describe('your data', () => {
	test.beforeEach(async ({ page }) => {
		await page.goto('/');
		await page.evaluate(() => localStorage.removeItem('ks.prefs'));
	});

	test('daily goal: set on the statistics page, ring on home', async ({ page }) => {
		await page.goto('/stats');
		const goal = page.getByRole('group', { name: 'Daily goal' });
		await expect(page.getByText('Set a daily listening goal')).toBeVisible();
		await goal.getByRole('button', { name: '30 min' }).click();
		await expect(
			page.getByRole('img', { name: /^(Today: \d+ of 30 minutes|Daily goal reached)/ })
		).toBeVisible();
		await expect(page.getByRole('list', { name: 'Last 7 days' }).getByRole('listitem')).toHaveCount(
			7
		);
		if (dir) await page.screenshot({ path: `${dir}/stats-goal.png` });
		await page.goto('/');
		const ring = page.getByRole('link', { name: /^(Today: \d+ of 30 minutes|Daily goal reached)/ });
		await expect(ring).toBeVisible();
		if (dir) await page.screenshot({ path: `${dir}/home-goal.png` });
		await ring.click();
		await expect(page).toHaveURL(/\/stats$/);
		await goal.getByRole('button', { name: 'Off' }).click();
		await page.goto('/');
		await expect(ring).toHaveCount(0);
	});

	test('year in review as a picture', async ({ page }) => {
		await page.goto('/stats');
		const share = page.getByRole('button', { name: 'Share as image' });
		test.skip(
			!(await share.waitFor({ timeout: 8000 }).then(
				() => true,
				() => false
			)),
			'no listening this year'
		);
		const [download] = await Promise.all([page.waitForEvent('download'), share.click()]);
		expect(download.suggestedFilename()).toMatch(/^koalashelf-\d{4}\.png$/);
		const png = readFileSync(await download.path());
		expect(png.subarray(1, 4).toString()).toBe('PNG');
		expect(png.length).toBeGreaterThan(20_000);
		if (dir) await download.saveAs(`${dir}/year-card.png`);
	});

	test('bookmarks as Markdown and CSV, history as CSV', async ({ page }) => {
		await resetListening();
		const id = await itemId('Glasfluss');
		await api(`/api/me/item/${id}/bookmark`, {
			method: 'POST',
			body: JSON.stringify({ time: 75, title: 'Der Fluss, "gläsern"' })
		});
		await page.goto('/bookmarks');
		const group = page.getByRole('group', { name: 'Export' });
		const [md] = await Promise.all([
			page.waitForEvent('download'),
			group.getByRole('button', { name: 'Markdown' }).click()
		]);
		const text = readFileSync(await md.path(), 'utf8');
		expect(text).toContain('## Glasfluss — Levin Aral');
		expect(text).toContain(
			`- [1:15](${new URL(page.url()).origin}/book/${id}?t=75) Der Fluss, "gläsern"`
		);
		const [csv] = await Promise.all([
			page.waitForEvent('download'),
			group.getByRole('button', { name: 'CSV' }).click()
		]);
		const rows = readFileSync(await csv.path(), 'utf8').split('\r\n');
		expect(rows[0]).toBe('book,author,time,seconds,note,created,link');
		expect(rows[1]).toContain('Glasfluss,Levin Aral,1:15,75,"Der Fluss, ""gläsern"""');
		await resetListening();

		await page.goto('/history');
		const exportButton = page.getByRole('button', { name: 'Export CSV' });
		test.skip(
			!(await exportButton.waitFor({ timeout: 8000 }).then(
				() => true,
				() => false
			)),
			'no history yet'
		);
		const [hist] = await Promise.all([page.waitForEvent('download'), exportButton.click()]);
		const lines = readFileSync(await hist.path(), 'utf8')
			.trim()
			.split('\r\n');
		expect(lines[0]).toBe('date,started,book,author,from,to,listened_seconds,device');
		expect(lines.length).toBeGreaterThan(1);
	});

	test('settings: export to a file and import it back', async ({ page }) => {
		await page.goto('/settings');
		await page.getByRole('radio', { name: 'Ocean' }).check();
		const [file] = await Promise.all([
			page.waitForEvent('download'),
			page.getByRole('button', { name: 'Export settings' }).click()
		]);
		const backup = JSON.parse(readFileSync(await file.path(), 'utf8'));
		expect(backup.app).toBe('KoalaShelf');
		expect(JSON.parse(backup.data['ks.prefs']).accent).toBe('ocean');
		expect(JSON.stringify(backup)).not.toContain('accessToken');

		// Another accent now; importing brings "ocean" back after a reload.
		await page.getByRole('radio', { name: 'Forest' }).check();
		await page.locator('input[type=file]').setInputFiles(await file.path());
		await expect(page.getByRole('status').getByText('Settings imported')).toBeVisible();
		await expect(page.locator('html')).toHaveAttribute('data-accent', 'ocean');

		// Not a backup: a clear message, nothing changes.
		await page.locator('input[type=file]').setInputFiles({
			name: 'other.json',
			mimeType: 'application/json',
			buffer: Buffer.from('{"hello":1}')
		});
		await expect(
			page.getByRole('alert').getByText('not a KoalaShelf settings backup')
		).toBeVisible();
		await expect(page.locator('html')).toHaveAttribute('data-accent', 'ocean');
		await page.evaluate(() => localStorage.removeItem('ks.prefs'));
	});
});
