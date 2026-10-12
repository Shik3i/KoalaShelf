// Initializes the local dev Audiobookshelf: root user, library, scan. Idempotent.
// Credentials are generated once and stored in dev/.env (gitignored).
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { randomBytes } from 'node:crypto';

const BASE = process.env.ABS_URL ?? 'http://localhost:13378';
const envFile = join(import.meta.dirname, '.env');
const env = existsSync(envFile)
  ? Object.fromEntries(readFileSync(envFile, 'utf8').split('\n').filter(Boolean).map((l) => l.split('=')))
  : {};
if (!env.ABS_USER) {
  env.ABS_URL = BASE;
  env.ABS_USER = 'koala';
  env.ABS_PASSWORD = randomBytes(12).toString('base64url');
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
console.log(`ready: ${BASE} (credentials in dev/.env)`);
