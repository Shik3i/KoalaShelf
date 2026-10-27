import { describe, it, expect } from 'vitest';
import { probeServer, unwrapServerUrl, ProbeError } from './server-probe.ts';

describe('server-probe', () => {
	it('unwraps proxied server URLs correctly', () => {
		expect(unwrapServerUrl('/_abs/https%3A%2F%2Faudio.example.org')).toBe(
			'https://audio.example.org'
		);
		expect(unwrapServerUrl('/_abs/https%3A%2F%2Faudio.example.org/status')).toBe(
			'https://audio.example.org'
		);
		expect(unwrapServerUrl('https://direct.example.org')).toBe('https://direct.example.org');
	});

	it('rejects insecure http:// URLs with ProbeError insecure-http', async () => {
		await expect(probeServer('http://insecure.example.com')).rejects.toThrowError(
			expect.objectContaining({ kind: 'insecure-http' })
		);
		await expect(probeServer('http://insecure.example.com')).rejects.toBeInstanceOf(ProbeError);
		await expect(probeServer('http://localhost:13378')).rejects.toThrowError(
			expect.objectContaining({ kind: 'insecure-http' })
		);
	});
});
