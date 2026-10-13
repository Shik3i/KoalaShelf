import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

// Fresh listening state for every run: no progress, no bookmarks.
export default function globalSetup() {
	const script = fileURLToPath(new URL('../../../dev/setup-abs.mjs', import.meta.url));
	execFileSync('node', [script, '--reset'], { stdio: 'inherit' });
}
