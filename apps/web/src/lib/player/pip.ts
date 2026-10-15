// Pop-out player: a small always-on-top window (Document Picture-in-Picture, Chromium desktop).
import { mount, unmount } from 'svelte';
import PipPlayer from './PipPlayer.svelte';

interface DocumentPip {
	requestWindow(options?: { width?: number; height?: number }): Promise<Window>;
	window: Window | null;
}

const api = () =>
	(globalThis as unknown as { documentPictureInPicture?: DocumentPip }).documentPictureInPicture;

export const pipAvailable = () => typeof window !== 'undefined' && !!api();

/** Opens the pop-out player, or closes it when it is already open. */
export async function togglePip() {
	const pip = api();
	if (!pip) return;
	if (pip.window) return pip.window.close();
	const win = await pip.requestWindow({ width: 340, height: 220 });
	const doc = win.document;
	// Same styles and theme as the app.
	for (const sheet of document.styleSheets) {
		try {
			const style = doc.createElement('style');
			style.textContent = [...sheet.cssRules].map((r) => r.cssText).join('\n');
			doc.head.append(style);
		} catch {
			if (sheet.href) {
				const link = doc.createElement('link');
				link.rel = 'stylesheet';
				link.href = sheet.href;
				doc.head.append(link);
			}
		}
	}
	const html = document.documentElement;
	doc.documentElement.className = html.className;
	for (const { name, value } of html.attributes)
		if (name.startsWith('data-') || name === 'lang') doc.documentElement.setAttribute(name, value);
	doc.title = 'KoalaShelf';
	const app = mount(PipPlayer, { target: doc.body });
	win.addEventListener('pagehide', () => void unmount(app));
}
