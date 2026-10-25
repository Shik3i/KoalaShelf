import { app, BrowserWindow, protocol, net } from 'electron';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, join } from 'node:path';
import { writeFileSync, existsSync } from 'node:fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const ARTIFACT_DIR = 'C:\\Users\\s3ish\\.gemini\\antigravity\\brain\\3a48339f-e9e0-411f-9c4f-61355c3b8b0b';

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

console.log('>>> [VISUAL AUDIT] Starting visual verification and screenshot capture...');

async function run() {
  await app.whenReady();

  const buildDir = join(__dirname, '../../web/build');
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

  const { configureWindowSecurity } = await import('../dist/main/security.js');

  // 1. Create Main Window
  console.log('>>> [VISUAL AUDIT] Creating 1280x820 Main Window...');
  const mainWin = new BrowserWindow({
    width: 1280,
    height: 820,
    show: true,
    backgroundColor: '#17130F',
    webPreferences: {
      preload: join(__dirname, '../dist/main/preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true
    }
  });

  configureWindowSecurity(mainWin);
  await mainWin.loadURL('app://koalashelf/');
  await new Promise((r) => setTimeout(r, 1500));

  // Capture Screenshot 1: Clean Sign-in
  console.log('>>> [VISUAL AUDIT] Capturing Screenshot 1: Main Sign-in view...');
  const img1 = await mainWin.webContents.capturePage();
  const file1 = join(ARTIFACT_DIR, 'desktop-01-main-signin.png');
  writeFileSync(file1, img1.toPNG());
  console.log('✔ Screenshot 1 saved to:', file1);

  // 2. Perform interactive UI actions on Main Window
  console.log('>>> [VISUAL AUDIT] Performing interactive form fill in Main Window...');
  await mainWin.webContents.executeJavaScript(`
    (() => {
      const inputs = document.querySelectorAll('input');
      if (inputs[0]) {
        inputs[0].value = 'https://audiobooks.internal.net';
        inputs[0].dispatchEvent(new Event('input', { bubbles: true }));
      }
      if (inputs[1]) {
        inputs[1].value = 'timo';
        inputs[1].dispatchEvent(new Event('input', { bubbles: true }));
      }
      if (inputs[2]) {
        inputs[2].value = 'SuperSecretAudiobook123!';
        inputs[2].dispatchEvent(new Event('input', { bubbles: true }));
      }
    })()
  `);

  await new Promise((r) => setTimeout(r, 800));

  // Capture Screenshot 2: Interactive Form Fill
  console.log('>>> [VISUAL AUDIT] Capturing Screenshot 2: Interactive state...');
  const img2 = await mainWin.webContents.capturePage();
  const file2 = join(ARTIFACT_DIR, 'desktop-02-main-interactive.png');
  writeFileSync(file2, img2.toPNG());
  console.log('✔ Screenshot 2 saved to:', file2);

  // 3. Create Frameless Mini-Player Window
  console.log('>>> [VISUAL AUDIT] Creating 340x240 Mini-Player Window...');
  const miniWin = new BrowserWindow({
    width: 340,
    height: 240,
    show: true,
    alwaysOnTop: true,
    frame: false,
    backgroundColor: '#17130F',
    webPreferences: {
      preload: join(__dirname, '../dist/main/preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  configureWindowSecurity(miniWin);
  await miniWin.loadURL('app://koalashelf/#/mini');
  await new Promise((r) => setTimeout(r, 1000));

  // Inject active book into miniWin
  await miniWin.webContents.executeJavaScript(`
    (() => {
      if (window.__ks_player) {
        window.__ks_player.item = {
          id: 'kangaroo-chronicles',
          media: {
            metadata: {
              title: 'Die K\u00e4nguru-Chroniken',
              authorName: 'Marc-Uwe Kling',
              narratorName: 'Marc-Uwe Kling',
              seriesName: 'K\u00e4nguru-Trilogie'
            },
            duration: 3600,
            chapters: [
              { id: 0, title: 'Kapitel 1: Die Einladung', start: 0, end: 600 }
            ]
          }
        };
        window.__ks_player.chapters = [
          { id: 0, title: 'Kapitel 1: Die Einladung', start: 0, end: 600 }
        ];
        window.__ks_player.currentTime = 142;
        window.__ks_player.duration = 3600;
        window.__ks_player.playing = true;
      }
    })()
  `);

  await new Promise((r) => setTimeout(r, 800));

  // Capture Screenshot 3: Active Mini-Player
  console.log('>>> [VISUAL AUDIT] Capturing Screenshot 3: Active Mini-Player window...');
  const img3 = await miniWin.webContents.capturePage();
  const file3 = join(ARTIFACT_DIR, 'desktop-03-mini-player-active.png');
  writeFileSync(file3, img3.toPNG());
  console.log('✔ Screenshot 3 saved to:', file3);

  mainWin.destroy();
  miniWin.destroy();
  console.log('>>> [VISUAL AUDIT] All visual audit screenshots captured successfully!');
  app.quit();
  setTimeout(() => process.exit(0), 100);
}

run().catch((err) => {
  console.error('>>> [VISUAL AUDIT FAILED]:', err);
  process.exit(1);
});
