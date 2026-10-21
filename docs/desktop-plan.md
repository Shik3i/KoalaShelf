# Desktop app: plan (branch `desktop`)

Status: planned 2026-10-21. Target: **Desktop 1.0.0** with feature parity to Web 1.0.0, built on Tauri 2 (stable line), released through `release-desktop.yml` with `desktop-vX.Y.Z` tags.

The desktop app is the web app (`apps/web`) shipped locally inside a Tauri shell, plus a small, audited Rust core that does what a browser cannot: streaming media without CORS, native downloads, OS keychain, OS media controls, tray, mini player, deep links, signed updates. **Audiobookshelf stays the only backend**: no extra server, no own sync protocol, no telemetry.

## Guiding rules

1. **Security and privacy first.** Least-privilege capabilities, strict CSP, isolation pattern, no wildcard network scope in production, refresh tokens only in the OS keychain, no telemetry, no third-party requests (the updater only talks to GitHub Releases and only when the listener allows it).
2. **Performance.** Instant start from local files, no service worker, disk cover cache, streamed media through Rust, small binary (LTO, `opt-level = "s"`, strip), idle CPU ≈ 0 when paused, no polling loops in Rust.
3. **Feature parity.** Every Web 1.0.0 feature works on desktop or has a native replacement; the few exceptions are documented with a reason (see the parity table).
4. **Native where it helps.** OS media controls (SMTC / MPRIS / Now Playing), tray, always-on-top mini player, native save dialogs, deep links, taskbar/dock progress, native menus on macOS, system sleep inhibition while playing.
5. **One code base.** The Svelte UI stays shared. Platform differences go through small adapters in `apps/web/src/lib/platform/` (feature-detected, lazily imported), never through forks of components.
6. **Current, stable dependencies only.** Latest stable releases at implementation time; **no alpha/beta/RC** (Tauri 3 is alpha as of this plan: stay on the newest Tauri **2.x** and its 2.x plugins). Versions and the check date go into [tech-stack.md](tech-stack.md). npm `@tauri-apps/*` packages and their crates are bumped together.

## Current state (scaffold)

| Item | State | Problem |
|---|---|---|
| `tauri.conf.json` | `"csp": null` | no CSP at all |
| `capabilities/default.json` | `http:default` with `http://**`, `https://**` | the webview may reach any host |
| Plugins | http, window-state, single-instance | no keychain, media controls, downloads, updater |
| `rust-version` | 1.90 | toolchain is 1.99; bump MSRV |
| Web app in Tauri | `isTauri` guards: no service worker, no preset server, no CORS probe, **no Web Audio** (sound chain, leveling, skip silence), **no downloads**, no ZIP, no Cast | parity gaps |
| Session | refresh tokens in `localStorage` | should be OS keychain |
| Release workflow | Windows x64, macOS universal, Linux x64; unsigned pre-release | no arm64 Windows/Linux, no updater artifacts, no signing |

## Architecture

```
apps/desktop/src-tauri/src/
  main.rs            entry (windows_subsystem in release)
  lib.rs             builder, plugins, protocols, tray, menu, run events
  error.rs           one error type (thiserror), serialisable to the UI without secrets
  scope.rs           allowed servers: persisted list, URL normalisation, http/https policy
  secrets.rs         OS keychain (keyring crate) for refresh tokens
  media_protocol.rs  ksmedia:// streaming proxy with Range (remote and downloaded files)
  cover_cache.rs     kscover:// disk cache with LRU size cap
  downloads.rs       resumable downloads (reqwest + rustls, .part files, Range, verify)
  media_controls.rs  souvlaki: SMTC / MPRIS / MPNowPlayingInfoCenter bridge
  power.rs           inhibit system sleep while playing (keepawake), opt-in
  tray.rs            tray icon + menu (now playing, play/pause, skip, show, quit)
  mini.rs            mini player window (always on top, frameless)
  files.rs           native save dialogs + streamed writes for exports and ZIP
  updater.rs         signed updates (opt-in)
apps/web/src/lib/platform/
  index.ts           isTauri, capability flags (canWebAudio, canDownload, canCast, canPip …)
  fetch.ts           apiFetch (existing)
  secrets.ts         SecretStore: localStorage (web) | keychain commands (desktop)
  media.ts           mediaUrl(): plain token URL (web) | ksmedia:// URL (desktop)
  downloads.ts       DownloadStore: OPFS (web) | native commands (desktop)
  files.ts           saveFile(): anchor download (web) | native save dialog (desktop)
  mediaSession.ts    navigator.mediaSession (web) | native bridge (desktop) — or both
  window.ts          mini player, tray state, close-to-tray (desktop only)
```

