# Changelog

## Android 1.1.2

- **Player Modal & Haptic Touch Architecture**: Replaced awkward downward-expanding drawers with clean Material 3 modal bottom sheets with tactile haptics. Single tap toggles sound profile and sleep timer on/off, single tap cycles playback speeds, and long-press opens fine-grained configuration sheets.
- **Customizable Player Layout Presets**: Added one-tap layout presets (`compact`, `standard`, `detailed`) selectable directly in the player top bar and settings: Compact minimalist turntable, Standard balanced view, and Detailed audiophile view featuring audio codec/bitrate badge, simultaneous chapter and total book remaining times, ends-at clock, and silence-saved pill.
- **Live Daily Listening Goal & Real-Time Progress**: Live session listening seconds delta tracks listening progress second-by-second while playing audio, resolving the static 1/30 min display bug, accompanied by background 45s stats synchronization.
- **Full Settings Feature Parity with Web**: Added complete configuration suites for player presets, playback scope (chapter vs book), clock readout mode (remaining vs elapsed), speed-adjusted remaining time, auto-queue next book, headset media keys, sound processing engine (voice boost, mono downmix, volume leveling, skip silence, turntable vinyl crackle, needle drop cues), sleep timer (smooth fade-out, fade duration, post-sleep rewind, auto-sleep smart windows), and daily goal picker.
- **Dynamic Year in Review & Stats Selection**: Fixed non-functional year selector by dynamically parsing available years from listening history, reloading year statistics on selection, and surfacing longest book finished, top authors, and top genres.
- **Instant Cold-Start Cache Hydration**: Home shelves disk caching and Room database preloading provide instantaneous 0 ms first-frame painting on cold launch without blank screen loading delays.

## Web 1.1.3

- **HTTPS-Only Server Enforcement & Auto-Prefix**: Server input automatically defaults to secure HTTPS if no protocol is entered (e.g. `audio.example.org`), and explicitly rejects insecure `http://` connections with a dedicated error message.
- **Throttled Server Probing & Instant Feedback**: Replaced eager keystroke probing with a 2-second idle debounce to protect servers against excessive traffic, while supporting instant probing upon pressing Enter or leaving (blur) the server address field.
- **Aligned Chapter & Book Remaining Times**: Fixed inverted scope time display in `FullPlayer.svelte` so chapter scope displays remaining chapter time and book scope displays remaining book time, in exact alignment with the Android application.
- **Smooth Fullscreen Player Transitions & Motion Polish**: Added fluid cubic-bezier modal transitions with backdrop blur, coordinated deck/turntable entrance scaling, smooth side-panel slide reveals, and tactile active button press feedback.

## Web 1.1.2

- **Universal Multi-Server Dynamic CORS Proxy**: Added a lightweight, SSRF-protected streaming reverse proxy daemon listening on loopback behind Caddy inside the Docker container. This allows the web app to connect to any user-specified Audiobookshelf server (including `https://audio.shik3i.net`) from any web origin (such as `https://shelf.koalastuff.net`) without requiring custom CORS headers on the Audiobookshelf server. Features automatic direct fetch probing with fallback to proxy, and domain unwrapping in the UI.
- **Turntable Scratching 120 FPS Performance**: Decoupled vinyl platter rotation from reactive component re-renders via direct DOM style manipulation and batched audio seek events through `requestAnimationFrame`, achieving fluid 120 FPS scratching without audio stutter.
- **History & Stats Crash Prevention**: Hardened date parsing and locale formatting against unexpected or malformed session timestamp formats (`parseSessionDate`), preventing runtime `RangeError: Invalid time value` crashes on `/history` and `/stats`.
- **Permissions-Policy Modernization**: Removed deprecated `interest-cohort` directive from Caddy HTTP security headers.
- **Svelte 5 Reactive Cleanliness**: Eliminated `derived_inert` warnings in `Cover.svelte` by reading image attributes directly in load/error callbacks.

## Web 1.1.1

- **Atomic Library Preload on Sign-In**: Prefetches accessible book libraries before activating the client session, preventing transient null-library states during navigation from `/signin` to the home dashboard.
- **Defensive Metadata & Cover Resolution**: Hardened `view()` and `coverSrc()` against sparse or incomplete Audiobookshelf server payloads (missing `authors`, `narrators`, `series`, or `coverPath`), avoiding unnecessary 404 cover requests and runtime exceptions.
- **Extended Desktop Media Bridge**: Added `prev-chapter` and `next-chapter` IPC media command handling in the audio player engine.

## Desktop 1.1.1

- **System Tray Chapter Navigation**: Added direct Previous Chapter and Next Chapter controls to the OS taskbar tray menu alongside Play/Pause and 30-second skip actions.
- **Clean Shutdown & Resource Hygiene**: Automatically releases active `powerSaveBlocker` instances and destroys system tray handles on application exit (`will-quit`), while routing diagnostic logs to `userData` instead of the installation directory.
- **Synchronized Web 1.1.1 Engine**: Includes atomic library preload on sign-in and defensive metadata normalization.

## Android 1.1.1

