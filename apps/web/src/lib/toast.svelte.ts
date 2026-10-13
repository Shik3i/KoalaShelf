// Small, unobtrusive confirmations with an optional action (e.g. undo).
export interface Toast {
	id: number;
	text: string;
	action?: { label: string; run: () => void };
}

class Toasts {
	list = $state<Toast[]>([]);
	#next = 1;

	show(text: string, action?: Toast['action'], ms = action ? 6000 : 3000) {
		const id = this.#next++;
		// At most three at a time; the oldest goes first.
		this.list = [...this.list.slice(-2), { id, text, action }];
		setTimeout(() => this.dismiss(id), ms);
	}

	dismiss(id: number) {
		this.list = this.list.filter((t) => t.id !== id);
	}
}

export const toasts = new Toasts();
