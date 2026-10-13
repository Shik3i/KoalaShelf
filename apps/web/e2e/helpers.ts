import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { expect, type Page } from '@playwright/test';

const envFile = fileURLToPath(new URL('../../../dev/.env', import.meta.url));
export const env = Object.fromEntries(
	readFileSync(envFile, 'utf8')
		.trim()
		.split('\n')
		.map((l) => l.split('=') as [string, string])
);

export const ABS = env.ABS_URL;

export async function signIn(page: Page, user = env.ABS_USER, password = env.ABS_PASSWORD) {
	await page.goto('/signin');
	await expect(page.getByText(/Audiobookshelf .* found/)).toBeVisible();
	await page.getByLabel('Username').fill(user);
	await page.getByLabel('Password').fill(password);
	await page.getByRole('button', { name: 'Sign in' }).click();
	await expect(page).toHaveURL(/\/$/);
}

let token: Promise<string> | null = null;

/** Direct API access for assertions (bypasses the UI); signs in once per run. */
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
	token ??= fetch(`${ABS}/login`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json', 'x-return-tokens': 'true' },
		body: JSON.stringify({ username: env.ABS_USER, password: env.ABS_PASSWORD })
	})
		.then((r) => r.json())
		.then((r) => r.user.accessToken as string);
	const res = await fetch(`${ABS}${path}`, {
		...init,
		headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${await token}`, ...init.headers }
	});
	return (res.headers.get('content-type')?.includes('json') ? res.json() : res.text()) as Promise<T>;
}

export async function itemId(title: string) {
	const { libraries } = await api<{ libraries: { id: string }[] }>('/api/libraries');
	const { results } = await api<{ results: { id: string; media: { metadata: { title: string } } }[] }>(
		`/api/libraries/${libraries[0].id}/items?limit=0&minified=1`
	);
	return results.find((r) => r.media.metadata.title === title)!.id;
}

/** Reads a (nested) property of the app's player instance, e.g. "sleep.kind". */
export async function player<T>(page: Page, path: string): Promise<T> {
	return page.evaluate(async (path) => {
		const mod = '/src/lib/player/player.svelte.ts';
		const { player } = await import(/* @vite-ignore */ mod);
		return path.split('.').reduce((o: Record<string, unknown> | undefined, k) => o?.[k] as Record<string, unknown>, player) as unknown;
	}, path) as Promise<T>;
}

/** Calls a player method with JSON-serialisable arguments (no eval: the app's CSP forbids it). */
export async function playerCall(page: Page, method: string, ...args: unknown[]) {
	await page.evaluate(
		async ([method, args]) => {
			const mod = '/src/lib/player/player.svelte.ts';
			const { player } = await import(/* @vite-ignore */ mod);
			await player[method as string](...(args as unknown[]));
		},
		[method, args] as const
	);
}
