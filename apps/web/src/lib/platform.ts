// Platform detection and the fetch implementation to use for API calls.
import type { FetchFn } from '@koalashelf/abs-client';

/** True inside the Tauri desktop shell. */
export const isTauri = typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window;

let tauriFetch: Promise<FetchFn> | null = null;

/**
 * In the desktop app, API requests go through Rust (tauri-plugin-http), so the server
 * needs no CORS configuration. The plugin is only loaded there (separate chunk).
 */
export function apiFetch(): Promise<FetchFn> {
	if (!isTauri) return Promise.resolve(globalThis.fetch.bind(globalThis));
	tauriFetch ??= import('@tauri-apps/plugin-http').then((m) => m.fetch as FetchFn);
	return tauriFetch;
}
