import { describe, it, expect } from 'vitest';
import { isPrivateIp, parseProxyRequest } from '../../docker/proxy.mjs';

describe('Dynamic Audiobookshelf Proxy', () => {
	it('correctly blocks private, loopback, link-local, and reserved IPs', () => {
		expect(isPrivateIp('127.0.0.1')).toBe(true);
		expect(isPrivateIp('127.255.255.254')).toBe(true);
		expect(isPrivateIp('10.0.0.1')).toBe(true);
		expect(isPrivateIp('172.16.0.1')).toBe(true);
		expect(isPrivateIp('172.31.255.255')).toBe(true);
		expect(isPrivateIp('192.168.1.1')).toBe(true);
		expect(isPrivateIp('169.254.169.254')).toBe(true); // AWS/cloud metadata
		expect(isPrivateIp('100.64.0.1')).toBe(true); // CGNAT
		expect(isPrivateIp('::1')).toBe(true);
		expect(isPrivateIp('fc00::1')).toBe(true);
		expect(isPrivateIp('fe80::1')).toBe(true);
		expect(isPrivateIp('::ffff:127.0.0.1')).toBe(true);
		expect(isPrivateIp('::ffff:192.168.1.100')).toBe(true);

		// Public internet addresses must be allowed
		expect(isPrivateIp('1.1.1.1')).toBe(false);
		expect(isPrivateIp('8.8.8.8')).toBe(false);
		expect(isPrivateIp('93.184.216.34')).toBe(false);
		expect(isPrivateIp('2606:4700:4700::1111')).toBe(false);
	});

	it('validates target URLs and Audiobookshelf route whitelist', () => {
		// Valid routes
		const r1 = parseProxyRequest('/_abs/https%3A%2F%2Faudio.shik3i.net/status');
		expect(r1).not.toBeNull();
		expect(r1?.targetUrl.href).toBe('https://audio.shik3i.net/status');

		const r2 = parseProxyRequest('/_abs/https%3A%2F%2Faudio.shik3i.net/login');
		expect(r2).not.toBeNull();
		expect(r2?.targetUrl.href).toBe('https://audio.shik3i.net/login');

		const r3 = parseProxyRequest(
			'/_abs/https%3A%2F%2Faudio.shik3i.net/api/items/book-123/file/track-1'
		);
		expect(r3).not.toBeNull();
		expect(r3?.targetUrl.href).toBe('https://audio.shik3i.net/api/items/book-123/file/track-1');

		// Invalid routes (non-ABS or illegal protocols)
		expect(parseProxyRequest('/_abs/ftp%3A%2F%2Fevil.com/status')).toBeNull();
		expect(parseProxyRequest('/_abs/https%3A%2F%2Fevil.com/etc/passwd')).toBeNull();
		expect(parseProxyRequest('/_abs/https%3A%2F%2Fevil.com/admin/secrets')).toBeNull();
		expect(parseProxyRequest('/_abs/not-a-url/status')).toBeNull();
		expect(parseProxyRequest('/status')).toBeNull();
	});
});
