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

**Accounts**: several accounts on one or more Audiobookshelf servers, switched with one tap; each keeps its own queue, downloads and recent searches; signing out ends the session on the server and removes the account's data from the device.

**Yours to tailor**: theme, six accent colors or your own (kept readable in both themes), an easy-to-read font (Atkinson Hyperlegible) or the system font, text size, motion, cover size, skip steps, media buttons, home shelves, start page and more, stored on the device only.

**A record shop feel**: series are crates of records to flip through, every book in reading order (pointer, sideways scroll, swipe or keys), the record in front opens its book; records peek out of their sleeves, the playing book's record stays out and turns at the playback speed; finished books become gold records; a new book is put on the platter; the navigation highlight glides along; a new theme spreads from where you click; author portraits become record labels. Calm and instant with reduced motion.

**Self-hosting**: one small container serves the app and passes Audiobookshelf through on the same origin, with a strict Content Security Policy; your reverse proxy only points a subdomain at it. Ready-made `docker-compose.yml`, `.env.example` and `Caddyfile.example`; nothing to back up and no volume (everything personal stays in the browser or in Audiobookshelf). Runs unprivileged and read-only; images for amd64 and arm64 with signed provenance and SBOM. Personal API answers are never cached, even behind reverse proxies that mark them as public.
