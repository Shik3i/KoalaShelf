import { describe, expect, it } from 'vitest';
import { activeCount, parseQuery, plan } from './library-query.ts';

const q = (s: string) => parseQuery(new URLSearchParams(s));

describe('library query', () => {
	it('reads repeated values and defaults', () => {
		const x = q('genre=Krimi&genre=Roman&narrator=Paul%20Dahm&length=lt2');
		expect(x.values.genre).toEqual(['Krimi', 'Roman']);
		expect(x.values.narrator).toEqual(['Paul Dahm']);
		expect(x.length).toBe('lt2');
		expect(x.sort).toBe('title');
		expect(x.desc).toBe(false);
	});

	it('ignores unknown values', () => {
		const x = q('status=weird&length=forever&sort=nope');
		expect(x.status).toBe('');
		expect(x.length).toBe('');
		expect(x.sort).toBe('title');
	});

	it('orders a series by volume and never groups inside one', () => {
		const x = q('series=abc&group=1');
		expect(x.sort).toBe('sequence');
		expect(x.group).toBe(false);
	});

	it('pages through the server for one filter or none', () => {
		expect(plan(q('')).mode).toBe('paged');
		expect(plan(q('genre=Krimi')).mode).toBe('paged');
		expect(plan(q('status=finished')).mode).toBe('paged');
		expect(plan(q('sort=added&desc=1')).mode).toBe('paged');
	});

	it('computes an exact set for anything combined', () => {
		expect(plan(q('genre=Krimi&genre=Roman')).mode).toBe('set');
		expect(plan(q('genre=Krimi&status=finished')).mode).toBe('set');
		expect(plan(q('series=a&author=b')).mode).toBe('set');
		expect(plan(q('length=lt2')).mode).toBe('set');
		expect(plan(q('q=lindq')).mode).toBe('set');
		expect(plan(q('sort=random')).mode).toBe('set');
	});

	it('counts active filters', () => {
		expect(activeCount(q('genre=A&genre=B&author=x&status=finished&q=z&length=lt2'))).toBe(6);
	});

	it('supports new duration ranges like lt4, lt8, 4-8', () => {
		expect(q('length=lt4').length).toBe('lt4');
		expect(q('length=lt8').length).toBe('lt8');
		expect(q('length=4-8').length).toBe('4-8');
		expect(q('length=8-16').length).toBe('8-16');
	});
});

describe('parseDurationQuery', () => {
	it('parses natural language under expressions', async () => {
		const { parseDurationQuery } = await import('./library-query.ts');
		const res1 = parseDurationQuery('unter 8 stunden');
		expect(res1.maxSeconds).toBe(8 * 3600);
		expect(res1.cleanQuery).toBe('');

		const res2 = parseDurationQuery('harry potter <4h');
		expect(res2.maxSeconds).toBe(4 * 3600);
		expect(res2.cleanQuery).toBe('harry potter');

		const res3 = parseDurationQuery('nur unter 8 std');
		expect(res3.maxSeconds).toBe(8 * 3600);
	});

	it('parses over and range expressions', async () => {
		const { parseDurationQuery } = await import('./library-query.ts');
		const res1 = parseDurationQuery('sci-fi >12h');
		expect(res1.minSeconds).toBe(12 * 3600);
		expect(res1.cleanQuery).toBe('sci-fi');

		const res2 = parseDurationQuery('4-8h');
		expect(res2.minSeconds).toBe(4 * 3600);
		expect(res2.maxSeconds).toBe(8 * 3600);
		expect(res2.cleanQuery).toBe('');
	});
});
