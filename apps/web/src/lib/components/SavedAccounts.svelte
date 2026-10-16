<script lang="ts">
	import { goto } from '$app/navigation';
	import { session } from '#lib/session.svelte.ts';
	import { addAccountUrl, byServer, switchAccount } from '#lib/accounts.ts';
	import { toasts } from '#lib/toast.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Avatar from './Avatar.svelte';
	import UserPlus from '@lucide/svelte/icons/user-plus';
	import Server from '@lucide/svelte/icons/server';

	/** Settings > Account: every saved account, to switch, sign in again or sign out. */
	const t = $derived(i18n.m.accounts);
	const groups = $derived(byServer(session.accounts, session.activeKey));
	/** Signing out asks once more, inline. */
	let confirming = $state<string | null>(null);

	async function signOut(key: string) {
		const a = session.accounts.find((x) => x.key === key);
		confirming = null;
		await session.forget(key);
		if (a) toasts.show(t.signedOut(a.user.username));
	}
</script>

<div class="saved">
	<div class="head">
		<span class="stack">
			<span class="strong">{t.title}</span>
			<small class="muted">{t.privacy}</small>
		</span>
		<button class="btn ghost" onclick={() => goto(addAccountUrl())}>
			<UserPlus size={16} aria-hidden="true" />
			{t.add}
		</button>
	</div>
	{#each groups as g (g.label)}
		<section aria-label={t.onServer(g.label)}>
			<h3 class="label"><Server size={14} aria-hidden="true" />{g.label}</h3>
			<ul>
				{#each g.accounts as a (a.key)}
					{@const current = a.key === session.activeKey}
					<li>
						<Avatar name={a.user.username} />
						<span class="who">
							<strong>{a.user.username}</strong>
							<span class="muted">
								{current ? t.active : a.tokens ? t.signedIn : t.needsPassword}
							</span>
						</span>
						<span class="acts">
							{#if confirming === a.key}
								<span class="confirm" role="alert">{t.signOutConfirm(a.user.username)}</span>
								<button class="btn danger sm" onclick={() => signOut(a.key)}>{t.signOut}</button>
								<button class="btn ghost sm" onclick={() => (confirming = null)}>{t.cancel}</button>
							{:else}
								{#if !current}
									<button
										class="btn ghost sm"
										disabled={session.switching}
										onclick={() => (a.tokens ? switchAccount(a.key) : goto(addAccountUrl(a)))}
										>{a.tokens ? t.switch : t.signInAgain}</button
									>
								{/if}
								<button
									class="btn ghost sm"
									onclick={() => (confirming = a.key)}
									aria-label={t.signOutOf(a.user.username, g.label)}>{t.signOut}</button
								>
							{/if}
						</span>
					</li>
				{/each}
			</ul>
		</section>
	{/each}
</div>

<style>
	.saved {
		display: grid;
		gap: var(--sp-16);
	}

	.head {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		justify-content: space-between;
		gap: var(--sp-12);
	}

	.stack {
		display: grid;
		gap: var(--sp-4);
		max-width: 52ch;
	}

	.stack small {
		font-size: var(--fs-13);
	}

	.strong {
		font-weight: var(--fw-semibold);
	}

	h3 {
		display: flex;
		align-items: center;
		gap: var(--sp-8);
		margin-bottom: var(--sp-8);
	}

	ul {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--sp-8);
	}

	li {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--sp-12);
		padding: var(--sp-8) var(--sp-12);
		border-radius: var(--r-card);
		background: var(--c-bg);
	}

	.who {
		flex: 1 1 140px;
		display: grid;
		min-width: 0;
	}

	.who > * {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.who .muted {
		font-size: var(--fs-13);
	}

	.acts {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--sp-8);
	}

	.confirm {
		flex-basis: 100%;
		font-weight: var(--fw-semibold);
		font-size: var(--fs-13);
	}

	.btn.sm {
		min-height: 40px;
		font-size: var(--fs-13);
	}

	.btn.danger {
		background: var(--c-accent);
		color: var(--c-on-accent);
	}
</style>
