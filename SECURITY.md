# Security

## Reporting a vulnerability

Please report security problems privately through GitHub: **Security → Report a vulnerability** on this repository. Do not open a public issue for them.

Helpful details: what is affected (web app, Docker image, desktop app), how to reproduce it, and what an attacker could do.

## Scope

KoalaShelf is a client for an Audiobookshelf server. Problems in Audiobookshelf itself belong to [its project](https://github.com/advplyr/audiobookshelf/security).

In scope, for example: leaking tokens or listening data to third parties, cross-site scripting, the Docker image's proxy or headers, and anything that lets one listener act as another.

## What the app does to stay safe

- No third-party requests, trackers or analytics; fonts are self-hosted.
- A strict Content Security Policy (scripts only from the app itself), no framing, no referrer.
- Same-origin hosting by default: the refresh token stays in an httpOnly cookie. Further saved accounts (and servers on other addresses) keep theirs in this browser's storage, covered by the CSP; signing out revokes it on the server and deletes the account's data from the device.
- Server descriptions are rendered as text, never as HTML.
- Exports and backups are built on the device; settings backups never contain sign-in data.
- Dependencies: `pnpm audit` and CodeQL on every push, Dependabot security updates, secret scanning with push protection.
