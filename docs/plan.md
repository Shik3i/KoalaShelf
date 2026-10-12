# KoalaShelf: Plan

Stand: 2026-10-12.

- Briefing: [briefing.md](briefing.md)
- Design: [design/koalashelf-design-grundlagen.png](design/koalashelf-design-grundlagen.png)
- API: [api/audiobookshelf.md](api/audiobookshelf.md)
- Versionen: [tech-stack.md](tech-stack.md)

## Stack

Grundsatz: jeweils die aktuelle stabile Hauptversion (Stand Oktober 2026). Keine Alpha/Beta/RC-Versionen. Ausnahmen stehen begründet in [tech-stack.md](tech-stack.md).

| Teil | Wahl | Warum |
|---|---|---|
| Monorepo | **pnpm 12 Workspaces**, Node 26 | Ein Lockfile für Web, Desktop und Pakete. Android bleibt ein eigenes Gradle-Projekt im selben Repo. |
| Web | **SvelteKit 3, Svelte 5 (Runes), Vite 8, TypeScript**, `adapter-static` als SPA | Kein SSR nötig, die Server-URL kommt erst zur Laufzeit. Der statische Build läuft als PWA, im Docker-Container und unverändert in Tauri. |
| PWA | SvelteKit-eigener Service Worker (`src/service-worker.ts`) + Web-App-Manifest | Kein Plugin nötig. `@vite-pwa/sveltekit` unterstützt SvelteKit 3 noch nicht. |
| Styles | Plain CSS mit CSS-Variablen aus `design-tokens`, Container Queries, `color-scheme`, `light-dark()` wo sinnvoll | Kein Wert hart codiert, kein Framework-Ballast. |
| Schrift | `@fontsource/gloock`, `@fontsource-variable/manrope` | Selbst gehostet, offline-fähig, OFL. |
| Qualität | ESLint 10 (Flat Config), Prettier 3, svelte-check, Vitest 5, Playwright | |
| Web-Hosting (später) | Docker-Image: Caddy liefert die SPA aus und leitet `/api`, `/auth`, `/login`, `/socket.io` an ABS weiter | Gleiche Origin, also kein CORS und der Refresh-Token kann als httpOnly-Cookie bleiben. |
| Desktop | **Tauri 2.12**, Rust stable | Nutzt den Web-Build. HTTP über Rust (kein CORS), Tokens im OS-Schlüsselbund. |
| Android | **Kotlin 2.4, AGP 9.4 (eingebautes Kotlin), Gradle 9.8, compileSdk/targetSdk 37**, Compose (BOM 2026.09), Material 3, Navigation 3, Media3 1.11 (`MediaLibraryService`), Ktor 3.6, kotlinx.serialization, Room 3, DataStore, Coil 3, Koin 4 | Aktuelle Jetpack-Generation: Navigation 3 statt Navigation-Compose, Room 3, AGP 9 ohne separates Kotlin-Plugin. Media3 bringt Hintergrundwiedergabe, Benachrichtigung und später Android Auto. |
| Tokens | **Style Dictionary 5**, Quelle im DTCG-Format (`$value`, `$type`) | Eine Quelle → CSS (`.th-light`/`.th-dark`), TypeScript, Kotlin (Compose). |

## Repo-Struktur

```
KoalaShelf/
├─ apps/
│  ├─ web/          Website / PWA (SvelteKit). Später mit Dockerfile + Caddyfile.
│  ├─ desktop/      Native Desktop-App (Tauri 2), nutzt den Build von apps/web
│  └─ android/      Native Android-App (Gradle, Kotlin, Compose, Media3)
├─ packages/
│  ├─ design-tokens/  Farben, Schrift, Abstände: eine Quelle für Web, Desktop, Android
│  └─ abs-client/     TypeScript-Typen + Client für die Audiobookshelf-API
├─ docs/
│  ├─ briefing.md     Auftrag
│  ├─ plan.md         dieses Dokument
│  ├─ tech-stack.md   Versionen und Begründungen
│  ├─ api/            geprüfte API-Notizen
│  └─ design/         Design-Grundlagen
├─ package.json       Workspace-Root (nur Skripte, keine App)
└─ pnpm-workspace.yaml
```

Innerhalb der Apps:

