// Theme: follows the system by default, manual override stored locally (see app.html).
export type ThemeChoice = 'system' | 'light' | 'dark';

const KEY = 'ks.theme';

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

	set(choice: ThemeChoice) {
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
