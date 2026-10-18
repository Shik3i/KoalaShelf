// PWA App Badge API integration: shows remaining chapters or goal status on the app icon.

export function setBadge(count?: number): void {
	if (typeof navigator === 'undefined' || !('setAppBadge' in navigator)) return;
	try {
		if (count !== undefined && count > 0) {
			void navigator.setAppBadge(Math.round(count)).catch(() => undefined);
		} else {
			void navigator.clearAppBadge().catch(() => undefined);
		}
	} catch {
		// Ignore if badges are blocked or unsupported
	}
}

export function clearBadge(): void {
	if (typeof navigator === 'undefined' || !('clearAppBadge' in navigator)) return;
	try {
		void navigator.clearAppBadge().catch(() => undefined);
	} catch {
		// Ignore
	}
}
