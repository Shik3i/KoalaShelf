import { describe, it, expect, vi, beforeEach } from 'vitest';
import { vault, type ElectronBridge } from './platform.ts';

describe('vault', () => {
	beforeEach(() => {
		vi.restoreAllMocks();
		if (typeof window !== 'undefined') {
			delete (window as unknown as { electron?: unknown }).electron;
		}
		delete (globalThis as unknown as { electron?: unknown }).electron;
	});

	it('safely handles environments without electron bridge', async () => {
		// In test runner without electron
		const secret = await vault.get('ks.tokens.test');
		expect(secret).toBeNull();

		await expect(vault.set('ks.tokens.test', 'secret-val')).resolves.not.toThrow();
		await expect(vault.delete('ks.tokens.test')).resolves.not.toThrow();
	});

	it('delegates to electron safeStorage bridge when present', async () => {
		const mockStorage = {
			getSecret: vi.fn().mockResolvedValue('encrypted-val'),
			setSecret: vi.fn().mockResolvedValue(undefined),
			deleteSecret: vi.fn().mockResolvedValue(undefined)
		};

		const fakeBridge = {
			isElectron: true,
			platform: 'win32',
			storage: mockStorage
		} as unknown as ElectronBridge;

		if (typeof window !== 'undefined') {
			(window as unknown as { electron: ElectronBridge }).electron = fakeBridge;
		}
		(globalThis as unknown as { electron: ElectronBridge }).electron = fakeBridge;

		const retrieved = await vault.get('testKey');
		expect(retrieved).toBe('encrypted-val');
		expect(mockStorage.getSecret).toHaveBeenCalledWith('testKey');

		await vault.set('testKey', 'my-token');
		expect(mockStorage.setSecret).toHaveBeenCalledWith('testKey', 'my-token');

		await vault.delete('testKey');
		expect(mockStorage.deleteSecret).toHaveBeenCalledWith('testKey');
	});
});
