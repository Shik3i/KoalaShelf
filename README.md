# KoalaShelf

[![CI](https://github.com/Shik3i/KoalaShelf/actions/workflows/ci.yml/badge.svg)](https://github.com/Shik3i/KoalaShelf/actions/workflows/ci.yml) [![CodeQL](https://github.com/Shik3i/KoalaShelf/actions/workflows/codeql.yml/badge.svg)](https://github.com/Shik3i/KoalaShelf/actions/workflows/codeql.yml) [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

A fast, private, self-hostable client for your [Audiobookshelf](https://www.audiobookshelf.org) server: web app (PWA) today, native desktop and Android apps next. Your server stays untouched; KoalaShelf only replaces the interface.

Design language: **KoalaShelf Vinyl**: covers first, the record as a motif, crates and shelves.

![Design foundations](docs/design/koalashelf-design-grundlagen.png)

## Status

| Platform | State |
|---|---|
| Web app / PWA ([`apps/web`](apps/web)) | Feature-complete, verified (see [docs/audit.md](docs/audit.md)) |
| Desktop ([`apps/desktop`](apps/desktop), Tauri 2) | Scaffolded; continues once the web app is signed off |
| Android ([`apps/android`](apps/android), Compose, Media3) | Planned; skeleton parked on branch `wip/android` |

## Principles

- **Privacy first.** No trackers, no analytics, no third-party requests, fonts self-hosted, not indexed by search engines. Data flows only between your device and your server.
- **Self-hostable.** One small container in front of Audiobookshelf; no extra backend, no database.
- **Performance.** Static SPA, about 130 kB of compressed JavaScript on first load (the HLS player is only loaded when a stream needs it), instant navigation, cover cache, offline app shell. Lighthouse: 100 for performance, accessibility and best practices.
- **Accessible.** WCAG 2.2 AA, checked on every page in both themes at laptop and phone widths; keyboard, screen readers, high-contrast mode, reduced motion and any text size.
- **International.** English and German UI, type-checked translations; code, routes and docs in English.
- **Maintained.** Every push runs the dependency audit, types, lint, unit and end-to-end tests and builds the image (GitHub Actions); CodeQL scans the code; Dependabot keeps dependencies current.

## Features (web)

**Listening**
- **Player**: multi-file books, chapters (with a filter and "go to time"), configurable skip steps (10–60 s), speed 0.5–3.5× in 0.05 steps (per book or one default), timeline and clock for the whole book or the current chapter, "ends at" clock, smart rewind after pauses, **jump back** after an accidental jump, bookmarks as pins with editable notes and shareable links, lock screen and media keys, keyboard shortcuts, HLS fallback for formats the browser cannot play
- **Sound**: volume, boost up to +100 %, clearer voices, mono, volume leveling and **skip silence** (with the time it saved)
- **Sleep timer**: exact minutes with fade-out, end of chapter, or **smart**: the first chapter end inside a window such as 20–40 min, with a forecast of where it will stop; more time with one tap, a last-chance hint before it stops, and an automatic timer at night
- **Up next**: a queue that continues automatically when a book ends; otherwise the next book of the series is offered
- **Cast / AirPlay** through the browser, and a **pop-out player** that stays on top of other windows (Chrome, Edge)
- **Listening history**: every session on every device by day, with jumps to where it started or ended; per book on its page
- **Live progress** from other devices; **downloads** for offline listening (per book, series or playlist; synced when back online; finished books can remove themselves); **save a book's files as a ZIP**

**Finding**
- **Library**: shelf or list; **multi-select** to mark books finished or not, queue, add to a playlist or collection, or download them at once; filters for status, author, series, narrator, genre, tag, language, publisher, decade and length, freely combined (several values per kind, exact results); a text filter that also finds the books of matching authors, series and narrators; sorting by title, author, added, played, started, finished, length, year or random; grouped series; saved views; everything in the URL
- **Search**: books, series, authors, narrators, genres and tags, in one library or all of them, with highlighted matches and recent searches
- **Quick search** from anywhere (Ctrl/⌘ K or `/`): books, people, pages and actions by keyboard

**Browsing**
- **Home**: "continue listening" turntable and crates from your personalized shelves
- **Book page**: spinning record, chapters, bookmarks, history, files, more by the author, details (ISBN, ASIN, tags, edition) with links into the library, description; reset progress (with undo), mark the earlier volumes of a series finished, share
- **Author pages** (photo, bio, books by series), **series pages** (progress, the next book, add the rest to the queue, mark all finished, download all), **narrators**
- **Playlists**: your own lists, synced through the server: create, add from any book, reorder, play all
- **Bookmarks** of every book in one place, exportable as Markdown or CSV
- **Collections**: play or queue all, save as playlist; accounts with edit rights create, arrange, rename and delete them
- **Statistics**: a **daily goal** (ring on the home page) and streak, your year in review (time, books finished, top authors and genres, best month, voice of the year) also as a **picture to share**, heatmap, weekdays, most listened, the library in numbers; listening history as CSV

**Yours to tailor** (stored on the device only)
- Theme, six accent colors or **your own color** (automatically kept readable in both themes), an **easy-to-read font** (Atkinson Hyperlegible) or the system font, text size on top of the system size, animations, record spin, cover size
- Skip steps, chapter or book timeline, clock, speed for new books and per book, headset/lock-screen buttons as chapter or skip, smart rewind, sound (volume, boost, voices, mono, leveling, skip silence), queue and series behavior, keyboard shortcuts
- Sleep timer fade-out, rewind and night start; start page; home shelves (show, hide, reorder); daily goal; downloads; **settings backup** (export and import as a file); reset
- **Several accounts and servers**: add accounts on the same or other Audiobookshelf servers and switch with one tap (sidebar, "More", quick search); each keeps its own queue, downloads and recent searches
- Account: signed-in devices with remote sign-out, password change

**Everywhere**
- Responsive from 320 px phones (portrait and landscape) through tablets to 2560 px screens; a "More" sheet on phones
- Installable PWA with offline app shell; light/dark following the system; English and German
- Small touches: undo for deletions, links to a moment (`?t=12:34`), `?` shortcut overview, gentle motion that respects reduced motion

## Repository

| Folder | Contents |
|---|---|
| [`apps/web`](apps/web) | Web app / PWA (SvelteKit 3, Svelte 5), Docker image |
| [`apps/desktop`](apps/desktop) | Native desktop app (Tauri 2) |
| [`apps/android`](apps/android) | Native Android app (Kotlin, Compose, Media3) |
| [`packages/design-tokens`](packages/design-tokens) | Colors, accents, type, spacing, motion: one source for every platform |
| [`packages/abs-client`](packages/abs-client) | Typed Audiobookshelf API client |
| [`dev`](dev) | Local Audiobookshelf with generated sample audiobooks |
| [`docs`](docs) | Plan and decisions, tech stack, API notes, audit, design |

## Self-hosting (Docker)

One small container serves the app and talks to your Audiobookshelf; your own reverse proxy only points a subdomain at it.

```bash
cp .env.example .env    # set ABS_URL: where the container reaches Audiobookshelf
docker compose up -d    # KoalaShelf on 127.0.0.1:8080
```

- [`docker-compose.yml`](docker-compose.yml): hardened (read-only, unprivileged, no capabilities), optionally with Audiobookshelf itself
- [`.env.example`](.env.example): every setting, explained
- [`Caddyfile.example`](Caddyfile.example): your Caddy in front (TLS, your subdomain), on the host or in Docker

**Nothing to back up, no volume:** KoalaShelf stores nothing on the server. Accounts, saved servers, settings and offline downloads live in each listener's browser; progress, bookmarks and the library stay in Audiobookshelf.

| Variable | Meaning |
|---|---|
| `ABS_URL` | Where the container reaches Audiobookshelf (Docker service, LAN address or public address) |
| `ABS_PUBLIC_URL` | Optional: the server address prefilled in the sign-in form (defaults to the app's own address) |
| `KOALASHELF_VERSION` | Image tag in the Compose file: `latest`, or pinned like `0.1.0` or `0.1` |
| `KOALASHELF_BIND`, `KOALASHELF_PORT` | Where the container listens on the host (default `127.0.0.1:8080`) |

Images for linux/amd64 and linux/arm64 are published per release (`X.Y.Z`, `X.Y`, `latest`) with signed build provenance and an SBOM:

```bash
gh attestation verify oci://ghcr.io/shik3i/koalashelf:latest -R Shik3i/KoalaShelf
```

To build the image yourself: `docker build -f apps/web/Dockerfile -t koalashelf-web .`

Inside the container a small web server (Caddy) serves the app and passes the Audiobookshelf API through on the same origin, so no CORS setup is needed and the refresh token stays in an httpOnly cookie. It sends a strict Content Security Policy, blocks framing, never lets personal API answers be cached, compresses with zstd/gzip and caches hashed assets for a year.

Listeners can also add any other Audiobookshelf server in the app: add KoalaShelf's address to *Allowed origins* in that server's settings. Requires Audiobookshelf **2.26 or newer**.

## Development

Requirements: Node.js 24+ (26 recommended), pnpm 12, Docker and ffmpeg for the local test server, Microsoft Edge for the end-to-end tests.

```bash
pnpm install
node dev/make-samples.mjs && docker compose -f dev/compose.yaml up -d && node dev/setup-abs.mjs
pnpm --filter @koalashelf/web dev
```

Checks (in `apps/web`):

```bash
pnpm check        # types (svelte-check)
pnpm lint         # prettier + eslint
pnpm test         # unit tests (packages/abs-client has its own: pnpm --filter @koalashelf/abs-client test)
pnpm test:e2e     # end-to-end against the local test server (Playwright, Edge)
E2E_BASE_URL=http://localhost:8088 pnpm test:e2e   # against a production container
SCREENS_DIR=/tmp/shots pnpm test:e2e screens responsive   # screenshots for visual review
```

How KoalaShelf compares with the Audiobookshelf web app, Audible and other players: [docs/feature-parity.md](docs/feature-parity.md).

Releases: [CHANGELOG.md](CHANGELOG.md) and [docs/releasing.md](docs/releasing.md) (web and desktop are released separately by tag). Security reports: [SECURITY.md](SECURITY.md).

More in [docs/plan.md](docs/plan.md), [docs/tech-stack.md](docs/tech-stack.md), [docs/audit.md](docs/audit.md) and [docs/api/audiobookshelf.md](docs/api/audiobookshelf.md).

## License

[MIT](LICENSE). Audiobookshelf is a separate project by its own authors; KoalaShelf is an independent client and not affiliated with it.
