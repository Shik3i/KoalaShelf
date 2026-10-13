import { test as setup } from '@playwright/test';
import { signIn } from './helpers.ts';

setup('sign in once', async ({ page }) => {
	await signIn(page);
	await page.context().storageState({ path: 'e2e/.auth/user.json' });
});
