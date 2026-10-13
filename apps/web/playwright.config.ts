import { defineConfig } from '@playwright/test';

// End-to-end tests against the local Audiobookshelf from dev/ (see dev/README.md).
// Edge is used because it plays AAC/m4b (the bundled Chromium lacks proprietary codecs).
// E2E_BASE_URL runs the suite against a production build (e.g. the Docker image) instead
// of the dev server; specs that inspect dev modules skip themselves there.
const external = process.env.E2E_BASE_URL;

const use = {
	baseURL: external ?? 'http://localhost:5174',
	channel: 'msedge',
	locale: 'en-US',
	viewport: { width: 1280, height: 800 },
	trace: 'retain-on-failure',
	screenshot: 'only-on-failure'
} as const;

export default defineConfig({
	testDir: 'e2e',
	workers: 1,
	fullyParallel: false,
	timeout: 60_000,
	expect: { timeout: 10_000 },
	reporter: [['list']],
	globalSetup: './e2e/global-setup.ts',
	projects: [
		// Signs in once and stores the session (cookie + local storage) for the other tests.
		{ name: 'auth', testMatch: /auth\.setup\.ts/, use },
		{ name: 'signin', testMatch: /signin\.spec\.ts/, use },
		{
			name: 'app',
			testIgnore: [/signin\.spec\.ts/, /auth\.setup\.ts/],
			dependencies: ['auth'],
			use: { ...use, storageState: 'e2e/.auth/user.json' }
		}
	],
	webServer: external
		? undefined
		: {
				command: 'pnpm exec vite dev --port 5174 --strictPort',
				port: 5174,
				reuseExistingServer: true,
				env: { ABS_URL: 'http://localhost:13378' }
			}
});
