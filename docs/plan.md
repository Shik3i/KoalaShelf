# KoalaShelf: plan

- Original briefing (German): [briefing.md](briefing.md)
- Design: [design/koalashelf-design-grundlagen.png](design/koalashelf-design-grundlagen.png)
- API notes: [api/audiobookshelf.md](api/audiobookshelf.md)
- Versions and rationale: [tech-stack.md](tech-stack.md)

## Guiding principles

Every decision is checked against: **performance, quality-of-life features, self-hostability, privacy first**, plus a design that delights. Everything is international: English code, routes and docs; every UI string translated (English base, German).

## Phases

| # | Phase | Status |
|---|---|---|
| 1 | Repo, design tokens (CSS/TS/Kotlin from one DTCG source, contrast check), API client | done |
| 2 | Web: sign-in, library (shelf/list, filters, sort), book page | done |
| 3 | Web: player (multi-track, sync, chapters, speed, sleep timer, bookmarks, Media Session, shortcuts), mini player | done |
| 4 | Web: home with crates, series, authors, search, theme, i18n, PWA, Docker image | done |
| 5 | Desktop: Tauri 2 shell (HTTP via Rust, OS keychain, media keys, installers) | scaffolded, paused until the web app is approved |
| 6 | Android: Compose with tokens, Media3 service, background playback, downloads, offline sessions | skeleton parked on branch `wip/android`, paused |

Later: downloads/offline in the web app, Android Auto, driving mode, statistics, author pages, collections, socket.io live progress.

## Routes (web)

`/signin`, `/` (home), `/library`, `/series`, `/authors`, `/search`, `/book/:id`.
Not `/login`: that is Audiobookshelf's login endpoint, which the same-origin proxy forwards.

Library URL parameters: `view=list`, `status=in-progress|not-started|finished`, `genre`, `narrator`, `series`, `author`, `sort=title|author|added|played|duration|published`, `desc=0|1`.

## Decisions

- **Same origin by default.** The Docker image (Caddy) serves the SPA and proxies Audiobookshelf. No CORS, and the refresh token lives in an httpOnly cookie instead of browser storage. A separate origin works via Audiobookshelf's *Allowed origins* setting; then the refresh token is stored locally.
- **Audio auth.** `<audio>` cannot send headers, so streams carry the short-lived access token as `?token=`. On a stream error the player refreshes the token and resumes at the same position. Covers are public on the server, so no token ends up in image URLs.
- **Filters.** The server accepts one filter per request. The most selective filter goes to the server, the rest is applied client-side.
- **Status "New"** = `not-started`. The "Recently added" crate = shelf `recently-added`.
- **Bookmark "note"** = the bookmark title (the server has no separate note field).
- **"Share clip"** is not supported by the server; left out until clarified.
- **Sync.** Every 15 s while playing, on pause, on hide/unload. Listening time is counted from wall-clock while playing, robust against seeks. If the server forgot the session (404), a new one is opened at the same spot.
- **Service worker** caches the app shell and covers only; API responses and audio are never cached.
- **Server version.** Audiobookshelf ≥ 2.26 (access/refresh tokens); older servers get a clear message.
- **Android package name:** `net.koalastuff.koalashelf`.

## Design tokens

Source: `packages/design-tokens/tokens/*.json` (DTCG). Outputs: `dist/tokens.css` (`.th-light` / `.th-dark` plus `prefers-color-scheme` fallback), `dist/tokens.ts`, and `KoalaTokens.kt` committed under `apps/android/core/designsystem`. The build fails if a text/background pair drops below WCAG 4.5:1 (3:1 for accent UI parts).
