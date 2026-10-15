import { describe, expect, it } from 'vitest';
import { describeAgent } from './device.ts';

describe('describeAgent', () => {
	it('names browsers and systems', () => {
		expect(
			describeAgent(
				'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36 Edg/155.0.0.0'
			)
		).toEqual({ app: 'Edge', system: 'Windows' });
		expect(
			describeAgent(
				'Mozilla/5.0 (iPhone; CPU iPhone OS 19_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/19.0 Mobile/15E148 Safari/604.1'
			)
		).toEqual({ app: 'Safari', system: 'iOS' });
		expect(
			describeAgent('Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0')
		).toEqual({ app: 'Firefox', system: 'Linux' });
		expect(
			describeAgent(
				'Mozilla/5.0 (Linux; Android 16; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0 Mobile Safari/537.36'
			)
		).toEqual({ app: 'Chrome', system: 'Android' });
	});

	it('recognizes the mobile apps and tools', () => {
		expect(describeAgent('okhttp/4.12.0').app).toBe('Audiobookshelf app');
		expect(describeAgent('curl/8.17.0')).toEqual({ app: 'curl', system: '' });
		expect(describeAgent(null)).toEqual({ app: '', system: '' });
	});
});