- **Definitive Post-Login Freeze & Cancellation Fix**: Executed `signIn`, `restore`, and `switchTo` on `Dispatchers.IO` with `NonCancellable` and resolved libraries + user profile before emitting `_activeAccount`, eliminating `LeftCompositionCancellationException` when `SignInScreen` leaves the composition.
- **R8 / ProGuard Serialization Hardening**: Preserved Kotlin `Metadata`, generic `Signature` attributes, `**$$serializer`, `$Companion`, `KSerializer`, and Guava `ListenableFuture` classes in release builds, and added safe default values across all network DTOs.
- **Authenticated Cover URLs & 404 Prevention**: Appended access token query authentication (`token=...`) for cover and author artwork requests and gated remote cover loading on `item.hasCover`.
- **Media3 System Controls & Android Auto Browse Tree**: Added full `Player.COMMAND_*` support on `ForwardingPlayer`, registered `MediaButtonReceiver`, supported `isRecent` resumption, and expanded the Android Auto browse tree with Series and Authors navigation.
- **Smooth Compose Animations & Main-Thread I/O Removal**: Added directional screen transitions, animated player drawers and progress rings (respecting `reducedMotion`), replaced eager chapter loops with lazy `itemsIndexed`, switched all state collection to `collectAsStateWithLifecycle()`, and moved all repository/JSON/Room operations to `Dispatchers.IO`.

## Web 1.1.0

- **Server-Admin User Statistics & History**: Server administrators (`root` or `admin`) can view listening statistics, year summaries, and listening sessions across all registered server users via an interactive user selector.
- **Per-User History CSV Export**: Export user-specific CSV listening session logs named with the target user's username.
- **Admin Status Badging & Quick Reset**: Visual admin badges for clear context awareness, instant reset button to switch back to personal statistics, and graceful empty states for zero-session accounts.
- **Multi-Platform Feature Parity Alignment**: Complete consistency with desktop and Android releases across all library navigation, playlists, collections, and audio playback features.

## Desktop 1.1.0

- **Server-Admin User Statistics & History**: Seamless integration of server administrator user switching in the native Electron desktop shell.
- **Native OS Save Dialogs for User Exports**: Native file export dialogs dynamically handle per-user session history exports with sanitized user filenames.
- **Multi-Platform Feature Parity Alignment**: Complete feature and audio processing graph parity across Web, Desktop, and Android clients.

## Android 1.1.0

- **Root-Cause Login Crash Fix**: Hardened Kotlinx Serialization and Ktor network layer (`coerceInputValues = true`, `isLenient = true`, `ignoreUnknownKeys = true`, flexible nullable/optional fallbacks across ABS DTOs) preventing application termination after server sign-in.
- **Server-Admin User Statistics & History**: Server administrators can switch between server users via Material 3 `FilterChip` horizontal carousels to inspect user stats and session histories.
- **Audiobook ZIP Download Action**: Direct download of entire audiobook directory archives (audio files, cover, metadata) into the device Downloads folder.
- **Compose Stability & List Recycling**: Defensive indexing safeguards for `itemsIndexed` in lazy layouts, preventing crashes under rapid scroll or dynamic library updates.
- **Multi-Platform Feature Parity Alignment**: Full feature parity with Web 1.1.0 and Desktop 1.1.0.

## Desktop 1.0.0

Milestone 1.0.0 Production Release:

- **Universal Chromium Audio & Codec Consistency**: Bundles Chromium for 100% reliable playback of all audiobook formats (AAC, M4B, MP3, Opus, FLAC) across Windows, macOS, and Linux without external host WebView or GStreamer plugin dependencies.
- **Full Web Audio DSP Suite Parity**: Complete Web Audio signal processing graph with Tape Warmth EQ, vinyl crackle simulation, voice boost, volume leveling, and skip-silence detection.
- **Hardware & OS-Encrypted Secrets (`safeStorage`)**: All access and refresh tokens are encrypted using OS credential vaults (Windows DPAPI, macOS Keychain, Linux Secret Service) in a local protected vault (`vault.enc`), keeping web `localStorage` completely token-free.
- **Native Sleep Prevention (`powerSaveBlocker`)**: Automated OS sleep inhibition (`prevent-app-suspension`) active during audio playback, preventing system standby while listening.
- **Hardware Media Keys & OS Media Session Controls**: Integrated with Windows SMTC, macOS Now Playing, and Linux MPRIS; hardware media keys (Play/Pause, Skip, Prev/Next) respond immediately.
- **Frameless Always-on-Top Mini-Player**: Floating pop-out mini player window with window drag region, cover art, track progress, chapter navigation, and playback controls.
- **System Tray Integration**: Taskbar notification area tray icon with quick controls (Play/Pause, 30s skip back/forward), real-time metadata tooltips, and double-click window restore.
- **Native Save Dialogs**: Direct export of listening history, bookmarks (CSV), and book file backups (ZIP) via native platform file picker dialogs.
- **Multi-Platform Distribution & Provenance**: Packaged with `electron-builder` for Windows (NSIS, MSI), macOS (DMG, universal ZIP), and Linux (AppImage, deb, rpm) with signed cryptographic build provenance attestations.

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
