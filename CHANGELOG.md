# Changelog

## Desktop 0.6.0

Phase D4 packaging & CI pipeline release:

- **Multi-Platform Electron-Builder Pipeline**: Configured production distribution targets for Windows (NSIS installer, MSI), macOS (DMG, ZIP universal), and Linux (AppImage, deb, rpm).
- **Hardened Release Automation**: Integrated CI packaging workflows (`release-desktop.yml`) with cryptographic build provenance attestations, strict package manager supply-chain caching isolation, and automated release asset bundling.
- **Packaging Scripts**: Standardized compilation, pack, and release scripts across the monorepo workspace.

## Desktop 0.5.0

Phase D3 native UX & tray release:

- **Frameless Always-on-Top Mini-Player**: Pop-out mini-player window with drag regions and full playback, chapter, and seek controls that floats above all desktop applications.
- **System Tray Controls**: Quick playback controls (Play/Pause, 30s skip back/forward), real-time track metadata tooltip, and window management directly from the OS taskbar notification area.
- **Native Save Dialog for Data Export**: Direct file export (CSV history/bookmarks and ZIP backups) using OS native file chooser dialogs without intermediate web browser download prompts.

## Desktop 0.4.0

Phase D2 audio & media controls release:

- **Full Web Audio DSP Parity**: Unlocked Web Audio DSP chain on desktop Chromium (Tape Warmth EQ, vinyl crackle simulation, voice boost, volume leveling, skip silence) without CORS or WebView restrictions.
- **System Media Controls & Hardware Keys**: Integrated media session synchronization with Windows SMTC, macOS Now Playing, and Linux MPRIS; hardware media keys (Play/Pause, Seek, Prev/Next) respond immediately.
- **Sleep Prevention (`powerSaveBlocker`)**: Automated OS sleep inhibition (`prevent-app-suspension`) engaged dynamically during active audiobook playback and cleanly released when paused or stopped.

## Desktop 0.3.0

Phase D1 secure session vault release:

- **OS Keychain Integration (`safeStorage`)**: Refresh and access tokens encrypted using platform credential vaults (Windows DPAPI, macOS Keychain, Linux Secret Service) and persisted in a protected disk vault (`vault.enc`).
- **Token Migration & Storage Hygiene**: Seamless migration of legacy plain tokens from local storage into the encrypted vault on startup; tokens are wiped from web `localStorage` so credentials are never stored in plaintext.
- **Multi-Account Switching**: Transparent multi-account and multi-server persistence with instant session restoration and server token revocation on account forget.

## Desktop 0.2.0

Phase D0 foundation & hardening release:

- **Electron & TypeScript Foundation**: Sandboxed main and preload process architecture with strict `contextIsolation: true`, `nodeIntegration: false`, and `sandbox: true`.
- **Security & Navigation Hardening**: Restrictive Content Security Policy (`default-src 'self'`), unconditional permission denial, webview blocking, and safe external navigation via OS default browser.
- **CI Pipeline Integration**: Strict TypeScript type checking and compilation verified in continuous integration.

## Android 1.0.0

Milestone 1.0.0 Production Release:

- **Seamless Device Handoff Banner**: When playback progress is made on another active client (web, car, or desktop), the player updates position smoothly and displays an interactive acknowledgment banner with direct dismissal.
- **Full Architecture & Sensory Integration**: Complete suite of vinyl tactile haptics (`KoalaHaptics`), Android Auto dashboard media browsing, live 4x2 / 2x2 Turntable home screen widget, HTTP range-resume download resilience, background WorkManager offline session synchronization, Bedtime zero-glare blind gestures, and analog locked-groove runout synthesis.
- **Accessibility & WCAG 2.2**: Verified tactile and ocular contrast standards across dark vinyl themes.

## Web 1.0.0

Milestone 1.0.0 Production Release:

- **Full Audiobookshelf Backbone Ecosystem Parity**: Complete alignment of tactile haptic vocabulary, Web Audio analog warmth DSP filtering, locked-groove runout cue, resilient offline caching, and responsive vinyl crate browsing.

## Android 0.5.0

Phase 4 bedtime sensory mode & analog warmth DSP release:

- **Bedtime Blind-Gesture Mode (`BedtimeBlindGestureOverlay`)**: Full-screen pitch-black ocular sanctuary with gentle warm amber typography and tactile gesture navigation. Single tap anywhere toggles Play/Pause, horizontal swipes jump 30 seconds back or forward, and double-tapping or tapping the pill button extends sleep timer by 15 minutes.
- **Analog Locked Run-Out Groove Simulation**: At the end of the sleep timer countdown, the player gently fades out playback and executes 2 revolutions (3.6s at 33⅓ RPM) of rhythmic vinyl lead-out locked groove clicks before sleep.
- **Analog & Tape Warmth DSP Profile (`warmth`)**: Hardware Equalizer and Web Audio pipeline with gentle tube low-end body (+100Hz), speech clarity presence lift (2.4kHz), and smoothed high-frequency tape roll-off (>6kHz).

