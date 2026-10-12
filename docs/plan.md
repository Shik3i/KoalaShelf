# KoalaShelf: Plan

Stand: 2026-10-12. Briefing: [briefing.md](briefing.md). Design: [design/koalashelf-design-grundlagen.png](design/koalashelf-design-grundlagen.png). API: [abs-api-notes.md](abs-api-notes.md).

## Stack

| Teil | Wahl | Warum |
|---|---|---|
| Monorepo | **pnpm Workspaces** | Schnell, strikte Abhängigkeiten, ein Lockfile für Web, Desktop und Pakete. Android bleibt ein eigenes Gradle-Projekt im selben Repo. |
| Web | **SvelteKit (Svelte 5), TypeScript, `adapter-static` als SPA** | Kein SSR nötig, denn die Server-URL kommt erst zur Laufzeit. Statischer Build läuft als PWA, hinter einem Reverse-Proxy und unverändert in Tauri. Kleine Bundles, Runes eignen sich gut für den Player-Zustand. |
| PWA | `@vite-pwa/sveltekit` (Workbox) | Manifest, Service Worker, Cover-Cache. |
| Styles | Plain CSS + CSS-Variablen aus `design-tokens` | Container Queries (`cqw`) für Cover. Kein Tailwind, damit kein Wert hart codiert wird. |
| Schrift | `@fontsource/gloock`, `@fontsource-variable/manrope` | Selbst gehostet: offline-fähig, keine Google-Anfragen. Beide OFL. |
| Tests | Vitest (Logik: Zeit ↔ Track, Filter, Token-Refresh), Playwright (Smoke) | |
| Desktop | **Tauri 2** | Nutzt den Web-Build. Klein, Rust-HTTP-Plugin umgeht CORS, Tokens im OS-Schlüsselbund. |
| Android | **Kotlin, Jetpack Compose, Media3** (`MediaLibraryService`), Ktor + kotlinx.serialization, Room, DataStore + Android Keystore | Hintergrundwiedergabe, Benachrichtigung, später Android Auto ohne Umbau. |
| Tokens | **Style Dictionary v4**, Quelle im DTCG-JSON-Format | Eine Quelle → CSS (`.th-light`/`.th-dark`), TypeScript, Kotlin (Compose). |

**Entscheidung SvelteKit vs. React:** SvelteKit. React + Vite ginge auch, bringt hier aber nur mehr Code für denselben Zweck.

## Ordnerstruktur

```
KoalaShelf/
├─ apps/
│  ├─ web/                 SvelteKit-SPA + PWA
│  │  └─ src/lib/
│  │     ├─ api/           dünne Hülle um packages/abs-client (Auth-Store, Refresh)
│  │     ├─ player/        Audio-Engine, Session-Sync, Timer, Media Session API
│  │     ├─ components/    Cover, Platte, Tonarm, Kiste, Regal, Chip, Zeitleiste …
│  │     └─ routes/        login, start, bibliothek, serien, item/[id]
│  ├─ desktop/             Tauri 2 (src-tauri/), lädt apps/web/build
│  └─ android/             Gradle-Projekt (app/, core/designsystem, core/network, core/player)
├─ packages/
│  ├─ design-tokens/
│  │  ├─ tokens/           color.light.json, color.dark.json, type.json, space.json, radius.json, motion.json
│  │  ├─ build.mjs         Style-Dictionary-Konfiguration
│  │  └─ dist/             tokens.css, tokens.ts (generiert, nicht eingecheckt)
│  └─ abs-client/          TypeScript-Typen + fetch-Client für die ABS-API (Web und Desktop)
├─ docs/                   Briefing, Design, API-Notizen, Plan
├─ package.json
└─ pnpm-workspace.yaml
```

Die Kotlin-Ausgabe schreibt der Token-Build direkt nach `apps/android/core/designsystem/.../KoalaTokens.kt` und **checkt sie ein**. So baut Android ohne Node.

## Design-Tokens: Umfang

- Farben laut Tabelle im Briefing, je Theme: outer, bg, panel, card, ink, muted, line, ctrl, ctrl-ink, accent, on-accent, track, ledge, sel, sel-ink, cur-bg, cur-ink, platter, vinyl-a, vinyl-b.
- Schrift: Familien (Gloock, Manrope), Gewichte 400/600/800, Größen 12, 13, 14, 15, 18, 24, 34, 56, Player-Zeit 38, Label-Stil (12/800/Versalien/+0,1 em), Cover-Typo (Autor 4,4 cqw, Titel 13 cqw).
- Abstände: 4er-Raster (4 … 64).
- Radien: cover 5, card 16/22, pill 999.
- Bewegung: Platte 9 s linear, Tonarm 18° + p × 22°.
- Layout: Sidebar 196 px, Breakpoint 800 px, Touch-Ziel 44 px.
- Build prüft die Kontraste (ink/bg, muted/card, on-accent/accent ≥ 4,5:1) und bricht bei Verstößen ab.

