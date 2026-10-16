# Web app audit

Last full pass: 2026-10-16 (after the real-library fixes). Besides the sample books, every page was clicked through in a signed-in browser against a real Audiobookshelf with 574 audiobooks and an e-book library (no errors), and CI runs everything on every push.

## How it is verified

| Check | Scope | Result |
|---|---|---|
| `pnpm check` | svelte-check over app, tests, configs | 0 errors, 0 warnings |
| `pnpm lint` | Prettier + ESLint | clean |
| `pnpm audit` | all workspace dependencies | no known vulnerabilities |
| GitHub | Dependabot alerts and security updates, secret scanning with push protection, CodeQL; CI runs all of the checks below on every push | enabled |
| `pnpm test` | unit (web): formatting, placeholder contrast, turntable ramp, smart sleep forecast and window, library queries, search highlighting, device names, CSV export, streak, own accent color readability, duplicate-free lists | 43/43 |
| `pnpm --filter @koalashelf/abs-client test` | unit (client): filter encoding, version check, token expiry | 4/4 |
| Design token build | every theme × every accent preset, WCAG contrast pairs | passes (fails the build otherwise) |
| `pnpm test:e2e` | 139 Playwright tests in 19 files, Edge, against a local Audiobookshelf 2.37.1 with generated sample books | 131 passed, 8 skipped (on-demand screenshot spec, production-only checks) |
| `E2E_BASE_URL=… pnpm test:e2e` | production Docker image (Caddy, strict CSP, service worker) | 105 passed, 34 skipped: tests that read player internals need the dev server and skip before any playback starts |
| axe-core | every page, the "More" sheet, mini player, full player and all its panels; light + dark; laptop and phone widths; WCAG 2.2 AA **and** axe best practices | no violations |
| Responsive matrix (`e2e/responsive.spec.ts`) | 11 viewports from 320 × 640 to 2560 × 1440 incl. landscape phone and tablets, every page plus the full player; 125 % text on a small phone | no sideways scrolling, player controls on screen everywhere |
| Lighthouse 13.5 | production image, signed in: home, library, book, statistics, settings; mobile and desktop | performance 100, accessibility 100, best practices 100 (LCP ≤ 1.2 s mobile / 0.3 s desktop, TBT ≤ 60 ms, CLS ≤ 0.04); SEO 63–66 only because of the deliberate `noindex` |
| Screenshot reviews | all pages and player states (desktop and mobile, light and dark), the responsive matrix, Windows high-contrast mode | reviewed zoomed in; findings fixed |

Both full runs (dev and production image) were repeated on 2026-10-16 after the dependency updates, and Lighthouse on the final image. A sporadic accessibility finding (low contrast on one library tile) turned out to be the scan running while tiles were still fading in after their data arrived; scans now wait for the network to settle before waiting for animations.

## Workflows covered by end-to-end tests

- **Sign-in**: preset server, wrong password, unreachable server, CORS hint, a changed address checked again before signing in, reload, sign-out, deep link return, expired session, silent token refresh, guest without download permission
- **Library**: sort and reverse, every filter kind, several values of one kind (OR) combined with other kinds (AND), author + series together, length, text filter over titles and the books of matching authors and narrators, random order and reshuffle, saved views, series order and chip, list view, group series, URL state after reload, remembered view, switching libraries, paging through 70 titles (infinite scroll)
- **Browsing**: author page (books by series, link to the library), narrators overview, series page (progress, next book, "continue series" with undo), playlists (create, add from a book and from the dialog, reorder, remove with undo, play all, delete), all bookmarks (play, delete with undo), year in review and library statistics
- **Search**: genres and tags, recent searches, every library at once with library labels; quick search (Ctrl K and `/`) by keyboard to books and pages
- **Managing**: multi-select in shelf and list view (mark finished / not finished, queue, several books into a new playlist, select all, Escape), reset progress with undo, mark earlier volumes finished, files tab, linked details, series "mark finished" with undo, collections (create from a book, add, arrange, remove with undo, rename with description, save as playlist, delete)
- **Player extras**: fine speed (steps, slider, quick choices, keys), volume, mute, boost, clearer voices, mono through the whole chain, skip silence (rate while silent, saved time) and the silence detector itself (worklet reports pause and sound), jump back (toast and `U`), go to a typed time, "ends at", sleep timer +5/+15 min, chapter timer to countdown, last-chance hint with +10 min, night start
- **Accounts**: a second account added from the sidebar (the first keeps the cookie, the added one its own token), separate queues, switching from the sidebar and the quick search, signing out from the settings, the remaining account offered with one tap, an ended session asking for its password with server and name prefilled
- **Appearance**: own accent color (darkened for readability, same hue, applied before the first paint after a reload), easy-to-read and system fonts; accessibility scans of the switcher, the settings and the library in an own color in both themes
- **Your data**: daily goal (statistics and home ring, off again), year picture (valid PNG), bookmarks as Markdown and CSV (quoting), history as CSV, settings export and import (accent restored, foreign files refused)
- **Book**: details, description without HTML, bookmarks from the server, mark finished/unfinished (also with a slow first load and with a duplicate progress entry from the server), no chapters, not found, save files as ZIP (valid archive with every file), history tab
- **Player**: play/pause/sync to server, skip, chapter navigation, speed per book and fixed default, bookmarks add/edit/delete/pins with undo and links, sleep timer at chapter end and without rewind, smart sleep timer (forecast, chapter stop, latest-time fallback), multi-file boundary, end of book → finished → listen again, next from the queue, next in series, reload restores position, keyboard, quick switching between books, chapter timeline (range, clock, scope)
- **Listening extras**: queue (add, reorder, remove with undo), volume leveling (audible output on/off and across files, also on a server of another origin with CORS), listening history (by day, jump points, device names)
- **Settings**: accent, text size, motion and cover size apply at once and before first paint; accent contrast in both themes; home shelves hidden and reordered; start page; shortcuts off; reset with confirmation; signed-in devices (this device first, remote sign-out); password change errors
- **Pages**: home continue card and hide/undo, series, authors, collections, statistics, search, history, downloads
- **Live & offline**: progress from another device without reload; download → offline playback → sync on reconnect → remove; offline app shell and playback after a full reload (production build)
- **Mobile**: tab bar with "More" sheet (all pages, library switch), mini player above it, full player on one screen, no horizontal overflow on any page (measured against the device width)

