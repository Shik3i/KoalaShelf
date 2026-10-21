# KoalaShelf: plan and decisions

- Original briefing (German): [briefing.md](briefing.md)
- Design: [design/koalashelf-design-grundlagen.png](design/koalashelf-design-grundlagen.png)
- API notes: [api/audiobookshelf.md](api/audiobookshelf.md)
- Versions and rationale: [tech-stack.md](tech-stack.md)
- Verification: [audit.md](audit.md)
- Feature parity with the Audiobookshelf web app, Audible and other players: [feature-parity.md](feature-parity.md)

## Guiding principles

Every decision is checked against: **performance, quality-of-life features, self-hostability, privacy first**, plus a design that delights. Everything is international: English code, routes and docs; every UI string translated (English base, German). The web app comes first; native apps follow once it is signed off.

## Phases

| # | Phase | Status |
|---|---|---|
| 1 | Repo, design tokens (CSS/TS/Kotlin from one DTCG source, contrast check), API client | done |
| 2 | Web: sign-in, library (shelf/list, filters, sort), book page | done |
| 3 | Web: player (multi-track, sync, chapters, speed, sleep timer, bookmarks, Media Session, shortcuts), mini player | done |
| 4 | Web: home with crates, series, authors, search, theme, i18n, PWA, Docker image | done |
| 4b | Web extras: downloads and offline listening, statistics, collections, live progress, queue, smart sleep timer, volume leveling, listening history, ZIP download, chapter timeline, settings for nearly everything, responsive and accessibility audits | done |
| 4c | Web parity: combined filters, search, quick search, author/series/narrator pages, playlists, bookmarks, year in review, devices; fine speed, sound tools (boost, voices, mono, skip silence), jump back, sleep timer extras, Cast, pop-out player; multi-select, collections management, book actions; daily goal, year picture, exports and settings backup | done |
| 5 | Desktop: Electron shell (Chromium audio engine, OS keychain via safeStorage, media keys, installers) | planned on branch `desktop`: [desktop-plan.md](desktop-plan.md) |
| 6 | Android & Web 1.0: Compose with tokens, Media3 service, background playback, downloads with HTTP Range resume, WorkManager offline sessions sync, vinyl aesthetic, faceted search, tactile haptics, Android Auto, live turntable widget, bedtime mode, tape warmth EQ, R8 optimization | done ([`apps/android`](../apps/android), release 1.0.0) |

Later (native roadmap): Driving mode, Wear OS tiles, iOS native client.

## Routes (web)

`/signin`, `/` (home), `/library`, `/book/:id`, `/author/:id`, `/series`, `/series/:id`, `/authors` (`?tab=narrators`), `/playlists`, `/playlists/:id`, `/bookmarks`, `/collections`, `/collections/:id`, `/history`, `/stats`, `/downloads`, `/search`, `/settings`.
Not `/login`: that is Audiobookshelf's login endpoint, which the same-origin proxy forwards.

- Library: `view=list`, `status=in-progress|not-started|finished`, `genre`, `tag`, `narrator`, `language` (each repeatable: any of the values), `author`, `series`, `publisher`, `decade`, `length=lt2|2-6|6-12|12-24|gt24`, `q` (text), `group=1`, `sort=title|author|added|played|started|finished|duration|published|random`, `desc=0|1`, `shuffle`. View, sort and grouping are remembered; filters are not (saved views keep them on purpose).
- Search: `?q=`, `in=all` for every library.
- Book: `?t=754` or `?t=12:34` opens the book at that moment (shared links).
- Search: `?q=`.

## Decisions

