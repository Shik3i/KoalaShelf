// Minimal type-safe i18n: English is the base, other locales must match its shape.
// No dependencies, works offline, nothing leaves the device.
import { en, type Messages } from './en.ts';
import { de } from './de.ts';

export const LOCALES = { en: 'English', de: 'Deutsch' } as const;
export type Locale = keyof typeof LOCALES;

const DICTS: Record<Locale, Messages> = { en, de };
const KEY = 'ks.locale';

function detect(): Locale {
	try {
		const saved = localStorage.getItem(KEY);
		if (saved && saved in DICTS) return saved as Locale;
	} catch {
		/* storage blocked */
	}
	for (const lang of navigator.languages ?? []) {
		const base = lang.slice(0, 2).toLowerCase();
		if (base in DICTS) return base as Locale;
	}
	return 'en';
}

class I18n {
	locale = $state<Locale>(detect());

	constructor() {
		document.documentElement.lang = this.locale;
	}

	/** Current messages. Reading this inside markup or $derived is reactive. */
	get m(): Messages {
		return DICTS[this.locale];
	}

	/** BCP 47 tag for Intl formatters. */
	get tag() {
		return this.locale === 'de' ? 'de-DE' : 'en-US';
	}

	set(locale: Locale) {
		this.locale = locale;
		document.documentElement.lang = locale;
		try {
			localStorage.setItem(KEY, locale);
		} catch {
			/* ignore */
		}
	}
}

export const i18n = new I18n();
