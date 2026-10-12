# packages/design-tokens

Die eine Quelle für Farben, Schrift, Abstände, Radien und Bewegung von KoalaShelf („KoalaShelf Vinyl“).

Aus den Token-Dateien (DTCG-Format) erzeugt Style Dictionary:

- `dist/tokens.css`: CSS-Variablen mit den Theme-Klassen `.th-light` / `.th-dark`
- `dist/tokens.ts`: TypeScript-Konstanten
- `apps/android/core/designsystem/.../KoalaTokens.kt`: Kotlin für Compose (eingecheckt)

Der Build bricht ab, wenn ein Kontrast unter 4,5:1 fällt. Wird in Phase 1 gebaut.
