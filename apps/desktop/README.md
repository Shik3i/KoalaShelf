# apps/desktop

Native KoalaShelf desktop app (Windows, macOS, Linux) built with Tauri 2. It ships the `apps/web` build locally, so the UI loads instantly and works without a web server.

**Status:** scaffolded and compiling; development continues once the web app is signed off. Until then the web app is the reference for every feature.

What the desktop shell adds:

- **No CORS setup:** API calls go through Rust (`tauri-plugin-http`), so any Audiobookshelf server works without "Allowed origins".
- **Media keys and OS media controls** via the Media Session API (Windows SMTC, macOS Now Playing, MPRIS).
- **Single instance:** launching again focuses the running player.
- **Window state** (size, position) is remembered.
- Small and private: system WebView, no telemetry, no bundled browser.

## Develop

Requirements: Rust stable, plus the [Tauri prerequisites](https://v2.tauri.app/start/prerequisites/) for your OS (on Windows: MSVC build tools and WebView2).

```bash
pnpm --filter @koalashelf/desktop dev      # starts the web dev server and the app
pnpm --filter @koalashelf/desktop build    # installers in src-tauri/target/release/bundle
```

Icons are generated from `src-tauri/app-icon.png` (rendered by `apps/web/scripts/icons.mjs`):

```bash
pnpm --filter @koalashelf/desktop tauri icon src-tauri/app-icon.png
```

## Known differences to the browser

- Volume leveling and "Save files (ZIP)" are hidden: media and downloads from a remote server would need CORS, which the Rust HTTP path avoids only for API calls. Native implementations are planned.
- The service worker is not registered (the files are local anyway).

Note: in the desktop app the session (including the refresh token) is stored in the app's private WebView storage. Moving it to the OS keychain is planned.
