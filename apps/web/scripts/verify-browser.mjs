import { chromium } from '@playwright/test';

(async () => {
	console.log('--- STARTING IN-BROWSER PLAYWRIGHT VERIFICATION ---');
	const browser = await chromium.launch({ channel: 'msedge', headless: true });
	const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
	const page = await context.newPage();
	const errors = [];

	page.on('console', (msg) => {
		if (msg.type() === 'error') errors.push(`[CONSOLE_ERROR] ${msg.text()}`);
	});
	page.on('pageerror', (err) => errors.push(`[PAGE_ERROR] ${err.message}\n${err.stack}`));
	page.on('requestfailed', (req) =>
		errors.push(`[REQ_FAILED] ${req.url()}: ${req.failure()?.errorText}`)
	);
	page.on('response', (res) => {
		if (res.status() >= 400) {
			console.log(`[HTTP_${res.status()}] ${res.url()}`);
		}
		if (res.status() >= 500) errors.push(`[HTTP_${res.status()}] ${res.url()}`);
	});

	// Mock all API endpoints so test runs without hitting unauthenticated real endpoints
	await page.route('**/api/libraries', async (route) => {
		await route.fulfill({
			status: 200,
			contentType: 'application/json',
			body: JSON.stringify({
				libraries: [
					{ id: 'lib-1', name: 'Audiobooks', mediaType: 'book', displayOrder: 1 },
					{ id: 'lib-2', name: 'Podcasts', mediaType: 'book', displayOrder: 2 }
				]
			})
		});
	});

	await page.route('**/api/me', async (route) => {
		await route.fulfill({
			status: 200,
			contentType: 'application/json',
			body: JSON.stringify({
				user: { id: 'tester', username: 'Tester' },
				mediaProgress: []
			})
		});
	});

	await page.route('**/api/authorize', async (route) => {
		await route.fulfill({
			status: 200,
			contentType: 'application/json',
			body: JSON.stringify({
				user: { id: 'tester', username: 'Tester' },
				mediaProgress: []
			})
		});
	});

	await page.route('**/api/**', async (route) => {
		await route.fulfill({
			status: 200,
			contentType: 'application/json',
			body: JSON.stringify({ results: [], total: 0 })
		});
	});

	await page.route('**/auth/**', async (route) => {
		await route.fulfill({
			status: 200,
			contentType: 'application/json',
			body: JSON.stringify({
				user: { id: 'tester', username: 'Tester' },
				userDefaultLibraryId: 'lib-1'
			})
		});
	});

	await page.route('**/socket.io/**', async (route) => {
		await route.fulfill({
			status: 200,
			contentType: 'text/plain',
			body: 'ok'
		});
	});

	// Pre-populate mock session in localStorage before page loads
	await page.addInitScript(() => {
		localStorage.setItem(
			'ks.accounts',
			JSON.stringify({
				active: '|tester',
				accounts: [
					{
						key: '|tester',
						baseUrl: '',
						user: { id: 'tester', username: 'Tester' },
						tokens: { accessToken: 'valid.mock.token' },
						libraryId: 'lib-1',
						libraries: [
							{ id: 'lib-1', name: 'Audiobooks', mediaType: 'book', displayOrder: 1 },
							{ id: 'lib-2', name: 'Podcasts', mediaType: 'book', displayOrder: 2 }
						],
						lastUsed: Date.now()
					}
				]
			})
		);
	});

	await page.goto('http://localhost:8080/library', { waitUntil: 'domcontentloaded' });
	await page.waitForTimeout(400);

	console.log('Current URL:', page.url());
	console.log('Page Title:', await page.title());

	const aside = await page.$('aside.side');
	console.log('✓ Sidebar rendered:', !!aside);
	if (!aside) {
		throw new Error('Sidebar was not rendered!');
	}

	// 1. TEST PERSONAL GROUP COLLAPSE & EXPAND
	console.log('\n--- 1. Testing "Persönlich" group collapse & expand ---');
	const personalHeader = await page.$(
		'button.nav-header:has-text("Persönlich"), button.nav-header:has-text("Personal")'
	);
	if (!personalHeader) throw new Error('Persönlich header button not found!');

	let isExpanded = await personalHeader.getAttribute('aria-expanded');
	console.log('Initial aria-expanded:', isExpanded);
	if (isExpanded !== 'true') throw new Error('Expected group to be expanded by default');

	// Click to collapse
	await personalHeader.click();
	await page.waitForTimeout(200);
	isExpanded = await personalHeader.getAttribute('aria-expanded');
	console.log('After click aria-expanded:', isExpanded);
	if (isExpanded !== 'false')
		throw new Error('Group failed to collapse! aria-expanded is not false.');

	const downloadsLink = await page.$('aside.side nav a[href="/downloads"]');
	console.log('Downloads link in DOM after collapse:', !!downloadsLink);
	if (downloadsLink) {
		throw new Error('Downloads link is still present when group is collapsed!');
	}

	// Click again to re-expand
	await personalHeader.click();
	await page.waitForTimeout(200);
	isExpanded = await personalHeader.getAttribute('aria-expanded');
	console.log('After 2nd click aria-expanded:', isExpanded);
	if (isExpanded !== 'true')
		throw new Error('Group failed to re-expand! aria-expanded is not true.');

	const downloadsLinkReopened = await page.$('aside.side nav a[href="/downloads"]');
	console.log(
		'Downloads link visible after re-expand:',
		downloadsLinkReopened ? await downloadsLinkReopened.isVisible() : false
	);
	if (!downloadsLinkReopened) {
		throw new Error('Downloads link should be visible after re-expansion!');
	}
	console.log('✓ "Persönlich" collapse & re-expand verified successfully!');

	// 2. TEST SIDEBAR ICON-ONLY COLLAPSE BUTTON
	console.log('\n--- 2. Testing Sidebar Collapse Toggle Button ---');
	const collapseBtn = await page.$('.collapse-btn');
	if (!collapseBtn) throw new Error('Collapse button not found in sidebar header!');

	await collapseBtn.click();
	await page.waitForTimeout(300);

	let sidebarWidth = await page.evaluate(() =>
		getComputedStyle(document.documentElement).getPropertyValue('--layout-sidebar').trim()
	);
	let dataCollapsed = await page.evaluate(() => document.documentElement.dataset.sidebarCollapsed);
	console.log('Collapsed state: width =', sidebarWidth, 'data-sidebar-collapsed =', dataCollapsed);
	if (sidebarWidth !== '68px' || dataCollapsed !== 'true') {
		throw new Error(`Expected 68px and dataset true, got ${sidebarWidth} and ${dataCollapsed}`);
	}

	// Re-expand
	await collapseBtn.click();
	await page.waitForTimeout(300);
	sidebarWidth = await page.evaluate(() =>
		getComputedStyle(document.documentElement).getPropertyValue('--layout-sidebar').trim()
	);
	dataCollapsed = await page.evaluate(() => document.documentElement.dataset.sidebarCollapsed);
	console.log(
		'Re-expanded state: width =',
		sidebarWidth,
		'data-sidebar-collapsed =',
		dataCollapsed
	);
	if (sidebarWidth !== '240px' || dataCollapsed !== 'false') {
		throw new Error(`Expected 240px and dataset false, got ${sidebarWidth} and ${dataCollapsed}`);
	}
	console.log('✓ Sidebar icon-only collapse toggle verified successfully!');

	// 3. TEST RESIZE HANDLE DRAG & DOUBLE-CLICK RESET
	console.log('\n--- 3. Testing Resize Handle Drag & Reset ---');
	const handle = await page.$('.resize-handle');
	if (!handle) throw new Error('Resize handle not found!');

	const box = await handle.boundingBox();
	console.log('Handle box:', box);
	if (!box) throw new Error('Could not get resize handle bounding box');

	const elAtPoint = await page.evaluate((b) => {
		const el = document.elementFromPoint(b.x + b.width / 2, b.y + 100);
		return el ? el.tagName + '.' + el.className : 'null';
	}, box);
	console.log('Element at point:', elAtPoint);

	// Check if handle has listeners or triggers
	const hasResizingClassBefore = await page.evaluate(() =>
		document.documentElement.classList.contains('is-resizing')
	);
	console.log('is-resizing before mousedown:', hasResizingClassBefore);

	// Drag resize handle from 240px to 310px
	await page.mouse.move(box.x + box.width / 2, box.y + 100);
	await page.mouse.down();
	const hasResizingClassDown = await page.evaluate(() =>
		document.documentElement.classList.contains('is-resizing')
	);
	console.log('is-resizing after mousedown:', hasResizingClassDown);

	await page.mouse.move(310, box.y + 100);
	const widthDuringMove = await page.evaluate(() =>
		getComputedStyle(document.documentElement).getPropertyValue('--layout-sidebar').trim()
	);
	console.log('Width during move to 310px:', widthDuringMove);

	await page.mouse.up();
	await page.waitForTimeout(200);

	sidebarWidth = await page.evaluate(() =>
		getComputedStyle(document.documentElement).getPropertyValue('--layout-sidebar').trim()
	);
	console.log('Width after drag to 310px:', sidebarWidth);
	if (sidebarWidth !== '310px') {
		throw new Error(`Expected 310px after dragging, got ${sidebarWidth}`);
	}

	// Double-click reset to 240px
	const newBox = await handle.boundingBox();
	await page.mouse.dblclick(newBox.x + newBox.width / 2, newBox.y + 100);
	await page.waitForTimeout(200);

	sidebarWidth = await page.evaluate(() =>
		getComputedStyle(document.documentElement).getPropertyValue('--layout-sidebar').trim()
	);
	console.log('Width after double click reset:', sidebarWidth);
	if (sidebarWidth !== '240px') {
		throw new Error(`Expected 240px after double click reset, got ${sidebarWidth}`);
	}
	console.log('✓ Sidebar resize handle and double-click reset verified successfully!');

	// 4. TEST TOP MODERN LIBRARY SWITCHER
	console.log('\n--- 4. Testing Modern Library Switcher ---');
	const libTrigger = await page.$('.lib-trigger');
	if (!libTrigger) throw new Error('Library switcher trigger not found!');

	const currentLibName = await page.$eval('.lib-name', (el) => el.innerText);
	console.log('Current library displayed:', currentLibName);
	if (!currentLibName.includes('Audiobooks')) {
		throw new Error(`Expected Audiobooks, got ${currentLibName}`);
	}

	// Open dropdown
	await libTrigger.click();
	await page.waitForTimeout(200);
	const dropdown = await page.$('.lib-dropdown');
	if (!dropdown) throw new Error('Library dropdown failed to open!');

	const libOptions = await page.$$('.lib-opt');
	console.log('Library options count:', libOptions.length);
	if (libOptions.length !== 2) throw new Error(`Expected 2 libraries, got ${libOptions.length}`);

	// Click Podcasts
	const podcastsBtn = libOptions[1];
	await podcastsBtn.click();
	await page.waitForTimeout(200);

	const isDropdownClosed = !(await page.$('.lib-dropdown'));
	console.log('Dropdown closed after pick:', isDropdownClosed);
	if (!isDropdownClosed) throw new Error('Dropdown should close after selection!');
	console.log('✓ Library switcher dropdown interaction verified successfully!');

	// 5. ERROR CHECK
	console.log('\n--- 5. Console & Network Error Check ---');
	console.log('Total Captured Errors:', errors.length);
	if (errors.length) {
		console.error('Captured errors:', errors);
		throw new Error(`Browser encountered ${errors.length} errors during verification!`);
	}
	console.log('✓ Zero errors captured in browser console and network!');

	await browser.close();
	console.log('\n======================================================');
	console.log('🎉 ALL IN-BROWSER TESTS PASSED FLAWLESSLY IN EDGE! 🎉');
	console.log('======================================================');
})().catch((err) => {
	console.error('\n❌ IN-BROWSER VERIFICATION FAILED:', err);
	process.exit(1);
});
