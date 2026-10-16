# Feature parity: audiobooks

What a listener can do with KoalaShelf, compared with the Audiobookshelf server API, the Audiobookshelf web app, Audible and other audiobook players (Smart AudioBook Player, BookPlayer, Voice, Prologue, ShelfPlayer, the Audiobookshelf mobile apps). Podcasts and e-books are out of scope for now (by decision).

Last review: 2026-10-15. Legend: ✅ in KoalaShelf · ➖ not possible or not sensible in a web app (see notes) · ⏸ waits for the native apps.

## Audiobookshelf API (listener endpoints)

Every listener-facing endpoint of Audiobookshelf 2.37.1 is used, except the ones listed at the end.

| Area | Endpoints | KoalaShelf |
|---|---|---|
| Sign-in, tokens, sign-out | `/login`, `/auth/refresh`, `/logout`, `/status` | ✅ |
| Libraries, items, filters, sorting, grouping | `/api/libraries/:id/items`, `filterdata`, `items/batch/get`, `items/:id` | ✅ |
| Home shelves | `/api/libraries/:id/personalized` | ✅ |
| Search | `/api/libraries/:id/search` | ✅ one or all libraries |
| Series, authors, narrators | `series`, `series/:id?include=progress`, `authors/:id?include=items,series`, `narrators` | ✅ |
| Collections | read; create, edit, reorder, add/remove (single and batch), delete | ✅ editing for accounts with the "update" permission, deleting with "delete" |
| Playlists | list, create, rename, reorder, add/remove (single and batch), from a collection, delete | ✅ |
| Playback | `items/:id/play`, `session/:id/sync`, `session/:id/close`, `session/local` | ✅ incl. offline sessions |
| Progress | `me/progress/:id` (read, update), `me/progress/batch/update`, `DELETE me/progress/:id`, hide from / return to "continue" | ✅ |
| Bookmarks | per book and all, create, rename, delete | ✅ |
| Listening history and stats | `me/listening-sessions`, `me/item/listening-sessions/:id`, `me/listening-stats`, `me/stats/year/:y`, `libraries/:id/stats` | ✅ |
| Account | `me/sessions` (signed-in devices, remote sign-out), `me/password` | ✅ |
| Files | item ZIP, single files (offline downloads), covers, author images | ✅ |
| Live updates | socket.io `user_item_progress_updated` | ✅ |

Not used, on purpose: podcast and e-book routes (scope), RSS feeds, shares, metadata editing, uploads, scans, matching, users, backups, notifications, server settings (admin tools; KoalaShelf is a listening client), `me/ereader-devices` (e-books), OpenID sign-in (needs per-server setup; username and password work everywhere).

## Compared with the Audiobookshelf web app

| Listener feature | ABS web | KoalaShelf |
|---|---|---|
| Library with filters and sorting | one filter at a time | ✅ any combination, text filter, saved views, random order |
| Multi-select: mark finished / not finished, add to playlist or collection | ✅ | ✅ plus queue and download |
| Book page: chapters, files, details, description | ✅ | ✅ plus history, more by the author, links on genres, tags, publisher, language |
| Discard progress, mark finished | ✅ | ✅ with undo; plus "mark earlier volumes finished" |
| Collections and playlists | ✅ | ✅ plus "save collection as playlist", play all, queue all |
| Player: speed, sleep timer, chapters, bookmarks, jump amounts | ✅ | ✅ plus smart sleep timer, sound tools, jump back, go to time |
| Queue | ✅ | ✅ kept on the device, continues automatically, next in series offered |
| Statistics, year in review | ✅ | ✅ plus daily goal, streak, shareable year picture |
| Chromecast | ✅ | ✅ Cast / AirPlay through the browser |
| Offline listening | mobile apps only | ✅ in the browser (PWA) |
| Admin: users, libraries, metadata, scans, backups | ✅ | ➖ (stays in Audiobookshelf) |

## Compared with Audible and other players