## Web 0.5.0

Phase 4 bedtime & warmth DSP release:

- **Analog Warmth Sound Profile**: Added `warmth` scene preset to `SoundChain` and `SoundPanel.svelte` with custom biquad filtering.
- **Vinyl Runout Locked Groove**: Web Audio synthesis for sleep timer runout revolutions.

## Android 0.4.0

Phase 3 offline resilience and network sync release:

- **HTTP Range-Resume Download Engine**: In `DownloadRepository` and `AbsApiClient`, downloads now support HTTP `Range: bytes=X-` headers and stream into `.part` temporary files. Interrupted downloads preserve downloaded bytes across connectivity losses instead of purging directory files. Completed tracks are verified and skipped automatically on retry.
- **WorkManager Offline Session Auto-Flush (`OfflineSessionSyncWorker`)**: Listening progress made while disconnected is recorded locally in Room and automatically flushed back to the Audiobookshelf server when the device reconnects to an active network with `NetworkType.CONNECTED` constraints.

## Web 0.4.0

Phase 3 offline resilience release:

- **Offline Audio & Service Worker Hardening**: Optimized cache strategies for offline cover art and OPFS audiobook playback without third-party requests.

## Android 0.3.0

Phase 2 automotive and live widgets release:

- **Android Auto & Automotive OS Integration**: Implemented full `MediaLibrarySession.Callback` media tree hierarchy (`onGetLibraryRoot`, `onGetChildren`, `onGetItem`, `onSetMediaItems`, `onAddMediaItems`). Listeners can browse "Continue Listening", "Downloaded", and "Recently Added" directly from dashboard head units with Audiobookshelf as the backend source of truth.
- **Automotive Descriptor**: Registered official Android Automotive OS media capability XML descriptor (`automotive_app_desc.xml`).
- **Live Turntable Home Screen Widget (`KoalaTurntableWidgetProvider`)**: Native 4x2 / 2x2 responsive app widget featuring vinyl deck styling, spinning platter aesthetic, book title, author, and instant play/pause, skip-back, and skip-forward controls with real-time broadcast updates.
- **Unit Testing**: Robolectric test coverage for widget broadcast handling and lifecycle stability.

## Web 0.3.0

Phase 2 companion ecosystem alignment:

- **Audiobookshelf Single-Backbone Protocol**: Hardened media item and session compatibility matching Android MediaLibrary structure.

## Android 0.2.0

Phase 1 tactile sensory immersion release:

- **Tactile Vocabulary (`KoalaHaptics`)**: Native API 30+ composition vibration primitives for needle contact (`needleDrop`), groove tracking (`grooveTick` with 35ms rate limiter), chapter snap (`chapterSnap`), sleep timer arming (`sleepArmed`), and boundary bumps.
- **Haptic Controls & Settings**: Added user toggle in Settings for tactile haptics, respected system-wide and in-app.
- **Turntable & Crate Integration**: Interactive DJ scratching and crate record flipping now produce realistic micro-haptic feedback.
- **Automated Testing**: Robolectric test coverage verifying safe execution and no-ops when haptics are disabled.

## Web 0.2.0

Phase 1 tactile and sensory alignment release:

- **Tactile Vocabulary (`haptic`)**: Integrated Web Vibration API tactile patterns matching Android (`needleDrop`, throttled `grooveTick`, `chapterSnap`, `sleepArmed`, `boundary`).
- **Interactive Turntable & Record Crate**: Enhanced DJ platter scratching and series crate browsing with tactile feedback.
- **Preferences**: Added tactile haptics toggle in Settings with multi-language strings (DE, EN).

## Android 0.1.2

Phase 0 foundation release establishing continuous integration, test automation, and modular player refactoring:

- **CI Automation**: Integrated full Android test, lint, and build pipeline into GitHub Actions (`ci.yml`) on every pull request and push to main.
- **Player Architecture Refactoring**: Decoupled monolithic player controller into testable domain engines (`SleepTimerEngine`, `AmbienceEngine`, `AudioEffectsController`, `RemoteProgressResolver`).
- **Test Automation & Verification**: Added comprehensive unit test coverage for sleep timer modes, volume fade-outs, and live progress event conflict resolution; added Robolectric Compose rendering tests for core vinyl deck components (`VinylDisc`, `BookCover`, `TurntableDeck`).

## Android 0.1.1

The initial release of the native Android client for KoalaShelf with vinyl aesthetic, background media playback and full Audiobookshelf library integration:

