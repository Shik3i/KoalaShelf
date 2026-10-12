import type { Attachment } from 'svelte/attachments';

/** Calls `fn` whenever the element scrolls into view (infinite lists). */
export function onVisible(fn: () => void, rootMargin = '600px'): Attachment<HTMLElement> {
	return (el) => {
		const io = new IntersectionObserver(
			(entries) => entries.some((e) => e.isIntersecting) && fn(),
			{
				rootMargin
			}
		);
		io.observe(el);
		return () => io.disconnect();
	};
}
