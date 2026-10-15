import { expect, test, type Locator, type Page } from '@playwright/test';
import { itemId, needsDevServer, player, playerCall, stopPlayer } from './helpers.ts';

// Player extras: fine speed, sound (volume, boost, mono, voice, silence), jump undo,
// go to time, sleep timer extension and night start, pop-out window.
const dir = process.env.SCREENS_DIR;

async function openPlayer(page: Page, title = 'Der lange Weg'): Promise<Locator> {
	await page.goto(`/book/${await itemId(title)}`);
	await page.locator('.cta .btn.primary').click();
	await expect.poll(() => player<boolean>(page, 'playing')).toBe(true);
	await page.getByRole('button', { name: 'Open player' }).click();
	const dialog = page.getByRole('dialog', { name: 'Player' });
	await expect(dialog).toBeVisible();
	return dialog;
}

const audio = (page: Page, prop: 'volume' | 'playbackRate') =>
	page.evaluate(async (prop) => {
		const mod = '/src/lib/player/player.svelte.ts';
		const { player } = await import(/* @vite-ignore */ mod);
		return player.audio[prop] as number;
	}, prop);

test.describe('player extras', () => {
	test.beforeEach(async ({ page }) => {
		needsDevServer();
		await page.goto('/');
		await page.evaluate(() => localStorage.removeItem('ks.prefs'));
	});
	test.afterEach(async ({ page }) => {
		await stopPlayer(page);
	});

	test('speed: fine steps, slider, quick choices, keys', async ({ page }) => {
		const dialog = await openPlayer(page);
		await playerCall(page, 'setRate', 1);
		await dialog.getByRole('button', { name: 'Speed: 1.0×' }).click();
		await dialog.getByRole('button', { name: 'Faster' }).click();
		await expect(dialog.locator('output').filter({ hasText: '1.05×' })).toBeVisible();
		expect(await audio(page, 'playbackRate')).toBeCloseTo(1.05);
		await dialog.getByRole('button', { name: '1.75×' }).click();
		expect(await player<number>(page, 'rate')).toBe(1.75);
		await dialog.getByRole('slider', { name: 'Speed' }).fill('2.6');
		expect(await player<number>(page, 'rate')).toBe(2.6);
		if (dir) await page.screenshot({ path: `${dir}/player-speed.png` });
		// Keys: [ and ] change by 0.1, within 0.5–3.5.
		await page.keyboard.press('Escape');
		await page.locator('body').click({ position: { x: 5, y: 5 } });
		await playerCall(page, 'setRate', 3.45);
		await page.keyboard.press(']');
		expect(await player<number>(page, 'rate')).toBe(3.5);
		await page.keyboard.press('[');
		expect(await player<number>(page, 'rate')).toBe(3.4);
		await playerCall(page, 'setRate', 1);
	});

	test('sound: volume, boost, voice, mono, skip silence', async ({ page }) => {
		const dialog = await openPlayer(page);
		await dialog.getByRole('button', { name: 'Sound' }).click();
		await dialog.getByRole('slider', { name: 'Volume' }).fill('0.4');
		expect(await audio(page, 'volume')).toBeCloseTo(0.4);
		await dialog.getByRole('button', { name: 'Mute' }).click();
		expect(await audio(page, 'volume')).toBe(0);
		await dialog.getByRole('button', { name: 'Unmute' }).click();
		expect(await audio(page, 'volume')).toBe(1);

		// Boost routes the audio through Web Audio with extra gain.
		await dialog.getByRole('group', { name: 'Volume boost' }).getByText('+100 %').click();
		expect(await player<boolean>(page, 'leveler.attached')).toBe(true);
		const gain = () =>
			page.evaluate(async () => {
				const mod = '/src/lib/player/player.svelte.ts';
				const { player } = await import(/* @vite-ignore */ mod);
				return player.leveler.output.gain.value as number;
			});
		expect(await gain()).toBe(2);
		await dialog.getByRole('switch', { name: 'Clearer voices' }).click();
		await dialog.getByRole('switch', { name: 'Mono' }).click();
		await expect(dialog.getByRole('switch', { name: 'Mono' })).toHaveAttribute(
			'aria-checked',
			'true'
		);
		// Still audible through the whole chain.
		await expect.poll(() => player<boolean>(page, 'playing')).toBe(true);

		// Skip silence: silent stretches play faster, and the saved time is counted.
		await dialog.getByRole('switch', { name: 'Skip silence' }).click();
		await page.evaluate(async () => {
			const mod = '/src/lib/player/player.svelte.ts';
			const { player } = await import(/* @vite-ignore */ mod);
			player.leveler.onSilence(true);
		});
		expect(await audio(page, 'playbackRate')).toBe(2.5);
		await page.waitForTimeout(1200);
		await page.evaluate(async () => {
			const mod = '/src/lib/player/player.svelte.ts';
			const { player } = await import(/* @vite-ignore */ mod);
			player.leveler.onSilence(false);
		});
		expect(await audio(page, 'playbackRate')).toBe(1);
		expect(await player<number>(page, 'silenceSavedBook')).toBeGreaterThan(1);
		await expect(dialog.getByText(/^Saved .* in this book/)).toBeVisible();
		if (dir) await page.screenshot({ path: `${dir}/player-sound.png` });

		// The settings page offers the same controls.
		await page.keyboard.press('Escape');
		await page.goto('/settings');
		await expect(page.getByRole('switch', { name: 'Skip silence' })).toHaveAttribute(
			'aria-checked',
			'true'
		);
	});

	test('silence detector reports pauses and sound', async ({ page }) => {
		await page.goto('/');
		const events = await page.evaluate(async () => {
			const ctx = new AudioContext();
			await ctx.audioWorklet.addModule('/worklets/silence.js');
			const node = new AudioWorkletNode(ctx, 'ks-silence');
			const seen: boolean[] = [];
			node.port.onmessage = (e) => seen.push(e.data);
			const osc = ctx.createOscillator();
			const gate = ctx.createGain();
			osc.connect(gate).connect(node).connect(ctx.destination);
			gate.gain.setValueAtTime(1, ctx.currentTime);
			gate.gain.setValueAtTime(0, ctx.currentTime + 0.3);
			gate.gain.setValueAtTime(1, ctx.currentTime + 1.2);
			osc.start();
			await new Promise((r) => setTimeout(r, 1600));
			await ctx.close();
			return seen;
		});
		expect(events).toEqual([true, false]);
	});

	test('jump back after a long jump; go to a typed time', async ({ page }) => {
		const dialog = await openPlayer(page);
		await playerCall(page, 'seek', 20);
		await dialog.getByRole('button', { name: 'Chapters' }).click();
		await dialog.getByRole('button', { name: /^4 Kapitel 4/ }).click();
		await expect.poll(() => player<number>(page, 'currentTime')).toBeGreaterThanOrEqual(135);
		const toast = page.getByRole('status').filter({ hasText: 'Jumped from 0:20' });
		await expect(toast).toBeVisible();
		if (dir) await page.screenshot({ path: `${dir}/player-chapters-jump.png` });
		await toast.getByRole('button', { name: 'Back' }).click();
		await expect.poll(() => player<number>(page, 'currentTime')).toBeLessThan(30);
		// "U" undoes again (back to chapter 4).
		await page.locator('body').press('u');
		await expect.poll(() => player<number>(page, 'currentTime')).toBeGreaterThanOrEqual(135);

		await dialog.getByRole('textbox', { name: 'Go to time (h:mm:ss)' }).fill('9:99:00');
		await dialog.getByRole('button', { name: 'Go', exact: true }).click();
		await expect(dialog.getByText('Enter a time within the book')).toBeVisible();
		await dialog.getByRole('textbox', { name: 'Go to time (h:mm:ss)' }).fill('1:00');
		await dialog.getByRole('button', { name: 'Go', exact: true }).click();
		await expect.poll(() => player<number>(page, 'currentTime')).toBeGreaterThanOrEqual(60);
		expect(await player<number>(page, 'currentTime')).toBeLessThan(75);
		await expect(dialog.getByText(/^ends \d/)).toBeVisible();
	});

	test('sleep timer: more time, warning before it stops, night start', async ({ page }) => {
		const dialog = await openPlayer(page);
		await dialog.getByRole('button', { name: 'Sleep timer' }).click();
		await dialog.getByRole('button', { name: '15 min' }).click();
		await dialog.getByRole('button', { name: '+5 min' }).click();
		expect(await player<number>(page, 'sleep.minutes')).toBe(20);
		// A chapter-end timer becomes a countdown past this chapter's end.
		await dialog.getByRole('button', { name: 'End of chapter' }).click();
		await dialog.getByRole('button', { name: '+15 min' }).click();
		expect(await player<string>(page, 'sleep.kind')).toBe('minutes');
		// Shortly before the end: a hint with "+10 min".
		await playerCall(page, 'setSleep', { kind: 'minutes', minutes: 1, endsAt: Date.now() + 6000 });
		const hint = page.getByRole('status').filter({ hasText: 'Stopping in a few seconds' });
		await expect(hint).toBeVisible();
		await hint.getByRole('button', { name: '+10 min' }).click();
		await page.waitForTimeout(6000);
		expect(await player<boolean>(page, 'playing')).toBe(true);
		await playerCall(page, 'setSleep', { kind: 'off' });

		// Night window around now: playback starting without a timer gets one by itself.
		await page.keyboard.press('Escape');
		await playerCall(page, 'pause');
		await page.evaluate(() => {
			const now = new Date();
			const m = now.getHours() * 60 + now.getMinutes();
			localStorage.setItem(
				'ks.prefs',
				JSON.stringify({
					autoSleep: true,
					autoSleepFrom: (m + 1430) % 1440,
					autoSleepTo: (m + 10) % 1440,
					autoSleepKind: 'minutes',
					autoSleepMinutes: 45
				})
			);
		});
		await page.reload();
		await page.getByRole('button', { name: 'Play', exact: true }).click();
		await expect(
			page.getByRole('status').getByText('Night time: sleep timer started')
		).toBeVisible();
		expect(await player<number>(page, 'sleep.minutes')).toBe(45);
		await playerCall(page, 'setSleep', { kind: 'off' });

		await page.goto('/settings');
		await expect(
			page.getByRole('switch', { name: 'Start automatically at night' })
		).toHaveAttribute('aria-checked', 'true');
		await expect(page.getByLabel('From')).toBeVisible();
	});
});
