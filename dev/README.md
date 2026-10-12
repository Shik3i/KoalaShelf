# dev: local Audiobookshelf test server

Fictional sample audiobooks (test tones, chapters, covers, one multi-file book) in a local Audiobookshelf. Nothing leaves your machine.

Requirements: Docker, ffmpeg.

```bash
node dev/make-samples.mjs
docker compose -f dev/compose.yaml up -d
node dev/setup-abs.mjs
```

- Server: http://localhost:13378
- Credentials are generated on the first run and stored in `dev/.env` (gitignored).
- Web app against this server: `pnpm --filter @koalashelf/web dev` (the Vite proxy reads `ABS_URL` from `dev/.env`).
