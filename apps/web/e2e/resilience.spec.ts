import { expect, test, type Route } from '@playwright/test';
import { itemId } from './helpers.ts';

// Real libraries are messier than the sample books: a book in two series shows up twice on a
// shelf, metadata lists a narrator or genre twice, chapter ids repeat. The server's answers are
// doubled up here; every page must still render (no "each_key_duplicate" crash).

/** Rewrites a JSON response from the server. */
async function rewrite(route: Route, change: (body: Record<string, unknown>) => unknown) {
	try {
		const res = await route.fetch();
		const body = await res.json();
		await route.fulfill({ response: res, json: change(body) });
	} catch {
		// The page moved on or the test ended meanwhile: nothing to rewrite.
		await route.continue().catch(() => undefined);
	}
}

test.describe('resilience against duplicate data', () => {
	test.afterEach(({ page }) => page.unrouteAll({ behavior: 'ignoreErrors' }));

	test('home: the same book twice on a shelf, a shelf twice', async ({ page }) => {
		const errors: string[] = [];
		page.on('pageerror', (e) => errors.push(e.message));
		await page.route('**/api/libraries/*/personalized*', (route) =>
			rewrite(route, (shelves) => {
				const list = shelves as unknown as { id: string; entities: unknown[] }[];
				for (const s of list) s.entities = [...s.entities, ...s.entities];
				return [...list, ...list];
			})
		);
		await page.goto('/');
		await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
		await expect(page.locator('.broken')).toHaveCount(0);
		await expect(page.getByText('Recently added').first()).toBeVisible();
		expect(errors).toEqual([]);
	});

	test('book: doubled authors, narrators, genres, tags, chapter ids and files', async ({
		page
	}) => {
		const errors: string[] = [];
		page.on('pageerror', (e) => errors.push(e.message));
		await page.route('**/api/items/*?*', (route) =>
			rewrite(route, (item) => {
				const media = item.media as {
					metadata: Record<string, unknown[]>;
					chapters: { id: number }[];
					audioFiles: unknown[];
					tags: string[];
				};
				const m = media.metadata;
				for (const k of ['authors', 'narrators', 'genres']) m[k] = [...m[k], ...m[k]];
				media.tags = [...(media.tags ?? []), 'Doppelt', 'Doppelt'];
				media.chapters = media.chapters.map((c) => ({ ...c, id: 0 }));
				media.audioFiles = [...media.audioFiles, ...media.audioFiles];
				return item;
			})
		);
		await page.goto(`/book/${await itemId('Der lange Weg')}`);
		await expect(page.getByRole('heading', { name: 'Der lange Weg' })).toBeVisible();
		for (const tab of ['Chapters', 'Files']) await page.getByRole('tab', { name: tab }).click();
		await expect(page.locator('.broken')).toHaveCount(0);
		expect(errors).toEqual([]);
	});

	test('library: pages that overlap while loading more', async ({ page }) => {
		const errors: string[] = [];
		page.on('pageerror', (e) => errors.push(e.message));
		// Every page also returns the first book again (as when books are added meanwhile).
		let first: unknown = null;
		await page.route('**/api/libraries/*/items?*', (route) =>
			rewrite(route, (body) => {
				const results = body.results as unknown[];
				first ??= results[0];
				body.results = [...results, first];
				return body;
			})
		);
		await page.goto('/library');
		await expect(page.locator('.tile').first()).toBeVisible();
		await expect(page.locator('.broken')).toHaveCount(0);
		expect(errors).toEqual([]);
	});

	test('a page that fails anyway shows a way back, navigation stays', async ({ page }) => {
		await page.route('**/api/me/listening-stats*', (route) =>
			rewrite(route, () => ({ totalTime: 'x', days: null, items: null, dayOfWeek: null }))
		);
		await page.goto('/stats');
		const broken = page
			.getByRole('alert')
			.filter({ hasText: 'This page could not be shown' })
			.first();
		const nav = page.getByRole('navigation', { name: 'Main navigation' });
		// Either the page copes with the bad data, or the safety net shows; the app never goes blank.
		await expect(broken.or(page.getByRole('heading', { name: 'Statistics' }))).toBeVisible();
		await expect(page.getByText('500')).toHaveCount(0);
		await expect(nav).toBeVisible();
	});
});
