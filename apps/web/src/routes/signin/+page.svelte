<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { AbsError, isVersionAtLeast, MIN_SERVER_VERSION } from '@koalashelf/abs-client';
	import { presetServer, ServerTooOldError, session } from '#lib/session.svelte.ts';
	import { byServer, switchAccount } from '#lib/accounts.ts';
	import Avatar from '#lib/components/Avatar.svelte';
	import KeyRound from '@lucide/svelte/icons/key-round';
	import {
		forgetServer,
		normalizeServerInput,
		ProbeError,
		probeProxy,
		probeServer,
		sameServer,
		recentServers,
		rememberServer
	} from '#lib/server-probe.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import LocaleSwitch from '#lib/components/LocaleSwitch.svelte';

	const t = $derived(i18n.m.signin);

	/** ?add=1: another account while signed in (the current one stays signed in). */
	const adding = $derived(page.url.searchParams.get('add') === '1');
	const wantServer = page.url.searchParams.get('server');
	const wantUser = page.url.searchParams.get('user');
	/** Saved accounts to continue with (not while adding one). */
	const saved = $derived(adding ? [] : byServer(session.accounts, null));
	let passwordInput = $state<HTMLInputElement>();

	let recent = $state(recentServers());
	let server = $state(
		wantServer && wantServer !== 'proxy' ? wantServer : (recentServers()[0] ?? '')
	);
	let username = $state(wantUser ?? '');
	let password = $state('');
	let busy = $state(false);
	let error = $state<unknown>(null);
	/** Preconfigured server (Docker/dev proxy); undefined while loading. */
	let preset = $state<string | null | undefined>(undefined);
	/** Signing in to the preconfigured server goes through the proxy (no CORS, httpOnly cookie). */
	const viaProxy = $derived(!!preset && sameServer(server, preset));

	type Check =
		| { state: 'idle' | 'checking' }
		| { state: 'ok'; url: string; version: string; input: string }
		| { state: 'error'; error: unknown };
	let check = $state<Check>({ state: 'idle' });

	presetServer().then((p) => {
		preset = p;
		if (p && (!server || wantServer === 'proxy')) server = p;
		if (wantUser) queueMicrotask(() => passwordInput?.focus());
	});

	/** Set once this form signed in (adding an account starts while already signed in). */
	let signedIn = $state(false);
	$effect(() => {
		if (session.ready && session.client && (!adding || signedIn)) {
			const rawNext = page.url.searchParams.get('next') ?? '/';
			const safeNext = rawNext.startsWith('/') && !rawNext.startsWith('//') ? rawNext : '/';
			goto(safeNext, { replace: true });
		}
	});

	/** A saved account: open it, or ask for its password when its session ended. */
	async function resume(key: string) {
		const a = session.accounts.find((x) => x.key === key);
		if (!a) return;
		if (a.tokens) return switchAccount(key);
		username = a.user.username;
		server = a.baseUrl || preset || location.origin;
		passwordInput?.focus();
	}

	let probeTimer: ReturnType<typeof setTimeout> | undefined;
	let probeCtrl: AbortController | undefined;

	function runProbe(target: string) {
		const input = target.trim();
		if (preset === undefined || input.length < 3) return;
		probeCtrl?.abort();
		clearTimeout(probeTimer);
		const ctrl = new AbortController();
		probeCtrl = ctrl;
		check = { state: 'checking' };
		const proxy = viaProxy;
		(proxy ? probeProxy(ctrl.signal) : probeServer(input, ctrl.signal))
			.then((r) => {
				if (!ctrl.signal.aborted) check = { state: 'ok', ...r, input };
			})
			.catch((e) => {
				if (!ctrl.signal.aborted) check = { state: 'error', error: e };
			});
	}

	// Live server check while typing (debounced 2s), or immediately on Enter / blur.
	$effect(() => {
		const input = server.trim();
		check = { state: 'idle' };
		clearTimeout(probeTimer);
		if (preset === undefined || input.length < 3) return;
		probeTimer = setTimeout(() => runProbe(input), 2000);
		return () => {
			clearTimeout(probeTimer);
			probeCtrl?.abort();
		};
	});

	function describe(e: unknown) {
		if (e instanceof ServerTooOldError) return t.errTooOld(e.version, e.min);
		if (e instanceof ProbeError) {
			if (e.kind === 'insecure-http') return t.errInsecureHttp;
			if (e.kind === 'cors') return t.errCors(location.origin);
			if (e.kind === 'not-abs') return t.errNotAbs;
			if (e.kind === 'local-disabled') return t.errLocalDisabled;
			return t.errUnreachable;
		}
		if (e instanceof AbsError) {
			if (e.status === 401) return t.errWrongCredentials;
			if (e.status === 429) return t.errRateLimited;
			if (e.status === 0) return t.errNotAbs;
			return t.errServer(e.message);
		}
		if (e instanceof TypeError) return t.errUnreachable;
		return e instanceof Error ? e.message : t.errUnknown;
	}

	// Translated at render time, so switching the language also translates a shown error.
	const message = $derived(error ? describe(error) : null);
	const checkMessage = $derived(
		check.state === 'error'
			? describe(check.error)
			: check.state === 'ok' && !isVersionAtLeast(check.version, MIN_SERVER_VERSION)
				? t.errTooOld(check.version, MIN_SERVER_VERSION)
				: null
	);
	const shortUrl = (u: string) => u.replace(/^https?:\/\//, '');

	async function submit(e: SubmitEvent) {
		e.preventDefault();
		busy = true;
		error = null;
		try {
			const trimmed = server.trim();
			const normalized = normalizeServerInput(trimmed);
			if (server !== normalized) server = normalized;
			const url = viaProxy
				? ''
				: check.state === 'ok' && check.input === server.trim()
					? check.url
					: (await probeServer(server.trim())).url;
			await session.signIn(url, username.trim(), password);
			recent = rememberServer(viaProxy && preset ? preset : url);
			password = '';
			signedIn = true;
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
			<img
				src="/mascot.png"
				alt=""
				class="mascot"
				class:busy={busy || check.state === 'checking'}
			/>
		</div>
		<h1>{adding ? t.addTitle : 'KoalaShelf'}</h1>
		<p class="muted">{adding ? t.addIntro : t.intro}</p>
		{#if session.expired && !adding}<p class="error" role="status">{t.expired}</p>{/if}

		{#if saved.length}
			<section class="saved" aria-labelledby="saved-title">
				<h2 class="label" id="saved-title">{t.savedAccounts}</h2>
				<ul>
					{#each saved as g (g.label)}
						{#each g.accounts as a (a.key)}
							<li>
								<button type="button" class="acc" onclick={() => resume(a.key)}>
									<Avatar name={a.user.username} />
									<span class="who">
										<strong>{a.user.username}</strong>
										<span class="muted">{g.label}</span>
									</span>
									{#if !a.tokens}<span class="again"
											><KeyRound size={14} aria-hidden="true" />{t.signInAgain}</span
										>{/if}
								</button>
							</li>
						{/each}
					{/each}
				</ul>
				<p class="muted or">{t.orAnother}</p>
			</section>
		{/if}

		<form onsubmit={submit}>
			<label class="field">
				<span class="label">{t.server}</span>
				<input
					bind:value={server}
					type="text"
					inputmode="url"
					autocomplete="url"
					autocapitalize="none"
					spellcheck="false"
					placeholder={t.serverPlaceholder}
					onblur={() => {
						server = normalizeServerInput(server);
						runProbe(server);
					}}
					onkeydown={(e) => {
						if (e.key === 'Enter') {
							server = normalizeServerInput(server);
							runProbe(server);
						}
					}}
					required
				/>
			</label>
			<p class="status" aria-live="polite">
				{#if check.state === 'checking'}
					<span class="muted">{t.serverChecking}</span>
				{:else if checkMessage}
					<span class="warn">{checkMessage}</span>
				{:else if check.state === 'ok'}
					<span class="ok">✓ {t.serverOk(check.version)}</span>
				{/if}
			</p>
			{#if recent.some((r) => shortUrl(r) !== shortUrl(server.trim()))}
				<div class="recent" role="group" aria-label={t.serverRecent}>
					{#each recent as r (r)}
						<span class="chip" class:on={shortUrl(r) === shortUrl(server.trim())}>
							<button type="button" class="pick" onclick={() => (server = r)}>{shortUrl(r)}</button>
							<button
								type="button"
								class="x"
								aria-label={t.serverForget}
								onclick={() => (recent = forgetServer(r))}>✕</button
							>
						</span>
					{/each}
				</div>
			{/if}

			<label class="field">
				<span class="label">{t.username}</span>
				<input bind:value={username} autocomplete="username" autocapitalize="none" required />
			</label>
			<label class="field">
				<span class="label">{t.password}</span>
				<input
					bind:this={passwordInput}
					bind:value={password}
					type="password"
					autocomplete="current-password"
				/>
			</label>

			{#if message}<p class="error" role="alert">{message}</p>{/if}

			<button class="btn primary" type="submit" disabled={busy || preset === undefined}>
				{busy ? t.connecting : adding ? t.addSubmit : t.submit}
			</button>
			{#if adding && session.client}
				<button type="button" class="btn ghost" onclick={() => history.back()}>{t.cancel}</button>
			{/if}
		</form>
		<p class="note muted">{t.privacy}</p>
		<div class="lang"><LocaleSwitch /></div>
	</section>
</div>

<style>
	.saved {
		display: grid;
		gap: var(--sp-8);
		margin-bottom: var(--sp-8);
	}

	.saved ul {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--sp-4);
	}

	.acc {
		width: 100%;
		display: grid;
		grid-template-columns: auto 1fr auto;
		align-items: center;
		gap: var(--sp-12);
		min-height: 56px;
		padding: var(--sp-8) var(--sp-12);
		border: 1.5px solid var(--c-field);
		border-radius: var(--r-card);
		background: var(--c-bg);
		color: inherit;
		font: inherit;
		text-align: left;
		cursor: pointer;
	}

	.acc:hover {
		border-color: var(--c-accent);
	}

	.acc .who {
		display: grid;
		min-width: 0;
	}

	.acc .who > * {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.acc .muted,
	.again {
		font-size: var(--fs-13);
	}

	.again {
		display: inline-flex;
		align-items: center;
		gap: 4px;
		font-weight: var(--fw-semibold);
	}

	.or {
		margin: var(--sp-8) 0 0;
		font-size: var(--fs-13);
		text-align: center;
	}

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
		width: 136px;
		margin: calc(-1 * var(--sp-64) - var(--sp-32)) auto var(--sp-8);
	}

	.mascot {
		width: 100%;
		height: auto;
		display: block;
		filter: drop-shadow(0 12px 24px rgb(0 0 0 / 0.35));
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

	.status {
		min-height: 1.4em;
		margin-top: calc(-1 * var(--sp-8));
		font-size: var(--fs-13);
	}

	.ok {
		font-weight: var(--fw-semibold);
	}

	.warn {
		color: var(--c-accent);
		font-weight: var(--fw-semibold);
	}

	.recent {
		display: flex;
		flex-wrap: wrap;
		gap: var(--sp-8);
		margin-top: calc(-1 * var(--sp-8));
	}

	.recent .chip {
		padding: 0 var(--sp-4) 0 var(--sp-12);
		gap: 0;
	}

	.recent button {
		min-height: 36px;
		border: 0;
		background: transparent;
		color: inherit;
		font: inherit;
	}

	.recent .x {
		width: 32px;
		opacity: 0.7;
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
