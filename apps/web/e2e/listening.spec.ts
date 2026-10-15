import { expect, test, type Page } from '@playwright/test';
import { api, env, itemId, needsDevServer, player, playerCall, stopPlayer } from './helpers.ts';

const playing = (page: Page) => player<boolean>(page, 'playing');
const time = (page: Page) => player<number>(page, 'currentTime');

async function start(page: Page, title: string) {
	await page.goto(`/book/${await itemId(title)}`);
	await page.locator('.cta .btn.primary').click();
	await expect.poll(() => playing(page)).toBe(true);
}

/** Loudest sample at the leveler's output over ~1 s (0 = silence, e.g. audio without CORS). */
async function peak(page: Page) {
	needsDevServer();
	return page.evaluate(async () => {
		const mod = '/src/lib/player/player.svelte.ts';
		const { player } = await import(/* @vite-ignore */ mod);
		const { context, output } = player.leveler;
		if (!context || !output) return -1;
		const analyser: AnalyserNode = context.createAnalyser();
		output.connect(analyser);
		const buf = new Float32Array(analyser.fftSize);
		let max = 0;
		for (let i = 0; i < 20; i++) {
			await new Promise((r) => setTimeout(r, 50));
			analyser.getFloatTimeDomainData(buf);
			for (const v of buf) max = Math.max(max, Math.abs(v));
		}
		output.disconnect(analyser);
		return max;
	});
}

async function unfinish(title: string) {
	await api(`/api/me/progress/${await itemId(title)}`, {
		method: 'PATCH',
		body: JSON.stringify({ isFinished: false, currentTime: 0, progress: 0 })
	});
}

