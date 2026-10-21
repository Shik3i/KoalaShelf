# apps/web

KoalaShelf web app / PWA: SvelteKit 3 as a client-side SPA (`adapter-static`), Svelte 5 runes, Vite 8, served by Caddy in the Docker image.

```bash
pnpm dev          # dev server; proxies the Audiobookshelf API (ABS_URL from dev/.env or .env.local)
pnpm check        # svelte-check
pnpm lint         # prettier + eslint
pnpm test         # unit tests (Vitest)
pnpm test:e2e     # Playwright in Edge against the local test server (see dev/README.md)
pnpm build        # static build in build/
pnpm icons        # render PWA and app icons from the SVG
```

## Structure

| Path                                             | What it does                                                                                                                                                                                                                                                                                                                                                  |
| ------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `src/routes/`                                    | Pages: `/` home, `/library`, `/book/[id]`, `/author/[id]`, `/series(/[id])`, `/authors` (and narrators), `/playlists(/[id])`, `/bookmarks`, `/collections(/[id])`, `/history`, `/stats`, `/search`, `/downloads`, `/settings`, `/signin`                                                                                                                      |
| `src/lib/session.svelte.ts`                      | Sign-in, token rotation, libraries, preset server from the container (`/koalashelf.json`)                                                                                                                                                                                                                                                                     |
| `src/lib/player/`                                | Audio engine (`player.svelte.ts`: multi-track timeline, sync, Media Session, HLS fallback, jump back, Cast), smart sleep forecast (`sleep.ts`), sound chain (`sound.ts`: boost, voices, mono, leveling, analog crackle & tape warmth EQ profile, silence detection with `static/worklets/silence.js`), tactile haptics (`haptics.ts`), pop-out player (`pip.ts`), full and mini player, timeline, speed, sound, queue and sleep panels |
| `src/lib/prefs.svelte.ts`                        | Every listener setting with defaults; stored on the device only                                                                                                                                                                                                                                                                                               |
| `src/lib/progress.svelte.ts`                     | Listening progress from the server (latest load and newest entry win), live updates via `live.ts` (socket.io)                                                                                                                                                                                                                                                 |
| `src/lib/downloads.svelte.ts`                    | Offline books in OPFS, offline sessions synced later                                                                                                                                                                                                                                                                                                          |
| `src/lib/queue.svelte.ts`, `shelves.ts`          | "Up next" queue, home shelf order and visibility                                                                                                                                                                                                                                                                                                              |
| `src/lib/library-query.ts`                       | Library queries: URL ↔ filters, server paging for simple ones, exact combined sets (cached id sets, text filter, local status and length)                                                                                                                                                                                                                     |
| `src/lib/search.ts`                              | Search in one or all libraries, recent searches, accent-insensitive highlighting                                                                                                                                                                                                                                                                              |
| `src/lib/export.ts`, `backup.ts`, `year-card.ts` | Files for the listener: CSV/Markdown exports, streak, settings backup and import, the year-in-review picture (canvas)                                                                                                                                                                                                                                         |
| `src/lib/i18n/`                                  | UI strings: English base, German must match its shape (type-checked)                                                                                                                                                                                                                                                                                          |
| `src/lib/components/`                            | Cover (generated placeholder art), Vinyl, Turntable, BookTile, Crate, AppNav, Segmented, Toggle, SessionRow, Toaster, …                                                                                                                                                                                                                                       |
| `src/app.css`, `static/theme.js`                 | Global styles (text size, accents, motion, high contrast, print); appearance applied before first paint                                                                                                                                                                                                                                                       |
| `src/service-worker/`                            | Offline app shell and cover cache (never API responses or audio)                                                                                                                                                                                                                                                                                              |
| `docker/Caddyfile`, `Dockerfile`                 | Static files, same-origin proxy to Audiobookshelf, CSP, caching, compression                                                                                                                                                                                                                                                                                  |

## Environment

| Variable         | Used by               | Meaning                                                                        |
| ---------------- | --------------------- | ------------------------------------------------------------------------------ |
| `ABS_URL`        | dev server, container | Audiobookshelf address the proxy talks to                                      |
| `ABS_PUBLIC_URL` | container             | Server address shown in the sign-in form (optional)                            |
| `E2E_BASE_URL`   | Playwright            | Run the suite against a running production container instead of the dev server |
| `SCREENS_DIR`    | Playwright            | Render screenshots (`screens`, `responsive` specs) for visual review           |

## Tests (`e2e/`)

`signin`, `library` (filters, combinations, text filter, saved views), `manage` (multi-select, book actions, series, collections), `player-plus` (speed, sound, skip silence, jump back, go to time, sleep extras), `data` (daily goal, year picture, exports, settings backup), `browse` (author, series, narrators, playlists, bookmarks, year in review, devices, quick search, search), `pages`, `player`, `qol`, `listening` (queue, smart sleep timer, leveling, history), `customize` (settings, ZIP, chapter timeline), `edge` (live, offline, guest, mobile), `pwa`, `responsive` (11 viewports, 125 % text), `a11y` (axe WCAG 2.2 AA + best practices, laptop and phone, both themes), `screens` (screenshots only). Tests that read player internals need the dev server and skip themselves on production builds.