- `apps/web/src/lib/`: `api/` (Auth-Store, Token-Refresh), `player/` (Audio-Engine, Sync, Timer, Media Session), `components/` (Cover, Platte, Tonarm, Kiste, Regal, Chip, Zeitleiste), `routes/` (login, start, bibliothek, serien, item/[id]).
- `apps/android/`: Module `app`, `core/designsystem`, `core/network`, `core/data`, `core/player`, `feature/*`. Gradle Version Catalog in `gradle/libs.versions.toml`.
- Die Kotlin-Ausgabe der Tokens landet in `apps/android/core/designsystem/` und wird **eingecheckt**. So baut Android ohne Node.

## Design-Tokens: Umfang

- Farben laut Briefing, je Theme: outer, bg, panel, card, ink, muted, line, ctrl, ctrl-ink, accent, on-accent, track, ledge, sel, sel-ink, cur-bg, cur-ink, platter, vinyl-a, vinyl-b.
- Schrift: Gloock 400, Manrope 400/600/800. Größen 12, 13, 14, 15, 18, 24, 34, 56, Player-Zeit 38. Label 12/800/Versalien/+0,1 em. Cover-Typo: Autor 4,4 cqw, Titel 13 cqw.
- Abstände im 4er-Raster. Radien: cover 5, card 16/22, pill voll rund.
- Bewegung: Platte 9 s linear, Tonarm 18° + p × 22°.
- Layout: Sidebar 196 px, Breakpoint 800 px, Touch-Ziel 44 px.
- Der Build prüft die Kontraste (ink/bg, muted/card, on-accent/accent ≥ 4,5:1) und bricht bei Verstößen ab. Hell `#FFFFFF` auf `#C23D20` ≈ 5,3:1.

## Phasen

Jede Phase endet mit lauffähigem Stand, Test-Anleitung und Commit. Dazwischen committe ich in kleinen, logischen Schritten und pushe regelmäßig.

1. **Repo, Design-Tokens**: pnpm-Workspace, Lint/Format, `packages/design-tokens` mit Build (CSS, TS, Kotlin) und Kontrastprüfung, Token-Vorschau, `packages/abs-client` mit Typen.
2. **Web lesend**: Login mit Access/Refresh-Token, Bibliothekswahl, Regal und Liste, Serien, Detailseite, Cover mit Platzhalter, Layout mit Seitenleiste bzw. Tab-Leiste.
3. **Web-Player**: Session, Multi-Track, Sync, Kapitel, ±30 s, Geschwindigkeit, Sleep-Timer, Lesezeichen als Pins, Mini-Player, Platte mit Tonarm, Media Session API.
4. **Web: Start, Kisten, Filter, Theme, PWA, Docker**: Startseite aus `/personalized`, Filter und Sortierung, Theme-Umschalter, Service Worker, Docker-Image mit Caddy.
5. **Desktop**: Tauri-Hülle, HTTP über Rust, Schlüsselbund, Medientasten, Windows-Installer.
6. **Android**: Compose mit Tokens, Media3-Service, Hintergrundwiedergabe, Downloads mit Room, Offline-Sessions.

## Risiken und Annahmen

- **CORS:** Ohne gleiche Origin muss die Web-Adresse in den ABS-Einstellungen unter `allowedOrigins` stehen. Das Docker-Image (Phase 4) löst das per Reverse-Proxy. In der Entwicklung nutze ich den Vite-Proxy.
- **Token im Browser:** Bei gleicher Origin nutze ich das httpOnly-Cookie des Servers. Sonst bleibt der Access-Token nur im Speicher und der Refresh-Token liegt in IndexedDB.
- **Audio-Auth:** `<audio>` kann keine Header senden, deshalb `?token=`. Läuft der Token ab, erneuern und an derselben Stelle weiterspielen.
- **Server-Version:** ABS ≥ 2.26 vorausgesetzt (Access/Refresh-Tokens).
- **Filter:** Der Server nimmt nur einen Filter pro Anfrage. Kombinationen schneidet der Client.
- **Status „Neu“** = `not-started`. Die Kiste „Neu“ = `recently-added`.
- **„Notiz“ am Lesezeichen** = Lesezeichen-Titel.
- **„Clip teilen“** kann der Server nicht. Bleibt draußen bis zur Klärung.
- **Android-Paketname:** `net.koalastuff.koalashelf`.
