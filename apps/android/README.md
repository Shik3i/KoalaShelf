# apps/android

Native KoalaShelf Android app: Kotlin, Jetpack Compose, Media3. A standalone multi-module Gradle project, independent of the pnpm workspace.

**Status:** Production release (v1.1.0). Fully implements the KoalaShelf Vinyl design language, background audio playback with Media3, Android Auto automotive browse tree, live turntable home screen app widget, bedtime zero-glare gesture mode, tactile vinyl haptics, HTTP Range download resume, WorkManager offline session sync, faceted library search, analog crackle ambience & warmth EQ, multi-account switching, and server-admin user statistics & history switching.

## Key Features (v1.0.0 & v1.1.0)

- **Server-Admin User Statistics & History Switching:** Server administrators can inspect listening statistics, year-in-review summaries, and session histories across all registered users with smooth FilterChip switching and custom user exports.
- **Root-Cause Crash Prevention & Resilient DTOs:** Kotlinx Serialization configured with `coerceInputValues = true`, lenient decoding, and flexible optional field defaults across server responses.
- **ZIP Download & Full Parity:** Save audiobook files as ZIP directly to Downloads, and parity gaps closed across all platforms.
- **Vinyl Haptics Engine (`KoalaHaptics`):** Tactile micro-vibrations for needle drop, vinyl surface crackle, tonearm placement, and timeline scrubbing with graceful fallback.
- **Android Auto & Automotive Media Tree (`KoalaMediaSessionService`):** Compliant `automotive_app_desc.xml` descriptor and browsable tree partitions (`continue`, `downloaded`, `recent`) with safe in-car playback.
- **Live Turntable App Widget (`KoalaTurntableWidgetProvider`):** 4×2 and 2×2 interactive home screen widget with spinning vinyl, play/pause and skip controls.
- **Bedtime Mode (`BedtimeBlindGestureOverlay`):** Pitch-black, zero-glare full-screen mode for sleepy listening with intuitive single-finger swipe and tap gestures.
- **Resilient Offline Engine (`DownloadRepository` & `OfflineSessionSyncWorker`):** Resumable downloads with HTTP Range support (`.part` files) and automatic WorkManager background syncing when network connectivity returns.
- **Analog Warmth EQ (`AudioEffectsController`):** Dynamic 5-band Equalizer profile simulating tape warmth and vintage tube amplification.

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

