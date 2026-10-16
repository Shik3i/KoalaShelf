// Initializes the local dev Audiobookshelf: root user, library, scan. Idempotent.
// Credentials are generated once and stored in dev/.env (gitignored).
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { randomBytes } from 'node:crypto';

const BASE = process.env.ABS_URL ?? 'http://localhost:13378';
const envFile = join(import.meta.dirname, '.env');
// Read directly (no exists-check first: the file could change in between).
let env = {};
try {
  env = Object.fromEntries(readFileSync(envFile, 'utf8').split('\n').filter(Boolean).map((l) => l.split('=')));
} catch {
  /* first run: no credentials yet */
}
if (env.ABS_USER && !env.ABS_GUEST_USER) {
  env.ABS_GUEST_USER = 'gast';
  env.ABS_GUEST_PASSWORD = randomBytes(12).toString('base64url');
  writeFileSync(envFile, Object.entries(env).map(([k, v]) => `${k}=${v}`).join('\n') + '\n');
}
if (!env.ABS_USER) {
  env.ABS_URL = BASE;
  env.ABS_USER = 'koala';
  env.ABS_PASSWORD = randomBytes(12).toString('base64url');
  env.ABS_GUEST_USER = 'gast';
  env.ABS_GUEST_PASSWORD = randomBytes(12).toString('base64url');
  writeFileSync(envFile, Object.entries(env).map(([k, v]) => `${k}=${v}`).join('\n') + '\n');
}

const json = (method, path, body, token) =>
  fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined
  }).then(async (r) => {
    if (!r.ok) throw new Error(`${method} ${path}: ${r.status} ${await r.text()}`);
    return r.headers.get('content-type')?.includes('json') ? r.json() : r.text();
  });

let status;
for (let i = 0; i < 60 && !status; i++) {
  status = await json('GET', '/status').catch(() => null);
  if (!status) await new Promise((r) => setTimeout(r, 1000));
}
if (!status) throw new Error(`No Audiobookshelf at ${BASE}`);
console.log('server', status.serverVersion);

if (!status.isInit) await json('POST', '/init', { newRoot: { username: env.ABS_USER, password: env.ABS_PASSWORD } });

const login = await fetch(BASE + '/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', 'x-return-tokens': 'true' },
  body: JSON.stringify({ username: env.ABS_USER, password: env.ABS_PASSWORD })
}).then((r) => r.json());
const token = login.user.accessToken;

const { libraries } = await json('GET', '/api/libraries', null, token);
let lib = libraries.find((l) => l.name === 'Hörbücher');
if (!lib) {
  lib = await json('POST', '/api/libraries', { name: 'Hörbücher', mediaType: 'book', icon: 'audiobookshelf', folders: [{ fullPath: '/audiobooks' }] }, token);
  console.log('library created', lib.id);
}
await json('POST', `/api/libraries/${lib.id}/scan`, null, token).catch(() => undefined);

// Second library (70 short stories) for paging and library switching.
let stories = libraries.find((l) => l.name === 'Kurzgeschichten');
if (!stories) {
  stories = await json('POST', '/api/libraries', { name: 'Kurzgeschichten', mediaType: 'book', icon: 'books', folders: [{ fullPath: '/stories' }] }, token);
}
await json('POST', `/api/libraries/${stories.id}/scan`, null, token).catch(() => undefined);
// Wait until the scanner has indexed all sample books.
let items = [];
for (let i = 0; i < 60; i++) {
  items = (await json('GET', `/api/libraries/${lib.id}/items?limit=0&minified=1`, null, token)).results;
  if (items.length >= 7) break;
  await new Promise((r) => setTimeout(r, 1000));
}
const byTitle = (t) => items.find((i) => i.media.metadata.title === t);

// A collection for the collections page.
const { results: collections } = await json('GET', `/api/libraries/${lib.id}/collections`, null, token);
if (!collections.some((c) => c.name === 'Lieblinge')) {
  await json('POST', '/api/collections', { libraryId: lib.id, name: 'Lieblinge', description: 'Fiktive Testsammlung.', books: [byTitle('Der lange Weg').id, byTitle('Nachtzug').id] }, token);
}

// A second user without download permission (edge cases).
const { users } = await json('GET', '/api/users', null, token);
if (!users.some((u) => u.username === env.ABS_GUEST_USER)) {
  await json('POST', '/api/users', {
    username: env.ABS_GUEST_USER,
    password: env.ABS_GUEST_PASSWORD,
    type: 'user',
    isActive: true,
    permissions: { download: false, update: false, delete: false, upload: false, accessAllLibraries: true, accessAllTags: true, accessExplicitContent: true },
    librariesAccessible: []
  }, token);
}

// --reset: fresh listening state for the main user (progress, bookmarks).
if (process.argv.includes('--reset')) {
  const me = await json('GET', '/api/me', null, token);
  for (const p of me.mediaProgress) await json('DELETE', `/api/me/progress/${p.id}`, null, token).catch(() => undefined);
  for (const b of me.bookmarks) await json('DELETE', `/api/me/item/${b.libraryItemId}/bookmark/${b.time}`, null, token).catch(() => undefined);
}

console.log(`ready: ${BASE}, ${items.length} items (credentials in dev/.env)`);