Hinweis: In Hell erreicht `on-accent #FFFFFF` auf `accent #C23D20` ca. 5,3:1. Passt. Dunkel `#1A1206` auf `#E8A33D` passt ebenfalls deutlich.

## Phasen

Jede Phase endet mit lauffähigem Stand, Test-Anleitung in `docs/` und Commit. Dazwischen committe ich in kleinen, logischen Schritten und pushe regelmäßig.

1. **Plan, Stack, Repo, Design-Tokens**
   pnpm-Workspace, `packages/design-tokens` mit Build (CSS, TS, Kotlin) und Kontrastprüfung, eine statische Token-Vorschauseite, `packages/abs-client` nur mit Typen.
   *Test:* `pnpm tokens:build`, Vorschauseite hell/dunkel ansehen.
2. **Web lesend**
   Login (URL, Benutzer, Passwort, Access/Refresh-Token, automatische Erneuerung), Bibliothekswahl, Regal und Liste mit Pagination, Serien, Detailseite inkl. Kapitel und Datei-Infos, Cover-Komponente mit Platzhalter, Layout mit Seitenleiste bzw. Tab-Leiste.
3. **Web Player**
   Session starten, Multi-Track-Wiedergabe, Sync alle 15 s und beim Pausieren/Schließen, Kapitel, ±30 s, Geschwindigkeit 0,8–2,0, Sleep-Timer (Minuten und „bis Kapitelende“), Lesezeichen als Pins, Mini-Player, Platte mit Tonarm und Kapitel-Ticks, Media Session API (Sperrbildschirm, Tasten).
4. **Web Start, Kisten, Filter, Theme, PWA**
   Startseite aus `/personalized` (Weiterhören, Kisten), Filter Status/Genre/Sprecher:in/Serie, Sortierung, Theme-Umschalter (System/Hell/Dunkel), PWA-Manifest und Service Worker.
5. **Desktop**
   Tauri-Hülle, HTTP über Rust (kein CORS), Tokens im Schlüsselbund, Tastatur-Medientasten, Installer für Windows.
6. **Android**
   Compose-UI mit den Tokens, Media3-Service, Hintergrundwiedergabe, Benachrichtigung, Downloads mit Room, Offline-Sessions über `/api/session/local`.

## Risiken und Annahmen

- **CORS:** Die Web-App erreicht den ABS-Server nur bei gleicher Origin oder mit Eintrag in den ABS-Einstellungen (`allowedOrigins`). Der Server bleibt dabei unverändert, es ist nur eine Einstellung. Für die Entwicklung nutze ich einen Vite-Proxy.
- **Token-Speicher im Browser:** Es gibt dort keinen wirklich sicheren Speicher. Plan: Access-Token nur im Speicher, Refresh-Token in IndexedDB. Bei gleicher Origin stattdessen das httpOnly-Cookie des Servers (sicherer).
- **Audio-Auth:** `<audio>` kann keinen Header senden, also `?token=`. Läuft der Token ab, erneuern und an derselben Stelle weiterspielen.
- **Server-Version:** Access/Refresh-Tokens gibt es erst in neueren Versionen. Ich setze **ABS ≥ 2.26** voraus und zeige sonst einen Hinweis.
- **Filter:** Der Server nimmt nur einen Filter je Anfrage. Kombinierte Filter schneidet der Client.
- **Status „Neu“:** Ich bilde ihn auf `not-started` ab. Die Kiste „Neu“ auf Start nutzt `recently-added`.
- **„Notiz“ am Lesezeichen** ist der Lesezeichen-Titel. Ein extra Feld gibt es nicht.
- **„Clip teilen“** kann der Server nicht. Bleibt bis zur Klärung draußen.
- **Android-Paketname:** Vorschlag `net.koalastuff.koalashelf`.

## Werkzeuge

- Vorhanden: Node 25, npm 11, Git, gh.
- Fehlt: **pnpm** (`npm i -g pnpm`, ab Phase 1), **Rust/Cargo** (erst Phase 5), Android Studio/JDK 17+ (erst Phase 6).
