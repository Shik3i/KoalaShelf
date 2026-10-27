import { app, BrowserWindow, ipcMain, dialog, powerSaveBlocker, Tray, Menu, nativeImage, protocol, net } from 'electron';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, join, resolve } from 'node:path';
import { existsSync, appendFileSync } from 'node:fs';
import { setupSecurity, configureWindowSecurity } from './security.js';
import { secureStorage } from './storage.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

function log(msg: string) {
  const line = `[${new Date().toISOString()}] ${msg}\n`;
  try {
    if (app.isReady()) {
      appendFileSync(join(app.getPath('userData'), 'debug.log'), line);
    }
  } catch {}
  console.log(msg);
}

process.on('uncaughtException', (err) => {
  log(`UNCAUGHT EXCEPTION: ${err?.stack || err}`);
});

process.on('unhandledRejection', (reason) => {
  log(`UNHANDLED REJECTION: ${reason}`);
});

let isQuitting = false;
app.on('before-quit', () => {
  isQuitting = true;
  log('app event: before-quit');
});
app.on('will-quit', () => {
  log('app event: will-quit');
  if (sleepBlockerId !== null && powerSaveBlocker.isStarted(sleepBlockerId)) {
    powerSaveBlocker.stop(sleepBlockerId);
    sleepBlockerId = null;
  }
  if (tray) {
    tray.destroy();
    tray = null;
  }
});
app.on('quit', (e, code) => log(`app event: quit (exitCode: ${code})`));

log('Electron main process starting...');

protocol.registerSchemesAsPrivileged([
  {
    scheme: 'app',
    privileges: {
      standard: true,
      secure: true,
      supportFetchAPI: true,
      corsEnabled: true,
      stream: true
    }
  }
]);

let mainWindow: BrowserWindow | null = null;
let miniWindow: BrowserWindow | null = null;
let tray: Tray | null = null;
let sleepBlockerId: number | null = null;

interface MediaState {
  title: string;
  author: string;
  coverUrl?: string;
  playing: boolean;
  position: number;
  duration: number;
}

let currentMediaState: MediaState | null = null;

// Enforce single instance
const gotLock = app.requestSingleInstanceLock();
log(`requestSingleInstanceLock: ${gotLock}`);
if (!gotLock) {
  log('Could not get single instance lock, quitting...');
  app.quit();
} else {
  app.on('second-instance', () => {
    log('app event: second-instance received, focusing mainWindow');
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });
}

function getWebBuildDir(): string {
  const candidates = [
    join(__dirname, '../../../web/build'),
    join(__dirname, '../../web/build'),
    join(app.getAppPath(), 'web/build'),
    join(app.getAppPath(), '../web/build')
  ];

  for (const p of candidates) {
    if (existsSync(join(p, 'index.html'))) return p;
  }
  return candidates[0];
}

function getTrayIconPath(): string {
  const iconsDir = join(__dirname, '../../resources/icons');
  if (process.platform === 'win32') {
    const ico = join(iconsDir, 'icon.ico');
    if (existsSync(ico)) return ico;
  }
  const png = join(iconsDir, '32x32.png');
  if (existsSync(png)) return png;
  return join(iconsDir, 'icon.png');
}

function getWindowIconPath(): string | undefined {
  const iconsDir = join(__dirname, '../../resources/icons');
  if (process.platform === 'win32') {
    const ico = join(iconsDir, 'icon.ico');
    if (existsSync(ico)) return ico;
  }
  const png = join(iconsDir, 'icon.png');
  if (existsSync(png)) return png;
  return undefined;
}

function createMainWindow(): BrowserWindow {
  const win = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 400,
    minHeight: 600,
    backgroundColor: '#17130F',
    title: 'KoalaShelf',
    icon: getWindowIconPath(),
    show: true,
    webPreferences: {
      preload: join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true
    }
  });

  configureWindowSecurity(win);

  win.once('ready-to-show', () => {
    log('mainWindow event: ready-to-show -> calling win.show() and win.focus()');
    win.show();
    win.focus();
  });

  win.webContents.on('did-finish-load', () => {
    log(`mainWindow webContents did-finish-load: URL=${win.webContents.getURL()}`);
  });

  win.webContents.on('did-fail-load', (e, code, desc, url) => {
    log(`mainWindow webContents did-fail-load: code=${code}, desc=${desc}, url=${url}`);
  });

  win.webContents.on('render-process-gone', (e, details) => {
    log(`mainWindow render-process-gone: reason=${details.reason}, exitCode=${details.exitCode}`);
  });

  win.on('close', (e) => {
    log('mainWindow event: close');
    if (!isQuitting && miniWindow && !miniWindow.isDestroyed()) {
      e.preventDefault();
      win.hide();
      log('mainWindow hidden while miniPlayer is active');
    }
  });

  win.on('closed', () => {
    log('mainWindow event: closed');
    mainWindow = null;
  });

  const devUrl = process.env.VITE_DEV_SERVER_URL;
  if (devUrl) {
    void win.loadURL(devUrl);
  } else {
    void win.loadURL('app://koalashelf/');
  }

  return win;
}

