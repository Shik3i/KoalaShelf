import { describe, expect, it } from 'vitest';
import { byId, unique } from './unique.ts';

describe('unique', () => {
	it('keeps the first of each key, in order', () => {
		expect(unique(['a', 'b', 'a', 'c', 'b'])).toEqual(['a', 'b', 'c']);
		expect(
			unique(
				[
					{ id: '1', n: 1 },
					{ id: '2', n: 2 },
					{ id: '1', n: 3 }
				],
				byId
			)
		).toEqual([
			{ id: '1', n: 1 },
			{ id: '2', n: 2 }
		]);
	});

	it('copes with missing lists', () => {
		expect(unique(undefined)).toEqual([]);
		expect(unique(null)).toEqual([]);
	});
});
