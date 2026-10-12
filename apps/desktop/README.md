# apps/desktop

Native KoalaShelf desktop app (Windows, macOS, Linux) built with Tauri 2. It ships the `apps/web` build locally, so the UI loads instantly and works without a web server.

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

Note: in the desktop app the session (including the refresh token) is stored in the app's private WebView storage. Moving it to the OS keychain is planned.
