// Release checks for the GitHub workflows (and for running by hand before tagging).
//
//   node scripts/release.mjs policy                 workflows pinned, image bases pinned, roles kept apart
//   node scripts/release.mjs version web-v0.1.0     the tag matches the component's version files
//   node scripts/release.mjs notes web 0.1.0        prints that version's CHANGELOG section
//   node scripts/release.mjs gate                   release commit = main, CodeQL green, no open alerts
//
// Components can be released separately: web-vX.Y.Z (Docker image), desktop-vX.Y.Z (installers), android-vX.Y.Z (APK), or together with unified vX.Y.Z tags.
import { execFileSync } from 'node:child_process';
import { readFileSync, readdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export const ROOT = resolve(import.meta.dirname, '..');
const read = (path) => readFileSync(join(ROOT, path), 'utf8');

/** Which workflow may publish what. */
const PUBLISHERS = {
	'release-web.yml': 'web',
	'release-desktop.yml': 'desktop',
	'release-android.yml': 'android'
};
const CODEQL_LANGUAGES = ['javascript-typescript', 'actions'];

/** Version files per component; every one must carry the released version. */
export const VERSION_FILES = {
	web: { 'apps/web/package.json': (s) => JSON.parse(s).version },
	desktop: {
		'apps/desktop/package.json': (s) => JSON.parse(s).version
	},
	android: {
		'apps/android/app/build.gradle.kts': (s) => s.match(/versionName\s*=\s*"([^"]+)"/)?.[1]
	}
};

