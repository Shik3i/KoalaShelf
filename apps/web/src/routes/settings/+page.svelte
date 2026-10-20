<script lang="ts">
	import { getServerStatus } from '@koalashelf/abs-client';
	import { accents, type AccentName } from '@koalashelf/design-tokens';
	import { customAccent } from '#lib/accent.ts';
	import { session, presetServer } from '#lib/session.svelte.ts';
	import { theme, type ThemeChoice } from '#lib/theme.svelte.ts';
	import { i18n } from '#lib/i18n/index.svelte.ts';
	import { prefs, SKIP_STEPS, TEXT_SIZES, type Prefs } from '#lib/prefs.svelte.ts';
	import { player, RATES } from '#lib/player/player.svelte.ts';
	import SoundPanel from '#lib/player/SoundPanel.svelte';
	import { exportSettings, importSettings, justImported } from '#lib/backup.ts';
	import Upload from '@lucide/svelte/icons/upload';
	import Download from '@lucide/svelte/icons/download';
	import { downloads, supported as downloadsSupported } from '#lib/downloads.svelte.ts';
	import { CUSTOM_SHELVES, moveShelf, setShelfVisible, shelfOrder } from '#lib/shelves.ts';
	import { toasts } from '#lib/toast.svelte.ts';
	import { apiFetch } from '#lib/platform.ts';
	import { bytes, speed } from '#lib/format.ts';
	import LocaleSwitch from '#lib/components/LocaleSwitch.svelte';
	import Segmented from '#lib/components/Segmented.svelte';
	import Toggle from '#lib/components/Toggle.svelte';
	import AccountSecurity from '#lib/components/AccountSecurity.svelte';
	import SavedAccounts from '#lib/components/SavedAccounts.svelte';
	import ChevronUp from '@lucide/svelte/icons/chevron-up';
	import ChevronDown from '@lucide/svelte/icons/chevron-down';
	import Check from '@lucide/svelte/icons/check';
	import ChartColumn from '@lucide/svelte/icons/chart-column';

	const t = $derived(i18n.m.settings);
	const home = $derived(i18n.m.home);
	const THEMES: ThemeChoice[] = ['system', 'light', 'dark'];
	const themeLabel = (c: ThemeChoice) =>
		({ system: t.themeSystem, light: t.themeLight, dark: t.themeDark })[c];
	const ACCENTS = Object.keys(accents) as AccentName[];
	/** The listener's color in readable variants (shown on the swatch and as a preview). */
	const own = $derived(customAccent(prefs.customAccent));
	function pickColor(hex: string) {
		prefs.set('customAccent', hex);
		prefs.set('accent', 'custom');
	}
	/** Swatch in the theme that is showing right now. */
	const dark = $derived(
		theme.choice === 'dark' ||
			(theme.choice === 'system' && matchMedia('(prefers-color-scheme: dark)').matches)
	);
	const shelfLabel = $derived<Record<string, string>>({
		'continue-series': home.continueSeries,
		'recently-added': home.recentlyAdded,
		'recent-series': home.recentSeries,
		recommended: home.discover,
		'listen-again': home.listenAgain
	});
	const order = $derived(shelfOrder());

	/** Typed shorthand for a segmented setting. */
	const choose =
		<K extends keyof Prefs>(key: K) =>
		(value: Prefs[K]) =>
			prefs.set(key, value);

	const sections = $derived([
		['appearance', t.appearance],
		['player', t.playback],
		['sound', i18n.m.sound.title],
		['sleep', t.sleep],
		['library', t.libraryHome],
		['storage', t.storage],
		['stats', t.statsTitle],
		['account', t.account]
	] as const);

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

	// Downloads: count and size on this device.
	void downloads.init();
	const downloaded = $derived([...downloads.books.values()]);
	const downloadedSize = $derived(downloaded.reduce((s, b) => s + b.size, 0));

	/** "22:00" <-> minutes after midnight. */
	const hhmm = (m: number) =>
		`${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
	const minutesOf = (v: string) => {
		const [h, m] = v.split(':').map(Number);
		return Number.isFinite(h) && Number.isFinite(m) ? h * 60 + m : null;
	};

	// Settings backup: a file to move them to another browser or device.
	let importInput = $state<HTMLInputElement>();
	let importFailed = $state(false);
	$effect(() => {
		if (justImported()) toasts.show(t.importDone);
	});
	async function onImport(e: Event & { currentTarget: HTMLInputElement }) {
		const file = e.currentTarget.files?.[0];
		e.currentTarget.value = '';
		if (!file) return;
		importFailed = !(await importSettings(file));
		if (!importFailed) location.reload();
	}

	/** Destructive actions ask once more, inline (no modal). */
	let confirming = $state<'downloads' | 'reset' | null>(null);

	async function removeAllDownloads() {
		for (const b of downloaded) await downloads.remove(b.item.id);
		confirming = null;
	}

	function resetSettings() {
		prefs.reset();
		theme.set('system');
		confirming = null;
		toasts.show(t.resetDone);
	}
</script>

<svelte:head><title>{t.title} · KoalaShelf</title></svelte:head>

<h1>{t.title}</h1>

<nav class="jump" aria-label={t.sections}>
	{#each sections as [id, label] (id)}
		<a class="chip" href="#{id}">{label}</a>
	{/each}
</nav>

<section class="card" id="appearance" aria-labelledby="h-appearance">
	<h2 class="label" id="h-appearance">{t.appearance}</h2>
	<div class="row">
		<span>{t.theme}</span>
		<Segmented
			label={t.theme}
			value={theme.choice}
			options={THEMES.map((c) => ({ value: c, label: themeLabel(c) }))}
			onchange={(c) => theme.set(c)}
		/>
	</div>
	<fieldset class="row stacked">
		<legend>{t.accent}</legend>
		<div class="swatches">
			{#each ACCENTS as name (name)}
				<label class="swatch" style:--sw={accents[name][dark ? 'dark' : 'light'].accent}>
					<input
						type="radio"
						name="accent"
						value={name}
						checked={prefs.accent === name}
						onchange={() => prefs.set('accent', name)}
					/>
					<span class="dot" aria-hidden="true"><Check size={16} strokeWidth={3} /></span>
					<span class="name">{t.accents[name]}</span>
				</label>
			{/each}
			<label
				class="swatch"
				style:--sw={own[dark ? 'dark' : 'light'].accent}
				style:--sw-ink={own[dark ? 'dark' : 'light'].onAccent}
			>
				<input
					type="radio"
					name="accent"
					value="custom"
					checked={prefs.accent === 'custom'}
					onchange={() => prefs.set('accent', 'custom')}
				/>
				<span class="dot own" aria-hidden="true"><Check size={16} strokeWidth={3} /></span>
				<span class="name">{t.accents.custom}</span>
			</label>
		</div>
		{#if prefs.accent === 'custom'}
			<div class="own-color">
				<label class="picker">
					<input
						type="color"
						value={prefs.customAccent}
						oninput={(e) => pickColor(e.currentTarget.value.toUpperCase())}
					/>
					<span>{t.customColor}</span>
				</label>
				<small class="muted" aria-live="polite">
					{own.adjusted ? t.customAdjusted : t.customOk}
				</small>
				<span class="previews" aria-hidden="true">
					<span class="preview" style:background={own.light.accent} style:color={own.light.onAccent}
						>{t.themeLight}</span
					>
					<span class="preview" style:background={own.dark.accent} style:color={own.dark.onAccent}
						>{t.themeDark}</span
					>
				</span>
			</div>
		{/if}
	</fieldset>
	<div class="row stacked">
		<span class="stack">
			{t.font}
			<small class="muted">{t.fontHint}</small>
		</span>
		<Segmented
			size="sm"
			label={t.font}
			value={prefs.font}
			options={(['standard', 'readable', 'system'] as const).map((f) => ({
				value: f,
				label: t.fonts[f]
			}))}
			onchange={choose('font')}
		/>
	</div>
	<div class="row stacked">
		<span class="stack">
			{t.textSize}
			<small class="muted">{t.textSizeHint}</small>
		</span>
		<Segmented
			size="sm"
			label={t.textSize}
			value={prefs.textSize}
			options={(Object.keys(TEXT_SIZES) as Prefs['textSize'][]).map((s) => ({
				value: s,
				label: t.textSizes[s]
			}))}
			onchange={choose('textSize')}
		/>
	</div>
	<div class="row">
		<span>{t.language}</span>
		<LocaleSwitch />
	</div>
	<div class="row stacked">
		<span>{t.motion}</span>
		<Segmented
			size="sm"
			label={t.motion}
			value={prefs.motion}
			options={(['system', 'reduce', 'full'] as const).map((m) => ({
				value: m,
				label: t.motions[m]
			}))}
			onchange={choose('motion')}
		/>
	</div>
	<Toggle label={t.spin} hint={t.spinHint} checked={prefs.spin} onchange={choose('spin')} />
	<div class="row stacked">
		<span>{t.coverSize}</span>
		<Segmented
			size="sm"
			label={t.coverSize}
			value={prefs.coverSize}
			options={(['sm', 'md', 'lg'] as const).map((s) => ({ value: s, label: t.coverSizes[s] }))}
			onchange={choose('coverSize')}
		/>
	</div>
</section>

<section class="card" id="player" aria-labelledby="h-player">
	<h2 class="label" id="h-player">{t.playback}</h2>
	<div class="row stacked">
		<span>{t.skipBack}</span>
		<Segmented
			size="sm"
			label={t.skipBack}
			value={prefs.skipBack}
			options={SKIP_STEPS.map((s) => ({ value: s, label: t.seconds(s) }))}
			onchange={choose('skipBack')}
		/>
	</div>
	<div class="row stacked">
		<span>{t.skipForward}</span>
		<Segmented
			size="sm"
			label={t.skipForward}
			value={prefs.skipForward}
			options={SKIP_STEPS.map((s) => ({ value: s, label: t.seconds(s) }))}
			onchange={choose('skipForward')}
		/>
	</div>
	<div class="row stacked">
		<span>{t.scope}</span>
		<Segmented
			size="sm"
			label={t.scope}
			value={prefs.progressScope}
			options={(['book', 'chapter'] as const).map((s) => ({ value: s, label: t.scopes[s] }))}
			onchange={(s) => player.setScope(s)}
		/>
	</div>
	<div class="row stacked">
		<span>{t.clock}</span>
		<Segmented
			size="sm"
			label={t.clock}
			value={prefs.remainingTime ? 'remaining' : 'elapsed'}
			options={(['elapsed', 'remaining'] as const).map((c) => ({ value: c, label: t.clocks[c] }))}
			onchange={(c) => prefs.set('remainingTime', c === 'remaining')}
		/>
	</div>
	<Toggle
		label={t.speedAdjusted}
		hint={t.speedAdjustedHint}
		checked={prefs.speedAdjusted}
		onchange={choose('speedAdjusted')}
	/>
	<div class="row stacked">
		<span>{t.defaultRate}</span>
		<Segmented
			size="sm"
			label={t.defaultRate}
			value={prefs.defaultRate === 'last' ? 'last' : 'fixed'}
			options={(['last', 'fixed'] as const).map((m) => ({
				value: m,
				label: m === 'last' ? t.rateLast : t.rateFixed
			}))}
			onchange={(m) => prefs.set('defaultRate', m === 'last' ? 'last' : player.rate)}
		/>
		{#if prefs.defaultRate !== 'last'}
			<Segmented
				size="sm"
				label={t.rateFixed}
				value={prefs.defaultRate}
				options={RATES.map((r) => ({ value: r as number, label: speed(r) }))}
				onchange={choose('defaultRate')}
			/>
		{/if}
	</div>
	<Toggle
		label={t.rememberRate}
		hint={t.rememberRateHint}
		checked={prefs.rememberRate}
		onchange={choose('rememberRate')}
	/>
	<div class="row stacked">
		<span>{t.mediaKeys}</span>
		<Segmented
			size="sm"
			label={t.mediaKeys}
			value={prefs.mediaKeys}
			options={(['chapter', 'skip'] as const).map((m) => ({ value: m, label: t.mediaKeyModes[m] }))}
			onchange={choose('mediaKeys')}
		/>
	</div>
	<Toggle
		label={t.smartRewind}
		hint={t.smartRewindHint}
		checked={prefs.smartRewind}
		onchange={choose('smartRewind')}
	/>
	<div class="divider" role="separator"></div>
	<Toggle
		label={t.autoQueue}
		hint={t.autoQueueHint}
		checked={prefs.autoQueue}
		onchange={choose('autoQueue')}
	/>
	<Toggle label={t.seriesOffer} checked={prefs.seriesOffer} onchange={choose('seriesOffer')} />
	<Toggle
		label={t.shortcuts}
		hint={t.shortcutsHint}
		checked={prefs.shortcuts}
		onchange={choose('shortcuts')}
	/>
</section>

<section class="card" id="sound" aria-labelledby="h-sound">
	<h2 class="label" id="h-sound">{i18n.m.sound.title}</h2>
	<SoundPanel hideTitle />
</section>

<section class="card" id="sleep" aria-labelledby="h-sleep">
	<h2 class="label" id="h-sleep">{t.sleep}</h2>
	<Toggle
		label={t.sleepFade}
		hint={t.sleepFadeHint}
		checked={prefs.sleepFade}
		onchange={choose('sleepFade')}
	/>
	{#if prefs.sleepFade}
		<div class="row stacked">
			<span>{t.sleepFadeDuration}</span>
			<Segmented
				size="sm"
				label={t.sleepFadeDuration}
				value={prefs.sleepFadeDuration}
				options={([10, 30, 60] as const).map((s) => ({
					value: s,
					label: t.seconds(s)
				}))}
				onchange={choose('sleepFadeDuration')}
			/>
		</div>
	{/if}
	<div class="row stacked">
		<span>{t.sleepRewind}</span>
		<Segmented
			size="sm"
			label={t.sleepRewind}
			value={prefs.sleepRewind}
			options={([0, 15, 30] as const).map((s) => ({
				value: s,
				label: s ? t.seconds(s) : t.off
			}))}
			onchange={choose('sleepRewind')}
		/>
	</div>
	<Toggle
		label={t.autoSleep}
		hint={t.autoSleepHint}
		checked={prefs.autoSleep}
		onchange={choose('autoSleep')}
	/>
	{#if prefs.autoSleep}
		<div class="row times">
			<label>
				<span>{t.autoSleepFrom}</span>
				<input
					type="time"
					value={hhmm(prefs.autoSleepFrom)}
					onchange={(e) => {
						const m = minutesOf(e.currentTarget.value);
						if (m !== null) prefs.set('autoSleepFrom', m);
					}}
				/>
			</label>
			<label>
				<span>{t.autoSleepTo}</span>
				<input
					type="time"
					value={hhmm(prefs.autoSleepTo)}
					onchange={(e) => {
						const m = minutesOf(e.currentTarget.value);
						if (m !== null) prefs.set('autoSleepTo', m);
					}}
				/>
			</label>
		</div>
		<div class="row stacked">
			<span>{t.autoSleepKind}</span>
			<Segmented
				size="sm"
				label={t.autoSleepKind}
				value={prefs.autoSleepKind}
				options={(['smart', 'chapter', 'minutes'] as const).map((k) => ({
					value: k,
					label: t.autoSleepKinds[k]
				}))}
				onchange={choose('autoSleepKind')}
			/>
			{#if prefs.autoSleepKind === 'minutes'}
				<Segmented
					size="sm"
					label={t.autoSleepMinutes}
					value={prefs.autoSleepMinutes}
					options={[15, 30, 45, 60].map((m) => ({
						value: m,
						label: i18n.m.player.timerMinutes(m)
					}))}
					onchange={choose('autoSleepMinutes')}
				/>
			{:else if prefs.autoSleepKind === 'smart'}
				<small class="muted">{t.autoSleepSmart(prefs.sleepMin, prefs.sleepMax)}</small>
			{/if}
		</div>
	{/if}
</section>

<section class="card" id="library" aria-labelledby="h-library">
	<h2 class="label" id="h-library">{t.libraryHome}</h2>
	<div class="row stacked">
		<span>{t.startPage}</span>
		<Segmented
			size="sm"
			label={t.startPage}
			value={prefs.startPage}
			options={(['home', 'library'] as const).map((p) => ({ value: p, label: t.startPages[p] }))}
			onchange={choose('startPage')}
		/>
	</div>
	<div class="stack">
		<span class="strong">{t.shelves}</span>
		<small class="muted">{t.shelvesHint}</small>
	</div>
	<ol class="shelves">
		{#each order as id, i (id)}
			<li>
				<div class="grow">
					<Toggle
						label={shelfLabel[id]}
						checked={!prefs.hiddenShelves.includes(id)}
						onchange={(on) => setShelfVisible(id, on)}
					/>
				</div>
				<button
					class="move"
					disabled={i === 0}
					onclick={() => moveShelf(id, -1)}
					aria-label={t.moveUp(shelfLabel[id])}
					title={t.moveUp(shelfLabel[id])}><ChevronUp size={18} /></button
				>
				<button
					class="move"
					disabled={i === CUSTOM_SHELVES.length - 1}
					onclick={() => moveShelf(id, 1)}
					aria-label={t.moveDown(shelfLabel[id])}
					title={t.moveDown(shelfLabel[id])}><ChevronDown size={18} /></button
				>
			</li>
		{/each}
	</ol>
</section>

<section class="card" id="storage" aria-labelledby="h-storage">
	<h2 class="label" id="h-storage">{t.storage}</h2>
	{#if downloadsSupported()}
		<p class="summary">{t.downloadsSummary(downloaded.length, bytes(downloadedSize))}</p>
		<Toggle
			label={t.removeFinished}
			hint={t.removeFinishedHint}
			checked={prefs.removeFinished}
			onchange={choose('removeFinished')}
		/>
		<div class="actions">
			<a class="btn ghost" href="/downloads">{t.manageDownloads}</a>
			{#if downloaded.length}
				{#if confirming === 'downloads'}
					<span class="confirm" role="alert">{t.removeAllConfirm(downloaded.length)}</span>
					<button class="btn danger" onclick={removeAllDownloads}>{t.remove}</button>
					<button class="btn ghost" onclick={() => (confirming = null)}>{t.cancel}</button>
				{:else}
					<button class="btn ghost" onclick={() => (confirming = 'downloads')}>{t.removeAll}</button
					>
				{/if}
			{/if}
		</div>
	{/if}
	<div class="stack">
		<span class="strong">{t.backup}</span>
		<small class="muted">{t.backupHint}</small>
	</div>
	<div class="actions">
		<button class="btn ghost" onclick={exportSettings}>
			<Download size={16} aria-hidden="true" />
			{t.exportSettings}
		</button>
		<button class="btn ghost" onclick={() => importInput?.click()}>
			<Upload size={16} aria-hidden="true" />
			{t.importSettings}
		</button>
		<input
			bind:this={importInput}
			class="sr-only"
			type="file"
			accept="application/json,.json"
			tabindex="-1"
			aria-hidden="true"
			onchange={onImport}
		/>
	</div>
	{#if importFailed}<p class="confirm" role="alert">{t.importFailed}</p>{/if}
	<div class="stack">
		<span class="strong">{t.reset}</span>
		<small class="muted">{t.resetHint}</small>
	</div>
	<div class="actions">
		{#if confirming === 'reset'}
			<span class="confirm" role="alert">{t.resetConfirm}</span>
			<button class="btn danger" onclick={resetSettings}>{t.reset}</button>
			<button class="btn ghost" onclick={() => (confirming = null)}>{t.cancel}</button>
		{:else}
			<button class="btn ghost" onclick={() => (confirming = 'reset')}>{t.reset}</button>
		{/if}
	</div>
</section>

<section class="card" id="stats" aria-labelledby="h-stats">
	<h2 class="label" id="h-stats">{t.statsTitle}</h2>
	<div class="row">
		<div>
			<strong>{t.statsHeading}</strong>
			<p class="desc">{t.statsDesc}</p>
		</div>
		<a href="/stats" class="btn primary">
			<ChartColumn size={18} aria-hidden="true" />
			{t.openStats}
		</a>
	</div>
</section>

<section class="card" id="account" aria-labelledby="h-account">
	<h2 class="label" id="h-account">{t.account}</h2>
	<dl>
		<dt>{t.user}</dt>
		<dd>{session.user?.username}</dd>
		<dt>{t.server}</dt>
		<dd>{serverUrl ?? '…'}</dd>
		<dt>{t.version}</dt>
		<dd>{serverVersion ? `Audiobookshelf ${serverVersion}` : '…'}</dd>
	</dl>
	<SavedAccounts />
	<AccountSecurity />
</section>

<section class="card" aria-labelledby="h-about">
	<h2 class="label" id="h-about">{t.about}</h2>
	<p class="muted">{t.aboutText}</p>
	<p class="muted version">KoalaShelf Web {__APP_VERSION__}</p>
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
		margin-bottom: var(--sp-16);
	}

	/* Section shortcuts; scrolls sideways on narrow screens. */
	.jump {
		display: flex;
		gap: var(--sp-8);
		max-width: 720px;
		margin-bottom: var(--sp-24);
		overflow-x: auto;
		scrollbar-width: none;
	}

	.jump .chip {
		flex: none;
	}

	.card {
		display: grid;
		gap: var(--sp-20);
		max-width: 720px;
		padding: var(--sp-24);
		margin-bottom: var(--sp-16);
		border-radius: var(--r-card-lg);
		/* Jump links land with some air above the card. */
		scroll-margin-top: var(--sp-16);
	}

	.row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--sp-16);
		flex-wrap: wrap;
	}

	.divider {
		height: 1px;
		background: var(--c-line);
		margin: var(--sp-4) 0;
	}

	.times {
		justify-content: flex-start;
	}

	.times label {
		display: grid;
		gap: var(--sp-4);
		font-weight: var(--fw-semibold);
	}

	.times input {
		min-height: var(--layout-touch);
		padding: 0 var(--sp-12);
		border: 1.5px solid var(--c-field);
		border-radius: var(--r-card);
		background: var(--c-bg);
		color: var(--c-ink);
		font: inherit;
	}

	/* Label above the control (wide segmented rows stay aligned on phones). */
	.row.stacked {
		flex-direction: column;
		align-items: flex-start;
		gap: var(--sp-8);
	}

	.row > span,
	legend,
	.strong {
		font-weight: var(--fw-semibold);
	}

	fieldset {
		margin: 0;
		padding: 0;
		border: 0;
		min-width: 0;
	}

	legend {
		padding: 0;
		margin-bottom: var(--sp-8);
	}

	.stack {
		display: grid;
		gap: var(--sp-4);
	}

	.stack small {
		font-size: var(--fs-13);
		font-weight: var(--fw-regular);
	}

	/* Segmented controls sit on a card here: give them the page background. */
	.card :global(.seg) {
		background: var(--c-bg);
		max-width: 100%;
	}

	/* Accent colors: real radio buttons, drawn as swatches. */
	.swatches {
		display: flex;
		flex-wrap: wrap;
		gap: var(--sp-12) var(--sp-16);
	}

	.swatch {
		position: relative;
		display: grid;
		justify-items: center;
		gap: var(--sp-4);
		min-width: 56px;
		cursor: pointer;
	}

	/* The (invisible) radio is the hit area, above the drawn swatch. */
	.swatch input {
		position: absolute;
		z-index: 1;
		inset: 0;
		opacity: 0;
		margin: 0;
		cursor: pointer;
	}

	.dot {
		display: grid;
		place-items: center;
		width: 40px;
		height: 40px;
		border-radius: 50%;
		background: var(--sw);
		color: transparent;
		box-shadow:
			0 0 0 3px var(--c-card),
			0 0 0 4px var(--c-line);
		transition:
			box-shadow var(--motion-fast) var(--motion-ease),
			scale var(--motion-base) var(--motion-spring);
	}

	.swatch:hover .dot {
		scale: 1.08;
	}

	.swatch input:checked + .dot {
		color: var(--sw-ink, #fff);
		box-shadow:
			0 0 0 3px var(--c-card),
			0 0 0 5px var(--sw);
	}

	.swatch input:focus-visible + .dot {
		outline: 2px solid var(--c-ink);
		outline-offset: 6px;
	}

	.name {
		font-size: var(--fs-12);
		font-weight: var(--fw-semibold);
	}

	/* The own color: a rainbow ring around it says "any color". */
	.dot.own {
		background:
			radial-gradient(var(--sw) 55%, transparent 56%),
			conic-gradient(#e5484d, #f5a524, #45b26b, #3b82f6, #8b5cf6, #e5484d);
	}

	.own-color {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--sp-8) var(--sp-16);
		margin-top: var(--sp-16);
	}

	.picker {
		display: inline-flex;
		align-items: center;
		gap: var(--sp-8);
		font-weight: var(--fw-semibold);
		cursor: pointer;
	}

	.picker input {
		width: 44px;
		height: 44px;
		padding: 2px;
		border: 1.5px solid var(--c-field);
		border-radius: var(--r-card);
		background: var(--c-card);
		cursor: pointer;
	}

	.own-color small {
		flex-basis: 100%;
		font-size: var(--fs-13);
	}

	.previews {
		display: flex;
		gap: var(--sp-8);
	}

	.preview {
		padding: 4px 12px;
		border-radius: var(--r-pill);
		font-size: var(--fs-12);
		font-weight: var(--fw-bold);
	}

	.shelves {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		gap: var(--sp-8);
	}

	.shelves li {
		display: flex;
		align-items: center;
		gap: var(--sp-4);
		padding: var(--sp-8) var(--sp-8) var(--sp-8) var(--sp-16);
		border-radius: var(--r-card);
		background: var(--c-bg);
	}

	.grow {
		flex: 1;
		min-width: 0;
		margin-right: var(--sp-8);
	}

	.move {
		display: grid;
		place-items: center;
		width: var(--layout-touch);
		height: var(--layout-touch);
		flex: none;
		padding: 0;
		border: 0;
		border-radius: 50%;
		background: transparent;
		color: var(--c-ink);
	}

	.move:hover:not(:disabled) {
		background: var(--c-card);
	}

	.move:disabled {
		opacity: 0.3;
	}

	.summary {
		margin: 0;
	}

	.actions {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--sp-8);
	}

	.confirm {
		flex-basis: 100%;
		font-weight: var(--fw-semibold);
	}

	.btn.danger {
		background: var(--c-accent);
		color: var(--c-on-accent);
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
