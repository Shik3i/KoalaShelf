# Releasing

KoalaShelf can be released using a unified version tag (`vX.Y.Z`, building web, desktop, and mobile), or component-specific tags (`web-vX.Y.Z`, `desktop-vX.Y.Z`, or `android-vX.Y.Z`). Each release takes its notes from its section in CHANGELOG.md; see below.

| Part | Tag | Workflow | Result |
|---|---|---|---|
| Unified | `vX.Y.Z` | All release workflows | Builds all release artifacts with matching versions |
| Web (Docker image) | `web-vX.Y.Z` or `vX.Y.Z` | [`release-web.yml`](../.github/workflows/release-web.yml) | `ghcr.io/shik3i/koalashelf` for linux/amd64 and linux/arm64, tagged `X.Y.Z`, `X.Y`, `X` (from 1.0) and `latest`; signed build provenance and SBOM attestations; a GitHub Release with `docker-compose.yml`, `.env.example`, `Caddyfile.example` and the SBOM |
| Desktop | `desktop-vX.Y.Z` or `vX.Y.Z` | [`release-desktop.yml`](../.github/workflows/release-desktop.yml) | Windows (.msi, setup .exe), macOS (universal .dmg) and Linux (.deb, .rpm, .AppImage) with signed build provenance, as a GitHub pre-release (not code-signed yet) |
| Android | `android-vX.Y.Z` or `vX.Y.Z` | [`release-android.yml`](../.github/workflows/release-android.yml) | Production APK (`KoalaShelf-X.Y.Z.apk`) with R8 code shrinking and ProGuard optimization, published as a GitHub release with release notes from `CHANGELOG.md` |

## Steps

1. Bump the version:
   - Web: `apps/web/package.json`
   - Desktop: `apps/desktop/package.json`, `apps/desktop/src-tauri/tauri.conf.json`, `apps/desktop/src-tauri/Cargo.toml` (and `Cargo.lock` via `cargo update -p koalashelf`)
   - Android: `apps/android/app/build.gradle.kts` (`versionName` and `versionCode`)
2. Add a section `## Web X.Y.Z`, `## Desktop X.Y.Z`, or `## Android X.Y.Z` to [CHANGELOG.md](../CHANGELOG.md). It becomes the release notes.
3. Check locally: `node scripts/release.mjs version web-vX.Y.Z` (or `node scripts/release.mjs version android-vX.Y.Z android`, `node scripts/release.mjs version desktop-vX.Y.Z desktop`)
4. Push to `main` and wait for CI and CodeQL to finish green.
5. Tag and push the tag:

```bash
git tag web-v0.1.0 # or desktop-v0.1.0 or android-v0.1.1
```

```bash
git push origin web-v0.1.0 # or desktop-v0.1.0 or android-v0.1.1
```

## What a release checks before publishing

1. The full CI: dependency audit, types, lint, unit tests, build, Docker image, the end-to-end suite.
2. Release policy (`scripts/release.mjs policy`): every action pinned to a commit hash, Docker base images pinned to digests, only release workflows may publish, CodeQL stays read-only, no untrusted event text in workflows.
3. The tag's version equals every version file, and CHANGELOG.md has a non-empty section for it.
4. Security gate: the tagged commit is the current `main`, CodeQL (JavaScript/TypeScript and Actions) analysed exactly that commit successfully, and no code scanning alert is open. It waits up to 30 minutes for a CodeQL run still in progress.

"Run workflow" in the Actions tab runs a release workflow as a dry run: everything is built (both image architectures, all three desktop platforms), nothing is published.

## Verifying a download

```bash
gh attestation verify oci://ghcr.io/shik3i/koalashelf:0.1.0 -R Shik3i/KoalaShelf
```

```bash
gh attestation verify KoalaShelf_0.1.0_x64-setup.exe -R Shik3i/KoalaShelf
```

## After the first web release

GitHub creates the `koalashelf` package on the first push. Check under the repository's **Packages** that it is public and linked to this repository (it is, if the image's `org.opencontainers.image.source` label is honored); otherwise set the visibility to public once in the package settings.
