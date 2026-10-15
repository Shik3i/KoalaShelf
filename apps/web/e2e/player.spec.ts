import { expect, test, type Page } from '@playwright/test';
import {
	api,
	itemId,
	player,
	playerCall,
	needsDevServer,
	resetListening,
	stopPlayer
} from './helpers.ts';

/** Known starting point for tests that toggle finished. */
async function unfinished(id: string) {
	await api(`/api/me/progress/${id}`, {
		method: 'PATCH',
		body: JSON.stringify({ isFinished: false, currentTime: 0, progress: 0 })
	});
}

async function openBook(page: Page, title: string) {
	await page.goto(`/book/${await itemId(title)}`);
	await expect(page.getByRole('heading', { level: 1, name: title })).toBeVisible();
}

async function startPlaying(page: Page, title: string) {
	await openBook(page, title);
	await page.locator('.cta .btn.primary').click();
	await expect.poll(() => playing(page)).toBe(true);
}

const seek = (page: Page, t: number) => playerCall(page, 'seek', t);
const time = (page: Page) => player<number>(page, 'currentTime');
const playing = (page: Page) => player<boolean>(page, 'playing');

test.describe('player', () => {
	test.beforeAll(resetListening);

	test.afterEach(async ({ page }) => {
		await stopPlayer(page);
	});

	test('plays, shows the mini player, pauses and syncs progress', async ({ page }) => {
		needsDevServer(); // reads player internals: skip before any playback starts
		await startPlaying(page, 'Der lange Weg');
		await expect(page.getByRole('region', { name: 'Player' })).toBeVisible();
		const t0 = await time(page);
		await page.waitForTimeout(2500);
		expect(await time(page)).toBeGreaterThan(t0 + 1);
		await page
			.getByRole('region', { name: 'Player' })
			.getByRole('button', { name: 'Pause' })
			.click();
		await expect.poll(() => playing(page)).toBe(false);
		const local = await time(page);
		const id = await itemId('Der lange Weg');
		await expect
			.poll(async () => (await api<{ currentTime: number }>(`/api/me/progress/${id}`)).currentTime)
			.toBeGreaterThan(local - 2);
	});

	test('skip, chapter navigation and speed', async ({ page }) => {
		needsDevServer(); // reads player internals: skip before any playback starts
		await startPlaying(page, 'Der lange Weg');
		await page.getByRole('button', { name: 'Open player' }).click();
		const dialog = page.getByRole('dialog', { name: 'Player' });
		await seek(page, 10);
		await dialog.getByRole('button', { name: 'Forward 30 seconds' }).click();
		await expect.poll(() => time(page)).toBeGreaterThanOrEqual(40);
		await dialog.getByRole('button', { name: 'Next chapter' }).click();
		await expect.poll(() => time(page).then(Math.floor)).toBeGreaterThanOrEqual(45);
		await expect(dialog.getByText('Chapter 2 of 6')).toBeVisible();
		await seek(page, 60);
		await dialog.getByRole('button', { name: 'Previous chapter' }).click();
		await expect.poll(() => time(page).then(Math.round)).toBe(45);
		await dialog.getByRole('button', { name: /^Speed:/ }).click();
		await dialog.getByRole('button', { name: '1.5×' }).click();
		expect(await player<number>(page, 'audio.playbackRate')).toBe(1.5);
		expect(await page.evaluate(() => localStorage.getItem('ks.rate'))).toBe('1.5');
		await dialog.getByRole('button', { name: '1.0×' }).click();
	});

	test('bookmarks: add with note, edit, show as pin, delete', async ({ page }) => {
		needsDevServer(); // reads player internals: skip before any playback starts
		await startPlaying(page, 'Salzwasser');
		await seek(page, 20);
		await page.getByRole('button', { name: 'Open player' }).click();
		await page.keyboard.press('b');
		const dialog = page.getByRole('dialog', { name: 'Player' });
		await dialog.getByPlaceholder('What happens here?').fill('Die Möwe');
		await dialog.getByRole('button', { name: /Add bookmark/ }).click();
		await expect(dialog.locator('.list li', { hasText: 'Die Möwe' })).toBeVisible();
		await expect(dialog.locator('.timeline .pin')).toHaveCount(1);
		await dialog.getByRole('button', { name: 'Edit note' }).click();
		await dialog.locator('form.edit input').fill('Die zweite Möwe');
		await dialog.getByRole('button', { name: 'Save' }).click();
		await expect(dialog.locator('.list li', { hasText: 'Die zweite Möwe' })).toBeVisible();
		const id = await itemId('Salzwasser');
		await expect
			.poll(async () =>
				(await api<{ bookmarks: { title: string }[] }>(`/api/me/bookmarks/${id}`)).bookmarks.map(
					(b) => b.title
				)
			)
			.toEqual(['Die zweite Möwe']);
		await dialog.getByRole('button', { name: 'Delete' }).click();
		await expect(dialog.locator('.timeline .pin')).toHaveCount(0);
	});

	test('sleep timer "end of chapter" pauses at the chapter boundary', async ({ page }) => {
		needsDevServer(); // reads player internals: skip before any playback starts
		await startPlaying(page, 'Wintergrün');
		await seek(page, 41);
		await page.getByRole('button', { name: 'Open player' }).click();
		const dialog = page.getByRole('dialog', { name: 'Player' });
		await dialog.getByRole('button', { name: 'Sleep timer' }).click();
		await dialog.getByRole('button', { name: 'End of chapter' }).click();
		await expect.poll(() => playing(page), { timeout: 15_000 }).toBe(false);
		const t = await time(page);
		expect(t).toBeGreaterThan(44);
		expect(t).toBeLessThan(46);
		expect(await player<string>(page, 'sleep.kind')).toBe('off');
	});

	test('multi-file book continues seamlessly into the next file', async ({ page }) => {
		needsDevServer(); // reads player internals: skip before any playback starts
		await startPlaying(page, 'Nachtzug');
		await seek(page, 43);
		await expect.poll(() => time(page), { timeout: 15_000 }).toBeGreaterThan(46.5);
		expect(await playing(page)).toBe(true);
		// Seeking backwards across the boundary loads the previous file again.
		await seek(page, 30);
		await expect.poll(() => time(page)).toBeLessThan(35);
	});

	test('end of book marks it finished; playing again starts over', async ({ page }) => {
		needsDevServer(); // reads player internals: skip before any playback starts
		await startPlaying(page, 'Ohne Kapitel');
		await seek(page, 57);
		await expect.poll(() => playing(page), { timeout: 15_000 }).toBe(false);
		const id = await itemId('Ohne Kapitel');
		await expect
			.poll(async () => (await api<{ isFinished: boolean }>(`/api/me/progress/${id}`)).isFinished)
			.toBe(true);
		await page.goto(`/book/${id}`);
		await expect(page.getByRole('button', { name: /Listen again/ })).toBeVisible();
		await expect(page.getByText('This title has no chapters.')).toBeVisible();
	});

	test('reload restores the mini player at the saved position', async ({ page }) => {
		needsDevServer(); // reads player internals: skip before any playback starts
		await startPlaying(page, 'Glasfluss');
		await seek(page, 70);
		await page
			.getByRole('region', { name: 'Player' })
			.getByRole('button', { name: 'Pause' })
			.click();
		await page.waitForTimeout(1000);
		await page.reload();
		const mini = page.getByRole('region', { name: 'Player' });
		await expect(mini).toContainText('Glasfluss');
		await expect.poll(() => time(page).then(Math.round)).toBeGreaterThanOrEqual(69);
		await page.goto(`/book/${await itemId('Glasfluss')}`);
		await expect(page.getByRole('button', { name: /Resume at 1:1\d/ })).toBeVisible();
	});

	test('keyboard: space toggles, arrows skip', async ({ page }) => {
		needsDevServer(); // reads player internals: skip before any playback starts
		await startPlaying(page, 'Kleine Eulen');
		await page.locator('body').click({ position: { x: 5, y: 5 } });
		await page.keyboard.press('Space');
		await expect.poll(() => playing(page)).toBe(false);
		const t = await time(page);
		await page.keyboard.press('ArrowRight');
		await expect.poll(() => time(page)).toBeGreaterThan(t + 25);
		await page.keyboard.press('Space');
		await expect.poll(() => playing(page)).toBe(true);
	});

	test('switching books quickly keeps only the last one', async ({ page }) => {
		needsDevServer();
		await openBook(page, 'Der lange Weg');
		const a = await itemId('Salzwasser');
		const b = await itemId('Wintergrün');
		// Two requests without waiting for the first one (double click on two books).
		await page.evaluate(
			async ([a, b]) => {
				const mod = '/src/lib/player/player.svelte.ts';
				const { player } = await import(/* @vite-ignore */ mod);
				void player.play(a);
				await player.play(b);
			},
			[a, b]
		);
		await expect.poll(() => player<string>(page, 'item.id')).toBe(b);
		await expect.poll(() => playing(page)).toBe(true);
	});

	test('mark finished / not finished from the book page', async ({ page }) => {
		await openBook(page, 'Kleine Eulen');
		await page.getByRole('button', { name: 'Mark as finished' }).click();
		await expect(page.getByRole('button', { name: 'Mark as not finished' })).toBeVisible();
		await page.goto('/library?status=finished');
		await expect(page.locator('.tile', { hasText: 'Kleine Eulen' })).toBeVisible();
		await openBook(page, 'Kleine Eulen');
		await page.getByRole('button', { name: 'Mark as not finished' }).click();
		await expect(page.getByRole('button', { name: 'Mark as finished' })).toBeVisible();
	});

	test('an older duplicate progress entry from the server does not win', async ({ page }) => {
		// Seen on real servers: a late sync after a reset leaves a second, older entry for the
		// same book, listed after the current one.
		const id = await itemId('Ohne Kapitel');
		await unfinished(id);
		await page.route('**/api/me', async (route) => {
			if (route.request().method() !== 'GET') return route.continue();
			const response = await route.fetch();
			const me = await response.json();
			const current = me.mediaProgress.find(
				(p: { libraryItemId: string }) => p.libraryItemId === id
			);
			if (current)
				me.mediaProgress.push({
					...current,
					id: 'stale-duplicate',
					isFinished: !current.isFinished,
					lastUpdate: current.lastUpdate - 60_000
				});
			await route.fulfill({ response, json: me });
		});
		await page.goto(`/book/${id}`);
		await page.getByRole('button', { name: 'Mark as finished' }).click();
		await expect(page.getByRole('button', { name: 'Mark as not finished' })).toBeVisible();
		await page.reload();
		await expect(page.getByRole('button', { name: 'Mark as not finished' })).toBeVisible();
		await page.getByRole('button', { name: 'Mark as not finished' }).click();
		await expect(page.getByRole('button', { name: 'Mark as finished' })).toBeVisible();
	});

	test('a slow first progress load does not undo a newer change', async ({ page }) => {
		// The page's first /api/me is answered by the server right away (old state) but arrives
		// late: after the change and the reload that follows it.
		let delayed = false;
		await page.route('**/api/me', async (route) => {
			if (delayed || route.request().method() !== 'GET') return route.continue();
			delayed = true;
			const response = await route.fetch();
			await new Promise((r) => setTimeout(r, 2000));
			await route.fulfill({ response });
		});
		await unfinished(await itemId('Ohne Kapitel'));
		await page.goto(`/book/${await itemId('Ohne Kapitel')}`);
		await page.getByRole('button', { name: 'Mark as finished' }).click();
		await expect(page.getByRole('button', { name: 'Mark as not finished' })).toBeVisible();
		await page.waitForTimeout(2500);
		await expect(page.getByRole('button', { name: 'Mark as not finished' })).toBeVisible();
		await page.getByRole('button', { name: 'Mark as not finished' }).click();
		await expect(page.getByRole('button', { name: 'Mark as finished' })).toBeVisible();
	});
});
