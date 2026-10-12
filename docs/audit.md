# Web app audit (2026-10-12)

Full audit of `apps/web` before review: against the briefing, the Audiobookshelf feature set, code review, and a visual pass (desktop/mobile, dark/light) against a real server (Audiobookshelf 2.37.1, 571 titles) and the local sample server.

## Briefing checklist

| Requirement | Status |
|---|---|
| Sign-in with server URL, username, password; tokens stored safely | ✅ Server field always visible, live check, recent servers; access/refresh tokens; httpOnly cookie behind the proxy |
| Home: "Continue listening" card, crates (new, continue series) | ✅ Turntable hero, crates from personalized shelves, hide from continue listening |
| Library: shelf, list, series; filters status/genre/narrator/series; sort | ✅ Plus author filter, all state in the URL |
| Book page: cover with record, series/volume, author, narrator, duration, chapters, progress, description, file info | ✅ Description rendered from server HTML safely |
| Player: chapters, ±30 s, speed 0.8–2.0, sleep timer incl. end of chapter, bookmarks with note, progress sync | ✅ Skip steps configurable (10–60 s), smart rewind, bookmark notes editable |
| Mini player above navigation | ✅ |
| Theme light/dark following the system, switchable | ✅ |
| Sidebar: Home, Library, Series, Authors, Collections, Downloads, Statistics, profile | ✅ except Downloads (see open items) |
| Tab bar under 800 px | ✅ Home, Library, Series, Search (Offline: see open items) |
| Record as motif (spins, tonearm = progress, ticks = chapters, reduced motion) | ✅ |
| Touch targets ≥ 44 px, contrast ≥ 4.5:1 | ✅ Contrast enforced by the token build |
| UI texts translated | ✅ English + German, type-checked, no hard-coded strings |

## Fixed during the audit

- Sign-in hid the server field behind a proxy; now one "Audiobookshelf server" field, proxy used transparently.
- Blank page when opening `/book/:id` directly (load function deadlock).
- Long titles overflowed horizontally; pages now use container queries.
- Tonearm stylus was beside the record; geometry recomputed.
- Mini player showed an off-centre slider thumb; now a plain progress line.
- Timeline fill/ticks/pins misaligned at the edges.
- Raw `<br />` in descriptions.
- Vinyl label in the full player showed the placeholder (lazy image inside the dialog).
- Mobile full player did not fit one screen.
- Statistics heatmap scrolled to the oldest week.
- No loading states; library flashed "no titles" before loading.
- Play buttons gave no feedback during the initial buffering (seconds on large m4b files).
- Sign-out kept progress/cache of the previous user; playback errors were invisible; final sync could be lost on tab close.
- Formats the browser cannot play: HLS fallback via lazily loaded hls.js.

## Open items (need a decision or are planned later)

| Item | Note |
|---|---|
| Downloads / offline listening | Briefing: later. Needs storage strategy (Cache/OPFS) and offline session sync. |
| Live progress from other devices | Refresh on tab focus is in place; socket.io push is not. |
| Driving mode | Briefing: later. |
| Share clip | Not supported by the server. |
| Collapse series in the library | Server supports it; not in the UI yet. |
| Empty-state illustrations | Empty states are text only. |
| Service worker | Code in place; the in-app test browser cannot register service workers, so offline shell is untested here. |
| Automated tests | Unit tests for formatting, filters, version check; no end-to-end tests yet. |
