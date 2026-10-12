# KoalaShelf

Eigener Client für einen [Audiobookshelf](https://www.audiobookshelf.org)-Server (Hörbücher): Website/PWA, native Desktop-App und native Android-App.
Der Server bleibt unverändert, KoalaShelf ersetzt nur die Oberfläche. Design: „KoalaShelf Vinyl“.

![Design-Grundlagen](docs/design/koalashelf-design-grundlagen.png)

## Struktur

| Ordner | Inhalt |
|---|---|
| [`apps/web`](apps/web) | Website / PWA (SvelteKit 3), später als Docker-Image |
| [`apps/desktop`](apps/desktop) | Native Desktop-App (Tauri 2) |
| [`apps/android`](apps/android) | Native Android-App (Kotlin, Compose, Media3) |
| [`packages/design-tokens`](packages/design-tokens) | Farben, Schrift, Abstände: eine Quelle für alle Plattformen |
| [`packages/abs-client`](packages/abs-client) | TypeScript-Client für die Audiobookshelf-API |
| [`docs`](docs) | Briefing, Plan, Tech-Stack, API-Notizen, Design |

## Status

Phase 1: Repo und Design-Tokens. Siehe [docs/plan.md](docs/plan.md).

## Voraussetzungen

- Node.js 26 (oder 24 LTS), pnpm 12
- Für Desktop: Rust stable
- Für Android: Android Studio

Versionen und Begründungen: [docs/tech-stack.md](docs/tech-stack.md).