- **Audio Playback**: Media3 background playback with lock screen media notifications, hardware play/pause/skip support, playback speed control, sleep timer, smart rewind, voice boost and equalizer audio processing.
- **Library & Search**: Multi-library browsing, series, authors, and narrators views, live search with instant filtering, book details with scrollable summary and chapter navigation.
- **Vinyl Design System**: Custom vinyl record spinning disc artwork, interactive scrubber, accent color theming, and responsive layouts.
- **Hardening & Security**: Optimized release builds with R8 code shrinking and ProGuard optimization, disabled plaintext backup, and native audio resource lifecycle management.

## Web 0.1.1

A focused release improving navigation, customization, connection reliability, and browser testing:

- **Sidebar Navigation**:
  - Structured, responsive layout with collapsible visual groups (Main, Discover, Personal) with persisted state
  - Icon-only collapse toggle (68px) and draggable resize handle (180px–380px) with double-click reset
  - Modernized library switcher dropdown in the navigation header
  - Cleaner layout moving language toggle and listening stats into Settings
- **Connection & Streaming**:
  - Switched Caddy upstream transport to HTTP/1.1 for API, auth, and image routes, fixing WebSocket upgrade loops and 502 Bad Gateway errors
  - Improved progress store and view transition null-safety
- **Privacy & Customization**:
  - Self-hosted, local-first typography options (Inter, Atkinson Hyperlegible, JetBrains Mono, Fraunces, Literata, Gloock, OpenDyslexic) with zero third-party web resource requests
- **Automated Verification**:
  - In-browser end-to-end verification suite via Playwright with automatic console and network error detection

## Desktop 0.1.0

The first release: a fast, private client for Audiobookshelf as a native desktop app for Windows, macOS and Linux (built with Tauri).

- Native desktop window with system integration and saved window position
- Full parity with KoalaShelf web player, offline support and library features
- Unsigned pre-release installers for Windows (.msi, setup .exe), macOS (universal .dmg) and Linux (.deb, .rpm, .AppImage) with signed provenance attestations

## Web 0.1.0

The first release: a fast, private, self-hostable client for Audiobookshelf 2.26 or newer, as an installable web app (PWA) in one small Docker image.

**Listening**
- Player for multi-file books with chapters, speed 0.5–3.5× (per book or one default), timeline for the book or the current chapter, "ends at" clock, smart rewind after pauses, jump back after accidental jumps, go to a typed time
- Sound: volume, boost, clearer voices, mono, volume leveling, skip silence
- Sleep timer: minutes, end of chapter, or smart (the first chapter end in a window); more time with one tap, a hint before it stops, automatic at night
- Bookmarks with notes and links to the moment; up next queue; next in series; listening history from every device; live progress from other devices
- Offline listening (books, series, playlists), synced when back online; save a book's files as ZIP
- Cast / AirPlay, pop-out player, lock screen and media keys, keyboard shortcuts

**Finding and organizing**
- Library with freely combined filters, text filter, saved views and random order; multi-select to mark finished, queue, add to playlists or collections, download
- Search in one or all libraries; quick search (Ctrl/⌘ K)
- Author, series and narrator pages; playlists; collections (editable with the right permissions); all bookmarks in one place

**Statistics and your data**
- Daily goal and streak, year in review (also as a picture to share), heatmap, the library in numbers
- Export bookmarks (Markdown, CSV) and history (CSV); back up and restore settings

**Accounts**: several accounts on one or more Audiobookshelf servers, switched with one tap; each keeps its own queue, downloads and recent searches; signing out ends the session on the server and removes the account's data from the device.

**Yours to tailor**: theme, six accent colors or your own (kept readable in both themes), an easy-to-read font (Atkinson Hyperlegible) or the system font, text size, motion, cover size, skip steps, media buttons, home shelves, start page and more, stored on the device only.

**A record shop feel**: series are crates of records to flip through, every book in reading order (pointer, sideways scroll, swipe or keys), the record in front opens its book; records peek out of their sleeves, the playing book's record stays out and turns at the playback speed; finished books become gold records; a new book is put on the platter; the navigation highlight glides along; a new theme spreads from where you click; author portraits become record labels. Calm and instant with reduced motion.

**Self-hosting**: one small container serves the app and passes Audiobookshelf through on the same origin, with a strict Content Security Policy; your reverse proxy only points a subdomain at it. Ready-made `docker-compose.yml`, `.env.example` and `Caddyfile.example`; nothing to back up and no volume (everything personal stays in the browser or in Audiobookshelf). Runs unprivileged and read-only; images for amd64 and arm64 with signed provenance and SBOM. Personal API answers are never cached, even behind reverse proxies that mark them as public.
