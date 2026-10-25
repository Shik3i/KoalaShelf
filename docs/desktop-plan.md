# Desktop app: architecture & plan

Status: Released (v1.0.0 & v1.1.0) with complete feature parity to Web & Android, built on **Electron & TypeScript**, packaged via `electron-builder`, released through `release-desktop.yml` with `desktop-vX.Y.Z` or unified `vX.Y.Z` tags.

The desktop app packages the web app (`apps/web`) locally inside a hardened Electron shell. By bundling Chromium, it provides **100% audio codec consistency** (AAC, M4B, MP3, Opus, FLAC, Web Audio) across Windows, macOS, and Linux, eliminating Host WebView codec dependencies (e.g. Linux GStreamer issues). **Audiobookshelf remains the only backend**: no custom server, no P2P sync, no telemetry.

## Guiding rules

1. **Security and privacy first.** Strict sandboxing (`contextIsolation: true`, `nodeIntegration: false`, `sandbox: true`), strict Content Security Policy, no untrusted navigation, refresh tokens encrypted via OS keychain (`safeStorage`), no telemetry or third-party tracking.
2. **Audio & Web Audio consistency.** Chromium engine ensures full Web Audio capabilities (Tape Warmth EQ, vinyl crackle simulation, voice boost, volume leveling, skip silence) on all platforms.
3. **Performance.** Instant startup from local static assets, idle CPU ≈ 0 when playback is paused, flat memory footprint, hardware-accelerated vinyl rendering.
4. **Native where it helps.** OS Media Controls (SMTC / MPRIS / Now Playing via MediaSession), System Tray with quick controls, always-on-top mini player, native save dialogs, system sleep prevention (`powerSaveBlocker`).
5. **One code base.** UI remains shared in `apps/web`. Platform differences are abstracted behind `apps/web/src/lib/platform.ts` via `isDesktop`.
6. **Current, stable dependencies only.** Latest stable Electron and electron-builder; strict TypeScript without any alpha/beta packages.

## Architecture

```
apps/desktop/
  package.json              Electron & electron-builder scripts and dependencies
  tsconfig.json            NodeNext TypeScript configuration
  electron-builder.json     Multi-platform distribution configuration (NSIS, MSI, DMG, AppImage, deb, rpm)
  resources/                App icons (ICO, ICNS, PNG)
  src/
    main/
      index.ts              Lifecycle, main window, single-instance lock, tray, IPC handlers
      preload.ts            Hardened contextBridge exposing typed window.electron APIs
      security.ts           CSP headers, external link handling, navigation restriction
      storage.ts            Encrypted secret vault using safeStorage (OS Keychain)
apps/web/src/lib/platform.ts
  isDesktop                 Feature detection: Boolean(window.electron)
  apiFetch                  Unified fetch implementation for browser and desktop
```

### Security model

- **Sandboxing & Isolation:** Renderer runs with `contextIsolation: true`, `nodeIntegration: false`, and `sandbox: true`. No Node.js APIs are directly accessible in the webview.
- **IPC Whitelist:** All desktop capabilities (storage, window state, sleep blocker, dialogs) are mediated through explicit IPC handlers with parameter validation.
- **Secrets Management:** Refresh tokens are encrypted using `safeStorage` (Windows DPAPI, macOS Keychain, Linux Secret Service) and persisted in an encrypted vault in the user's data directory. Tokens are never exposed in URLs or logged.
- **CSP & Navigation:** In-app navigation is locked to the local application bundle. External links (`http://`, `https://`) are intercepted and delegated to the default OS browser via `shell.openExternal`.
- **Permissions:** Device permissions (camera, microphone, geolocation) are unconditionally denied.

### Audio & Feature Parity (Web 1.0.0 → Desktop 1.0.0)

| Feature | Desktop (Electron) | Status / Notes |
|---|---|---|
| Sign-in, multi-account, server switching | ✅ | Tokens encrypted via `safeStorage` (OS Keychain) |
| Library, search, faceted filters, crates | ✅ | 100% shared web codebase |
| Player: chapters, speed, bookmarks, sleep timer | ✅ | 100% shared web codebase |
| Sound chain (Warmth EQ, crackle, boost, leveling, skip silence) | ✅ | Full Chromium Web Audio support without CORS friction |
| Resilient audio codecs (AAC, M4B multi-file) | ✅ | Native Chromium codec support across all platforms |
| Offline downloads & caching | ✅ | Resilient local storage; native save dialog for ZIP export |
| OS Media Controls & Media Keys | ✅ | Windows SMTC, macOS Now Playing, Linux MPRIS |
| Pop-out / Mini player | ✅ | Dedicated frameless always-on-top mini window |
| System Tray | ✅ | Quick playback controls in OS taskbar / menu bar |
| Sleep Prevention | ✅ | `powerSaveBlocker` active during playback |
| Accessibility (WCAG 2.2 AA), themes, fonts | ✅ | Preserved from Web 1.0.0 |

## Packaging & Distribution

`electron-builder` packages native installers across platforms:
- **Windows:** NSIS installer + MSI (`x64`, `arm64`)
- **macOS:** DMG + ZIP (Universal binary: Apple Silicon + Intel)
- **Linux:** AppImage, deb, rpm (`x64`, `arm64`)

Build workflow in `.github/workflows/release-desktop.yml` generates artifacts with signed build provenance and attaches them to GitHub Releases.

## Phases

| Phase | Version | Scope | Exit criteria |
|---|---|---|---|
| D0 Foundation & Hardening | 0.2.0 | Electron scaffold, `preload.ts`, contextBridge, `security.ts`, CSP, TypeScript compilation, CI integration | Compiles cleanly, 0 type errors, CSP enforced |
| D1 Secure Session Vault | 0.3.0 | `safeStorage` keychain integration, token migration from web storage, multi-account persistence | Tokens stored encrypted, transparent switching |
| D2 Audio & Media Controls | 0.4.0 | Web Audio verification, SMTC / MPRIS media keys, `powerSaveBlocker` sleep prevention | Audio formats play smoothly, media keys respond |
| D3 Native UX & Tray | 0.5.0 | Frameless Mini-Player window, System Tray controls, native save dialog for ZIP/CSV export | Tray & Mini-Player functional |
| D4 Packaging & CI Pipeline | 0.6.0 | `electron-builder` setup for Windows/macOS/Linux, provenance attestations in release workflow | Installers generated and verified |
| D5 Release Readiness | 1.0.0 | Full audit, performance and memory check, documentation update, `desktop-v1.0.0` tagging | All tests green, release policy satisfied |
