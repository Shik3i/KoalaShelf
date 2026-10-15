# packages/abs-client

Typed, dependency-free `fetch` client for the Audiobookshelf API (2.26+), used by `apps/web` and later `apps/desktop`.

- Access/refresh tokens with single-flight rotation and a retry on 401; cookie mode for same-origin hosting
- `ensureFresh()` renews a token that is about to expire before its URL is handed to the browser (downloads, media); `tokenExpiry()` reads a JWT's expiry locally
- Public cover URLs (no token in image URLs), token-carrying stream and download URLs (`trackUrl`, `downloadUrl`)
- Libraries, items (single and batch), filter data (incl. publishers and decades), series and series detail, authors and author detail, narrators, collections, personalized shelves, search (incl. genres and tags)
- Playlists (list, create, rename, reorder, add, remove, several at once, from a collection, delete), collections (create, edit, reorder, add, remove, several at once, delete), all bookmarks, year and library statistics
- Progress of several books in one request, forgetting a book's progress
- Account: signed-in devices (with the current one marked) and remote sign-out, password change
- Playback sessions (start, sync, close, offline sessions), progress, bookmarks, listening stats and listening history (`listeningSessions`, `itemListeningSessions`)
- UTF-8 safe filter encoding (`encodeFilter`), server status and version check

```bash
pnpm --filter @koalashelf/abs-client test
```

Based on the verified notes in [docs/api/audiobookshelf.md](../../docs/api/audiobookshelf.md).
