import { defineConfig } from 'vitest/config';
import { loadEnv } from 'vite';
import adapter from '@sveltejs/adapter-static';
import { sveltekit } from '@sveltejs/kit/vite';

// Dev only: proxy the Audiobookshelf API so the app runs same-origin (no CORS, cookie mode).
// ABS_URL comes from the environment or dev/.env (see dev/README.md).
// Regex keys with exact segments, so app routes like /authors are never proxied.
const ABS_PATHS = ['^/(api|auth|hls)/', '^/socket\\.io/', '^/(login|logout|status)$'];

export default defineConfig(({ mode }) => {
	const env = { ...loadEnv(mode, '../../dev', 'ABS_'), ...loadEnv(mode, process.cwd(), 'ABS_') };
	const target = process.env.ABS_URL ?? env.ABS_URL ?? 'http://localhost:13378';

	return {
		plugins: [
			{
				// Mirrors the Docker image: tells the app which Audiobookshelf server the proxy serves.
				name: 'koalashelf-preset-server',
				configureServer(server) {
					server.middlewares.use('/koalashelf.json', (_req, res) => {
						res.setHeader('Content-Type', 'application/json');
						res.end(JSON.stringify({ server: target }));
					});
				}
			},
			sveltekit({
				compilerOptions: {
					// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
					runes: ({ filename }) =>
						filename.split(/[/\\]/).includes('node_modules') ? undefined : true
				},
				adapter: adapter({ fallback: 'index.html' }),
				// Registered in +layout.svelte (skipped in the desktop app).
				serviceWorker: { register: false },
				// Strict CSP: SvelteKit hashes its own inline bootstrap script.
				csp: {
					mode: 'hash',
					directives: {
						'default-src': ['self'],
						'script-src': ['self'],
						'style-src': ['self', 'unsafe-inline'],
						'img-src': ['self', 'data:', 'blob:', 'https:', 'http:'],
						'media-src': ['self', 'blob:', 'https:', 'http:'],
						'connect-src': ['self', 'https:', 'http:', 'wss:', 'ws:', 'ipc:'],
						'font-src': ['self', 'data:'],
						'worker-src': ['self', 'blob:'],
						'manifest-src': ['self'],
						'object-src': ['none'],
						'base-uri': ['self'],
						'form-action': ['self']
					}
				}
			})
		],
		server: {
			proxy: Object.fromEntries(
				ABS_PATHS.map((p) => [p, { target, changeOrigin: true, ws: p.includes('socket') }])
			)
		},
		build: { target: 'es2024' },
		test: {
			expect: { requireAssertions: true },
			projects: [
				{
					extends: './vite.config.ts',
					test: {
						name: 'unit',
						environment: 'node',
						include: ['src/**/*.{test,spec}.{js,ts}']
					}
				}
			]
		}
	};
});
