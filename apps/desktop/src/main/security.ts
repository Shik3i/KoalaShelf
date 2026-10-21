import { session, shell } from 'electron';

export function setupSecurity(): void {
  // Disallow all permission requests (camera, mic, geolocation, notifications handled via native API)
  session.defaultSession.setPermissionRequestHandler((_webContents, _permission, callback) => {
    callback(false);
  });

  // Strict CSP and security headers
  session.defaultSession.webRequest.onHeadersReceived((details, callback) => {
    const csp = [
      "default-src 'self' 'unsafe-inline' data: blob:",
      "media-src * blob: data:",
      "img-src * data: blob:",
      "connect-src * ws: wss:",
      "font-src 'self' data:",
      "worker-src 'self' blob:",
      "object-src 'none'",
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
    if (parsed.protocol !== 'file:' && parsed.protocol !== 'app:' && parsed.hostname !== 'localhost') {
      event.preventDefault();
      void shell.openExternal(navigationUrl);
    }
  });
}
