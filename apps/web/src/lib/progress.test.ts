import { describe, expect, it } from 'vitest';
import { progress } from './progress.svelte.ts';
import type { MediaProgress } from '@koalashelf/abs-client';

describe('progress store', () => {
	it('accepts remote finished progress even if local progress was recorded earlier', () => {
		const localProgress: MediaProgress = {
			id: 'p-1',
			libraryItemId: 'book-knut-hansen',
			episodeId: null,
			duration: 1000,
			progress: 0.75,
			currentTime: 750,
			isFinished: false,
			hideFromContinueListening: false,
			lastUpdate: 100,
			startedAt: 10,
			finishedAt: null
		};

		// Local listening sets 75%
		progress.set(localProgress);
		expect(progress.get('book-knut-hansen')?.currentTime).toBe(750);
		expect(progress.status('book-knut-hansen')).toBe('running');

		// Remote device finished the book (isFinished: true)
		const remoteFinished: MediaProgress = {
			id: 'p-1',
			libraryItemId: 'book-knut-hansen',
			episodeId: null,
			duration: 1000,
			progress: 1,
			currentTime: 1000,
			isFinished: true,
			hideFromContinueListening: false,
			lastUpdate: 200,
			startedAt: 10,
			finishedAt: 200
		};

		const merged = progress.merge(remoteFinished);
		expect(merged).toBe(true);
		expect(progress.get('book-knut-hansen')?.isFinished).toBe(true);
		expect(progress.status('book-knut-hansen')).toBe('done');
	});

	it('returns correct status for items', () => {
		expect(progress.status('unknown-book')).toBe('new');

		progress.set({
			id: 'p-2',
			libraryItemId: 'book-in-progress',
			episodeId: null,
			duration: 500,
			progress: 0.2,
			currentTime: 100,
			isFinished: false,
			hideFromContinueListening: false,
			lastUpdate: 50,
			startedAt: 10,
			finishedAt: null
		});
		expect(progress.status('book-in-progress')).toBe('running');
	});
});