function createMiniPlayer(): BrowserWindow {
  if (miniWindow) {
    miniWindow.focus();
    return miniWindow;
  }

  const win = new BrowserWindow({
    width: 340,
    height: 240,
    minWidth: 280,
    minHeight: 180,
    alwaysOnTop: true,
    frame: false,
    resizable: true,
    backgroundColor: '#17130F',
    title: 'KoalaShelf Mini',
    webPreferences: {
      preload: join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  configureWindowSecurity(win);

  const devUrl = process.env.VITE_DEV_SERVER_URL;
  if (devUrl) {
    void win.loadURL(`${devUrl}#/mini`);
  } else {
    void win.loadURL('app://koalashelf/#/mini');
  }

  win.on('closed', () => {
    miniWindow = null;
  });

  miniWindow = win;
  return win;
}

function updateTrayMenu(): void {
  if (!tray) return;

  const tooltip = currentMediaState
    ? `${currentMediaState.title} - ${currentMediaState.author}`
    : 'KoalaShelf';
  tray.setToolTip(tooltip);

  const contextMenu = Menu.buildFromTemplate([
    {
      label: currentMediaState ? currentMediaState.title : 'KoalaShelf',
      enabled: false
    },
    {
      label: currentMediaState?.playing ? 'Pause' : 'Wiedergabe',
      enabled: !!currentMediaState,
      click: () => {
        const target = (mainWindow && !mainWindow.isDestroyed()) ? mainWindow : miniWindow;
        target?.webContents.send('media:command', 'play-pause');
      }
    },
    {
      label: '30s zurück',
      enabled: !!currentMediaState,
      click: () => {
        const target = (mainWindow && !mainWindow.isDestroyed()) ? mainWindow : miniWindow;
        target?.webContents.send('media:command', 'skip-back');
      }
    },
    {
      label: '30s vor',
      enabled: !!currentMediaState,
      click: () => {
        const target = (mainWindow && !mainWindow.isDestroyed()) ? mainWindow : miniWindow;
        target?.webContents.send('media:command', 'skip-forward');
      }
    },
    {
      label: 'Vorheriges Kapitel',
      enabled: !!currentMediaState,
      click: () => {
        const target = (mainWindow && !mainWindow.isDestroyed()) ? mainWindow : miniWindow;
        target?.webContents.send('media:command', 'prev-chapter');
      }
    },
    {
      label: 'Nächstes Kapitel',
      enabled: !!currentMediaState,
      click: () => {
        const target = (mainWindow && !mainWindow.isDestroyed()) ? mainWindow : miniWindow;
        target?.webContents.send('media:command', 'next-chapter');
      }
    },
    { type: 'separator' },
    {
      label: 'KoalaShelf öffnen',
      click: () => {
        if (mainWindow) {
          mainWindow.show();
          mainWindow.focus();
        }
      }
    },
    {
      label: 'Mini-Player',
      click: () => {
        createMiniPlayer();
      }
    },
    { type: 'separator' },
    {
      label: 'Beenden',
      click: () => {
        app.quit();
      }
    }
  ]);

  tray.setContextMenu(contextMenu);
}

function setupTray(): void {
  const iconPath = getTrayIconPath();
  if (!existsSync(iconPath)) return;

  try {
    const icon = process.platform === 'win32' && iconPath.endsWith('.ico')
      ? nativeImage.createFromPath(iconPath)
      : nativeImage.createFromPath(iconPath);
    tray = new Tray(icon.isEmpty() ? iconPath : icon);
    updateTrayMenu();

    tray.on('double-click', () => {
      if (mainWindow) {
        if (mainWindow.isMinimized()) mainWindow.restore();
        mainWindow.show();
        mainWindow.focus();
      }
    });
  } catch (err) {
    console.error('Tray initialization error:', err);
  }
}

function registerIpcHandlers(): void {
  // Vault storage handlers (OS Keychain via safeStorage)
  ipcMain.handle('storage:getSecret', (_event, key: string) => {
    return secureStorage.get(key);
  });

  ipcMain.handle('storage:setSecret', (_event, key: string, value: string) => {
    secureStorage.set(key, value);
  });

  ipcMain.handle('storage:deleteSecret', (_event, key: string) => {
    secureStorage.delete(key);
  });

  // Power save blocker (prevents sleep during playback)
  ipcMain.handle('power:inhibitSleep', (_event, _reason?: string) => {
    if (sleepBlockerId === null || !powerSaveBlocker.isStarted(sleepBlockerId)) {
      sleepBlockerId = powerSaveBlocker.start('prevent-app-suspension');
    }
    return sleepBlockerId;
  });

  ipcMain.handle('power:releaseSleep', (_event, id: number) => {
    if (powerSaveBlocker.isStarted(id)) {
      powerSaveBlocker.stop(id);
    }
    sleepBlockerId = null;
  });

  // Media state synchronization
  ipcMain.on('media:updateState', (_event, state: MediaState) => {
    currentMediaState = state;
    updateTrayMenu();

    if (state.playing) {
      if (sleepBlockerId === null || !powerSaveBlocker.isStarted(sleepBlockerId)) {
        sleepBlockerId = powerSaveBlocker.start('prevent-app-suspension');
      }
    } else {
      if (sleepBlockerId !== null && powerSaveBlocker.isStarted(sleepBlockerId)) {
        powerSaveBlocker.stop(sleepBlockerId);
        sleepBlockerId = null;
      }
    }
  });

  // Native net:fetch bridge (bypasses CORS for direct Audiobookshelf API requests)
  ipcMain.handle('net:fetch', async (_event, input: string, init?: {
    method?: string;
    headers?: Record<string, string>;
    body?: string;
  }) => {
    // Security: Only allow http: and https: protocols
    if (!input.startsWith('http://') && !input.startsWith('https://')) {
      throw new Error(`Forbidden protocol in net:fetch: ${input}`);
    }

    const reqInit: RequestInit = {
      method: init?.method || 'GET',
      headers: init?.headers,
      body: init?.body
    };
    const res = await net.fetch(input, reqInit);
    const buffer = Buffer.from(await res.arrayBuffer());
    const headers: Record<string, string> = {};
    res.headers.forEach((value, key) => {
      headers[key.toLowerCase()] = value;
    });
    return {
      ok: res.ok,
      status: res.status,
      statusText: res.statusText,
      headers,
      body: new Uint8Array(buffer)
    };
  });

  // Window control
  ipcMain.on('window:minimize', () => {
    mainWindow?.minimize();
  });

  ipcMain.on('window:maximize', () => {
    if (mainWindow?.isMaximized()) {
      mainWindow.unmaximize();
    } else {
      mainWindow?.maximize();
    }
  });

  ipcMain.on('window:close', () => {
    mainWindow?.close();
  });

  ipcMain.on('window:setAlwaysOnTop', (_event, always: boolean) => {
    mainWindow?.setAlwaysOnTop(always);
  });

  ipcMain.on('window:openMiniPlayer', () => {
    createMiniPlayer();
  });

  // Native save dialog
  ipcMain.handle('dialog:showSaveDialog', async (_event, options) => {
    const parentWin = mainWindow ?? BrowserWindow.getFocusedWindow();
    if (!parentWin) return null;
    const res = await dialog.showSaveDialog(parentWin, options);
    return res.canceled ? null : res.filePath;
  });

  ipcMain.handle('dialog:saveFile', async (_event, options: { name: string; content: string; isBase64?: boolean; filters?: Array<{ name: string; extensions: string[] }> }) => {
    const parentWin = mainWindow ?? BrowserWindow.getFocusedWindow();
    if (!parentWin) return false;
    const res = await dialog.showSaveDialog(parentWin, {
      defaultPath: options.name,
      filters: options.filters
    });
    if (res.canceled || !res.filePath) return false;
    const { writeFileSync } = await import('node:fs');
    if (options.isBase64) {
      writeFileSync(res.filePath, Buffer.from(options.content, 'base64'));
    } else {
      writeFileSync(res.filePath, options.content, 'utf8');
    }
    return true;
  });
}

app.whenReady().then(() => {
  setupSecurity();

  protocol.handle('app', (request) => {
    const url = new URL(request.url);
    const buildDir = resolve(getWebBuildDir());
    let pathname = decodeURIComponent(url.pathname);
    if (pathname.startsWith('/')) pathname = pathname.slice(1);
    const targetPath = resolve(buildDir, pathname);

    // Security: strictly prevent directory traversal outside buildDir
    let filePath = targetPath;
    if (!targetPath.startsWith(buildDir) || !pathname || !existsSync(targetPath)) {
      filePath = join(buildDir, 'index.html');
    }
    return net.fetch(pathToFileURL(filePath).toString());
  });

  registerIpcHandlers();
  mainWindow = createMainWindow();
  setupTray();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      mainWindow = createMainWindow();
    }
  });
});

app.on('window-all-closed', () => {
  log('app event: window-all-closed -> calling app.quit()');
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
