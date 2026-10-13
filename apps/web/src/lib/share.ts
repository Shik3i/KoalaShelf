import { toasts } from './toast.svelte.ts';
import { i18n } from './i18n/index.svelte.ts';

/** Copies a link that opens the book at this moment (e.g. /book/abc?t=754). */
export async function copyMomentLink(itemId: string, seconds: number) {
	const url = `${location.origin}/book/${itemId}?t=${Math.floor(seconds)}`;
	try {
		await navigator.clipboard.writeText(url);
		toasts.show(i18n.m.toast.linkCopied);
	} catch {
		/* clipboard blocked: nothing to do */
	}
}
