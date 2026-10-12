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

- Requests: `Authorization: Bearer <accessToken>`. GET also accepts `?token=` (needed for `<audio src>`).
- Covers and author images are **public**: `GET /api/items/:id/cover`, `GET /api/authors/:id/image`.
- OpenID (PKCE) exists under `/auth/openid*`; not used yet.

## CORS

The server only sends CORS headers when `ALLOW_CORS=1`, when the origin is listed in the server setting `allowedOrigins`, or for `capacitor://localhost` / `http://localhost`. Hence the same-origin proxy in the Docker image. Tauri can bypass CORS via its Rust HTTP plugin; Android is not affected.

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
| Start | `POST /api/items/:id/play` `{deviceInfo{deviceId,clientName,clientVersion,model}, supportedMimeTypes[], forceDirectPlay, forceTranscode, mediaPlayer}` → session with `id`, `audioTracks[{startOffset,duration,contentUrl,mimeType}]`, `chapters`, `currentTime`, `libraryItem` |
| Sync | `POST /api/session/:id/sync` `{currentTime, timeListened, duration}` |
| Close | `POST /api/session/:id/close` (same body) |
| Offline sessions | `POST /api/session/local`, `POST /api/session/local-all` |

- Books can consist of several audio files; the client maps the global time to track + offset.
- `contentUrl` is relative; auth via `?token=`.

## Progress and bookmarks

| Purpose | Call |
|---|---|
| All progress | part of `GET /api/me` (`mediaProgress[]`) |
| Set progress | `PATCH /api/me/progress/:libraryItemId` `{currentTime, progress, isFinished, duration}` |
| In progress | `GET /api/me/items-in-progress` |
| Bookmarks | `GET /api/me/bookmarks/:libraryItemId` |
| Create / update | `POST` / `PATCH /api/me/item/:id/bookmark` `{time, title}` |
| Delete | `DELETE /api/me/item/:id/bookmark/:time` |
| Stats (later) | `GET /api/me/listening-stats`, `GET /api/me/stats/year/:year` |

Bookmarks only have `time` + `title`. Socket.io (`/socket.io`, event `auth` with the token) pushes progress from other devices; planned.

## Not in the server

- Sleep timer: client-side only.
- "Share clip": the server only has admin shares of whole items (`/api/share/mediaitem`).
