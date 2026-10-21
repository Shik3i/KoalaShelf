import { session, shell } from 'electron';

export function setupSecurity(): void {
  // Disallow all permission requests (camera, mic, geolocation, notifications handled via native API)
  session.defaultSession.setPermissionRequestHandler((_webContents, _permission, callback) => {
    callback(false);
  });
  session.defaultSession.setPermissionCheckHandler((_webContents, _permission, _origin) => {
    return false;
  });

  // Strict CSP and security headers
  session.defaultSession.webRequest.onHeadersReceived((details, callback) => {
    const isDev = Boolean(process.env.VITE_DEV_SERVER_URL);
    const csp = [
      "default-src 'self' app:",
      isDev ? "script-src 'self' 'unsafe-inline' 'unsafe-eval' app:" : "script-src 'self' 'unsafe-inline' app:",
      "style-src 'self' 'unsafe-inline' app:",
      "media-src * blob: data:",
      "img-src * data: blob: app:",
      "connect-src * ws: wss:",
      "font-src 'self' data: app:",
      "worker-src 'self' blob: app:",
      "object-src 'none'",
      "base-uri 'self' app:",
      "form-action 'self' app:",
      "frame-ancestors 'none'"
    ].join('; ');

    callback({
      responseHeaders: {
        ...details.responseHeaders,
        'Content-Security-Policy': [csp],
        'X-Content-Type-Options': ['nosniff'],
        'X-Frame-Options': ['DENY']
      }
    });
  });
}

export function configureWindowSecurity(win: Electron.BrowserWindow): void {
  // Block webview tags
  win.webContents.on('will-attach-webview', (event) => {
    event.preventDefault();
  });

  // Prevent opening untrusted popups / new windows, open safe links in external browser
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('https://') || url.startsWith('http://')) {
      void shell.openExternal(url);
    }
    return { action: 'deny' };
  });

  // Block in-app navigation away from KoalaShelf
  win.webContents.on('will-navigate', (event, navigationUrl) => {
    const parsed = new URL(navigationUrl);
    if (parsed.protocol !== 'app:' && parsed.hostname !== 'localhost') {
      event.preventDefault();
      void shell.openExternal(navigationUrl);
    }
  });
}
