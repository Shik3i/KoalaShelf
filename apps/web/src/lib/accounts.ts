// Switching between saved accounts (sidebar, "More" sheet, quick search, settings).
import { goto } from '$app/navigation';
import { serverLabel, session, type SavedAccount } from './session.svelte.ts';
import { toasts } from './toast.svelte.ts';
import { i18n } from './i18n/index.svelte.ts';

/** The sign-in form for adding an account, or prefilled for one that needs its password. */
export function addAccountUrl(account?: SavedAccount) {
	if (!account) return '/signin?add=1';
	const q = new URLSearchParams({
		add: '1',
		user: account.user.username,
		server: account.baseUrl || 'proxy'
	});
	return `/signin?${q}`;
}

/**
 * Saved accounts grouped by server, the active server first. Grouped by the address shown,
 * so a server reached through the bundled proxy and directly is one group.
 */
export function byServer(accounts: SavedAccount[], activeKey: string | null) {
	const groups = new Map<string, SavedAccount[]>();
	for (const a of accounts) {
		const label = serverLabel(a.baseUrl);
		groups.set(label, [...(groups.get(label) ?? []), a]);
	}
	const activeAccount = accounts.find((a) => a.key === activeKey);
	const active = activeAccount ? serverLabel(activeAccount.baseUrl) : null;
	return [...groups.entries()]
		.map(([label, list]) => ({
			baseUrl: list[0].baseUrl,
			label,
			accounts: list.sort((a, b) => a.user.username.localeCompare(b.user.username))
		}))
		.sort(
			(a, b) =>
				Number(b.label === active) - Number(a.label === active) || a.label.localeCompare(b.label)
		);
}

/** Makes an account the active one and starts fresh on its home page. */
export async function switchAccount(key: string) {
	const target = session.accounts.find((a) => a.key === key);
	if (!target || key === session.activeKey) return;
	if (!target.tokens || !(await session.switchTo(key))) return goto(addAccountUrl(target));
	await goto('/');
	toasts.show(i18n.m.accounts.switched(target.user.username, serverLabel(target.baseUrl)));
}
