# packages/design-tokens

The single source for KoalaShelf colors, accents, type, spacing, radii and motion ("KoalaShelf Vinyl").

Style Dictionary turns `tokens/*.json` (DTCG) into:

- `dist/tokens.css`: CSS custom properties with `.th-light` / `.th-dark`, a `prefers-color-scheme` fallback and one block per accent preset (`:root[data-accent='…']`); font sizes in `rem`, so they follow the browser and system text size
- `dist/tokens.ts`: TypeScript constants, including `accents` (used for the swatches in the settings)
- `apps/android/core/designsystem/.../KoalaTokens.kt`: Compose colors and dimensions (committed)

| File | Contents |
|---|---|
| `tokens/color.json` | Light and dark theme colors; the default accent refers to the `vinyl` preset |
| `tokens/accent.json` | Accent presets (vinyl, ocean, teal, forest, indigo, plum), each for light and dark |
| `tokens/base.json` | Fonts, type scale, spacing, radii, layout, motion |

`pnpm build` (also runs on install) checks every theme with every accent preset and fails when a pair drops below WCAG: 4.5:1 for text, including the accent wherever it is used as text (on background, card and panel).