test.describe('listening', () => {
	test.afterEach(async ({ page }) => {
		await stopPlayer(page);
	});

	test('queue: add from book pages, reorder, undo, continue at the end of a book', async ({
		page
	}) => {
		needsDevServer(); // reads player internals: skip before any playback starts
		await page.goto('/');
		await page.evaluate(() => localStorage.removeItem('ks.queue'));
		for (const title of ['Kleine Eulen', 'Wintergrün']) {
			await page.goto(`/book/${await itemId(title)}`);
			await page.getByRole('button', { name: 'Add to queue' }).click();
			await expect(page.getByRole('button', { name: 'In queue' })).toBeVisible();
			await expect(page.getByRole('status').getByText(`Added to queue: ${title}`)).toBeVisible();
		}

		await start(page, 'Glasfluss');
		await page.getByRole('button', { name: 'Open player' }).click();
		const dialog = page.getByRole('dialog', { name: 'Player' });
		await dialog.getByRole('button', { name: 'Up next (2)' }).click();
		const rows = dialog.locator('.queue li');
		await expect(rows).toHaveText([/Kleine Eulen/, /Wintergrün/]);

		await rows.nth(1).getByRole('button', { name: 'Move up' }).click();
		await expect(rows).toHaveText([/Wintergrün/, /Kleine Eulen/]);
		await expect(rows.first().getByRole('button', { name: 'Move up' })).toBeDisabled();

		await rows.nth(1).getByRole('button', { name: 'Remove from queue' }).click();
		await expect(rows).toHaveCount(1);
		await dialog.getByRole('status').getByRole('button', { name: 'Undo' }).click();
		await expect(rows).toHaveText([/Wintergrün/, /Kleine Eulen/]);

		// The book ends: the first queued one starts and leaves the queue.
		await playerCall(page, 'seek', (await player<number>(page, 'duration')) - 1.5);
		await expect
			.poll(() => player<string>(page, 'item.id'), { timeout: 15_000 })
			.toBe(await itemId('Wintergrün'));
		await expect(dialog.getByRole('status').getByText('Now playing: Wintergrün')).toBeVisible();
		await expect(rows).toHaveText([/Kleine Eulen/]);
		await expect(dialog.getByRole('button', { name: 'Up next (1)' })).toBeVisible();
		await unfinish('Glasfluss');

		// Playing a queued book directly also takes it off the queue.
		await rows.first().getByRole('button', { name: 'Play Kleine Eulen now' }).click();
		await expect.poll(() => player<string>(page, 'item.id')).toBe(await itemId('Kleine Eulen'));
		await expect(dialog.getByText(/Nothing queued/)).toBeVisible();
	});

	test('smart sleep timer: window, forecast and the stop at a chapter end', async ({ page }) => {
		needsDevServer(); // reads player internals: skip before any playback starts
		await start(page, 'Wintergrün');
		await page.getByRole('button', { name: 'Open player' }).click();
		const dialog = page.getByRole('dialog', { name: 'Player' });
		await dialog.getByRole('button', { name: 'Sleep timer' }).click();
		await dialog.getByRole('button', { name: 'Smart' }).click();
		await expect(
			dialog.getByText(/No chapter ends in this window|Stops at the end of/)
		).toBeVisible();

		// The earliest time pushes the latest one along (always at least 5 minutes apart).
		const outputs = dialog.locator('.stepper output');
		await expect(outputs).toHaveText(['20 min', '40 min']);
		for (let i = 0; i < 4; i++)
			await dialog.getByRole('button', { name: 'Earliest: 5 minutes later' }).click();
		await expect(outputs).toHaveText(['40 min', '45 min']);
		await dialog.getByRole('button', { name: 'Latest: 5 minutes earlier' }).click();
		await expect(outputs).toHaveText(['35 min', '40 min']);

		await dialog.getByRole('button', { name: 'Start timer' }).click();
		expect(await player<string>(page, 'sleep.kind')).toBe('window');
		expect(await player<number>(page, 'sleep.min')).toBe(35);
		await expect(dialog.getByRole('status').filter({ hasText: 'Timer running' })).toBeVisible();
		await expect(dialog.getByRole('button', { name: /Stops in ≈/ })).toBeVisible();
		await page.keyboard.press('Escape');
		await expect(page.locator('.mini .sleep')).toHaveText(/^≈\d+:\d\d$/);

		// Short window for the real stop: the end of chapter 1 (0:45) lies inside it.
		await playerCall(page, 'seek', 41);
		const now = Date.now();
		await playerCall(page, 'setSleep', {
			kind: 'window',
			min: 0,
			max: 1,
			minAt: now + 1000,
			maxAt: now + 60_000
		});
		await expect.poll(() => playing(page), { timeout: 15_000 }).toBe(false);
		const t = await time(page);
		expect(t).toBeGreaterThan(44);
		expect(t).toBeLessThan(46);
		expect(await player<string>(page, 'sleep.kind')).toBe('off');
	});

	test('smart sleep timer without a chapter end in the window stops at the latest time', async ({
		page
	}) => {
		needsDevServer(); // reads player internals: skip before any playback starts
		await start(page, 'Wintergrün');
		await playerCall(page, 'seek', 41);
		const now = Date.now();
		// Chapter 1 ends in 4 s (too early), chapter 2 only after the window closes.
		await playerCall(page, 'setSleep', {
			kind: 'window',
			min: 0,
			max: 1,
			minAt: now + 8_000,
			maxAt: now + 14_000
		});
		await expect.poll(() => playing(page), { timeout: 25_000 }).toBe(false);
		// Stopped around 0:55 and rewound 15 s, like the exact timer.
		const t = await time(page);
		expect(t).toBeGreaterThan(36);
		expect(t).toBeLessThan(44);
	});

	test('volume leveling keeps the audio flowing, on and off and across files', async ({ page }) => {
		needsDevServer(); // reads player internals: skip before any playback starts
		await page.goto('/settings');
		const toggle = page.getByRole('switch', { name: 'Even out volume' });
		await toggle.click();
		await expect(toggle).toHaveAttribute('aria-checked', 'true');
		await start(page, 'Nachtzug');
		expect(await player<boolean>(page, 'leveler.attached')).toBe(true);
		expect(await peak(page)).toBeGreaterThan(0.01);

		await playerCall(page, 'setLeveling', false);
		expect(await peak(page)).toBeGreaterThan(0.01);
		await playerCall(page, 'setLeveling', true);
		// Into the second file of the book: the new source plays through the leveler, too.
		await playerCall(page, 'seek', 45);
		await expect.poll(() => time(page), { timeout: 15_000 }).toBeGreaterThan(47);
		expect(await peak(page)).toBeGreaterThan(0.01);
	});

	test('history: sessions by day with jump points; on the book page too', async ({ page }) => {
		needsDevServer(); // reads player internals: skip before any playback starts
		await start(page, 'Salzwasser');
		await playerCall(page, 'seek', 30);
		await page.waitForTimeout(6000);
		await playerCall(page, 'stop');

		await page.goto('/history');
		const today = page.locator('section.day').first();
		await expect(today.getByRole('heading')).toContainText('Today');
		const row = today.locator('li', { hasText: 'Salzwasser' }).first();
		await expect(row).toContainText('Edge');
		await row.getByRole('button', { name: /^Session end: Play from 0:3\d$/ }).click();
		await expect.poll(() => player<string>(page, 'item.id')).toBe(await itemId('Salzwasser'));
		await expect.poll(() => playing(page)).toBe(true);
		expect(await time(page)).toBeGreaterThanOrEqual(30);
		await playerCall(page, 'stop');

		await page.goto(`/book/${await itemId('Salzwasser')}`);
		await page.getByRole('tab', { name: 'History' }).click();
		await expect(page.locator('.sessions li').first()).toContainText('listened');

		// The server names the device from the browser, not from a user agent dump.
		const { sessions } = await api<{ sessions: { deviceInfo: { deviceName: string } }[] }>(
			'/api/me/listening-sessions?itemsPerPage=1'
		);
		expect(sessions[0].deviceInfo.deviceName).toMatch(/^Windows .* Edge$/);
	});

	test('phones reach every page through "More"', async ({ page }) => {
		await page.setViewportSize({ width: 390, height: 844 });
		await page.goto('/');
		await page.getByRole('button', { name: 'More', exact: true }).click();
		const sheet = page.getByRole('dialog', { name: 'More' });
		await expect(sheet.getByRole('link')).toHaveText([
			'Series',
			'Authors',
			'Collections',
			'Playlists',
			'Bookmarks',
			'History',
			'Statistics',
			'Settings'
		]);
		await expect(sheet.getByRole('combobox', { name: 'Library' })).toBeVisible();
		await sheet.getByRole('link', { name: 'History' }).click();
		await expect(page).toHaveURL(/\/history$/);
		await expect(sheet).toBeHidden();
		await expect(page.getByRole('button', { name: 'More', exact: true })).toHaveClass(/current/);
		// Escape closes the sheet as well.
		await page.getByRole('button', { name: 'More', exact: true }).click();
		await expect(sheet).toBeVisible();
		await page.keyboard.press('Escape');
		await expect(sheet).toBeHidden();
	});
});

