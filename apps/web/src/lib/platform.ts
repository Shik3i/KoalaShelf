// Platform detection and the fetch implementation to use for API calls.
import type { FetchFn } from '@koalashelf/abs-client';

export interface ElectronBridge {
	isElectron: true;
	platform: string;
	storage: {
		getSecret: (key: string) => Promise<string | null>;
		setSecret: (key: string, value: string) => Promise<void>;
		deleteSecret: (key: string) => Promise<void>;
	};
	power: {
		inhibitSleep: (reason?: string) => Promise<number>;
		releaseSleep: (id: number) => Promise<void>;
	};
	window: {
		minimize: () => void;
		maximize: () => void;
		close: () => void;
		setAlwaysOnTop: (always: boolean) => void;
		openMiniPlayer: () => void;
	};
	dialog: {
		showSaveDialog: (options: {
			title?: string;
			defaultPath?: string;
			filters?: Array<{ name: string; extensions: string[] }>;
		}) => Promise<string | null>;
		saveFile: (options: {
			name: string;
			content: string;
			isBase64?: boolean;
			filters?: Array<{ name: string; extensions: string[] }>;
		}) => Promise<boolean>;
	};
	media: {
		updateState: (state: {
			title: string;
			author: string;
			coverUrl?: string;
			playing: boolean;
			position: number;
			duration: number;
		}) => void;
		onMediaCommand?: (
			callback: (cmd: 'play-pause' | 'skip-back' | 'skip-forward' | 'stop') => void
		) => () => void;
	};
}

export function getElectron(): ElectronBridge | null {
	if (typeof window !== 'undefined' && (window as unknown as { electron?: ElectronBridge }).electron) {
		return (window as unknown as { electron?: ElectronBridge }).electron!;
	}
	if (typeof globalThis !== 'undefined' && (globalThis as unknown as { electron?: ElectronBridge }).electron) {
		return (globalThis as unknown as { electron?: ElectronBridge }).electron!;
	}
	return null;
}

/** True inside the Electron desktop shell. */
export const isDesktop = typeof window !== 'undefined' && Boolean(getElectron());

/** Legacy guard: desktop is now on Chromium, so features are not restricted like Tauri was. */
export const isTauri = false;

/**
 * Secure token and secrets vault. Uses safeStorage (OS Keychain) in Electron desktop.
 */
export const vault = {
	async get(key: string): Promise<string | null> {
		const electron = getElectron();
		if (electron?.storage) {
			try {
				return await electron.storage.getSecret(key);
			} catch {
				return null;
			}
		}
		return null;
	},
	async set(key: string, value: string): Promise<void> {
		const electron = getElectron();
		if (electron?.storage) {
			try {
				await electron.storage.setSecret(key, value);
			} catch {
				/* ignore */
			}
		}
	},
	async delete(key: string): Promise<void> {
		const electron = getElectron();
		if (electron?.storage) {
			try {
				await electron.storage.deleteSecret(key);
			} catch {
				/* ignore */
			}
		}
	}
};

/**
 * API fetch implementation. In Electron desktop and browser, standard fetch is used.
 */
export function apiFetch(): Promise<FetchFn> {
	return Promise.resolve(globalThis.fetch.bind(globalThis));
}
