<script lang="ts">
	import { AbsError, type AuthSession } from '@koalashelf/abs-client';
	import { session } from '#lib/session.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { toasts } from '#lib/toast.svelte.ts';
	import { describeAgent } from '#lib/device.ts';
	import Monitor from '@lucide/svelte/icons/monitor';
	import Smartphone from '@lucide/svelte/icons/smartphone';

	/** Signed-in devices (with remote sign-out) and the password change. Not for guests. */
	const t = $derived(i18n.m.security);
	let devices = $state.raw<AuthSession[] | null>(null);
	let guest = $state(false);

	function load() {
		session.api
			.authSessions()
			.then((d) => (devices = d))
			.catch(() => (devices = []));
	}

	$effect(() => {
		if (!session.client) return;
		session.api
			.me()
			.then((me) => {
				guest = me.type === 'guest';
				if (!guest) load();
			})
			.catch(() => load());
	});

	const ago = $derived((ms: number) => {
		const rtf = new Intl.RelativeTimeFormat(i18n.tag, { numeric: 'auto' });
		const s = (ms - Date.now()) / 1000;
		for (const [unit, size] of [
			['day', 86400],
			['hour', 3600],
			['minute', 60]
		] as const)
			if (Math.abs(s) >= size) return rtf.format(Math.round(s / size), unit);
		return rtf.format(0, 'minute');
	});

	const others = $derived((devices ?? []).filter((d) => !d.current));
	/** This device first, then the most recently used; long lists are folded. */
	const sorted = $derived(
		[...(devices ?? [])].sort((a, b) => +b.current - +a.current || b.updatedAt - a.updatedAt)
	);
	let showAll = $state(false);
	const shown = $derived(showAll ? sorted : sorted.slice(0, 5));

	async function signOut(d: AuthSession) {
		await session.api.signOutDevice(d.id);
		devices = (devices ?? []).filter((x) => x.id !== d.id);
	}

	async function signOutOthers() {
		for (const d of others) await session.api.signOutDevice(d.id);
		toasts.show(t.othersSignedOut);
		load();
	}

	let current = $state('');
	let next = $state('');
	let repeat = $state('');
	let pwError = $state('');
	let busy = $state(false);

	async function changePassword(e: SubmitEvent) {
		e.preventDefault();
		pwError = '';
		if (next !== repeat) {
			pwError = t.mismatch;
			return;
		}
		busy = true;
		try {
			await session.api.changePassword(current, next);
			current = next = repeat = '';
			toasts.show(t.changed);
		} catch (err) {
			pwError = err instanceof AbsError && err.status < 500 ? t.wrong : t.failed;
		} finally {
			busy = false;
		}
	}
</script>

{#if !guest}
	<div class="block">
		<div class="head">
			<span class="strong">{t.devices}</span>
			{#if others.length > 1}
				<button class="btn ghost sm" onclick={signOutOthers}>{t.signOutOthers}</button>
			{/if}
		</div>
		<small class="muted">{t.devicesHint}</small>
		{#if devices === null}
			<div class="skeleton" style="height: 120px; border-radius: 14px"></div>
		{:else}
			<ul class="devices">
				{#each shown as d (d.id)}
					{@const a = describeAgent(d.userAgent)}
					<li>
						<span class="ic" aria-hidden="true">
							{#if /Android|iOS/.test(a.system)}<Smartphone size={18} />{:else}<Monitor
									size={18}
								/>{/if}
						</span>
						<span class="what">
							<strong
								>{a.app || t.unknown}{a.system ? ` · ${a.system}` : ''}{#if d.current}<span
										class="badge">{t.thisDevice}</span
									>{/if}</strong
							>
							<span class="muted"
								>{t.lastUsed(ago(d.updatedAt))}{d.ipAddress
									? ` · ${d.ipAddress.replace(/^::ffff:/, '')}`
									: ''}</span
							>
						</span>
						{#if !d.current}
							<button class="btn ghost sm" onclick={() => signOut(d)}>{t.signOut}</button>
						{/if}
					</li>
				{/each}
			</ul>
			{#if sorted.length > shown.length}
				<button class="btn ghost sm more" onclick={() => (showAll = true)}
					>{t.showAll(sorted.length)}</button
				>
			{/if}
		{/if}
	</div>

	<form class="block" onsubmit={changePassword}>
		<span class="strong">{t.password}</span>
		<label class="field">
			<span class="label">{t.currentPassword}</span>
			<input type="password" bind:value={current} autocomplete="current-password" required />
		</label>
		<label class="field">
			<span class="label">{t.newPassword}</span>
			<input type="password" bind:value={next} autocomplete="new-password" required />
		</label>
		<label class="field">
			<span class="label">{t.repeatPassword}</span>
			<input type="password" bind:value={repeat} autocomplete="new-password" required />
		</label>
		{#if pwError}<p class="err" role="alert">{pwError}</p>{/if}
		<button class="btn ghost" type="submit" disabled={busy || !current || !next}
			>{t.changePassword}</button
		>
	</form>
{/if}

<style>
	.block {
		display: grid;
		gap: var(--sp-8);
	}

	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--sp-12);
		flex-wrap: wrap;
	}

	.strong {
		font-weight: var(--fw-semibold);
	}

	small {
		font-size: var(--fs-13);
	}

	.devices {
		list-style: none;
		margin: var(--sp-4) 0 0;
		padding: 0;
		display: grid;
		gap: var(--sp-4);
	}

	.devices li {
		display: flex;
		align-items: center;
		gap: var(--sp-12);
		padding: var(--sp-8) var(--sp-12);
		border-radius: var(--r-card);
		background: var(--c-bg);
	}

	.ic {
		display: grid;
		place-items: center;
		color: var(--c-muted);
	}

	.what {
		flex: 1;
		min-width: 0;
		display: grid;
	}

	.what strong {
		font-size: var(--fs-14);
		font-weight: var(--fw-semibold);
	}

	.what .muted {
		font-size: var(--fs-13);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.badge {
		margin-left: var(--sp-8);
		padding: 1px 8px;
		border-radius: var(--r-pill);
		background: var(--c-accent);
		color: var(--c-on-accent);
		font-size: var(--fs-12);
		font-weight: var(--fw-bold);
	}

	.sm {
		min-height: 36px;
		padding: 0 var(--sp-16);
		font-size: var(--fs-13);
	}

	.field input {
		max-width: 360px;
	}

	.err {
		margin: 0;
		color: var(--c-accent);
		font-weight: var(--fw-semibold);
	}

	.more,
	form .btn {
		justify-self: start;
	}
</style>
