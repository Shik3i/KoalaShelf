import { test } from 'node:test';
import assert from 'node:assert/strict';
import { gateErrors, parseTag, policyErrors, releaseNotes, versionErrors } from './release.mjs';

const SHA = 'a'.repeat(40);

test('policy: actions pinned to commits, base images to digests', () => {
	assert.deepEqual(
		policyErrors(
			{ 'ci.yml': `steps:\n  - uses: actions/checkout@${SHA} # v7\n  - uses: ./.github/workflows/x.yml` },
			{ Dockerfile: `FROM node:26@sha256:${'b'.repeat(64)} AS build\nFROM caddy@sha256:${'c'.repeat(64)}` }
		),
		[]
	);
	const errors = policyErrors(
		{ 'ci.yml': 'uses: actions/checkout@v7' },
		{ Dockerfile: 'FROM --platform=$BUILDPLATFORM node:26-alpine AS build' }
	);
	assert.equal(errors.length, 2);
});

test('policy: only release workflows publish; CodeQL stays read-only; no untrusted text', () => {
	assert.equal(policyErrors({ 'ci.yml': 'run: gh release create v1' }, {}).length, 1);
	assert.equal(policyErrors({ 'release-web.yml': 'run: gh release create v1' }, {}).length, 0);
	assert.equal(policyErrors({ 'codeql.yml': 'permissions:\n  contents: write' }, {}).length, 1);
	assert.equal(
		policyErrors({ 'ci.yml': 'run: echo "${{ github.event.head_commit.message }}"' }, {}).length,
		1
	);
});

test('tags name the component and a semver version', () => {
	assert.deepEqual(parseTag('web-v0.1.0'), { component: 'web', version: '0.1.0' });
	assert.deepEqual(parseTag('desktop-v1.2.3'), { component: 'desktop', version: '1.2.3' });
	assert.throws(() => parseTag('v0.1.0'));
	assert.throws(() => parseTag('web-v0.1'));
	assert.throws(() => parseTag('android-v1.0.0'));
});

test('every version file must carry the tagged version', () => {
	const files = {
		'apps/desktop/package.json': '{"version":"0.2.0"}',
		'apps/desktop/src-tauri/tauri.conf.json': '{"version":"0.2.0"}',
		'apps/desktop/src-tauri/Cargo.toml': '[package]\nname = "x"\nversion = "0.1.0"\n'
	};
	const errors = versionErrors('desktop', '0.2.0', (f) => files[f]);
	assert.equal(errors.length, 1);
	assert.match(errors[0], /Cargo\.toml has version 0\.1\.0/);
});

test('release notes come from the matching changelog section', () => {
	const log = '# Changelog\n\n## Web 0.2.0 — 2026-11-01\n\n- B\n\n## Desktop 0.1.0\n\n- D\n\n## Web 0.1.0\n\n- A\n';
	assert.equal(releaseNotes(log, 'web', '0.2.0'), '- B');
	assert.equal(releaseNotes(log, 'web', '0.1.0'), '- A');
	assert.throws(() => releaseNotes(log, 'web', '0.3.0'));
});

test('security gate: main head, CodeQL done for each language, no open alerts', () => {
	const analyses = ['javascript-typescript', 'actions'].map((l) => ({
		commit_sha: SHA,
		category: `/language:${l}`,
		error: ''
	}));
	assert.deepEqual(gateErrors({ sha: SHA, mainSha: SHA, analyses, alerts: [] }), []);
	assert.equal(gateErrors({ sha: SHA, mainSha: 'b'.repeat(40), analyses, alerts: [] }).length, 1);
	assert.equal(gateErrors({ sha: SHA, mainSha: SHA, analyses: analyses.slice(1), alerts: [] }).length, 1);
	assert.equal(gateErrors({ sha: SHA, mainSha: SHA, analyses, alerts: [{}] }).length, 1);
});