### Server and auth
- **Same origin by default.** The Docker image (Caddy) serves the SPA and proxies Audiobookshelf. No CORS, and the refresh token lives in an httpOnly cookie instead of browser storage. Without `ABS_PUBLIC_URL` the app's own address stands for the proxied server. A separate origin works via Audiobookshelf's *Allowed origins* setting; then the refresh token is stored locally.
- **The server field is always visible and editable.** The interface never talks about "the proxy"; it shows the Audiobookshelf address. A server check only counts for the exact address it was made for (a changed address is checked again before signing in).
- **Audio and downloads auth.** `<audio>` and download links cannot send headers, so they carry the short-lived access token as `?token=`; it is renewed first when it is about to expire. On a stream error the player refreshes the token and resumes at the same position. Covers are public on the server, so no token ends up in image URLs.
- **Server version.** Audiobookshelf ≥ 2.26 (access/refresh tokens); older servers get a clear message. SSO-only servers get a clear message too.
- **Device name.** Sessions send no `model`, so the server names the device from the browser ("Windows 10 Edge").

### Accounts

- **Several saved accounts**, one active (`ks.accounts`). Through the bundled proxy the first account keeps its refresh token in the httpOnly cookie; the cookie can only belong to one account, so further accounts on that address (and accounts on other servers) sign in with `credentials: 'omit'` and keep their refresh token in local storage, as cross-origin sign-ins always did. Audiobookshelf sets the cookie on every sign-in, but a response fetched with `omit` never stores it, so the first account's cookie stays intact.
- **Per-account data**: queue, last book, recent searches and offline sessions are stored per user (`key@userId`); downloads keep one copy on disk with a list of owners, shown only to them. Switching closes the playback session, swaps all of it and opens the new account's home page.
- **Sign out** revokes the session on the server and removes the account with its data from the device. An account whose session ended stays listed and asks for its password (server and username prefilled). Earlier single-session storage (`ks.session`) is migrated on first start.
- **Server address shown** is always the Audiobookshelf server's, also for accounts behind the proxy.