### Security model

- **CSP** in `tauri.conf.json` (Tauri adds hashes/nonces for its own scripts):
  `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'` (only if Svelte transitions need it; verify) `; img-src 'self' kscover: data: blob:; media-src 'self' ksmedia: blob:; connect-src 'self' ipc: http://ipc.localhost; font-src 'self'; worker-src 'self' blob:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'`.
  On Windows/Android custom schemes appear as `http://<scheme>.localhost`: list both forms. Socket.io live updates need `connect-src` for the allowed servers' `wss:`/`ws:` origins → set at runtime by routing the socket through the same allowed-server check, or (preferred) keep the socket in the webview with `transports: ['websocket']` and a CSP entry `wss: ws:` restricted by the scope check in JS **and** documented as the one broad entry; decide in phase 1 and record the decision.
- **Isolation pattern** (`app.security.pattern.use = "isolation"`) to validate every IPC message; `withGlobalTauri: false`; `freezePrototype: true`; devtools only in debug builds.
- **Capabilities**: one per window (`main`, `mini`), only the commands each needs; no `fs`/`shell` plugins at all; custom commands declared in `build.rs` permissions (`tauri_build::Attributes::app_manifest`) so each is individually allowed.
- **Network scope**: no `http://**`. A server becomes allowed when the listener submits it in the sign-in form (user intent) and is stored in `scope.rs`; Rust re-checks every HTTP request (plugin-http scope via runtime capability, `ksmedia`, `kscover`, downloads) against this list. Removing the last account on a server removes the server. Plain `http://` only for private/LAN addresses and `localhost` (RFC 1918, `.local`, `*.home.arpa`, loopback); public `http://` needs an explicit "insecure" confirmation and shows a warning in settings.
- **Secrets**: refresh tokens in the OS keychain (`keyring`, service `net.koalastuff.koalashelf`, account `<serverOrigin>|<userId>`); access tokens only in memory (Rust side for protocols, JS side for API). Linux without a Secret Service: fall back to "stay signed in" off (session-only) with a clear message, never plaintext files. One-time migration from `localStorage` (scaffold builds) into the keychain, then the localStorage copy is wiped.
- **Tokens never in URLs the webview can leak**: `ksmedia://` and `kscover://` URLs carry an opaque item/file id, never a token; Rust adds `Authorization: Bearer` upstream.
- **TLS**: reqwest with `rustls` and platform verifier (`rustls-platform-verifier`) so self-signed CAs installed in the OS trust store work; no "accept invalid certs" switch.
- **Supply chain**: `cargo deny` (advisories, licenses, bans, sources) and `cargo audit` in CI; Dependabot cargo already set; actions pinned by SHA (existing policy); updater artifacts signed (minisign key in GitHub secrets, public key in `tauri.conf.json`).
- **Logging**: `tauri-plugin-log` or `tracing` to a rotating file in the app log dir, **off by default above `warn`**, never logs URLs with queries, tokens, titles or user names.

### Media without CORS: `ksmedia://`

The web player uses `<audio src=…?token=>`; Web Audio (boost, voices, mono, leveling, warmth EQ, crackle mix, skip silence) needs CORS, which a remote server does not send to `tauri://localhost`. A custom URI scheme makes the media same-origin to the app:

