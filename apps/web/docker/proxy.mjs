// @ts-nocheck
// Dynamic, SSRF-protected Audiobookshelf reverse proxy for multi-server web deployments.
// Listens on 127.0.0.1:8081 behind Caddy and proxies /_abs/<encoded-origin>/<abs-path>
// so browser clients can connect to any external Audiobookshelf server without CORS errors.
import http from 'node:http';
import https from 'node:https';
import dns from 'node:dns';
import net from 'node:net';
import { pathToFileURL } from 'node:url';

const PORT = Number(process.env.ABS_PROXY_PORT || 8081);
const HOST = process.env.ABS_PROXY_HOST || '127.0.0.1';
const ALLOW_PRIVATE = process.env.ABS_ALLOW_PRIVATE_PROXY === 'true';

const ALLOWED_PATH = /^\/(status|login|logout)(\?|$)|^\/(api|auth|hls|public|feed|socket\.io)\//;
const IMAGE_PATH = /^\/api\/(items\/[^/]+\/cover|authors\/[^/]+\/image)(\?|$)/;

const REQ_HEADERS = [
	'authorization',
	'content-type',
	'content-length',
	'accept',
	'accept-encoding',
	'range',
	'if-range',
	'if-none-match',
	'if-modified-since',
	'x-return-tokens',
	'x-refresh-token',
	'user-agent'
];

const RES_HEADERS = [
	'content-type',
	'content-length',
	'content-encoding',
	'content-range',
	'accept-ranges',
	'etag',
	'last-modified',
	'content-disposition'
];

/** Returns true if an IPv4 or IPv6 address belongs to a loopback, private, link-local, or reserved range. */
export function isPrivateIp(ip) {
	const clean = ip
		.trim()
		.toLowerCase()
		.replace(/^\[|\]$/g, '');
	if (clean.startsWith('::ffff:')) {
		const v4 = clean.slice(7);
		if (net.isIPv4(v4)) return isPrivateIp(v4);
	}
	if (net.isIPv4(clean)) {
		const [a, b, c] = clean.split('.').map(Number);
		if (a === 0 || a === 10 || a === 127) return true;
		if (a === 100 && b >= 64 && b <= 127) return true; // CGNAT / Tailscale
		if (a === 169 && b === 254) return true; // Link-local / Cloud metadata
		if (a === 172 && b >= 16 && b <= 31) return true; // Private 172.16.0.0/12
		if (a === 192 && b === 168) return true; // Private 192.168.0.0/16
		if (a === 192 && b === 0 && (c === 0 || c === 2)) return true;
		if (a === 198 && (b === 18 || b === 19 || (b === 51 && c === 100))) return true;
		if (a === 203 && b === 0 && c === 113) return true;
		if (a >= 224) return true; // Multicast & reserved
		return false;
	}
	if (net.isIPv6(clean)) {
		if (
			clean === '::' ||
			clean === '::1' ||
			clean === '0:0:0:0:0:0:0:1' ||
			clean === '0:0:0:0:0:0:0:0'
		)
			return true;
		if (clean.startsWith('fc') || clean.startsWith('fd')) return true; // Unique local fc00::/7
		if (/^fe[89ab]/.test(clean)) return true; // Link-local fe80::/10
		if (clean.startsWith('ff')) return true; // Multicast ff00::/8
		if (clean.startsWith('2001:db8:')) return true; // Documentation
		return false;
	}
	return true;
}

/** Parses `/_abs/<encoded-base-url>/<rest>` and validates protocol + Audiobookshelf route whitelist. */
export function parseProxyRequest(rawUrl) {
	if (!rawUrl || !rawUrl.startsWith('/_abs/')) return null;
	const withoutPrefix = rawUrl.slice('/_abs/'.length);
	const slashIdx = withoutPrefix.indexOf('/');
	const encodedBase = slashIdx >= 0 ? withoutPrefix.slice(0, slashIdx) : withoutPrefix;
	const restPath = slashIdx >= 0 ? withoutPrefix.slice(slashIdx) : '/';

	if (!ALLOWED_PATH.test(restPath)) return null;

	let baseStr;
	try {
		baseStr = decodeURIComponent(encodedBase);
	} catch {
		return null;
	}

	let base;
	try {
		base = new URL(baseStr);
	} catch {
		return null;
	}

	if (base.protocol !== 'http:' && base.protocol !== 'https:') return null;
	if (base.username || base.password) return null;

	const basePath = base.pathname.replace(/\/+$/, '');
	const targetUrl = new URL(`${basePath}${restPath}`, base.origin);
	return { targetUrl, restPath };
}

