import { app, safeStorage } from 'electron';
import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

export class SecureStorage {
  private filePath: string;
  private cache: Record<string, string> = {};
  private initialized = false;

  constructor(customPath?: string) {
    const dir = customPath ?? app.getPath('userData');
    this.filePath = join(dir, 'vault.enc');
  }

  private async init(): Promise<void> {
    if (this.initialized) return;
    this.initialized = true;

    try {
      if (existsSync(this.filePath)) {
        const raw = readFileSync(this.filePath);
        const isAvail = typeof safeStorage.isAsyncEncryptionAvailable === 'function'
          ? await safeStorage.isAsyncEncryptionAvailable()
          : safeStorage.isEncryptionAvailable();

        if (isAvail) {
          if (typeof safeStorage.decryptStringAsync === 'function') {
            const dec = await safeStorage.decryptStringAsync(raw);
            this.cache = JSON.parse(dec.result);
          } else {
            const decrypted = safeStorage.decryptString(raw);
            this.cache = JSON.parse(decrypted);
          }
        } else {
          this.cache = JSON.parse(raw.toString('utf8'));
        }
      }
    } catch {
      this.cache = {};
    }
  }

  private async save(): Promise<void> {
    try {
      const dir = join(this.filePath, '..');
      if (!existsSync(dir)) mkdirSync(dir, { recursive: true });

      const text = JSON.stringify(this.cache);
      const isAvail = typeof safeStorage.isAsyncEncryptionAvailable === 'function'
        ? await safeStorage.isAsyncEncryptionAvailable()
        : safeStorage.isEncryptionAvailable();

      if (isAvail) {
        if (typeof safeStorage.encryptStringAsync === 'function') {
          const encrypted = await safeStorage.encryptStringAsync(text);
          writeFileSync(this.filePath, encrypted);
        } else {
          const encrypted = safeStorage.encryptString(text);
          writeFileSync(this.filePath, encrypted);
        }
      } else {
        writeFileSync(this.filePath, Buffer.from(text, 'utf8'));
      }
    } catch {
      // Ignore in production
    }
  }

  async get(key: string): Promise<string | null> {
    await this.init();
    return this.cache[key] ?? null;
  }

  async set(key: string, value: string): Promise<void> {
    await this.init();
    this.cache[key] = value;
    await this.save();
  }

  async delete(key: string): Promise<void> {
    await this.init();
    delete this.cache[key];
    await this.save();
  }
}

export const secureStorage = new SecureStorage();
