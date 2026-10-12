<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { AbsError } from '@koalashelf/abs-client';
	import { detectSameOrigin, ServerTooOldError, session } from '#lib/session.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import LocaleSwitch from '#lib/components/LocaleSwitch.svelte';
	import Vinyl from '#lib/components/Vinyl.svelte';

	const LAST_SERVER = 'ks.lastServer';
	const t = $derived(i18n.m.signin);

	let server = $state(storageGet(LAST_SERVER) ?? '');
	let username = $state('');
	let password = $state('');
	let busy = $state(false);
	let error = $state<unknown>(null);
	/** null = still checking. Behind a proxy (same origin) no server URL is needed. */
	let sameOrigin = $state<boolean | null>(null);

	function storageGet(key: string) {
		try {
			return localStorage.getItem(key);
		} catch {
			return null;
		}
	}

	detectSameOrigin().then((v) => (sameOrigin = v));

	$effect(() => {
		if (session.ready && session.client)
			goto(page.url.searchParams.get('next') ?? '/', { replace: true });
	});

	// Translated at render time, so switching the language also translates a shown error.
	const message = $derived.by(() => {
		const e = error;
		if (!e) return null;
		if (e instanceof ServerTooOldError) return t.errTooOld(e.version, e.min);
		if (e instanceof AbsError) {
			if (e.status === 401) return t.errWrongCredentials;
			if (e.status === 429) return t.errRateLimited;
			if (e.status === 0) return t.errNotAbs;
			return t.errServer(e.message);
		}
		if (e instanceof TypeError) return t.errUnreachable;
		return e instanceof Error ? e.message : t.errUnknown;
	});

	async function submit(e: SubmitEvent) {
		e.preventDefault();
		busy = true;
		error = null;
		try {
			let url = sameOrigin ? '' : server.trim();
			if (url && !/^https?:\/\//.test(url)) url = `https://${url}`;
			await session.signIn(url, username.trim(), password);
			if (url) localStorage.setItem(LAST_SERVER, url);
			password = '';
		} catch (err) {
			error = err;
		} finally {
			busy = false;
		}
	}
</script>

<svelte:head><title>{t.title} · KoalaShelf</title></svelte:head>

<div class="wrap">
	<section class="card">
		<div class="hero" aria-hidden="true">
			<Vinyl title="KoalaShelf" author="Audiobookshelf" playing={busy} label={0.42} />
		</div>
		<h1>KoalaShelf</h1>
		<p class="muted">{t.intro}</p>

		<form onsubmit={submit}>
			{#if sameOrigin === false}
				<label class="field">
					<span class="label">{t.server}</span>
					<input
						bind:value={server}
						type="url"
						inputmode="url"
						autocomplete="url"
						placeholder={t.serverPlaceholder}
						required
					/>
				</label>
			{/if}
			<label class="field">
				<span class="label">{t.username}</span>
				<input bind:value={username} autocomplete="username" autocapitalize="none" required />
			</label>
			<label class="field">
				<span class="label">{t.password}</span>
				<input bind:value={password} type="password" autocomplete="current-password" />
			</label>

			{#if message}<p class="error" role="alert">{message}</p>{/if}

			<button class="btn primary" type="submit" disabled={busy || sameOrigin === null}>
				{busy ? t.connecting : t.submit}
			</button>
		</form>
		<p class="note muted">{t.privacy}</p>
		<div class="lang"><LocaleSwitch /></div>
	</section>
</div>

<style>
	.wrap {
		min-height: 100dvh;
		display: grid;
		place-items: center;
		padding: var(--sp-64) var(--sp-16) var(--sp-24);
		background: var(--c-outer);
	}

	.card {
		width: min(400px, 100%);
		padding: var(--sp-32);
		border-radius: var(--r-card-lg);
		background: var(--c-bg);
		display: grid;
		gap: var(--sp-12);
		box-shadow: 0 30px 60px -30px rgb(0 0 0 / 0.35);
	}

	.hero {
		width: 132px;
		margin: calc(-1 * var(--sp-64) - var(--sp-32)) auto var(--sp-8);
	}

	h1 {
		text-align: center;
		font-size: var(--fs-34);
	}

	.card > p {
		text-align: center;
	}

	form {
		display: grid;
		gap: var(--sp-16);
		margin-top: var(--sp-12);
	}

	.btn {
		margin-top: var(--sp-8);
	}

	.error {
		color: var(--c-accent);
		font-weight: var(--fw-semibold);
		font-size: var(--fs-14);
	}

	.note {
		font-size: var(--fs-12);
		margin-top: var(--sp-8);
	}

	.lang {
		display: flex;
		justify-content: center;
	}
</style>
