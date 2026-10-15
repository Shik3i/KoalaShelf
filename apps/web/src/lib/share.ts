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

/** Shares a book's page (system share sheet on phones, otherwise the clipboard). */
export async function shareBook(itemId: string, title: string) {
	const url = `${location.origin}/book/${itemId}`;
	if (navigator.share && matchMedia('(pointer: coarse)').matches) {
		await navigator.share({ title, url }).catch(() => undefined);
		return;
	}
	try {
		await navigator.clipboard.writeText(url);
		toasts.show(i18n.m.toast.linkCopied);
	} catch {
		/* clipboard blocked: nothing to do */
	}
}