- `register_asynchronous_uri_scheme_protocol("ksmedia", …)`; URL form `ksmedia://localhost/<account>/<itemId>/<fileIno>` (or `http://ksmedia.localhost/...` on Windows).
- Remote files: Rust requests `GET <server>/api/items/<id>/file/<ino>` with the bearer token and forwards the `Range` header; responds **206 with a bounded chunk** (e.g. 2 MiB per request when the client asks for an open range) plus `Content-Range`, `Accept-Ranges: bytes`, `Content-Type`; the media element keeps requesting the next ranges. Tauri protocol responses are buffered (`Vec<u8>`), so the chunk cap keeps memory flat. If a future Tauri 2.x adds streaming bodies, switch to it.
- Downloaded files: same scheme, served from disk with the same Range logic (`tokio::fs`, seek + read).
- On 401 Rust refreshes the access token once (single flight, shared with the JS client through a command) and retries.
- HLS fallback (server transcode) keeps working: `.m3u8` and segments are proxied the same way and hls.js loads them through `ksmedia`.
- Result: `player.svelte.ts` drops the `!isTauri` guard on Web Audio; every sound feature works on desktop.

### Covers: `kscover://`

`kscover://localhost/<serverId>/<itemId>?w=…` → disk cache in the app cache dir (`<hash>.webp|jpg`), LRU by size (default 300 MB, setting 100 MB–2 GB, "clear cache" button), `ETag`/`Last-Modified` revalidation at most once per day. Covers are public on the server, so the cache holds no secrets; it is still removed when the last account on that server signs out.

### Downloads (native)

- Rust `downloads.rs`: queue with concurrency 2, `reqwest` streaming into `<file>.part`, resume with `Range: bytes=<len>-` (HTTP 206 check, `If-Range` with ETag), size check against the server's metadata, atomic rename, per-file and per-book progress events (throttled to ≤ 4/s).
- Location: app data dir `downloads/<serverId>/<itemId>/` (owners list per item, like the web version); a setting to move the folder via a native folder picker.
- Survives restarts (queue persisted in app data as JSON), pauses on metered/offline networks only when the OS tells us (no polling), retries with backoff.
- The web `downloads.svelte.ts` keeps its logic (offline sessions, sync on reconnect, auto-remove finished, per-account visibility) behind a `DownloadStore` interface: OPFS for web, commands for desktop. Offline playback goes through `ksmedia`.
- Taskbar/dock progress (`window.set_progress_bar`) while downloads run.
- "Save files as ZIP" → native save dialog; Rust streams the server's ZIP to the chosen path (no buffer in JS).

### Native integration

| Feature | Implementation | Notes |
|---|---|---|
| OS media controls | `souvlaki` (SMTC on Windows, MPRIS on Linux, MPNowPlayingInfoCenter on macOS) fed from the player's state via events; commands back to the player | Turn off `navigator.mediaSession` on desktop if the WebView also registers, to avoid duplicates (test per OS) |
| Media keys | come through the OS media controls; optional **global shortcuts** (`tauri-plugin-global-shortcut`) for play/pause/skip/sleep +15, off by default, configurable | |
| Tray | Tauri `tray-icon`: now playing title, play/pause, back/forward, sleep timer +15, show window, quit; tooltip with title and time left | Linux uses AppIndicator |
| Close to tray | setting "Keep playing when the window is closed" (default on for macOS behaviour parity, off on Windows/Linux; ask once on first close while playing) | |
| Mini player | second window `mini` (route `/mini`), 320×120/320×320, frameless, always on top, draggable, remembers position, vinyl spinning; replaces Document Picture-in-Picture | `isTauri` → PiP button opens the mini window |
| Sleep inhibition | `keepawake` while playing (system sleep only, display may sleep), opt-in setting "Prevent sleep while playing" (default on) | released on pause/timer end |
| Suspend/resume | on resume: re-sync position with the server, refresh token; on suspend/exit: final sync (`RunEvent::ExitRequested` → emit, wait ≤ 2 s) | |
| Deep links | `tauri-plugin-deep-link`: `koalashelf://book/<id>?t=12:34`, forwarded through single-instance | shared links from the web app also offer "Open in desktop app" only on desktop |
| Native menus | macOS app menu (About, Settings ⌘,, Hide, Quit), Playback menu (Play/Pause Space, Skip ←/→, Speed, Sleep timer), Window menu; Windows/Linux keep the in-app UI | |
| Save/export | `tauri-plugin-dialog` save dialog + Rust write command restricted to the path the dialog returned | CSV/MD/PNG exports, settings backup, ZIP |
| Notifications | `tauri-plugin-notification`, opt-in: download finished, sleep timer stopped playback | no notification content leaves the device |
| Updates | `tauri-plugin-updater`, signed; **off until the listener opts in** (first-run question, setting "Check for updates", manual "Check now"); only contacts GitHub Releases | |
| Autostart | not offered (not useful for a player; less surface) | |
| Window | window-state (existing); macOS `titleBarStyle: Overlay` with the vinyl header drawn into the title bar; dark/light follows the OS; `backgroundColor` per theme to avoid a white flash | |

