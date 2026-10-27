import { describe, expect, it } from 'vitest';
import type { LibraryItem, LibraryItemMinified } from '@koalashelf/abs-client';
import { coverSrc, view } from './items.ts';

describe('items view & coverSrc', () => {
	it('extracts fields from expanded LibraryItem', () => {
		const item = {
			id: 'b-1',
			updatedAt: 1000,
			media: {
				coverPath: null,
				duration: 3600,
				metadata: {
					title: 'Die drei ???',
					subtitle: 'Folge 1',
					authors: [{ id: 'a-1', name: 'Robert Arthur' }],
					narrators: ['Oliver Rohrbeck', 'Jens Wawrczeck'],
					series: [{ id: 's-1', name: 'Die drei ???', sequence: '1' }]
				}
			}
		} as unknown as LibraryItem;

		const v = view(item);
		expect(v.id).toBe('b-1');
		expect(v.title).toBe('Die drei ???');
		expect(v.author).toBe('Robert Arthur');
		expect(v.narrator).toBe('Oliver Rohrbeck, Jens Wawrczeck');
		expect(v.series).toBe('Die drei ??? #1');
		expect(v.duration).toBe(3600);
		expect(v.cover).toBeNull();
	});

	it('handles sparse/incomplete payloads without throwing', () => {
		const sparse = {
			id: 'b-sparse',
			media: {
				coverPath: '',
				metadata: {}
			}
		} as unknown as LibraryItemMinified;

		const v = view(sparse);
		expect(v.id).toBe('b-sparse');
		expect(v.title).toBe('Untitled');
		expect(v.author).toBe('');
		expect(v.narrator).toBe('');
		expect(v.series).toBe('');
		expect(v.duration).toBe(0);
		expect(coverSrc(sparse)).toBeNull();
	});
});
