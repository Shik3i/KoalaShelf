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
});
