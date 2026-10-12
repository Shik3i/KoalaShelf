# Tech-Stack und Versionen

Geprüft am 2026-10-12 gegen npm, Maven Central, Google Maven, crates.io, nodejs.org und services.gradle.org.
Regel: aktuelle stabile Version, keine Alpha/Beta/RC. Genaue Versionen stehen später in `package.json` bzw. `apps/android/gradle/libs.versions.toml`. Dieses Dokument begründet die Wahl.

## Laufzeit und Werkzeuge

| Werkzeug | Version | Hinweis |
|---|---|---|
| Node.js | 26.x | Wird Ende Oktober 2026 LTS. 24.x (aktuelle LTS) funktioniert auch. Node 25 ist bereits End-of-Life. |
| pnpm | 12.11 | Über `packageManager` in `package.json` fixiert. |
| Rust | 1.99 stable | Erst ab Phase 5. |
| JDK | von Android Studio mitgeliefert | Erst ab Phase 6. |

## Web und Pakete

| Paket | Version | Hinweis |
|---|---|---|
| SvelteKit | 3.0 | |
| Svelte | 5.57 | Runes |
| Vite | 8.3 | Rolldown-basiert |
| `@sveltejs/adapter-static` | 4.0 | SPA-Build für Web, Docker, Tauri |
| TypeScript | **6.0** | TypeScript 7.0 (nativer Compiler) ist erschienen. Aber SvelteKit 3, svelte-check und typescript-eslint unterstützen bisher nur ≤ 6. Wechsel auf 7, sobald sie nachziehen. |
| Vitest | 5.0 | |
| Playwright | 1.64 | |
| ESLint | 10 (Flat Config) | mit typescript-eslint 8.71, eslint-plugin-svelte 3 |
| Prettier | 3.9 | mit prettier-plugin-svelte 4 |
| Style Dictionary | 5.6 | DTCG-Token-Format |
| Fonts | `@fontsource/gloock` 5.3, `@fontsource-variable/manrope` 5.3 | |
| socket.io-client | 4.8 | erst ab Phase 3 |
| PWA | SvelteKit-Service-Worker | `@vite-pwa/sveltekit` 1.1 unterstützt SvelteKit 3 noch nicht (Peer `^2`). |

## Desktop

| Paket | Version |
|---|---|
| Tauri (crate, CLI, API) | 2.12 |

## Android

| Bibliothek | Version | Hinweis |
|---|---|---|
| Android Gradle Plugin | 9.4.1 | eingebaute Kotlin-Unterstützung |
| Gradle | 9.8.1 | |
| Kotlin | 2.4.21 | |
| compileSdk / targetSdk | 37 | minSdk 29 (Vorschlag) |
| Compose BOM | 2026.09.00 | |
| Material 3 | 1.4 | |
| Navigation 3 | 1.2 | ersetzt Navigation-Compose |
| Lifecycle | 2.11 | |
| Activity Compose | 1.13 | |
| Media3 (ExoPlayer, Session) | 1.11.1 | `MediaLibraryService` |
| Room | 3.0.3 (`androidx.room3`) | |
| DataStore | 1.2.1 | |
| Ktor Client | 3.6 | |
| kotlinx.serialization / coroutines | 1.11 | |
| Coil | 3.6 | |
| Koin | 4.2 | DI ohne Annotation-Processing |
| KSP | 2.3.12 | für Room |

## Internationalisierung

Alles ist international: Routen, Code und Doku-Kommentare sind englisch. Jeder UI-Text kommt aus `apps/web/src/lib/i18n/` (Englisch als Basis, Deutsch). Fehlt in einer Sprache ein Schlüssel, schlägt die Typprüfung fehl.

Eigene, abhängigkeitsfreie Lösung statt Paraglide: Paraglide lädt beim Build Plugins von einem CDN. Das passt nicht zu offline baubaren, selbst gehosteten Builds. Die Sprache folgt dem Browser und lässt sich manuell umschalten. Die Wahl liegt nur lokal.
