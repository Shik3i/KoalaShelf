import { app, BrowserWindow, ipcMain, dialog, powerSaveBlocker, Tray, Menu, nativeImage } from 'electron';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { existsSync } from 'node:fs';
import { setupSecurity, configureWindowSecurity } from './security.js';
import { secureStorage } from './storage.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

let mainWindow: BrowserWindow | null = null;
let miniWindow: BrowserWindow | null = null;
let tray: Tray | null = null;
let sleepBlockerId: number | null = null;

// Enforce single instance
const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore();
      mainWindow.focus();
    }
  });
}

function getWebBuildPath(): string {
  // apps/desktop/dist/main -> apps/web/build
  return join(__dirname, '../../../../web/build/index.html');
}

function createMainWindow(): BrowserWindow {
  const win = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 400,
    minHeight: 600,
    backgroundColor: '#17130F',
    title: 'KoalaShelf',
    webPreferences: {
      preload: join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true
    }
  });

  configureWindowSecurity(win);

  const devUrl = process.env.VITE_DEV_SERVER_URL;
  const buildPath = getWebBuildPath();

  if (devUrl) {
    void win.loadURL(devUrl);
  } else if (existsSync(buildPath)) {
    void win.loadFile(buildPath);
  } else {
    void win.loadURL('http://localhost:5173');
  }

  win.on('closed', () => {
    mainWindow = null;
  });

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
      preload: join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  configureWindowSecurity(win);

  const devUrl = process.env.VITE_DEV_SERVER_URL;
  const buildPath = getWebBuildPath();

  if (devUrl) {
    void win.loadURL(`${devUrl}#/mini`);
  } else if (existsSync(buildPath)) {
    void win.loadFile(buildPath, { hash: '/mini' });
  }

  win.on('closed', () => {
    miniWindow = null;
  });

  miniWindow = win;
  return win;
}

function setupTray(): void {
  const iconPath = join(__dirname, '../../resources/icons/32x32.png');
  if (!existsSync(iconPath)) return;

  const icon = nativeImage.createFromPath(iconPath);
  tray = new Tray(icon);
  tray.setToolTip('KoalaShelf');

  const contextMenu = Menu.buildFromTemplate([
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
  tray.on('double-click', () => {
    if (mainWindow) {
      mainWindow.show();
      mainWindow.focus();
    }
  });
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
    if (!mainWindow) return null;
    const res = await dialog.showSaveDialog(mainWindow, options);
    return res.canceled ? null : res.filePath;
  });
}

app.whenReady().then(() => {
  setupSecurity();
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
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
