import { defineConfig } from 'vitest/config';
import { loadEnv } from 'vite';
import adapter from '@sveltejs/adapter-static';
import { sveltekit } from '@sveltejs/kit/vite';

// Dev only: proxy the Audiobookshelf API so the app runs same-origin (no CORS, cookie mode).
// ABS_URL comes from the environment or dev/.env (see dev/README.md).
const ABS_PATHS = ['/api', '/login', '/logout', '/auth', '/status', '/socket.io', '/hls'];

export default defineConfig(({ mode }) => {
	const env = { ...loadEnv(mode, '../../dev', 'ABS_'), ...loadEnv(mode, process.cwd(), 'ABS_') };
	const target = process.env.ABS_URL ?? env.ABS_URL ?? 'http://localhost:13378';

	return {
		plugins: [
			sveltekit({
				compilerOptions: {
					// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
					runes: ({ filename }) =>
						filename.split(/[/\\]/).includes('node_modules') ? undefined : true
				},
				adapter: adapter({ fallback: 'index.html' })
			})
		],
		server: {
			proxy: Object.fromEntries(
				ABS_PATHS.map((p) => [p, { target, changeOrigin: true, ws: p === '/socket.io' }])
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
