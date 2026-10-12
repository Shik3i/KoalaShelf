# Audiobookshelf-API: geprüfte Notizen

Die offizielle Doku (https://api.audiobookshelf.org) ist laut eigener Aussage veraltet.
Die Endpunkte unten habe ich gegen den Server-Quellcode geprüft (advplyr/audiobookshelf, Release **v2.37.1**, 2026-09-29).
Quellcode schlägt Doku.

## Auth

| Zweck | Aufruf |
|---|---|
| Login | `POST /login` `{username, password}`, Header `x-return-tokens: true` → `user.accessToken` (kurzlebig) + `user.refreshToken` |
| Erneuern | `POST /auth/refresh`, Header `x-refresh-token: <rt>` → neues Paar (Rotation) |
| Logout | `POST /logout` (Refresh-Token im Header oder Cookie) |
| Prüfen | `GET /api/authorize`, `GET /api/me` |
| Server-Infos | `GET /status`, `GET /ping` |

- Requests: `Authorization: Bearer <accessToken>`. Bei GET geht auch `?token=` (wichtig für `<audio src>`).
- Ohne `x-return-tokens` setzt der Server den Refresh-Token als httpOnly-Cookie (nur sinnvoll bei gleicher Origin).
- Das alte `user.token` (Langzeit-Token) gilt als veraltet.
- OpenID (PKCE) existiert: `/auth/openid`, `/auth/openid/callback`. Erst später.

## CORS (wichtig für die Web-App)

Der Server sendet CORS-Header nur, wenn:
- `ALLOW_CORS=1` gesetzt ist, oder
- die Origin in den Server-Einstellungen unter `allowedOrigins` steht, oder
- die Origin `capacitor://localhost` / `http://localhost` ist.

Die Web-App braucht also **gleiche Origin** (Reverse-Proxy) oder einen Eintrag in `allowedOrigins`.
Tauri kann Anfragen über das Rust-HTTP-Plugin schicken und umgeht CORS. Android ist nicht betroffen.

## Bibliothek

| Zweck | Aufruf |
|---|---|
| Bibliotheken | `GET /api/libraries` |
| Items | `GET /api/libraries/:id/items?limit&page&sort&desc&filter&minified&collapseseries` |
| Filterwerte | `GET /api/libraries/:id/filterdata` (authors, genres, tags, series, narrators, languages) |
| Serien | `GET /api/libraries/:id/series?limit>0&page&sort&desc`, `GET /api/libraries/:id/series/:seriesId` |
| Autor:innen | `GET /api/libraries/:id/authors` |
| Sammlungen | `GET /api/libraries/:id/collections` |
| Startseite | `GET /api/libraries/:id/personalized?limit` → Shelves `continue-listening`, `continue-series`, `recently-added`, `recent-series`, `recommended`, `listen-again`, `newest-authors` |
| Suche | `GET /api/libraries/:id/search?q&limit` |

**Filter:** `filter=<gruppe>.<base64(wert)>`
- `progress`: `finished`, `not-started`, `in-progress`, `not-finished`
- `genres`, `narrators`, `tags`, `languages`: Klartext-Wert
- `authors`, `series`: ID
- `recent`: kürzlich hinzugefügt
- Nur **ein** Filter pro Request. Kombinationen (z. B. Genre + Laufend) muss der Client selbst schneiden.

**Sortierung:** `sort=media.metadata.title`, `media.metadata.authorName`, `media.duration`, `addedAt`, `media.metadata.publishedYear`, `progress` …; `desc=1`.

## Item

- `GET /api/items/:id?expanded=1&include=progress,authors` → `media.metadata` (title, subtitle, authors[], narrators[], series[{id,name,sequence}], genres[], language, publishedYear, description), `media.chapters[{id,start,end,title}]`, `media.audioFiles[]` (format, codec, bitRate, metadata.size), `media.tracks[]`, `media.duration`, `media.size`, `userMediaProgress`.
- Cover: `GET /api/items/:id/cover?width=&height=&format=webp|jpeg&raw=0|1`.

## Wiedergabe

| Zweck | Aufruf |
|---|---|
| Session starten | `POST /api/items/:id/play` `{deviceInfo{deviceId,clientName,clientVersion,manufacturer,model}, forceDirectPlay, forceTranscode, supportedMimeTypes[], mediaPlayer}` → Session mit `id`, `audioTracks[{index,startOffset,duration,contentUrl,mimeType}]`, `chapters`, `currentTime` |
| Sync | `POST /api/session/:id/sync` `{currentTime, timeListened, duration}` |
| Schließen | `POST /api/session/:id/close` (gleicher Body) |
| Offline-Sessions | `POST /api/session/local`, `POST /api/session/local-all` (Android-Offline) |

- Ein Hörbuch kann **mehrere Audiodateien** haben. Der Player rechnet Gesamtzeit ↔ Track + Offset um.
- `contentUrl` ist relativ; Auth per `?token=`. Läuft der Access-Token ab, Token erneuern und `src` an derselben Stelle neu setzen.

## Fortschritt und Lesezeichen

| Zweck | Aufruf |
|---|---|
| Fortschritt lesen | `GET /api/me/progress/:id` |
| Fortschritt setzen | `PATCH /api/me/progress/:libraryItemId` `{currentTime, progress, isFinished, duration}` |
| In Arbeit | `GET /api/me/items-in-progress` |
| Lesezeichen | `GET /api/me/bookmarks/:libraryItemId` |
| Anlegen | `POST /api/me/item/:id/bookmark` `{time, title}` |
| Ändern | `PATCH /api/me/item/:id/bookmark` `{time, title}` |
| Löschen | `DELETE /api/me/item/:id/bookmark/:time` |
| Statistik (später) | `GET /api/me/listening-stats`, `GET /api/me/stats/year/:year` |

- Lesezeichen haben nur `time` + `title`. **Die „Notiz“ ist der Titel.** Ein separates Notizfeld gibt es nicht.
- Socket.io (`/socket.io`, Event `auth` mit Token) meldet u. a. Fortschritt von anderen Geräten. Optional ab Phase 3.

## Nicht im Server

- Sleep-Timer: rein clientseitig.
- „Clip teilen“: Der Server kennt nur Admin-Freigaben ganzer Items (`/api/share/mediaitem`), keine Clips. Klären, bevor wir das bauen.
