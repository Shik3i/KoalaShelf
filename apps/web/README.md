# apps/web

KoalaShelf web app / PWA. SvelteKit 3 as a client-side SPA (`adapter-static`), Svelte 5 runes, Vite 8.

```bash
pnpm dev      # dev server, proxies the Audiobookshelf API (see dev/README.md)
pnpm check    # svelte-check
pnpm test     # unit tests
pnpm build    # static build in build/
```

- `src/lib/i18n/`: UI strings (English base, German). Every string goes through `i18n.m`.
- `src/lib/session.svelte.ts`: sign-in, token rotation, library selection.
- `src/lib/components/`: Cover (with generated placeholder art), Vinyl, Turntable, BookTile, AppNav.
