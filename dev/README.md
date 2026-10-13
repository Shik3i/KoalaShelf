# dev: local Audiobookshelf test server

A local Audiobookshelf 2.37.1 with fictional sample audiobooks (test tones, chapters, covers). Used for development and the end-to-end tests. Nothing leaves your machine.

Requirements: Docker, ffmpeg.

```bash
node dev/make-samples.mjs                  # generate the sample books (once)
docker compose -f dev/compose.yaml up -d   # start Audiobookshelf on http://localhost:13378
node dev/setup-abs.mjs                     # create users, libraries and a collection (idempotent; --reset starts over)
```

## What you get

- **Hörbücher**: 7 books, including a two-book series, a multi-file book (`Nachtzug`), a book without chapters (`Ohne Kapitel`) and books with and without covers
- **Kurzgeschichten**: 70 tiny stories, for paging (infinite scroll) and switching libraries
- A collection (`Lieblinge`), an admin user and a guest user without download permission
- Credentials are generated on the first run and stored in `dev/.env` (gitignored, never commit it)

The compose file sets `RATE_LIMIT_AUTH_MAX=0` because the test suite signs in often. Never do that on a real server.

## Using it

- Web app against this server: `pnpm --filter @koalashelf/web dev` (the Vite proxy reads `ABS_URL` from `dev/.env`; `apps/web/.env.local` can point it at another server).
- End-to-end tests: `pnpm --filter @koalashelf/web test:e2e` starts its own dev server on port 5174.
- Production container for `E2E_BASE_URL` runs:

```bash
docker build -f apps/web/Dockerfile -t koalashelf-web .
docker run -d --name ks-test --network dev_default -p 8088:8080 -e ABS_URL=http://audiobookshelf:80 -e ABS_PUBLIC_URL=http://localhost:13378 koalashelf-web
E2E_BASE_URL=http://localhost:8088 pnpm --filter @koalashelf/web test:e2e
```
