# apps/android

Native KoalaShelf Android app: Kotlin, Jetpack Compose, Media3. A standalone multi-module Gradle project, independent of the pnpm workspace.

**Status:** Feature-complete native client with dedicated release cycle. Fully implements the KoalaShelf Vinyl design language, background audio playback with Media3, faceted library search, offline downloads, analog crackle ambience, and multi-account switching.

## Architecture & Modules

The application follows a clean modular Android architecture:

| Module | Purpose |
|---|---|
| [`:app`](app) | Application entry point, Koin DI setup, Compose navigation graphs, release build config with R8 shrinking |
| [`:core:data`](core/data) | Repositories, DataStore preferences (`PrefsRepository`), Room database for local cache and downloads, string localization (`KoalaStrings`) |
| [`:core:designsystem`](core/designsystem) | `KoalaTokens.kt` (generated from [`packages/design-tokens`](../../packages/design-tokens)), `KoalaTheme` (light/dark with OKLCH contrast-checked accents), custom vinyl canvas components (turntable, spinning disc, grooves, tonearm) |
| [`:core:network`](core/network) | Ktor 3 HTTP client, Audiobookshelf API client, token renewal, session management, multi-account connection handling |
| [`:core:player`](core/player) | Media3 ExoPlayer integration (`KoalaPlayerController`), background playback service, lock screen MediaSession, synthesized analog vinyl crackle ambience, audio equalizer, voice boost, sleep timer with fade-out |
| [`:feature:auth`](feature/auth) | Server sign-in, server URL validation, multi-account switcher |
| [`:feature:home`](feature/home) | Home screen with continue-listening turntable and customizable shelf crates |
| [`:feature:library`](feature/library) | Faceted library filtering (status, author, narrator, genre, tag, language, duration), live search, narrator/author/series pages, book details, offline downloads, settings |
| [`:feature:player`](feature/player) | Fullscreen vinyl player bottom sheet with interactive tonearm, scrubber, speed selector (0.5×–3.5×), chapters, bookmarks, and sound controls |

## Development & Build

Requirements: JDK 21 (Temurin recommended) and Android SDK (compileSdk 37, minSdk 29).

```bash
cd apps/android
./gradlew test                   # Run unit tests across all modules
./gradlew :app:assembleDebug     # Build debug APK
./gradlew :app:assembleRelease   # Build production APK with R8 code shrinking and resource optimization
```

Release builds are automatically produced and published by GitHub Actions ([`release-android.yml`](../../.github/workflows/release-android.yml)) when pushing an `android-vX.Y.Z` or `vX.Y.Z` tag.

