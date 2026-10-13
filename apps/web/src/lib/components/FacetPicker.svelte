<script lang="ts">
	/**
	 * One filter facet as chips. Long lists get a search field and show the best matches;
	 * chosen values always stay visible.
	 */
	interface Props {
		label: string;
		options: { value: string; label: string }[];
		selected: string[];
		/** Several values (any of them) or exactly one. */
		multi?: boolean;
		searchLabel: string;
		showAllLabel: (n: number) => string;
		onchange: (values: string[]) => void;
	}

	let {
		label,
		options,
		selected,
		multi = false,
		searchLabel,
		showAllLabel,
		onchange
	}: Props = $props();

	const LIMIT = 18;
	let needle = $state('');
	let all = $state(false);

	const norm = (s: string) =>
		s
			.normalize('NFD')
			.replace(/\p{Diacritic}/gu, '')
			.toLowerCase();

	const matches = $derived.by(() => {
		const n = norm(needle.trim());
		const chosen = options.filter((o) => selected.includes(o.value));
		const others = options.filter(
			(o) => !selected.includes(o.value) && (!n || norm(o.label).includes(n))
		);
		// Prefix matches first ("ma" finds "Mara" before "Thomas").
		if (n) others.sort((a, b) => +!norm(a.label).startsWith(n) - +!norm(b.label).startsWith(n));
		return { chosen, others };
	});
	const visible = $derived(all || needle ? matches.others : matches.others.slice(0, LIMIT));
	const hidden = $derived(matches.others.length - visible.length);

	function toggle(value: string) {
		const on = selected.includes(value);
		if (multi) onchange(on ? selected.filter((v) => v !== value) : [...selected, value]);
		else onchange(on ? [] : [value]);
	}
</script>

<fieldset class="facet">
	<legend class="label">{label}</legend>
	{#if options.length > LIMIT}
		<input
			class="find"
			type="search"
			bind:value={needle}
			placeholder={searchLabel}
			aria-label="{label}: {searchLabel}"
		/>
	{/if}
	<div class="opts">
		{#each [...matches.chosen, ...visible] as o (o.value)}
			<button
				type="button"
				class="chip"
				aria-pressed={selected.includes(o.value)}
				onclick={() => toggle(o.value)}>{o.label}</button
			>
		{/each}
		{#if hidden > 0}
			<button type="button" class="chip outline more" onclick={() => (all = true)}
				>{showAllLabel(hidden)}</button
			>
		{/if}
	</div>
</fieldset>

<style>
	.facet {
		display: grid;
		align-content: start;
		gap: var(--sp-8);
		min-width: 0;
		margin: 0;
		padding: 0;
		border: 0;
	}

	legend {
		padding: 0;
		margin-bottom: var(--sp-8);
	}

	.find {
		min-height: 40px;
		padding: 0 var(--sp-12);
		border: 1.5px solid var(--c-line);
		border-radius: var(--r-pill);
		background: var(--c-bg);
		color: var(--c-ink);
		font: inherit;
		font-size: var(--fs-14);
	}

	.find:focus-visible {
		outline: none;
		border-color: var(--c-accent);
	}

	/* Chips keep their height when a neighbouring facet is taller. */
	.opts {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		align-content: flex-start;
		gap: var(--sp-8);
	}

	.chip {
		max-width: 100%;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.more {
		color: var(--c-muted);
	}
</style>
