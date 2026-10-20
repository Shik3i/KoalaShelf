<script lang="ts">
	import { goto } from '$app/navigation';
	import { session } from '#lib/session.svelte.ts';
	import { addAccountUrl, byServer, switchAccount } from '#lib/accounts.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import Avatar from './Avatar.svelte';
	import Check from '@lucide/svelte/icons/check';
	import UserPlus from '@lucide/svelte/icons/user-plus';
	import Server from '@lucide/svelte/icons/server';
	import KeyRound from '@lucide/svelte/icons/key-round';
	import ChartColumn from '@lucide/svelte/icons/chart-column';

	/** Saved accounts on every server, one tap to switch. */
	let { open = $bindable(false) }: { open?: boolean } = $props();

	const t = $derived(i18n.m.accounts);
	let dialog = $state<HTMLDialogElement>();
	const groups = $derived(byServer(session.accounts, session.activeKey));

	$effect(() => {
		if (!dialog) return;
		if (open && !dialog.open) dialog.showModal();
		if (!open && dialog.open) dialog.close();
	});

	async function pick(key: string) {
		open = false;
		await switchAccount(key);
	}
</script>

<dialog
	bind:this={dialog}
	onclose={() => (open = false)}
	onclick={(e) => e.target === dialog && (open = false)}
	aria-labelledby="acc-title"
>
	<h2 id="acc-title">{t.title}</h2>
	{#each groups as g (g.label)}
		<section aria-label={t.onServer(g.label)}>
			<h3 class="label"><Server size={14} aria-hidden="true" />{g.label}</h3>
			<ul>
				{#each g.accounts as a (a.key)}
					{@const current = a.key === session.activeKey}
					<li>
						<button
							class="account"
							aria-current={current ? 'true' : undefined}
							disabled={session.switching}
							onclick={() => (current ? (open = false) : pick(a.key))}
						>
							<Avatar name={a.user.username} />
							<span class="who">
								<strong>{a.user.username}</strong>
								<span class="muted">
									{current ? t.active : a.tokens ? t.switchTo : t.needsPassword}
								</span>
							</span>
							{#if current}<Check size={18} aria-hidden="true" />{:else if !a.tokens}<KeyRound
									size={16}
									aria-hidden="true"
								/>{/if}
						</button>
					</li>
				{/each}
			</ul>
		</section>
	{/each}
	<div class="actions">
		<button
			class="btn primary"
			onclick={() => {
				open = false;
				void goto(addAccountUrl());
			}}
		>
			<UserPlus size={16} aria-hidden="true" />
			{t.add}
		</button>
		<a class="btn ghost" href="/stats" onclick={() => (open = false)}>
			<ChartColumn size={16} aria-hidden="true" />
			{i18n.m.nav.stats}
		</a>
		<a class="btn ghost" href="/settings#account" onclick={() => (open = false)}>{t.manage}</a>
	</div>
	<p class="muted note">{t.privacy}</p>
</dialog>

<style>
	dialog {
		width: min(92vw, 420px);
		max-height: min(86dvh, 640px);
		padding: var(--sp-24);
		border: 0;
		border-radius: var(--r-card-lg);
		background: var(--c-card);
		color: var(--c-ink);
		box-shadow: 0 30px 60px -20px rgb(0 0 0 / 0.5);
	}

	dialog[open] {
		display: grid;
		gap: var(--sp-16);
		animation: pop var(--motion-base) var(--motion-spring);
	}

	dialog::backdrop {
		background: rgb(0 0 0 / 0.35);
		backdrop-filter: blur(2px);
	}

	@keyframes pop {
		from {
			scale: 0.96;
			opacity: 0;
		}
	}

	h2 {
		font-size: var(--fs-24);
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
		gap: var(--sp-4);
	}

	.account {
		width: 100%;
		display: grid;
		grid-template-columns: auto 1fr auto;
		align-items: center;
		gap: var(--sp-12);
		min-height: 56px;
		padding: var(--sp-8) var(--sp-12);
		border: 0;
		border-radius: var(--r-card);
		background: var(--c-bg);
		color: inherit;
		font: inherit;
		text-align: left;
		cursor: pointer;
	}

	.account:hover:not(:disabled) {
		background: color-mix(in oklab, var(--c-accent) 12%, var(--c-bg));
	}

	.account[aria-current='true'] {
		box-shadow: inset 0 0 0 2px var(--c-accent);
	}

	.who {
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

	.actions {
		display: flex;
		flex-wrap: wrap;
		gap: var(--sp-8);
	}

	.note {
		margin: 0;
		font-size: var(--fs-12);
	}
</style>
