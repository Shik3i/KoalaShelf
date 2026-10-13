# packages/abs-client

Typed, dependency-free `fetch` client for the Audiobookshelf API (2.26+), used by `apps/web` and later `apps/desktop`.

- Access/refresh tokens with single-flight rotation and a retry on 401; cookie mode for same-origin hosting
- `ensureFresh()` renews a token that is about to expire before its URL is handed to the browser (downloads, media); `tokenExpiry()` reads a JWT's expiry locally
- Public cover URLs (no token in image URLs), token-carrying stream and download URLs (`trackUrl`, `downloadUrl`)
- Libraries, items (single and batch), filter data, series, authors, collections, personalized shelves, search
- Playback sessions (start, sync, close, offline sessions), progress, bookmarks, listening stats and listening history (`listeningSessions`, `itemListeningSessions`)
- UTF-8 safe filter encoding (`encodeFilter`), server status and version check

```bash
pnpm --filter @koalashelf/abs-client test
```

Based on the verified notes in [docs/api/audiobookshelf.md](../../docs/api/audiobookshelf.md).
