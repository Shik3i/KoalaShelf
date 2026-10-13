<script lang="ts">
	import { getServerStatus } from '@koalashelf/abs-client';
	import { session, presetServer } from '#lib/session.svelte.ts';
	import { theme, type ThemeChoice } from '#lib/theme.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { prefs, SKIP_STEPS } from '#lib/prefs.svelte.ts';
	import { player } from '#lib/player/player.svelte.ts';
	import { apiFetch } from '#lib/platform.ts';
	import LocaleSwitch from '#lib/components/LocaleSwitch.svelte';
	import Segmented from '#lib/components/Segmented.svelte';

	const t = $derived(i18n.m.settings);
	const THEMES: ThemeChoice[] = ['system', 'light', 'dark'];
	const themeLabel = (c: ThemeChoice) =>
		({ system: t.themeSystem, light: t.themeLight, dark: t.themeDark })[c];

	let serverUrl = $state<string | null>(null);
	let serverVersion = $state<string | null>(null);

	$effect(() => {
		const base = session.client?.baseUrl;
		if (base === undefined) return;
		// Signed in through the proxy: show the server's real address, not the app's.
		if (base) serverUrl = base;
		else presetServer().then((p) => (serverUrl = p ?? location.origin));
		apiFetch()
			.then((f) => getServerStatus(base, f))
			.then((s) => (serverVersion = s.serverVersion))
			.catch(() => (serverVersion = null));
	});
</script>

<svelte:head><title>{t.title} · KoalaShelf</title></svelte:head>

<h1>{t.title}</h1>

<section class="card">
	<h2 class="label">{t.appearance}</h2>
	<div class="row">
		<span>{t.theme}</span>
		<Segmented
			label={t.theme}
			value={theme.choice}
			options={THEMES.map((c) => ({ value: c, label: themeLabel(c) }))}
			onchange={(c) => theme.set(c)}
		/>
	</div>
	<div class="row">
		<span>{t.language}</span>
		<LocaleSwitch />
	</div>
</section>

<section class="card">
	<h2 class="label">{t.playback}</h2>
	<div class="row stacked">
		<span>{t.skipBack}</span>
		<Segmented
			size="sm"
			label={t.skipBack}
			value={prefs.skipBack}
			options={SKIP_STEPS.map((s) => ({ value: s, label: t.seconds(s) }))}
			onchange={(s) => prefs.set('skipBack', s)}
		/>
	</div>
	<div class="row stacked">
		<span>{t.skipForward}</span>
		<Segmented
			size="sm"
			label={t.skipForward}
			value={prefs.skipForward}
			options={SKIP_STEPS.map((s) => ({ value: s, label: t.seconds(s) }))}
			onchange={(s) => prefs.set('skipForward', s)}
		/>
	</div>
	<div class="row">
		<span class="stack">
			{t.smartRewind}
			<small class="muted">{t.smartRewindHint}</small>
		</span>
		<button
			class="switch"
			role="switch"
			aria-checked={prefs.smartRewind}
			aria-label={t.smartRewind}
			onclick={() => prefs.set('smartRewind', !prefs.smartRewind)}
		>
			<span></span>
		</button>
	</div>
	{#if player.levelingAvailable}
		<div class="row">
			<span class="stack">
				{t.leveling}
				<small class="muted">{t.levelingHint}</small>
			</span>
			<button
				class="switch"
				role="switch"
				aria-checked={prefs.leveling}
				aria-label={t.leveling}
				onclick={() => player.setLeveling(!prefs.leveling)}
			>
				<span></span>
			</button>
		</div>
	{/if}
</section>

<section class="card">
	<h2 class="label">{t.account}</h2>
	<dl>
		<dt>{t.user}</dt>
		<dd>{session.user?.username}</dd>
		<dt>{t.server}</dt>
		<dd>{serverUrl ?? '…'}</dd>
		<dt>{t.version}</dt>
		<dd>{serverVersion ? `Audiobookshelf ${serverVersion}` : '…'}</dd>
	</dl>
	<button class="btn ghost" onclick={() => session.signOut()}>{t.signOut}</button>
</section>

<section class="card">
	<h2 class="label">{t.about}</h2>
	<p class="muted">{t.aboutText}</p>
	<a
		class="link"
		href="https://github.com/Shik3i/KoalaShelf"
		target="_blank"
		rel="noopener noreferrer"
	>
		{t.source}
	</a>
</section>

<style>
	h1 {
		margin-bottom: var(--sp-24);
	}

	.card {
		display: grid;
		gap: var(--sp-16);
		max-width: 720px;
		padding: var(--sp-24);
		margin-bottom: var(--sp-16);
		border-radius: var(--r-card-lg);
	}

	.row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--sp-16);
		flex-wrap: wrap;
	}

	/* Label above the control (wide segmented rows stay aligned on phones). */
	.row.stacked {
		flex-direction: column;
		align-items: flex-start;
		gap: var(--sp-8);
	}

	.row > span {
		font-weight: var(--fw-semibold);
	}

	.stack {
		display: grid;
		gap: var(--sp-4);
		flex: 1;
		min-width: 220px;
	}

	.stack small {
		font-size: var(--fs-13);
		font-weight: var(--fw-regular);
	}

	/* Segmented controls sit on a card here: give them the page background. */
	.card :global(.seg) {
		background: var(--c-bg);
	}

	.switch {
		position: relative;
		width: 52px;
		height: 32px;
		flex: none;
		padding: 0;
		border: 0;
		border-radius: var(--r-pill);
		background: var(--c-track);
		transition: background var(--motion-fast) var(--motion-ease);
	}

	.switch span {
		position: absolute;
		top: 4px;
		left: 4px;
		width: 24px;
		height: 24px;
		border-radius: 50%;
		background: var(--c-card);
		box-shadow: 0 1px 3px rgb(0 0 0 / 0.3);
		transition: translate var(--motion-fast) var(--motion-ease);
	}

	.switch[aria-checked='true'] {
		background: var(--c-accent);
	}

	.switch[aria-checked='true'] span {
		translate: 20px 0;
	}

	.switch::after {
		content: '';
		position: absolute;
		inset: -6px;
	}

	dl {
		display: grid;
		grid-template-columns: auto 1fr;
		gap: var(--sp-8) var(--sp-24);
		margin: 0;
		font-size: var(--fs-14);
	}

	dt {
		color: var(--c-muted);
	}

	dd {
		margin: 0;
		overflow-wrap: anywhere;
	}

	.btn {
		justify-self: start;
	}

	.link {
		justify-self: start;
		color: var(--c-accent);
		font-weight: var(--fw-semibold);
		text-decoration: underline;
		text-underline-offset: 3px;
	}
</style>