### Feature parity table (Web 1.0.0 → Desktop 1.0.0)

| Web feature | Desktop |
|---|---|
| Sign-in, several accounts and servers, sign-out, devices, password | ✅ same UI; tokens in keychain; no CORS hint needed |
| Library, search, quick search, pages, playlists, collections, bookmarks, stats, history, year picture | ✅ unchanged |
| Player: chapters, speed, skip, jump back, go to time, smart sleep timer, queue, bookmarks | ✅ unchanged |
| Sound chain (boost, voices, mono, leveling, warmth EQ, crackle, skip silence) | ✅ via `ksmedia` (today hidden) |
| HLS fallback | ✅ via `ksmedia` |
| Live progress (socket.io) | ✅ websocket transport |
| Downloads, offline listening, offline session sync, auto-remove | ✅ native (today hidden) |
| ZIP, exports, settings backup | ✅ native save dialog |
| Lock screen / media keys | ✅ OS media controls |
| Pop-out player | ✅ mini player window |
| Cast / AirPlay | ➖ not available in system WebViews; button hidden (documented). Revisit if a stable cross-platform native cast crate appears |
| PWA install, service worker | ➖ not needed (the app is installed; files are local) |
| Haptics | ➖ no vibration on desktop; the API is a no-op |
| Accessibility (WCAG 2.2 AA), i18n, themes, fonts | ✅ unchanged; plus native menus with accelerators |

## Platforms and packaging

| OS | Targets | Bundles | Notes |
|---|---|---|---|
| Windows 10 1809+ / 11 | x64, **arm64** | NSIS (per-user, no admin) and MSI | WebView2 Evergreen via bootstrapper (`webviewInstallMode: downloadBootstrapper`); offline installer variant optional |
| macOS 12+ | universal (arm64 + x64) | DMG, `.app` | hardened runtime, entitlements minimal (network client, audio); notarisation when an Apple Developer ID exists |
| Linux | x64, **arm64** (`ubuntu-24.04-arm` runner) | AppImage, deb, rpm | WebKitGTK 4.1; **AAC/M4B needs GStreamer plugins**: deb/rpm declare `gstreamer1.0-plugins-good`, `-bad`, `gstreamer1.0-libav` (rpm equivalents); AppImage with `bundleMediaFramework: true`; Flatpak later |

Code signing: Windows via Azure Trusted Signing (or an OV certificate) and macOS notarisation are wired into the workflow behind secrets; while the secrets are missing, releases stay pre-releases (existing behaviour). Updater signing (minisign) is independent and mandatory from the first updater-enabled release.

## Testing

