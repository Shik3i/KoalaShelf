import { describe, expect, it } from 'vitest';
import type { LibraryItemMinified } from '@koalashelf/abs-client';
import { inSeriesOrder, sequenceIn } from './series-order.ts';

const book = (title: string, seriesName: string) =>
	({ id: title, media: { metadata: { title, seriesName } } }) as unknown as LibraryItemMinified;

describe('series order', () => {
	it('reads the number of the named series, also among several', () => {
		expect(sequenceIn(book('a', 'Der dunkle Turm #2'), 'Der dunkle Turm')).toBe(2);
		expect(sequenceIn(book('a', 'Andere #9, Der dunkle Turm #1.5'), 'Der dunkle Turm')).toBe(1.5);
		expect(sequenceIn(book('a', 'Die drei Fragezeichen'), 'Die drei Fragezeichen')).toBeNull();
	});

	it('sorts by number, then titles naturally (radio plays numbered in their titles)', () => {
		const list = [
			book('096/und der rote Rächer', 'Die drei Fragezeichen'),
			book('024/und die silberne Spinne', 'Die drei Fragezeichen'),
			book('100/Toteninsel', 'Die drei Fragezeichen'),
			book('Wolfsmond', 'Der dunkle Turm #5'),
			book('Drei', 'Der dunkle Turm #2')
		];
		expect(
			inSeriesOrder(list, 'Die drei Fragezeichen')
				.map((b) => b.id)
				.slice(0, 3)
		).toEqual(['024/und die silberne Spinne', '096/und der rote Rächer', '100/Toteninsel']);
		expect(
			inSeriesOrder(list, 'Der dunkle Turm')
				.map((b) => b.id)
				.slice(0, 2)
		).toEqual(['Drei', 'Wolfsmond']);
	});
});
