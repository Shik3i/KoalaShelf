// Home shelves the listener can show, hide and reorder ("continue listening" always leads).
import { prefs } from './prefs.svelte.ts';

export const CUSTOM_SHELVES = [
	'continue-series',
	'recently-added',
	'recent-series',
	'recommended',
	'listen-again'
] as const;

export type CustomShelf = (typeof CUSTOM_SHELVES)[number];

/** Shelves in the listener's order; ones never ordered keep the default position at the end. */
export function shelfOrder(): CustomShelf[] {
	const known = new Set<string>(CUSTOM_SHELVES);
	const chosen = prefs.shelfOrder.filter((id): id is CustomShelf => known.has(id));
	return [...chosen, ...CUSTOM_SHELVES.filter((id) => !chosen.includes(id))];
}

export function moveShelf(id: CustomShelf, by: -1 | 1) {
	const order = shelfOrder();
	const from = order.indexOf(id);
	const to = from + by;
	if (to < 0 || to >= order.length) return;
	order.splice(to, 0, ...order.splice(from, 1));
	prefs.set('shelfOrder', order);
}

export function setShelfVisible(id: string, visible: boolean) {
	const hidden = prefs.hiddenShelves.filter((h) => h !== id);
	prefs.set('hiddenShelves', visible ? hidden : [...hidden, id]);
}