/** Custom DNS lookup that blocks private/internal IPs before TCP connection (prevents SSRF & DNS rebinding). */
function safeLookup(hostname, options, callback) {
	const cb = typeof options === 'function' ? options : callback;
	const opts = typeof options === 'object' && options !== null ? options : {};

	if (!ALLOW_PRIVATE && hostname.toLowerCase() === 'localhost') {
		return cb(new Error('Blocked private hostname'));
	}

	dns.lookup(hostname, { ...opts, all: true }, (err, addresses) => {
		if (err) return cb(err);
		const list = Array.isArray(addresses)
			? addresses
			: [{ address: addresses, family: net.isIP(addresses) }];
		if (!list.length) return cb(new Error('No DNS records found'));
		if (!ALLOW_PRIVATE) {
			for (const entry of list) {
				if (isPrivateIp(entry.address)) {
					return cb(new Error(`Blocked private IP address: ${entry.address}`));
				}
			}
		}
		if (opts.all) return cb(null, list);
		return cb(null, list[0].address, list[0].family);
	});
}

export function createProxyServer() {
	const server = http.createServer((req, res) => {
		const parsed = parseProxyRequest(req.url ?? '');
		if (!parsed) {
			res.writeHead(404, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
			res.end(JSON.stringify({ error: 'Not found' }));
			return;
		}

		const { targetUrl, restPath } = parsed;
		const isHttps = targetUrl.protocol === 'https:';
		const transport = isHttps ? https : http;

		const headers = { host: targetUrl.host };
		for (const h of REQ_HEADERS) {
			const val = req.headers[h];
			if (val !== undefined) headers[h] = val;
		}

		const upstreamReq = transport.request(
			targetUrl,
			{
				method: req.method,
				headers,
				lookup: safeLookup,
				timeout: 30_000
			},
			(upstreamRes) => {
				const outHeaders = {};
				for (const h of RES_HEADERS) {
					const val = upstreamRes.headers[h];
					if (val !== undefined) outHeaders[h] = val;
				}
				const isImage =
					(req.method === 'GET' || req.method === 'HEAD') && IMAGE_PATH.test(restPath);
				if (isImage) {
					outHeaders['cache-control'] =
						upstreamRes.headers['cache-control'] || 'public, max-age=3600';
				} else {
					outHeaders['cache-control'] = 'no-store';
				}
				res.writeHead(upstreamRes.statusCode || 502, outHeaders);
				upstreamRes.pipe(res);
			}
		);

		upstreamReq.on('timeout', () => {
			upstreamReq.destroy(new Error('Upstream timeout'));
		});

		upstreamReq.on('error', (err) => {
			if (res.headersSent) {
				res.destroy();
				return;
			}
			const isBlocked = /Blocked private/.test(err.message);
			res.writeHead(isBlocked ? 403 : 502, {
				'Content-Type': 'application/json',
				'Cache-Control': 'no-store'
			});
			res.end(
				JSON.stringify({
					error: isBlocked ? 'Target address is not allowed' : 'Upstream server unreachable'
				})
			);
		});

		req.pipe(upstreamReq);
	});

	server.on('upgrade', (req, clientSocket, head) => {
		const parsed = parseProxyRequest(req.url ?? '');
		if (!parsed || !parsed.restPath.startsWith('/socket.io/')) {
			clientSocket.destroy();
			return;
		}

		const { targetUrl } = parsed;
		const isHttps = targetUrl.protocol === 'https:';
		const transport = isHttps ? https : http;

		const headers = {
			host: targetUrl.host,
			connection: 'Upgrade',
			upgrade: req.headers['upgrade'] || 'websocket'
		};
		for (const h of [
			'sec-websocket-key',
			'sec-websocket-version',
			'sec-websocket-extensions',
			'sec-websocket-protocol',
			'user-agent'
		]) {
			if (req.headers[h]) headers[h] = req.headers[h];
		}

		const upstreamReq = transport.request(targetUrl, {
			method: 'GET',
			headers,
			lookup: safeLookup,
			timeout: 15_000
		});

		upstreamReq.on('upgrade', (upstreamRes, upstreamSocket, upstreamHead) => {
			let handshake = `HTTP/1.1 ${upstreamRes.statusCode || 101} ${upstreamRes.statusMessage || 'Switching Protocols'}\r\n`;
			for (let i = 0; i < upstreamRes.rawHeaders.length; i += 2) {
				const k = upstreamRes.rawHeaders[i];
				const v = upstreamRes.rawHeaders[i + 1];
				if (k.toLowerCase() === 'set-cookie') continue;
				handshake += `${k}: ${v}\r\n`;
			}
			handshake += '\r\n';
			clientSocket.write(handshake);
			if (upstreamHead?.length) clientSocket.write(upstreamHead);
			if (head?.length) upstreamSocket.write(head);
			upstreamSocket.pipe(clientSocket);
			clientSocket.pipe(upstreamSocket);
			upstreamSocket.on('error', () => clientSocket.destroy());
			clientSocket.on('error', () => upstreamSocket.destroy());
		});

		upstreamReq.on('response', () => clientSocket.destroy());
		upstreamReq.on('error', () => clientSocket.destroy());
		upstreamReq.end();
	});

	return server;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
	const srv = createProxyServer();
	srv.listen(PORT, HOST, () => {
		console.log(`KoalaShelf dynamic ABS proxy listening on http://${HOST}:${PORT}`);
	});
}