/** Problems in workflow and Dockerfile sources (empty when everything follows the rules). */
export function policyErrors(workflows, dockerfiles) {
	const errors = [];
	for (const [name, source] of Object.entries(workflows)) {
		for (const m of source.matchAll(/uses:\s+([^\s@]+)@([^\s#]+)/g)) {
			if (!m[1].startsWith('./') && !/^[0-9a-f]{40}$/.test(m[2]))
				errors.push(`${name}: ${m[1]} must be pinned to a full commit SHA`);
		}
		if (!(name in PUBLISHERS) && /\bgh\s+release\s+create\b|packages:\s*write|push:\s*true/.test(source))
			errors.push(`${name}: only the release workflows may publish`);
		if (name === 'codeql.yml' && /secrets\.|(contents|packages|id-token|attestations):\s*write/.test(source))
			errors.push(`${name}: code scanning must stay read-only`);
		if (/\$\{\{\s*github\.event\.(head_commit|pull_request|issue|comment)/.test(source))
			errors.push(`${name}: untrusted event text must not be expanded in workflows`);
	}
	for (const [name, source] of Object.entries(dockerfiles)) {
		for (const m of source.matchAll(/^FROM(?:\s+--platform=\S+)?\s+(\S+)/gm)) {
			if (!/@sha256:[0-9a-f]{64}$/.test(m[1]))
				errors.push(`${name}: base image ${m[1]} must be pinned to a sha256 digest`);
		}
	}
	return errors;
}

/** "web-v1.2.3", "desktop-v1.2.3", "android-v1.2.3" or "v1.2.3" -> { component: 'web', version: '1.2.3' } (or throws). */
export function parseTag(tag, expectedComponent = 'web') {
	const m = /^(?:(web|desktop|android)-)?v(\d+\.\d+\.\d+)$/.exec(tag ?? '');
	if (!m) throw new Error(`Not a release tag: ${tag} (expected [web|desktop|android-]vX.Y.Z)`);
	return { component: m[1] ?? expectedComponent, version: m[2] };
}

export function versionErrors(component, version, readFile = read) {
	return Object.entries(VERSION_FILES[component]).flatMap(([file, get]) => {
		const found = get(readFile(file));
		return found === version ? [] : [`${file} has version ${found}, the tag says ${version}`];
	});
}

/** The CHANGELOG section "## <Component> <version>" up to the next "## " heading. */
export function releaseNotes(changelog, component, version) {
	const title = `${component[0].toUpperCase()}${component.slice(1)} ${version}`;
	const lines = changelog.split('\n');
	const start = lines.findIndex((l) => l.startsWith(`## ${title}`));
	if (start < 0) throw new Error(`CHANGELOG.md has no section "## ${title}"`);
	const end = lines.findIndex((l, i) => i > start && l.startsWith('## '));
	const body = lines.slice(start + 1, end < 0 ? undefined : end).join('\n').trim();
	if (!body) throw new Error(`CHANGELOG.md section "## ${title}" is empty`);
	return body;
}

/** The release commit is main's head, CodeQL analysed exactly it, and nothing is open. */
export function gateErrors({ sha, mainSha, analyses, alerts }) {
	const errors = [];
	if (!/^[0-9a-f]{40}$/.test(sha)) errors.push('Invalid release commit');
	if (sha !== mainSha) errors.push('Release commit is not the current main commit');
	for (const language of CODEQL_LANGUAGES) {
		const done = analyses.find(
			(a) => a.commit_sha === sha && a.category === `/language:${language}` && !a.error
		);
		if (!done) errors.push(`No successful CodeQL analysis (${language}) of the release commit yet`);
	}
	if (alerts.length) errors.push(`${alerts.length} open code scanning alert(s) on main`);
	return errors;
}

function gh(path) {
	return JSON.parse(
		execFileSync('gh', ['api', '--paginate', '--slurp', path], {
			encoding: 'utf8',
			maxBuffer: 32 * 1024 * 1024
		})
	).flat();
}

async function gate() {
	const repo = process.env.GITHUB_REPOSITORY;
	if (!/^[\w.-]+\/[\w.-]+$/.test(repo ?? '')) throw new Error('GITHUB_REPOSITORY is required');
	const sha = execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
	// CodeQL on the tagged commit may still be running: wait up to 30 minutes for it.
	for (let attempt = 0; ; attempt++) {
		const mainSha = gh(`repos/${repo}/git/ref/heads/main`)[0].object.sha;
		const analyses = gh(`repos/${repo}/code-scanning/analyses?ref=refs/heads/main&per_page=100`);
		const alerts = gh(`repos/${repo}/code-scanning/alerts?state=open&ref=refs/heads/main&per_page=100`);
		const errors = gateErrors({ sha, mainSha, analyses, alerts });
		const waiting = errors.length && errors.every((e) => e.startsWith('No successful CodeQL'));
		if (!errors.length) return console.log(`Security gate passed for ${sha}`);
		if (!waiting || attempt >= 60) throw new Error(errors.join('\n'));
		console.log(`Waiting for CodeQL on ${sha.slice(0, 7)} …`);
		await new Promise((r) => setTimeout(r, 30_000));
	}
}

async function main([command, ...args]) {
	if (command === 'policy') {
		const dir = join(ROOT, '.github/workflows');
		const workflows = Object.fromEntries(
			readdirSync(dir)
				.filter((f) => /\.ya?ml$/.test(f))
				.map((f) => [f, readFileSync(join(dir, f), 'utf8')])
		);
		const errors = policyErrors(workflows, { 'apps/web/Dockerfile': read('apps/web/Dockerfile') });
		if (errors.length) throw new Error(errors.join('\n'));
		return console.log('Release policy: ok');
	}
	if (command === 'version') {
		const { component, version } = parseTag(args[0], args[1]);
		const errors = versionErrors(component, version);
		try {
			releaseNotes(read('CHANGELOG.md'), component, version);
		} catch (e) {
			errors.push(e.message);
		}
		if (errors.length) throw new Error(errors.join('\n'));
		return console.log(`${component} ${version}: version files and changelog agree`);
	}
	if (command === 'notes') return console.log(releaseNotes(read('CHANGELOG.md'), args[0], args[1]));
	if (command === 'gate') return gate();
	throw new Error('Usage: release.mjs policy | version <tag> | notes <component> <version> | gate');
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
	main(process.argv.slice(2)).catch((e) => {
		console.error(e.message);
		process.exitCode = 1;
	});
}
