# Tech stack

Versions verified on 2026-10-12 (web extras and test tools on 2026-10-13) against npm, Maven Central, Google Maven, crates.io, nodejs.org and services.gradle.org. Rule: current stable releases, no alpha/beta/RC. pnpm's `minimumReleaseAge` supply-chain policy stays on, so packages published within the last day are skipped.

## Runtime and tools

| Tool | Version | Note |
|---|---|---|
| Node.js | 26.x | LTS from late October 2026; 24 LTS works too. Node 25 is end of life. |
| pnpm | 12.11 | pinned via `packageManager` |
| Rust | 1.99 stable | desktop (phase 5) |
| JDK | 21 (Temurin / Android Studio) | Android ([`apps/android`](../apps/android)) |

## Web and packages

| Package | Version | Note |
|---|---|---|
| SvelteKit | 3.1 | SPA via `adapter-static`, config lives in the Vite plugin |
| Svelte | 5.57 | runes, attachments |
| Vite | 8.3 | Rolldown |
| TypeScript | **6.0** | TypeScript 7 (native compiler) is out, but SvelteKit 3, svelte-check and typescript-eslint support ≤ 6 so far (tried again 2026-10-16: `svelte-kit sync` and ESLint fail). Switch once they do; Dependabot skips TypeScript majors until then. |
| Vitest | 5.0 | |
| ESLint / Prettier | 10 / 3.9 | flat config, prettier-plugin-svelte |
| Style Dictionary | 5.6 | DTCG token format, custom formats |
| Icons | `@lucide/svelte` 1.57 | per-icon imports (tree-shaken) |
| Fonts | `@fontsource/gloock`, `@fontsource-variable/manrope` | self-hosted, OFL |
| PWA | SvelteKit service worker + manifest | `@vite-pwa/sveltekit` does not support SvelteKit 3 yet |
| Icons build | sharp 0.35 | renders PWA icons from SVG (`pnpm icons`) |
| Streaming fallback | hls.js 1.7 | loaded only when the server transcodes to HLS |
| Live updates | socket.io-client 4.8 | loaded after sign-in, progress from other devices |
| Hosting | Caddy 2.11 (Docker) | static files + same-origin proxy to Audiobookshelf, CSP and caching headers |

### Testing and audits

| Tool | Version | Note |
|---|---|---|
| Playwright | 1.64 | end-to-end in Microsoft Edge (AAC/M4B playback), also against the production image |
| axe-core (`@axe-core/playwright`) | 4.13 | WCAG 2.2 AA and best-practice rules |
| Lighthouse | 13.5 | run on demand against the production image; not a project dependency |
| Audiobookshelf | 2.37.1 | local test server in Docker (`dev/`) |
| GitHub Actions | — | CI on every push and pull request: `pnpm audit`, types, lint, unit tests, build, the Docker image (starts, serves, security headers) and the full end-to-end suite in Edge against a fresh Audiobookshelf with generated sample books; actions pinned to commit hashes |
| CodeQL | 4.39 | JavaScript/TypeScript and workflow analysis (security-extended) on every push, pull request and weekly |
| Dependabot | — | security updates immediately; weekly grouped version updates for npm, Cargo, Docker, Compose and Actions, with a 3-day cooldown for fresh releases |

### i18n

Own dependency-free, type-safe solution in `apps/web/src/lib/i18n/`: English is the base dictionary, other locales must match its shape (missing keys fail the type check). Paraglide was considered, but its build loads plugins from a CDN, which conflicts with offline, self-hosted builds. The locale follows the browser and can be switched manually; the choice is stored locally only.

## Desktop

| Package | Version | Note |
|---|---|---|
| Electron | 45.x | Hardened runtime with bundled Chromium (guaranteed AAC/M4B codecs) |
| electron-builder | 26.x | Multi-platform packaging (NSIS, MSI, DMG, AppImage, deb, rpm) |

## Android

| Library | Version | Note |
|---|---|---|
| Android Gradle Plugin | 9.4.1 | built-in Kotlin support |
| Gradle | 9.8.1 | |
| Kotlin | 2.4.21 | |
| compileSdk / targetSdk | 37 | minSdk 29 |
| Compose BOM | 2026.09.00 | |
| Material 3 | 1.4 | |
| Navigation 3 | 1.2 | replaces Navigation Compose |
| Lifecycle / Activity Compose | 2.11 / 1.13 | |
| Media3 (ExoPlayer, Session) | 1.11.1 | `MediaLibraryService` |
| Room | 3.0.3 (`androidx.room3`) | |
| DataStore | 1.2.1 | |
| Ktor Client | 3.6 | |
| kotlinx.serialization / coroutines | 1.11 | |
| Coil | 3.6 | |
| Koin | 4.2 | DI without annotation processing |
| KSP | 2.3.12 | for Room |
