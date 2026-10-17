// Theme: follows the system by default, manual override stored locally (see app.html).
import { reducedMotion } from './motion.ts';

export type ThemeChoice = 'system' | 'light' | 'dark';

const KEY = 'ks.theme';

/** Where the listener last pressed: the new theme spreads from there. */
let origin: { x: number; y: number; at: number } | null = null;
if (typeof document !== 'undefined')
	document.addEventListener(
		'pointerdown',
		(e) => (origin = { x: e.clientX, y: e.clientY, at: Date.now() }),
		{
			capture: true,
			passive: true
		}
	);

class Theme {
	choice = $state<ThemeChoice>('system');

	constructor() {
		try {
			const t = localStorage.getItem(KEY);
			if (t === 'light' || t === 'dark') this.choice = t;
		} catch {
			/* ignore */
		}
	}

	/** Changes the theme; the new one spreads in a circle from the click (unless motion is reduced). */
	set(choice: ThemeChoice) {
		if (choice === this.choice) return;
		const root = document.documentElement;
		if (!document.startViewTransition || reducedMotion()) return this.apply(choice);
		// From the click that chose it; from the middle for keyboard and older clicks.
		const recent = origin && Date.now() - origin.at < 1500 ? origin : null;
		const { x, y } = recent ?? { x: innerWidth / 2, y: innerHeight / 2 };
		origin = null;
		const r = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y));
		// Only the page as a whole takes part (no sliding covers or page fades meanwhile).
		root.classList.add('theme-vt');
		const vt = document.startViewTransition(() => this.apply(choice));
		vt.ready
			.then(() =>
				root.animate(
					{ clipPath: [`circle(0 at ${x}px ${y}px)`, `circle(${r}px at ${x}px ${y}px)`] },
					{
						duration: 560,
						easing: 'cubic-bezier(0.2, 0.8, 0.2, 1)',
						pseudoElement: '::view-transition-new(root)'
					}
				)
			)
			.catch(() => undefined);
		void vt.finished.finally(() => root.classList.remove('theme-vt'));
	}

	apply(choice: ThemeChoice) {
		this.choice = choice;
		const cl = document.documentElement.classList;
		cl.remove('th-light', 'th-dark');
		if (choice !== 'system') cl.add(`th-${choice}`);
		try {
			if (choice === 'system') localStorage.removeItem(KEY);
			else localStorage.setItem(KEY, choice);
		} catch {
			/* ignore */
		}
	}

	cycle() {
		const order: ThemeChoice[] = ['system', 'light', 'dark'];
		this.set(order[(order.indexOf(this.choice) + 1) % order.length]);
	}
}

export const theme = new Theme();
