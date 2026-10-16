# Releasing

KoalaShelf's parts are released separately, each by its own tag and workflow:

| Part | Tag | Workflow | Result |
|---|---|---|---|
| Web (Docker image) | `web-vX.Y.Z` | [`release-web.yml`](../.github/workflows/release-web.yml) | `ghcr.io/shik3i/koalashelf` for linux/amd64 and linux/arm64, tagged `X.Y.Z`, `X.Y`, `X` (from 1.0) and `latest`; signed build provenance and SBOM attestations; a GitHub Release with `compose.yaml` and the SBOM |
| Desktop | `desktop-vX.Y.Z` | [`release-desktop.yml`](../.github/workflows/release-desktop.yml) | Windows (.msi, setup .exe), macOS (universal .dmg) and Linux (.deb, .rpm, .AppImage) with signed build provenance, as a GitHub pre-release (not code-signed yet) |
| Android | — | — | planned (`android-vX.Y.Z`) |

A tag of one part never starts another part's workflow.

## Steps

1. Bump the version:
   - Web: `apps/web/package.json`
   - Desktop: `apps/desktop/package.json`, `apps/desktop/src-tauri/tauri.conf.json`, `apps/desktop/src-tauri/Cargo.toml` (and `Cargo.lock` via `cargo update -p koalashelf`)
2. Add a section `## Web X.Y.Z` (or `## Desktop X.Y.Z`) to [CHANGELOG.md](../CHANGELOG.md). It becomes the release notes.
3. Check locally: `node scripts/release.mjs version web-vX.Y.Z`
4. Push to `main` and wait for CI and CodeQL to finish green.
5. Tag and push the tag:

```bash
git tag web-v0.1.0
```

```bash
git push origin web-v0.1.0
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
