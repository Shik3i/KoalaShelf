import { describe, it, expect } from 'vitest';
import { probeServer, unwrapServerUrl, ProbeError, normalizeServerInput } from './server-probe.ts';

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

	it('normalizes server addresses defaulting to https for remote and http for loopback', () => {
		expect(normalizeServerInput('audio.example.org')).toBe('https://audio.example.org');
		expect(normalizeServerInput('127.0.0.1:13378')).toBe('http://127.0.0.1:13378');
		expect(normalizeServerInput('localhost:8080')).toBe('http://localhost:8080');
		expect(normalizeServerInput('https://already.ssl.com')).toBe('https://already.ssl.com');
	});

	it('rejects insecure http:// URLs with ProbeError insecure-http for remote hosts', async () => {
		await expect(probeServer('http://insecure.example.com')).rejects.toThrowError(
			expect.objectContaining({ kind: 'insecure-http' })
		);
		await expect(probeServer('http://insecure.example.com')).rejects.toBeInstanceOf(ProbeError);
		await expect(probeServer('http://audiobookshelf.other-domain.net:8080')).rejects.toThrowError(
			expect.objectContaining({ kind: 'insecure-http' })
		);
	});
});
