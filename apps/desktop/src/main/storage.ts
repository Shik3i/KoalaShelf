import { app, safeStorage } from 'electron';
import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

class SecureStorage {
  private filePath: string;
  private cache: Record<string, string> = {};
  private initialized = false;

  constructor() {
    const userData = app.getPath('userData');
    this.filePath = join(userData, 'vault.enc');
  }

  private init() {
    if (this.initialized) return;
    this.initialized = true;

    try {
      if (existsSync(this.filePath)) {
        const raw = readFileSync(this.filePath);
        if (safeStorage.isEncryptionAvailable()) {
          const decrypted = safeStorage.decryptString(raw);
          this.cache = JSON.parse(decrypted);
        }
      }
    } catch {
      this.cache = {};
    }
  }

  private save() {
    try {
      const dir = app.getPath('userData');
      if (!existsSync(dir)) mkdirSync(dir, { recursive: true });

      const text = JSON.stringify(this.cache);
      if (safeStorage.isEncryptionAvailable()) {
        const encrypted = safeStorage.encryptString(text);
        writeFileSync(this.filePath, encrypted);
      }
    } catch {
      // Ignore or log in debug mode
    }
  }

  get(key: string): string | null {
    this.init();
    return this.cache[key] ?? null;
  }

  set(key: string, value: string): void {
    this.init();
    this.cache[key] = value;
    this.save();
  }

  delete(key: string): void {
    this.init();
    delete this.cache[key];
    this.save();
  }
}

export const secureStorage = new SecureStorage();
