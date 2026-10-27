// Live updates from the server (socket.io): progress made on other devices shows up at once.
// The client library is only downloaded after sign-in.
import type { MediaProgress } from '@koalashelf/abs-client';
import { session } from './session.svelte.ts';
import { progress } from './progress.svelte.ts';
import { player } from './player/player.svelte.ts';

interface ProgressEvent {
	id: string;
	sessionId: string | null;
	data: MediaProgress;
}

let socket: { disconnect(): void } | null = null;
let starting: Promise<void> | null = null;

export function startLive() {
	if (socket || starting || !session.client) return;
	const api = session.api;
	starting = (async () => {
		const { io } = await import('socket.io-client');
		if (session.client !== api) return;
		const isSubpath = api.baseUrl.startsWith('/');
		const s = io(isSubpath ? undefined : api.baseUrl || undefined, {
			path: `${isSubpath ? api.baseUrl : ''}/socket.io`,
			transports: ['websocket']
		});
		let retried = false;

		s.on('connect', () => s.emit('auth', api.accessToken));
		// Access tokens are short-lived: refresh once and try again.
		s.on('auth_failed', async () => {
			if (retried) return;
			retried = true;
			if (await api.refresh()) s.emit('auth', api.accessToken);
		});
		s.on('init', () => (retried = false));
		s.on('user_item_progress_updated', (e: ProgressEvent) => {
			if (!e?.data || e.data.episodeId) return;
			if (!progress.merge(e.data)) return;
			// Our own syncs come back as events too; only other devices move the position.
			if (e.sessionId && e.sessionId === player.sessionId) return;
			player.applyRemoteProgress(e.data);
		});
		socket = s;
	})().finally(() => (starting = null));
}

export function stopLive() {
	socket?.disconnect();
	socket = null;
}

session.onSignOut(stopLive);
