# dev: lokaler Audiobookshelf-Testserver

Fiktive Beispiel-Hörbücher (Testtöne, Kapitel, Cover) in einem lokalen Audiobookshelf. Nichts verlässt den Rechner.

Voraussetzungen: Docker, ffmpeg.

```bash
node dev/make-samples.mjs
docker compose -f dev/compose.yaml up -d
node dev/setup-abs.mjs
```

- Server: http://localhost:13378
- Zugangsdaten: werden beim ersten Lauf erzeugt und in `dev/.env` gespeichert (nicht im Repo).
- Web-App gegen diesen Server: `pnpm dev` (der Vite-Proxy nutzt `ABS_URL` aus `dev/.env`).
