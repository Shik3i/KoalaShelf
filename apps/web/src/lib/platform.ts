// Platform detection and the fetch implementation to use for API calls.
import type { FetchFn } from '@koalashelf/abs-client';

/** True inside the Electron desktop shell. */
export const isDesktop =
	typeof window !== 'undefined' &&
	Boolean((window as unknown as { electron?: unknown }).electron);

/** Alias for backward compatibility across components. */
export const isTauri = isDesktop;

/**
 * API fetch implementation. In Electron desktop and browser, standard fetch is used.
 */
export function apiFetch(): Promise<FetchFn> {
	return Promise.resolve(globalThis.fetch.bind(globalThis));
}

