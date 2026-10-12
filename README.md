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

- Sign-in with access/refresh tokens (httpOnly cookie when served on the same origin)
- Home with "Continue listening" turntable and crates from your personalized shelves
- Library as shelf or list; filters for status, genre, narrator, series, author; sorting; everything in the URL
- Book page with spinning record, chapters, bookmarks, more by the author, file details
- Player: multi-file books, chapters, ±30 s, speed 0.8–2.0×, sleep timer (minutes with fade-out, or end of chapter), bookmarks as pins on the timeline, progress sync, lock-screen / media-key controls, keyboard shortcuts
- Series, authors, search; light/dark theme following the system; installable PWA

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

Requirements: Node.js 24+ (26 recommended), pnpm 12, Docker and ffmpeg for the local test server.

```bash
pnpm install
node dev/make-samples.mjs && docker compose -f dev/compose.yaml up -d && node dev/setup-abs.mjs
pnpm --filter @koalashelf/web dev
```

More in [docs/plan.md](docs/plan.md) and [docs/tech-stack.md](docs/tech-stack.md).
