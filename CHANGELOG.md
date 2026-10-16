# Changelog

KoalaShelf's parts are released separately: **Web** (the Docker image with the web app, tags `web-vX.Y.Z`) and **Desktop** (installers, tags `desktop-vX.Y.Z`). Each release takes its notes from its section here; see [docs/releasing.md](docs/releasing.md).

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

**Yours to tailor**: theme, six accent colors, text size, motion, cover size, skip steps, media buttons, home shelves, start page and more, stored on the device only.

**Self-hosting**: one container (Caddy) serves the app and proxies Audiobookshelf on the same origin, with a strict Content Security Policy; runs unprivileged and read-only; images for amd64 and arm64 with signed provenance and SBOM.
