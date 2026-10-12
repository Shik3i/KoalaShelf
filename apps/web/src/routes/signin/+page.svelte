<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { AbsError } from '@koalashelf/abs-client';
	import { presetServer, ServerTooOldError, session } from '#lib/session.svelte.ts';
	import {
		forgetServer,
		ProbeError,
		probeProxy,
		probeServer,
		sameServer,
		recentServers,
		rememberServer
	} from '#lib/server-probe.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import LocaleSwitch from '#lib/components/LocaleSwitch.svelte';
	import Vinyl from '#lib/components/Vinyl.svelte';

	const t = $derived(i18n.m.signin);

	let recent = $state(recentServers());
	let server = $state(recentServers()[0] ?? '');
	let username = $state('');
	let password = $state('');
	let busy = $state(false);
	let error = $state<unknown>(null);
	/** Preconfigured server (Docker/dev proxy); undefined while loading. */
	let preset = $state<string | null | undefined>(undefined);
	/** Signing in to the preconfigured server goes through the proxy (no CORS, httpOnly cookie). */
	const viaProxy = $derived(!!preset && sameServer(server, preset));

	type Check =
		| { state: 'idle' | 'checking' }
		| { state: 'ok'; url: string; version: string }
		| { state: 'error'; error: unknown };
	let check = $state<Check>({ state: 'idle' });

	presetServer().then((p) => {
		preset = p;
		if (p && !server) server = p;
	});

	$effect(() => {
		if (session.ready && session.client)
			goto(page.url.searchParams.get('next') ?? '/', { replace: true });
	});

	// Live server check while typing (debounced), so problems show up before signing in.
	$effect(() => {
		const input = server.trim();
		const proxy = viaProxy;
		if (preset === undefined || input.length < 3) {
			check = { state: 'idle' };
			return;
		}
		const ctrl = new AbortController();
		const timer = setTimeout(async () => {
			check = { state: 'checking' };
			try {
				const r = proxy ? await probeProxy(ctrl.signal) : await probeServer(input, ctrl.signal);
				check = { state: 'ok', ...r };
			} catch (e) {
				if (!ctrl.signal.aborted) check = { state: 'error', error: e };
			}
		}, 450);
		return () => {
			clearTimeout(timer);
			ctrl.abort();
		};
	});

	function describe(e: unknown) {
		if (e instanceof ServerTooOldError) return t.errTooOld(e.version, e.min);
		if (e instanceof ProbeError) {
			if (e.kind === 'cors') return t.errCors(location.origin);
			if (e.kind === 'not-abs') return t.errNotAbs;
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
	const checkMessage = $derived(check.state === 'error' ? describe(check.error) : null);
	const shortUrl = (u: string) => u.replace(/^https?:\/\//, '');

	async function submit(e: SubmitEvent) {
		e.preventDefault();
		busy = true;
		error = null;
		try {
			const url = viaProxy
				? ''
				: check.state === 'ok'
					? check.url
					: (await probeServer(server.trim())).url;
			await session.signIn(url, username.trim(), password);
			recent = rememberServer(viaProxy && preset ? preset : url);
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
			<Vinyl
				title="KoalaShelf"
				author="Audiobookshelf"
				playing={busy || check.state === 'checking'}
				label={0.42}
			/>
		</div>
		<h1>KoalaShelf</h1>
		<p class="muted">{t.intro}</p>

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
						required
					/>
				</label>
				<p class="status" aria-live="polite">
					{#if check.state === 'checking'}
						<span class="muted">{t.serverChecking}</span>
					{:else if check.state === 'ok'}
						<span class="ok">✓ {t.serverOk(check.version)}</span>
					{:else if checkMessage}
						<span class="warn">{checkMessage}</span>
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
				<input bind:value={password} type="password" autocomplete="current-password" />
			</label>

			{#if message}<p class="error" role="alert">{message}</p>{/if}

			<button class="btn primary" type="submit" disabled={busy || preset === undefined}>
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
