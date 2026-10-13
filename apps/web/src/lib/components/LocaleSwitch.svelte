<script lang="ts">
	import { i18n, LOCALES, type Locale } from '#lib/i18n/index.svelte.ts';
	import Segmented from './Segmented.svelte';

	const options = (Object.keys(LOCALES) as Locale[]).map((l) => ({
		value: l,
		label: l.toUpperCase(),
		lang: l
	}));
</script>

<Segmented
	size="sm"
	label={i18n.m.nav.language}
	value={i18n.locale}
	{options}
	onchange={(l) => i18n.set(l)}
/>
