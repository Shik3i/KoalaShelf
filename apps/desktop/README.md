# apps/desktop

Native KoalaShelf desktop app (Windows, macOS, Linux) built with Electron and TypeScript. It ships the `apps/web` build locally with full Chromium audio codec consistency (AAC, M4B, MP3, Opus, FLAC, Web Audio), OS Keychain token encryption, and native OS integration.

**Status:** Feature-complete production release 1.0.0 with full parity to Web 1.0.0.

What the Electron desktop shell adds:

- **Universal Audio Codec Guarantee:** Bündelt Chromium für verlässliche Wiedergabe aller Audiobook-Formate (insbesondere AAC/M4B) auf Windows, macOS und Linux ohne externe OS-GStreamer-Abhängigkeiten.
- **Hardware & OS-Verschlüsselung (`safeStorage`):** Refresh-Tokens werden im nativen OS-Schlüsselbund (Windows DPAPI, macOS Keychain, Linux Secret Service/KWallet) verschlüsselt hinterlegt.
- **Sleep Prevention (`powerSaveBlocker`):** Verhindert das Einschlafen des Betriebssystems während der Wiedergabe (Bildschirm darf schlafen).
- **Media Keys & OS Media Controls:** Windows SMTC, macOS Now Playing und Linux MPRIS via Standard MediaSession.
- **Always-on-top Mini Player:** Eigenes rahmenloses Mini-Player-Fenster mit Vinyl-Plattenspieler.
- **System Tray:** Menüleiste / Infobereich mit Schnellsteuerung (Play/Pause, Mini-Player, Beenden).
- **Single Instance Lock:** Ein erneuter Start fokussiert das bestehende Fenster.
- **Security by Design:** `contextIsolation: true`, `nodeIntegration: false`, `sandbox: true`, strikte CSP und blockierte unautorisierte Popups/Navigation.

## Develop

Requirements: Node.js 24+ and pnpm.

```bash
pnpm --filter @koalashelf/desktop compile   # TypeScript kompilieren
pnpm --filter @koalashelf/desktop build     # Installer für das aktuelle Betriebssystem bauen
pnpm --filter @koalashelf/desktop pack      # Entpacktes Verzeichnis (ohne Installer) bauen
```

