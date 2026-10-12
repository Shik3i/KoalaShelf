# packages/abs-client

Typed, dependency-free `fetch` client for the Audiobookshelf API, used by `apps/web` and (later) `apps/desktop`.

- Access/refresh tokens with single-flight rotation; cookie mode for same-origin hosting
- Public cover URLs (no token in image URLs), token-carrying stream URLs
- UTF-8 safe filter encoding

Based on the verified notes in [docs/api/audiobookshelf.md](../../docs/api/audiobookshelf.md).