- **Rust**: `cargo fmt --check`, `cargo clippy --all-targets -- -D warnings`, unit tests for Range parsing/chunking, scope rules (LAN detection, normalisation, http policy), download resume (local `hyper`/`axum` test server with Range), cover LRU eviction, keychain adapter behind a trait (mock in tests).
- **Web**: unit tests for every adapter (web and desktop variants with mocked `invoke`), existing suites stay green; `svelte-check`, lint.
- **E2E**: Windows: run the existing Playwright suite against the Tauri app through WebView2's CDP (`WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS=--remote-debugging-port=9222`, `chromium.connectOverCDP`) against the dev Audiobookshelf (`dev/`); Linux: `tauri-driver` + WebdriverIO smoke suite under `xvfb` (sign-in, play, Web Audio boost audible, download → offline play, mini player, deep link). macOS has no WKWebView driver: manual checklist per release.
- **Audits per release**: CSP violations = 0 in console, capabilities review, `cargo deny`, memory and CPU idle check (paused: ~0 % CPU; playing 30 min: flat RSS), cold start < 1 s to first paint on a mid-range laptop, installer sizes recorded in `docs/audit.md`.

## CI and release

- `ci.yml`: new job `desktop` (ubuntu): Rust toolchain stable, Linux deps, `cargo fmt`, `clippy`, `cargo test`, `cargo deny check`, `pnpm --filter @koalashelf/desktop tauri build --debug --no-bundle` (compile check). Windows E2E job on pull requests touching `apps/desktop/**` or `apps/web/src/lib/platform/**`.
- `release-desktop.yml`: add Windows arm64 and Linux arm64 matrix entries; updater artifacts (`createUpdaterArtifacts: true`) and `latest.json` uploaded to the release; signing steps behind secrets; keep attestations; keep "no caches in release jobs".
- `scripts/release.mjs` already checks the three desktop version files; add `tauri.conf.json` `plugins.updater.pubkey` presence to the policy once the updater ships.

## Phases

| Phase | Version | Content | Exit criteria |
|---|---|---|---|
| D0 Foundation & hardening | 0.2.0 | Branch `desktop`; deps to latest stable 2.x; MSRV 1.99; CSP; isolation pattern; per-window capabilities; scope.rs replaces wildcard; error/log setup; `platform/` adapter skeleton; CI desktop job; `cargo deny` | builds on 3 OS, CSP clean, no wildcard scope, CI green |
| D1 Secure session | 0.3.0 | keychain secrets + migration; server scope from sign-in; LAN/http policy; token refresh shared with Rust | sign-in/out/switch across servers, tokens absent from localStorage |
| D2 Media engine | 0.4.0 | `ksmedia` Range proxy (remote, HLS); Web Audio guard removed; `kscover` cache; OS media controls; sleep inhibition; suspend/resume sync | all sound features audible on 3 OS; seek in 20 h M4B < 300 ms; media keys work |
| D3 Offline | 0.5.0 | native downloads with resume; `DownloadStore`; offline playback via `ksmedia`; ZIP + exports via save dialog; taskbar progress | kill app mid-download → resumes; offline session syncs |
| D4 Native UX | 0.6.0 | tray, close to tray, mini player window, macOS menus/overlay title bar, deep links, notifications (opt-in), global shortcuts (opt-in) | parity table all ✅/➖ with reasons |
| D5 Release readiness | 1.0.0 | updater (signed, opt-in); arm64 builds; Linux GStreamer packaging; signing hooks; E2E (Windows CDP, Linux tauri-driver); a11y + performance audit; docs (README, apps/desktop/README, feature-parity, audit, tech-stack, releasing, CHANGELOG `## Desktop 1.0.0`) | all checks green, `node scripts/release.mjs version desktop-v1.0.0` ok |

Each phase ends with a `## Desktop X.Y.Z` section in CHANGELOG.md, matching versions in the three desktop version files, and green CI. Tags are only created after the branch is merged into `main` (the security gate requires the tagged commit to be `main`'s head).

## Open decisions (defaults chosen; change if needed)

1. **Socket.io and CSP**: websocket transport with a `wss:`/`ws:` CSP entry plus JS scope check (default) vs. proxying the socket through Rust.
2. **Close to tray default**: on for macOS, ask on first close elsewhere (default).
3. **Code signing**: Azure Trusted Signing + Apple Developer ID once accounts exist; until then pre-releases.
4. **Update checks**: opt-in (default) — privacy first.
