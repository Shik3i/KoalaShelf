# Changelog

## Android 0.1.1

The initial release of the native Android client for KoalaShelf with vinyl aesthetic, background media playback and full Audiobookshelf library integration:

- **Audio Playback**: Media3 background playback with lock screen media notifications, hardware play/pause/skip support, playback speed control, sleep timer, smart rewind, voice boost and equalizer audio processing.
- **Library & Search**: Multi-library browsing, series, authors, and narrators views, live search with instant filtering, book details with scrollable summary and chapter navigation.
- **Vinyl Design System**: Custom vinyl record spinning disc artwork, interactive scrubber, accent color theming, and responsive layouts.
- **Hardening & Security**: Optimized release builds with R8 code shrinking and ProGuard optimization, disabled plaintext backup, and native audio resource lifecycle management.

## Web 0.1.1

A focused release improving navigation, customization, connection reliability, and browser testing:

- **Sidebar Navigation**:
  - Structured, responsive layout with collapsible visual groups (Main, Discover, Personal) with persisted state
  - Icon-only collapse toggle (68px) and draggable resize handle (180px–380px) with double-click reset
  - Modernized library switcher dropdown in the navigation header
  - Cleaner layout moving language toggle and listening stats into Settings
- **Connection & Streaming**:
  - Switched Caddy upstream transport to HTTP/1.1 for API, auth, and image routes, fixing WebSocket upgrade loops and 502 Bad Gateway errors
  - Improved progress store and view transition null-safety
- **Privacy & Customization**:
  - Self-hosted, local-first typography options (Inter, Atkinson Hyperlegible, JetBrains Mono, Fraunces, Literata, Gloock, OpenDyslexic) with zero third-party web resource requests
- **Automated Verification**:
  - In-browser end-to-end verification suite via Playwright with automatic console and network error detection

## Desktop 0.1.0

The first release: a fast, private client for Audiobookshelf as a native desktop app for Windows, macOS and Linux (built with Tauri).

- Native desktop window with system integration and saved window position
- Full parity with KoalaShelf web player, offline support and library features
- Unsigned pre-release installers for Windows (.msi, setup .exe), macOS (universal .dmg) and Linux (.deb, .rpm, .AppImage) with signed provenance attestations

## Web 0.1.0

The first release: a fast, private, self-hostable client for Audiobookshelf 2.26 or newer, as an installable web app (PWA) in one small Docker image.

**Listening**
- Player for multi-file books with chapters, speed 0.5–3.5× (per book or one default), timeline for the book or the current chapter, "ends at" clock, smart rewind after pauses, jump back after accidental jumps, go to a typed time
- Sound: volume, boost, clearer voices, mono, volume leveling, skip silence
- Sleep timer: minutes, end of chapter, or smart (the first chapter end in a window); more time with one tap, a hint before it stops, automatic at night
- Bookmarks with notes and links to the moment; up next queue; next in series; listening history from every device; live progress from other devices
- Offline listening (books, series, playlists), synced when back online; save a book's files as ZIP
- Cast / AirPlay, pop-out player, lock screen and media keys, keyboard shortcuts

**Finding and organizing**
- Library with freely combined filters, text filter, saved views and random order; multi-select to mark finished, queue, add to playlists or collections, download
- Search in one or all libraries; quick search (Ctrl/⌘ K)
- Author, series and narrator pages; playlists; collections (editable with the right permissions); all bookmarks in one place

**Statistics and your data**
- Daily goal and streak, year in review (also as a picture to share), heatmap, the library in numbers
- Export bookmarks (Markdown, CSV) and history (CSV); back up and restore settings

**Accounts**: several accounts on one or more Audiobookshelf servers, switched with one tap; each keeps its own queue, downloads and recent searches; signing out ends the session on the server and removes the account's data from the device.

**Yours to tailor**: theme, six accent colors or your own (kept readable in both themes), an easy-to-read font (Atkinson Hyperlegible) or the system font, text size, motion, cover size, skip steps, media buttons, home shelves, start page and more, stored on the device only.

**A record shop feel**: series are crates of records to flip through, every book in reading order (pointer, sideways scroll, swipe or keys), the record in front opens its book; records peek out of their sleeves, the playing book's record stays out and turns at the playback speed; finished books become gold records; a new book is put on the platter; the navigation highlight glides along; a new theme spreads from where you click; author portraits become record labels. Calm and instant with reduced motion.

**Self-hosting**: one small container serves the app and passes Audiobookshelf through on the same origin, with a strict Content Security Policy; your reverse proxy only points a subdomain at it. Ready-made `docker-compose.yml`, `.env.example` and `Caddyfile.example`; nothing to back up and no volume (everything personal stays in the browser or in Audiobookshelf). Runs unprivileged and read-only; images for amd64 and arm64 with signed provenance and SBOM. Personal API answers are never cached, even behind reverse proxies that mark them as public.
