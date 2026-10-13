// Local preferences: everything a listener can tailor. Stored on this device only.
import type { AccentName } from '@koalashelf/design-tokens';

const KEY = 'ks.prefs';

export const SKIP_STEPS = [10, 15, 30, 45, 60] as const;
export const TEXT_SIZES = { sm: 0.9375, md: 1, lg: 1.125, xl: 1.25 } as const;

export interface Prefs {
	// Appearance
	accent: AccentName;
	textSize: keyof typeof TEXT_SIZES;
	/** Follow the system setting, or reduce / keep animations regardless of it. */
	motion: 'system' | 'reduce' | 'full';
	/** The record turns while playing. */
	spin: boolean;
	coverSize: 'sm' | 'md' | 'lg';
	// Player
	skipBack: number;
	skipForward: number;
	/** Rewind a little after longer pauses, so the listener finds back into the story. */
	smartRewind: boolean;
	/** Big player clock shows the time left instead of the elapsed time. */
	remainingTime: boolean;
	/** Timeline, clock and lock screen cover the current chapter or the whole book. */
	progressScope: 'book' | 'chapter';
	/** Remaining times shrink with the playback speed. */
	speedAdjusted: boolean;
	/** Speed for books without their own: the one used last, or a fixed one. */
	defaultRate: 'last' | number;
	rememberRate: boolean;
	/** Previous / next on headsets, lock screen and media keys. */
	mediaKeys: 'chapter' | 'skip';
	/** Even out loud and quiet passages (Web Audio compressor). */
	leveling: boolean;
	autoQueue: boolean;
	seriesOffer: boolean;
	shortcuts: boolean;
	// Sleep timer
	sleepFade: boolean;
	/** Seconds to go back when a timer stops playback. */
	sleepRewind: 0 | 15 | 30;
	/** Last smart sleep timer window, in minutes. */
	sleepMin: number;
	sleepMax: number;
	// Library & home
	startPage: 'home' | 'library';
	/** Home shelves (server ids) hidden by the listener, and their custom order. */
	hiddenShelves: string[];
	shelfOrder: string[];
}

export const DEFAULTS: Prefs = {
	accent: 'vinyl',
	textSize: 'md',
	motion: 'system',
	spin: true,
	coverSize: 'md',
	skipBack: 30,
	skipForward: 30,
	smartRewind: true,
	remainingTime: false,
	progressScope: 'book',
	speedAdjusted: true,
	defaultRate: 'last',
	rememberRate: true,
	mediaKeys: 'chapter',
	leveling: false,
	autoQueue: true,
	seriesOffer: true,
	shortcuts: true,
	sleepFade: true,
	sleepRewind: 15,
	sleepMin: 20,
	sleepMax: 40,
	startPage: 'home',
	hiddenShelves: [],
	shelfOrder: []
};

function load(): Prefs {
	try {
		const saved = JSON.parse(localStorage.getItem(KEY) ?? '{}');
		// Only known keys with a plausible type survive (older versions, hand edits).
		const valid = (k: string, v: unknown, fallback: unknown) =>
			k === 'defaultRate'
				? v === 'last' || (typeof v === 'number' && v > 0)
				: Array.isArray(fallback)
					? Array.isArray(v)
					: typeof v === typeof fallback;
		return Object.fromEntries(
			Object.entries(DEFAULTS).map(([k, v]) => [k, valid(k, saved[k], v) ? saved[k] : v])
		) as unknown as Prefs;
	} catch {
		return { ...DEFAULTS };
	}
}

function save(p: Prefs) {
	try {
		localStorage.setItem(KEY, JSON.stringify(p));
	} catch {
		/* storage blocked: the choice lasts for this visit */
	}
}

type PrefsStore = Prefs & {
	set<K extends keyof Prefs>(key: K, value: Prefs[K]): void;
	/** Everything back to the defaults. */
	reset(): void;
};

export const prefs: PrefsStore = $state({
	...(typeof localStorage === 'undefined' ? DEFAULTS : load()),
	set<K extends keyof Prefs>(this: PrefsStore, key: K, value: Prefs[K]) {
		this[key] = value as PrefsStore[K];
		save(this);
	},
	reset(this: PrefsStore) {
		Object.assign(this, structuredClone(DEFAULTS));
		save(this);
	}
});

const BOOK_RATES = 'ks.bookRates';

/** Speed remembered per book (e.g. a fast narrator), falls back to the global default. */
export function bookRate(itemId: string): number | null {
	try {
		return JSON.parse(localStorage.getItem(BOOK_RATES) ?? '{}')[itemId] ?? null;
	} catch {
		return null;
	}
}

export function setBookRate(itemId: string, rate: number) {
	try {
		const all = JSON.parse(localStorage.getItem(BOOK_RATES) ?? '{}');
		all[itemId] = rate;
		localStorage.setItem(BOOK_RATES, JSON.stringify(all));
	} catch {
		/* ignore */
	}
}

/** Seconds to rewind after a pause of `pausedMs`. */
export function smartRewindSeconds(pausedMs: number) {
	if (pausedMs > 60 * 60_000) return 30;
	if (pausedMs > 10 * 60_000) return 15;
	if (pausedMs > 60_000) return 5;
	return 0;
}
