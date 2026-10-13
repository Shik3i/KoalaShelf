// Local playback preferences. Stored on this device only.
const KEY = 'ks.prefs';

export const SKIP_STEPS = [10, 15, 30, 45, 60] as const;

interface Prefs {
	skipBack: number;
	skipForward: number;
	/** Rewind a little after longer pauses, so the listener finds back into the story. */
	smartRewind: boolean;
	/** Big player clock shows the time left in the book instead of the elapsed time. */
	remainingTime: boolean;
}

const DEFAULTS: Prefs = { skipBack: 30, skipForward: 30, smartRewind: true, remainingTime: false };

function load(): Prefs {
	try {
		return { ...DEFAULTS, ...JSON.parse(localStorage.getItem(KEY) ?? '{}') };
	} catch {
		return { ...DEFAULTS };
	}
}

class PrefsStore {
	#p = $state<Prefs>(load());

	get skipBack() {
		return this.#p.skipBack;
	}
	get skipForward() {
		return this.#p.skipForward;
	}
	get smartRewind() {
		return this.#p.smartRewind;
	}
	get remainingTime() {
		return this.#p.remainingTime;
	}

	set<K extends keyof Prefs>(key: K, value: Prefs[K]) {
		this.#p[key] = value;
		try {
			localStorage.setItem(KEY, JSON.stringify(this.#p));
		} catch {
			/* ignore */
		}
	}
}

export const prefs = new PrefsStore();

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
