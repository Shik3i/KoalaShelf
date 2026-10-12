# packages/design-tokens

The single source for KoalaShelf colors, type, spacing, radii and motion ("KoalaShelf Vinyl").

Style Dictionary turns `tokens/*.json` (DTCG) into:

- `dist/tokens.css`: CSS custom properties with `.th-light` / `.th-dark` and a `prefers-color-scheme` fallback
- `dist/tokens.ts`: TypeScript constants
- `apps/android/core/designsystem/.../KoalaTokens.kt`: Compose colors and dimensions (committed)

`pnpm build` (also runs on install) fails when a contrast pair drops below WCAG thresholds.