### Data
- **Filters.** The server accepts one filter per request. One filter (or none) pages through the server. Anything combined becomes an exact set: one cached server request per value, values of a kind OR'ed, kinds AND'ed, status and length checked locally against current progress. The text filter unions the server search's books with the books of matching authors, series, narrators, genres and tags. Grouping series works only with at most one filter (the server groups, not the client).
- **Search** runs in the current library or all of them in parallel; highlights are visual only, so screen readers read whole words. The quick search (Ctrl/⌘ K) is a combobox over search results, pages and actions.
- **Playlists live on the server** (shared across the listener's devices); the queue stays on the device. "Play all" puts the rest of a playlist in front of the queue. The server deletes a playlist when its last book is removed; KoalaShelf says so and offers undo (creates it again).
- **Collections** are shared by everyone on the server. Creating and editing needs the account's "update" permission, deleting needs "delete"; the server refuses empty new collections, so a collection starts from a book (book page or multi-select).
- **Bulk changes** (multi-select, a whole series) go through `PATCH /api/me/progress/batch/update` in one request; "reset progress" deletes the progress entry, its undo writes the old state back.
- **Exports** (bookmarks as Markdown/CSV, history as CSV, the year picture, the settings backup) are built on the device and saved as files; nothing is uploaded. CSV cells that a spreadsheet would run as formulas are defused. The settings backup never contains sessions, tokens, device ids or servers.
- **Progress.** The latest load wins over an older, slower one; when the server lists a book twice, the entry with the newest `lastUpdate` counts; live events only apply when they are newer than what is known (server timestamps only, device clocks can be off).
- **Sync.** Every 15 s while playing, on pause, on hide/unload. Listening time is counted from wall-clock while playing, robust against seeks. If the server forgot the session (404), a new one is opened at the same spot.
- **Bookmark "note"** = the bookmark title (the server has no separate note field).
- **Listening history** comes from the server's playback sessions (all devices); sessions with almost no listening are hidden as noise. The server cannot delete single listening sessions, so the app does not offer it.
- **Queue, settings, per-book speed** are stored on the device only (no server side exists; privacy first).
- **Service worker** caches the app shell and covers only; API responses and audio are never cached.

### Player
- **Smart sleep timer.** A window (earliest, latest): it stops at the first chapter end after the earliest time, at the latest time otherwise (with fade-out and rewind, both configurable). Timers only count while playing.
- **Volume leveling** routes the audio element through a Web Audio compressor. Opt-in, because on iOS/iPadOS Web Audio can pause in the background; remote audio then needs CORS, which the app sets up itself. Not offered in the desktop shell.
- **Chapter timeline.** The timeline, clock, mini-player line and lock-screen position can cover the current chapter instead of the whole book.
- **Speed** 0.5–3.5× in 0.05 steps (keys `[` `]` move by 0.1); browsers keep the pitch.
- **Sound chain** (Web Audio, opt-in, not in the desktop shell): mono downmix → clearer voices (high-pass 90 Hz, +5 dB presence at 2.8 kHz) → leveling compressor → gain (boost up to 2×). The element joins the graph only once something is switched on; everything off is a plain pass-through.
- **Skip silence**: an audio worklet measures the level on the audio thread and reports silent stretches (below −44 dBFS for 0.4 s); the player then plays at 2.5× (or 2.5 × the speed, at most 4×) until sound returns, and counts the time saved. Worklets need https, so the switch is hidden on plain http.
- **Jump back.** Jumps of 30 s or more (chapters, bookmarks, timeline, typed time) can be undone from a toast or with `U`; skip buttons are not jumps.
- **Sleep timer extras.** +5/+15/+30 min on a running timer (a chapter-end timer becomes a countdown past this chapter); a hint with "+10 min" while it fades out; an optional automatic timer when playback starts inside a night window (the listener's usual kind: smart, chapter end or minutes).
- **Cast / AirPlay** use the browser's Remote Playback API (or Safari's AirPlay picker); the button only appears when a receiver is around. The **pop-out player** uses Document Picture-in-Picture (Chromium desktop) and renders the same Svelte component in the small window.
- **End of book.** Next from the queue (if enabled), otherwise the next book of the series is offered (if enabled). A chapter-end sleep timer treats the book end as a chapter end.
- **"Share clip"** is not supported by the server; left out.

### Interface
- **Customization** is a single typed preferences object (`prefs.svelte.ts`) with safe defaults; unknown or malformed stored values fall back to defaults. Appearance settings are applied as `data-*` attributes on `<html>` before first paint (`static/theme.js`).
- **Accent colors** are design-token presets, each contrast-checked in both themes at build time (4.5:1 wherever the accent is text). **Your own color** goes through the same rules at runtime (`src/lib/accent.ts`): light and dark variants keep the hue and move only in OKLCH lightness until every surface pair reaches 4.5:1; the variants are stored so `static/theme.js` applies them before the first paint.
- **Fonts**: standard (Manrope, Gloock), easy to read (Atkinson Hyperlegible Next, self-hosted, only downloaded when chosen) or the system font (nothing downloaded).
- **Text size** works through `rem` font sizes: the browser/system text size applies, the app setting adds on top.
- **Motion** follows the system unless the listener chooses reduced or full; the record spin can be switched off separately.
- **Responsive.** Tab bar plus "More" sheet below 800 px; sidebar (scrollable on short screens) above. Page content is capped at 1600 px for readable lines on very wide screens; tile grids always keep at least two columns on phones.
- **Accessibility** beyond WCAG AA: high-contrast mode (system colors for progress, switches and swatches), `prefers-contrast: more`, skip link, label-in-name, 44 px touch targets.
- **Not indexed.** `noindex` and a disallowing `robots.txt`: a personal library does not belong in search engines (Lighthouse SEO scores this down on purpose).
- **Android package name:** `net.koalastuff.koalashelf`.

## Design tokens

Source: `packages/design-tokens/tokens/*.json` (DTCG). Outputs: `dist/tokens.css` (`.th-light` / `.th-dark`, `prefers-color-scheme` fallback, accent presets, `rem` font sizes), `dist/tokens.ts`, and `KoalaTokens.kt` committed under `apps/android/core/designsystem`. The build fails if a text/background pair drops below WCAG 4.5:1 in any theme with any accent preset.
