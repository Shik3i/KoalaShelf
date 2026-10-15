# Audiobookshelf API: verified notes

The official docs (https://api.audiobookshelf.org) state that they are outdated. Everything below was checked against the server source (advplyr/audiobookshelf, release **v2.37.1**) and exercised against a local server. Source beats docs.

## Auth

| Purpose | Call |
|---|---|
| Login | `POST /login` `{username, password}`; header `x-return-tokens: true` → `user.accessToken` (short-lived) + `user.refreshToken`. Without the header the refresh token is set as httpOnly cookie. |
| Refresh | `POST /auth/refresh`, header `x-refresh-token` (or the cookie) → rotated pair |
| Logout | `POST /logout` (refresh token via header or cookie) |
| Check | `GET /api/me`, `GET /api/authorize` |
| Server info | `GET /status` (unauthenticated: `app`, `serverVersion`, `isInit`, `authMethods`) |

- Requests: `Authorization: Bearer <accessToken>`. GET also accepts `?token=` (needed for `<audio src>` and download links).
- Access tokens are JWTs with an `exp` claim (one hour on 2.37.1); clients can check the expiry locally and refresh before handing a URL to the browser.
- Covers and author images are **public**: `GET /api/items/:id/cover`, `GET /api/authors/:id/image`.
- OpenID (PKCE) exists under `/auth/openid*`; not used yet.

## CORS

The server only sends CORS headers when `ALLOW_CORS=1`, when the origin is listed in the server setting `allowedOrigins`, or for `capacitor://localhost` / `http://localhost`. Hence the same-origin proxy in the Docker image. Tauri can bypass CORS via its Rust HTTP plugin; Android is not affected.

- For an allowed origin the headers apply to every route (API, streams, downloads): `Access-Control-Allow-Origin: <origin>`, `Allow-Headers: *`, `Allow-Credentials: true`; `OPTIONS` is answered with 200. Bearer requests from another origin pass the preflight (verified in Edge).
- Audio routed through Web Audio (volume leveling) must be loaded with `crossorigin="anonymous"`; otherwise a cross-origin stream plays silently.

## Library

| Purpose | Call |
|---|---|
| Libraries | `GET /api/libraries` |
| Items | `GET /api/libraries/:id/items?limit&page&sort&desc&filter&minified&collapseseries` (`limit=0` = all) |
| Filter values | `GET /api/libraries/:id/filterdata` (authors, genres, tags, series, narrators, languages) |
| Series | `GET /api/libraries/:id/series?limit>0&page&sort&desc` |
| Authors | `GET /api/libraries/:id/authors` |
| Home shelves | `GET /api/libraries/:id/personalized?limit` → `continue-listening`, `continue-series`, `recently-added`, `recent-series`, `recommended`, `listen-again`, `newest-authors` |
| Search | `GET /api/libraries/:id/search?q&limit` |

**Filter:** `filter=<group>.<base64(value)>` (UTF-8 base64)
- `progress`: `finished`, `not-started`, `in-progress`, `not-finished`
- `genres`, `narrators`, `tags`, `languages`: plain value
- `authors`, `series`: id
- one filter per request

**Sort:** `media.metadata.title`, `media.metadata.authorNameLF`, `addedAt`, `progress`, `media.duration`, `media.metadata.publishedYear`; `sequence` when filtering by series; `desc=1`.

## Item

`GET /api/items/:id?expanded=1&include=progress` → `media.metadata` (title, subtitle, authors[], narrators[], series[{id,name,sequence}], genres[], language, publishedYear, publisher, description), `media.chapters[{id,start,end,title}]`, `media.audioFiles[]` (format, codec, bitRate, metadata.size), `media.duration`, `media.size`, `userMediaProgress`.

Cover: `?width=&height=&format=webp|jpeg&raw=0|1`. Folder conventions picked up by the scanner: `reader.txt` (narrator), `desc.txt` (description), `cover.jpg`, `Author/Series/N - Title/`.

## Playback

| Purpose | Call |
|---|---|
| Start | `POST /api/items/:id/play` `{deviceInfo{deviceId,clientName,clientVersion}, supportedMimeTypes[], forceDirectPlay, forceTranscode, mediaPlayer}` → session with `id`, `audioTracks[{startOffset,duration,contentUrl,mimeType}]`, `chapters`, `currentTime`, `libraryItem` |
| Sync | `POST /api/session/:id/sync` `{currentTime, timeListened, duration}` |
| Close | `POST /api/session/:id/close` (same body) |
| Offline sessions | `POST /api/session/local`, `POST /api/session/local-all` |

- Books can consist of several audio files; the client maps the global time to track + offset.
- `contentUrl` is relative; auth via `?token=`.
- `deviceInfo.model` is meant for the mobile apps (with `manufacturer`). Without it the server derives the device name from the user agent ("Windows 10 Edge"); a web client should not send one.

## Progress and bookmarks

| Purpose | Call |
|---|---|
| All progress | part of `GET /api/me` (`mediaProgress[]`) |
| Set progress | `PATCH /api/me/progress/:libraryItemId` `{currentTime, progress, isFinished, duration}` |
| In progress | `GET /api/me/items-in-progress` |
| Bookmarks | `GET /api/me/bookmarks/:libraryItemId` → `{ bookmarks: [...] }` (not a bare array) |
| Create / update | `POST` / `PATCH /api/me/item/:id/bookmark` `{time, title}` |
| Delete | `DELETE /api/me/item/:id/bookmark/:time` |
| Stats | `GET /api/me/listening-stats`, `GET /api/me/stats/year/:year` |

Bookmarks only have `time` + `title`. Progress entries carry `lastUpdate` (server time).

## More endpoints in use

| Purpose | Call |
|---|---|
| Several items at once | `POST /api/items/batch/get` `{ libraryItemIds }` → `{ libraryItems }` |
| Hide from continue listening | `GET /api/me/progress/:progressId/remove-from-continue-listening` (progress id, not item id) |
| Listening stats | `GET /api/me/listening-stats` → `totalTime, today, days{date: sec}, dayOfWeek, items{id: {timeListening, mediaMetadata}}` |
| Collections | `GET /api/libraries/:id/collections` → `{ results }`, `GET /api/collections/:id` |
| File download | `GET /api/items/:id/file/:ino/download` (needs the user's download permission, `user.permissions.download`) |
| Whole item download | `GET /api/items/:id/download`: a ZIP of the item folder (`<title>.zip`), or the file itself for single-file items (`isFile`); same permission; answered as an attachment |
| Listening history | `GET /api/me/listening-sessions?itemsPerPage&page` → `{ total, numPages, page, itemsPerPage, sessions }`, newest first; per book `GET /api/me/item/listening-sessions/:libraryItemId`. A session has `startTime`/`currentTime` (book position at start and end), `timeListening`, `startedAt`, `updatedAt`, `date`, `deviceInfo` |
| Offline session upload | `POST /api/session/local` with a full session (id, libraryItemId, currentTime, timeListening, startedAt, updatedAt, date, dayOfWeek, playMethod 3); the server keeps whichever progress is newer |
| Live events | socket.io `/socket.io`: emit `auth` with the access token; `user_item_progress_updated` `{ id, sessionId, data }`; `auth_failed` on expired tokens |

- The auth rate limiter (`RATE_LIMIT_AUTH_MAX`, default 10 per 10 min) also covers `/auth/refresh`.
- Unauthenticated `/status` also returns `authMethods` (e.g. `["local", "openid"]`).
- `GET /api/me/sessions` lists the user's *login* sessions (signed-in devices: `userAgent`, `ipAddress`, `createdAt`, `updatedAt`, `current`); `current` is only set when the request carries the refresh token (header or cookie). `DELETE /api/me/sessions/:id` signs that device out. Single listening sessions cannot be deleted by users.

## Browse, playlists, account

| Purpose | Call |
|---|---|
| Author with books and series | `GET /api/authors/:id?include=items,series&library=:libraryId` → books in the compact format; in `series[].items` the volume is `media.metadata.series` (one object, not a list) |
| Series with progress | `GET /api/libraries/:id/series/:seriesId?include=progress` → `progress { libraryItemIds, libraryItemIdsFinished, isFinished }`; books via the series filter, sorted by `sequence` |
| "Continue series" shelf | `GET /api/me/series/:id/remove-from-continue-listening`, `…/readd-to-continue-listening` |
| Narrators | `GET /api/libraries/:id/narrators` → `{ narrators: [{ id, name, numBooks }] }` |
| All bookmarks | `GET /api/me/bookmarks` → `{ bookmarks }` |
| Year in review | `GET /api/me/stats/year/:year` → totals, `topAuthors`, `topGenres`, `mostListenedNarrator`, `mostListenedMonth` (0-11), `numBooksFinished`, `longestAudiobookFinished`, `booksWithCovers` (item ids) |
| Library statistics | `GET /api/libraries/:id/stats` → totals, `longestItems`, `largestItems`, `genresWithCount`, `authorsWithCount` |
| Playlists | `GET /api/libraries/:id/playlists`, `GET/PATCH/DELETE /api/playlists/:id` (PATCH `items` replaces the list, so it also reorders), `POST /api/playlists` `{ libraryId, name, items: [{ libraryItemId }] }`, `POST /api/playlists/:id/item`, `DELETE /api/playlists/:id/item/:libraryItemId` |
| Password | `PATCH /api/me/password` `{ password, newPassword }` (not for guests) |
| More filters | `publishers`, `publishedDecades`, `tags`, `languages`, `missing`, `tracks`, `ebooks`; sorts also `progress.createdAt`, `progress.finishedAt`, `random`, `size`. `explicit.<base64("true")>` and `abridged.…` returned every book on 2.37.1 (no filtering), so KoalaShelf shows these as details on the book page instead |
| Search | also returns `genres` and `tags` as `{ name, numItems }`; matches titles, not author names (those come as `authors`) |

## Managing books and lists

| Purpose | Call |
|---|---|
| Progress of several books | `PATCH /api/me/progress/batch/update` with an array `[{ libraryItemId, isFinished, currentTime?, progress? }]` |
| Forget a book's progress | `DELETE /api/me/progress/:progressId` (the progress entry's id) |
| Playlist: several books | `POST /api/playlists/:id/batch/add` and `…/batch/remove` with `{ items: [{ libraryItemId }] }` |
| Playlist from a collection | `POST /api/playlists/collection/:collectionId` → a new playlist with the collection's name and books |
| Collections | `POST /api/collections` `{ libraryId, name, description?, books: [itemId] }`, `PATCH /api/collections/:id` `{ name?, description?, books? }` (`books` reorders), `DELETE /api/collections/:id`, `POST /api/collections/:id/book` `{ id }`, `DELETE /api/collections/:id/book/:itemId`, `POST /api/collections/:id/batch/add` and `…/batch/remove` `{ books: [itemId] }` |

- Collections belong to the library (everyone sees them). `POST`/`PATCH` need the user's `update` permission, `DELETE` needs `delete`.
- A new collection must have at least one book ("Invalid collection data. No books").
- A playlist whose last book is removed is deleted by the server.

## Quirks seen on 2.37.1

- **Duplicate progress.** After a book's progress is deleted while a playback session for it is still open, a late sync can create a second progress entry for the same book. `/api/me` then lists both, and later updates may change either one. Clients should use the entry with the newest `lastUpdate`.
- **Crash on close.** Closing (`POST /api/session/:id/close`) a session whose progress was deleted meanwhile throws an unhandled `SequelizeInstanceError` and the server process restarts.

## Not in the server

- Sleep timer, queue ("up next") and listener settings: client-side only.
- "Share clip": the server only has admin shares of whole items (`/api/share/mediaitem`).