| Feature | Seen in | KoalaShelf |
|---|---|---|
| Speed 0.5–3.5× in fine steps, per book | Audible, BookPlayer, Smart AudioBook Player | ✅ 0.05 steps, quick choices, per book or one default |
| Sleep timer: minutes, end of chapter, fade-out | all | ✅ |
| Sleep timer that waits for a chapter end within a window | — | ✅ (KoalaShelf's own) |
| Extend the timer, "still listening?" before it stops | Audible, Smart AudioBook Player | ✅ +5/+15/+30 min, a hint with +10 min while it fades |
| Automatic sleep timer at night | Audiobookshelf app, Smart AudioBook Player | ✅ with a time window and the usual timer kind |
| Shake to extend the timer | Smart AudioBook Player, ShelfPlayer | ⏸ needs motion sensors with the screen off (native) |
| Rewind after a pause | BookPlayer, Smart AudioBook Player | ✅ smart rewind (longer pause, further back) |
| Skip silence | Voice, Smart AudioBook Player, Pocket Casts | ✅ audio-worklet detector, saved time shown |
| Volume boost | Voice, BookPlayer, Smart AudioBook Player | ✅ up to +100 % |
| Volume leveling, clearer voices, mono | Pocket Casts, Smart AudioBook Player | ✅ |
| Undo an accidental jump | Smart AudioBook Player ("jump history") | ✅ "jump back" toast and U key |
| Go to an exact time | Smart AudioBook Player | ✅ |
| "Ends at" clock, time left at the current speed | Audible, BookPlayer | ✅ |
| Chapter or book progress on the lock screen | Audible, BookPlayer | ✅ |
| Bookmarks with notes, export | Audible (clips), Smart AudioBook Player | ✅ notes, links to the moment, Markdown and CSV export |
| Queue / up next, next in series | Prologue, ShelfPlayer | ✅ |
| Listening stats, streak, goal | Audible (stats, badges) | ✅ daily goal ring, streak, heatmap, year in review picture |
| Downloads, auto-remove finished | Audible, Audiobookshelf app | ✅ per book, series or playlist; remove finished automatically |
| Mini window on the desktop | — | ✅ pop-out player (Document Picture-in-Picture, Chromium) |
| Cast / AirPlay | Audible (Sonos/Alexa), Audiobookshelf | ✅ through the browser's device picker |
| Settings backup | — | ✅ export and import as a file |
| Several accounts and servers, quick switching | Audiobookshelf app (server list), Plappa | ✅ one tap, each account with its own queue, downloads and searches |
| Own accent color, readable font | BookPlayer (themes), Smart AudioBook Player (fonts) | ✅ any color kept readable automatically; Atkinson Hyperlegible or system font |
| Lock screen controls, media keys, headset buttons | all | ✅ (chapter or skip, configurable) |
| Car mode, Android Auto, CarPlay | Audible, Audiobookshelf app | ⏸ native apps |
| Widgets, Siri / Assistant shortcuts, Wear OS / watchOS | Audible, BookPlayer | ⏸ native apps |
| Equalizer with bands | Smart AudioBook Player | ➖ replaced by "clearer voices" (one switch instead of ten sliders) |
| Ratings, reviews, wish list, store | Audible | ➖ not in Audiobookshelf |
| Character lists, read along (Whispersync) | Smart AudioBook Player, Audible | ➖ needs e-book sync (out of scope) |

## Web platform limits

- **Background audio on iPhone and iPad**: Web Audio (boost, leveling, clearer voices, mono, skip silence) can pause in the background on iOS/iPadOS; these are opt-in and say so. Plain playback is not affected.
- **Skip silence** needs an audio worklet, which browsers only provide on https (or localhost). On plain http the switch is hidden.
- **Volume** cannot be set by web pages on iOS (hardware buttons only); the slider is hidden there.
- **Pop-out player** exists in Chromium-based desktop browsers (Chrome, Edge); elsewhere the button is hidden.
- **Cast** depends on the browser (Chrome: Cast; Safari: AirPlay); the button appears only when a receiver is around. Effects from Web Audio do not travel to the receiver.