test.describe('server on another origin', () => {
	test.use({ storageState: { cookies: [], origins: [] } });

	test('volume leveling works when the server allows this app (CORS)', async ({ page }) => {
		needsDevServer();
		const abs = 'http://127.0.0.1:13378';
		// The dev server does not list this origin; answer the way Audiobookshelf does when it is
		// in "allowed origins" (same headers as its CORS middleware).
		await page.route(`${abs}/**`, async (route) => {
			const req = route.request();
			const cors = {
				'access-control-allow-origin': req.headers()['origin'] ?? new URL(page.url()).origin,
				'access-control-allow-methods': 'GET, POST, PATCH, PUT, DELETE, OPTIONS',
				'access-control-allow-headers': '*',
				'access-control-allow-credentials': 'true'
			};
			if (req.method() === 'OPTIONS') return route.fulfill({ status: 200, headers: cors });
			const res = await route.fetch();
			await route.fulfill({ response: res, headers: { ...res.headers(), ...cors } });
		});
		await page.addInitScript(() =>
			localStorage.setItem('ks.prefs', JSON.stringify({ leveling: true }))
		);
		await page.goto('/signin');
		await page.getByLabel('Audiobookshelf server').fill('127.0.0.1:13378');
		await expect(page.getByText(/Audiobookshelf .* found/)).toBeVisible();
		await page.getByLabel('Username').fill(env.ABS_USER);
		await page.getByLabel('Password').fill(env.ABS_PASSWORD);
		await page.getByRole('button', { name: 'Sign in' }).click();
		await expect(page).toHaveURL(/\/$/);

		await start(page, 'Der lange Weg');
		expect(await player<string>(page, 'audio.currentSrc')).toContain(abs);
		expect(await player<string>(page, 'audio.crossOrigin')).toBe('anonymous');
		expect(await peak(page)).toBeGreaterThan(0.01);
	});
});
