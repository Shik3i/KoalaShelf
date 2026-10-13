# Web app audit

Last full pass: 2026-10-13. The web app is verified by automated tests and a screenshot review; everything below is reproducible from the repository.

## How it is verified

| Check | Scope | Result |
|---|---|---|
| `pnpm check` | svelte-check over app, tests, configs | 0 errors, 0 warnings |
| `pnpm lint` | Prettier + ESLint | clean |
| `pnpm test` | unit: formatting, filters, version check, placeholder contrast | 5/5 |
| `pnpm test:e2e` | 48 Playwright tests in Edge against a local Audiobookshelf 2.37.1 with generated sample books | 48/48, stable over repeated runs |
| `E2E_BASE_URL=… pnpm test:e2e` | production Docker image (Caddy, strict CSP, service worker) | sign-in, library, pages, a11y and PWA/offline specs pass |
| axe-core | every page, mini player, full player and its panels, light + dark, WCAG 2.2 AA | no violations |
| Screenshot review (`e2e/screens.spec.ts`) | all pages and player states, desktop + mobile, light + dark | reviewed |

## Workflows covered by end-to-end tests

- **Sign-in**: preset server, wrong password, unreachable server, CORS hint, reload, sign-out, deep link return, expired session, silent token refresh, guest without download permission
- **Library**: sort and reverse, every filter, combined filters, series order and chip, list view, group series, author links, URL state after reload, switching libraries, paging through 70 titles (infinite scroll)
- **Book**: details, description without HTML, bookmarks from the server, mark finished/unfinished, no chapters, not found
- **Player**: play/pause/sync to server, skip, chapter navigation, speed persistence, bookmarks add/edit/delete/pins, sleep timer at chapter end, multi-file boundary, end of book → finished → listen again, reload restores position, keyboard, quick switching between books
- **Pages**: home continue card + hide, series, authors, collections, statistics, search, settings persistence (theme, language, skip steps)
- **Live & offline**: progress from another device without reload; download → offline playback → sync on reconnect → remove; offline app shell and playback after a full reload (production build)
- **Mobile**: tab bar, mini player above it, full player fits one screen, no horizontal overflow on any page

## Bugs found and fixed by the tests and reviews

Sign-in hid the server field · blank page on direct book URLs · horizontal overflow from long titles · tonearm beside the record · misaligned timeline · raw HTML in descriptions · placeholder instead of cover in the player · bookmarks from the server never loaded · series page reload loop · dev proxy swallowing `/authors` · insufficient contrast in two placeholder palettes · "no library" shown offline · 404s for missing covers in statistics · races when switching books quickly · finished books ending immediately · sleep timer counting while paused.

## Not included (by decision)

| Item | Reason |
|---|---|
| Single sign-on (OpenID) | Needs admin setup per server; username/password works everywhere. SSO-only servers get a clear message. |
| Share clip | Not supported by the Audiobookshelf server. |
| Driving mode, Android Auto | Native apps (paused until the web app is approved). |