## Bugs found and fixed by the tests and reviews

Sign-in hid the server field · blank page on direct book URLs · horizontal overflow from long titles · tonearm beside the record · misaligned timeline · raw HTML in descriptions · placeholder instead of cover in the player · bookmarks from the server never loaded · series page reload loop · dev proxy swallowing `/authors` · insufficient contrast in two placeholder palettes · "no library" shown offline · 404s for missing covers in statistics · races when switching books quickly · finished books ending immediately · sleep timer counting while paused · toasts hidden behind the open player · a11y scans racing fly-in animations · spinning record crashing the page under load (a frame timestamp before the ramp start produced a negative playback rate) · series, authors, collections and statistics unreachable on phones · book tabs widening the page on 375 px phones · a slower first progress load overwriting a newer change · a duplicate, older progress entry from the server winning over the current one · the device shown as a user agent dump in Audiobookshelf · signing in to the previously checked server after quickly changing the address · the default accent at 4.29:1 on the panel (active tab label on phones) · switch "off" state below 3:1 · `text-size-adjust: none` and `px` font sizes blocking text enlargement · sidebar cut off on landscape phones · unreadably wide lines on large screens · segmented labels overlapping on narrow screens · a stored fixed speed discarded on reload · progress, switches and swatches invisible in high-contrast mode · search page without a level-one heading · logo link name not matching its visible text · no cache lifetime for small static files and no framing protection · sign-in needing `ABS_PUBLIC_URL` to use the bundled proxy · author and series filters together dropping the author · history grouped by the server's day instead of the listener's (sessions after midnight showed as "yesterday") · highlighted search matches read as split words by screen readers · text fields with only a 1.3:1 border in dark mode · an endless signed-in devices list · the current chapter never highlighted in the player's chapter list (a more specific rule won) · the daily goal choices making the statistics page scroll sideways on phones · bookmark exports naming books by id when clicked before the titles had loaded (production build only) · two buttons named "More" on a phone's book page · the statistics page jumping (CLS 0.6) because the library numbers appeared before the listening statistics pushed them down · **found with a real library (574 books):** the home page crashing when a book in two series appeared twice on a shelf (duplicate keys; every server list is now deduplicated) · the sidebar pushed wider than itself by the account row · SvelteKit's bare "500" page instead of an error page inside the app.

## Server behavior worth knowing

Found while testing against Audiobookshelf 2.37.1 (details in [api/audiobookshelf.md](api/audiobookshelf.md)): the server can list two progress entries for one book after a reset during an open session, and closing a session whose progress was deleted crashes the server process (it restarts). The client copes with the first; the test suite avoids the second.

## Not included (by decision)

| Item | Reason |
|---|---|
| Single sign-on (OpenID) | Needs admin setup per server; username/password works everywhere. SSO-only servers get a clear message. |
| Share clip | Not supported by the Audiobookshelf server. |
| Deleting single history entries | Not supported by the server. |
| Podcasts and e-books | Out of scope for now (decided 2026-10-15); audiobooks first. |
| Shake to extend the sleep timer, car mode | Need sensors and background work with the screen off: native apps. |
| Editing metadata, RSS feeds, shares | Need editor or admin rights on the server; KoalaShelf is a listening client (collections are editable for accounts with that right). |
| Driving mode, Android Auto | Native apps (paused until the web app is signed off). |
