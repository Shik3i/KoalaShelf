# apps/android

Native KoalaShelf Android app: Kotlin, Jetpack Compose, Media3. A standalone Gradle project, independent of the pnpm workspace.

**Status:** planned. A first skeleton is parked on the branch `wip/android`; work continues once the web app is signed off. The web app ([apps/web](../web)) defines the features and behavior to carry over, [docs/tech-stack.md](../../docs/tech-stack.md) the library versions.

Planned modules (phase 6): `app`, `core/designsystem`, `core/network`, `core/data`, `core/player`, `feature/*`.

`core/designsystem` already contains the Kotlin tokens generated from `packages/design-tokens` (committed).
