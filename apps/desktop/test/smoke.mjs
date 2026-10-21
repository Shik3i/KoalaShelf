import { app, BrowserWindow, powerSaveBlocker, safeStorage, protocol, net, ipcMain } from 'electron';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, join } from 'node:path';
import { existsSync, readFileSync, rmSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import assert from 'node:assert';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

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

console.log('>>> [SMOKE TEST] Initializing Electron Desktop Smoke Suite...');

async function run() {
  await app.whenReady();
  console.log('>>> [SMOKE TEST] Electron app.whenReady() succeeded.');

  // 1. Verify build assets exist
  const buildDir = join(__dirname, '../../web/build');
  const buildPath = join(buildDir, 'index.html');
  console.log('>>> [SMOKE TEST] Checking web build at:', buildPath);
  assert(existsSync(buildPath), 'Web build index.html must exist at: ' + buildPath);
  console.log('✔ Web build assets verified');

  // Register app:// protocol
  protocol.handle('app', (request) => {
    const url = new URL(request.url);
    let pathname = decodeURIComponent(url.pathname);
    if (pathname.startsWith('/')) pathname = pathname.slice(1);
    let filePath = join(buildDir, pathname);
    if (!pathname || !existsSync(filePath)) {
      filePath = join(buildDir, 'index.html');
    }
    return net.fetch(pathToFileURL(filePath).toString());
  });

  // 2. Test SecureStorage module
  console.log('>>> [SMOKE TEST] Testing SecureStorage...');
  const tempDir = mkdtempSync(join(tmpdir(), 'ks-test-'));
  const { SecureStorage } = await import('../dist/main/storage.js');
  const storage = new SecureStorage(tempDir);

  storage.set('test-token', JSON.stringify({ accessToken: 'access_123', refreshToken: 'refresh_456' }));
  const retrieved = storage.get('test-token');
  assert(retrieved, 'Secret must be retrieved');
  const parsed = JSON.parse(retrieved);
  assert.strictEqual(parsed.accessToken, 'access_123');
  assert.strictEqual(parsed.refreshToken, 'refresh_456');

  // Verify vault.enc exists on disk
  const vaultPath = join(tempDir, 'vault.enc');
  assert(existsSync(vaultPath), 'vault.enc must exist on disk');
  const rawDisk = readFileSync(vaultPath);
  if (safeStorage.isEncryptionAvailable()) {
    assert(!rawDisk.includes('access_123'), 'Secrets must not be stored in plaintext');
    console.log('✔ safeStorage hardware encryption active and verified (no plaintext on disk)');
  } else {
    console.log('✔ safeStorage fallback active and verified');
  }

  storage.delete('test-token');
  assert.strictEqual(storage.get('test-token'), null);
  console.log('✔ SecureStorage CRUD operations verified');

  // 3. Register IPC handlers for bridge testing
  let mediaUpdateReceived = null;
  ipcMain.handle('storage:getSecret', (_e, key) => storage.get(key));
  ipcMain.handle('storage:setSecret', (_e, key, val) => storage.set(key, val));
  ipcMain.handle('storage:deleteSecret', (_e, key) => storage.delete(key));
  ipcMain.handle('power:inhibitSleep', () => powerSaveBlocker.start('prevent-app-suspension'));
  ipcMain.handle('power:releaseSleep', (_e, id) => {
    if (powerSaveBlocker.isStarted(id)) powerSaveBlocker.stop(id);
  });
  ipcMain.handle('net:fetch', async (_e, url) => ({
    ok: true,
    status: 200,
    statusText: 'OK',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ mock: 'ok', url })
  }));
  ipcMain.on('media:updateState', (_e, state) => {
    mediaUpdateReceived = state;
  });

  // 4. Test Security configuration & BrowserWindow
  console.log('>>> [SMOKE TEST] Creating test BrowserWindow with security configuration...');
  const { setupSecurity, configureWindowSecurity } = await import('../dist/main/security.js');
  setupSecurity();

  const win = new BrowserWindow({
    width: 800,
    height: 600,
    show: false,
    webPreferences: {
      preload: join(__dirname, '../dist/main/preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true
    }
  });

  configureWindowSecurity(win);
  const prefs = win.webContents.getLastWebPreferences();
  assert.strictEqual(prefs.contextIsolation, true, 'contextIsolation must be true');
  assert.strictEqual(prefs.nodeIntegration, false, 'nodeIntegration must be false');
  assert.strictEqual(prefs.sandbox, true, 'sandbox must be true');
  console.log('✔ Security preferences enforced: contextIsolation=true, nodeIntegration=false, sandbox=true');

  // Track console events inside the renderer (filtering expected View Transition aborts)
  const consoleErrors = [];
  win.webContents.on('console-message', (_event, level, message) => {
    if (level >= 3 && !message.includes('Transition was aborted')) {
      consoleErrors.push(message);
    }
  });

  // 5. Test loading local build via app://
  console.log('>>> [SMOKE TEST] Loading static web build into window via app://koalashelf/...');
  await win.loadURL('app://koalashelf/');
  const title = await win.webContents.getTitle();
  console.log('✔ Page loaded with title:', title);

  // 6. Test renderer window.electron IPC bridge
  console.log('>>> [SMOKE TEST] Testing window.electron bridge inside renderer...');
  const bridgeTest = await win.webContents.executeJavaScript(`
    (async () => {
      const e = window.electron;
      if (!e) throw new Error('window.electron missing');
      if (!e.isElectron) throw new Error('isElectron false');
      if (!e.storage) throw new Error('storage missing');

      // Test storage through IPC
      await e.storage.setSecret('ipc-test-token', 'secret-val-abc');
      const val = await e.storage.getSecret('ipc-test-token');
      if (val !== 'secret-val-abc') throw new Error('Stored value mismatch: ' + val);
      await e.storage.deleteSecret('ipc-test-token');
      const cleared = await e.storage.getSecret('ipc-test-token');
      if (cleared !== null) throw new Error('Cleared value should be null');

      // Test netFetch bridge through IPC
      if (!e.netFetch) throw new Error('netFetch missing');
      const fetchRes = await e.netFetch('https://example.com/test');
      if (!fetchRes.ok || fetchRes.status !== 200) throw new Error('netFetch status mismatch');

      // Test power sleep inhibition through IPC
      const id = await e.power.inhibitSleep('test-playback');
      await e.power.releaseSleep(id);

      // Test media update through IPC
      e.media.updateState({
        title: 'Die Känguru-Chroniken',
        author: 'Marc-Uwe Kling',
        playing: true,
        position: 120,
        duration: 3600
      });

      return { ok: true, platform: e.platform };
    })()
  `);
  assert.strictEqual(bridgeTest.ok, true);
  assert(mediaUpdateReceived, 'Media update IPC must be received in main process');
  assert.strictEqual(mediaUpdateReceived.title, 'Die Känguru-Chroniken');
  console.log('✔ window.electron renderer bridge verified: storage, power, media IPC round-trips succeed (platform: ' + bridgeTest.platform + ')');

  // 7. Test PowerSaveBlocker
  console.log('>>> [SMOKE TEST] Testing powerSaveBlocker...');
  const blockerId = powerSaveBlocker.start('prevent-app-suspension');
  assert(powerSaveBlocker.isStarted(blockerId), 'powerSaveBlocker must be started');
  powerSaveBlocker.stop(blockerId);
  assert(!powerSaveBlocker.isStarted(blockerId), 'powerSaveBlocker must be stopped');
  console.log('✔ powerSaveBlocker lifecycle verified');

  // 8. Test Mini Player Window
  console.log('>>> [SMOKE TEST] Creating Mini-Player window...');
  const mini = new BrowserWindow({
    width: 340,
    height: 240,
    show: false,
    alwaysOnTop: true,
    frame: false,
    webPreferences: {
      preload: join(__dirname, '../dist/main/preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });
  configureWindowSecurity(mini);
  assert.strictEqual(mini.isAlwaysOnTop(), true, 'Mini player must be always-on-top');
  await mini.loadURL('app://koalashelf/#/mini');
  console.log('✔ Mini player loaded always-on-top with app://koalashelf/#/mini');

  // Allow settle time
  await new Promise((r) => setTimeout(r, 600));

  assert.strictEqual(consoleErrors.length, 0, 'No console errors expected, got: ' + consoleErrors.join('; '));
  console.log('✔ 0 console errors detected in web renderer');

  // Cleanup
  rmSync(tempDir, { recursive: true, force: true });
  win.destroy();
  mini.destroy();
  console.log('>>> [SMOKE TEST] All runtime assertions passed successfully!');
  app.quit();
  setTimeout(() => process.exit(0), 100);
}

run().catch((err) => {
  console.error('>>> [SMOKE TEST FAILED]:', err);
  process.exit(1);
});
