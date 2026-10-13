# KoalaShelf

A fast, private, self-hostable client for your [Audiobookshelf](https://www.audiobookshelf.org) server: web app (PWA), native desktop app and native Android app. Your server stays untouched; KoalaShelf only replaces the interface.

Design language: **KoalaShelf Vinyl** – covers first, the record as a motif, crates and shelves.

![Design foundations](docs/design/koalashelf-design-grundlagen.png)

## Principles

- **Privacy first.** No trackers, no third-party requests, fonts self-hosted. Data flows only between your device and your server.
- **Self-hostable.** One container in front of Audiobookshelf; no extra backend, no database.
- **Performance.** Static SPA (about 70 kB of gzipped JavaScript for the whole app), instant navigation, cover cache, offline app shell.
- **International.** English and German UI out of the box, type-checked translations.

## Features (web)

- **Sign-in** to any Audiobookshelf server: live server check, recent servers, clear hints for CORS, old servers and SSO-only servers; access/refresh tokens (httpOnly cookie behind the bundled proxy)
- **Home**: "Continue listening" turntable, crates from your personalized shelves, hide from continue listening
- **Library**: shelf or list, filters (status, genre, narrator, series, author, combinable), sorting, group series; everything in the URL
- **Book page**: spinning record, chapters, bookmarks, more by the author, file details, description, download
- **Player**: multi-file books, chapters, configurable skip (10–60 s), speed 0.8–2.0×, sleep timer (minutes with fade-out or end of chapter), smart rewind after pauses, bookmarks as pins with editable notes, lock-screen/media-key controls, keyboard shortcuts, HLS fallback for formats the browser cannot play
- **Little things**: share a link to a moment, undo for deletions, speed remembered per book, elapsed/remaining clock, next book in the series offered when one ends, time preview when hovering the timeline, sleep timer badge, `?` shortcut overview, gentle motion everywhere (respects reduced motion)
- **Downloads & offline**: books stored in the browser (OPFS) and played from local files; offline listening is synced when the connection is back; installable PWA with offline app shell
- **Live progress** from other devices (socket.io)
- **Series, authors, collections, statistics** (heatmap, streak, weekdays, most listened), **search**, **settings**
- English and German, light/dark following the system, WCAG 2.2 AA checked

## Repository

| Folder | Contents |
|---|---|
| [`apps/web`](apps/web) | Web app / PWA (SvelteKit 3), Docker image |
| [`apps/desktop`](apps/desktop) | Native desktop app (Tauri 2) – planned |
| [`apps/android`](apps/android) | Native Android app (Kotlin, Compose, Media3) – planned |
| [`packages/design-tokens`](packages/design-tokens) | Colors, type, spacing: one source for every platform |
| [`packages/abs-client`](packages/abs-client) | Typed Audiobookshelf API client |
| [`dev`](dev) | Local Audiobookshelf with generated sample audiobooks |
| [`docs`](docs) | Plan, tech stack, API notes, design |

## Self-hosting (Docker)

```bash
docker build -f apps/web/Dockerfile -t koalashelf-web .
docker run -d -p 8080:8080 -e ABS_URL=http://your-audiobookshelf:80 koalashelf-web
```

Caddy serves the app and proxies the Audiobookshelf API on the same origin, so no CORS setup is needed. Put your usual TLS reverse proxy in front of port 8080. See [`compose.yaml`](compose.yaml).

Serving the app from a different origin also works: add that origin to *Allowed origins* in the Audiobookshelf server settings. Requires Audiobookshelf **2.26 or newer**.

## Development

Requirements: Node.js 24+ (26 recommended), pnpm 12, Docker and ffmpeg for the local test server, Microsoft Edge for the end-to-end tests.

```bash
pnpm install
node dev/make-samples.mjs && docker compose -f dev/compose.yaml up -d && node dev/setup-abs.mjs
pnpm --filter @koalashelf/web dev
```

Tests (in `apps/web`):

```bash
pnpm check        # types (svelte-check)
pnpm lint         # prettier + eslint
pnpm test         # unit tests
pnpm test:e2e     # end-to-end against the local test server (Playwright, Edge)
E2E_BASE_URL=http://localhost:8088 pnpm test:e2e   # against a production build, incl. PWA/offline
```

More in [docs/plan.md](docs/plan.md), [docs/tech-stack.md](docs/tech-stack.md) and [docs/audit.md](docs/audit.md).
