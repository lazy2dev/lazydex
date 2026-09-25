# LazyDex

Local-only Android media tracker for Novels, Anime, Manga, Games, Movies, TV. No accounts, no cloud, no social.

## Tech Stack

| Layer | Choice |
|-------|--------|
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| DI | Koin (no annotation processing) |
| DB | Room SQLite (v3, Flow, WAL) |
| Networking | OkHttp 4.x + Jsoup |
| Images | Coil v3 (local files, not URLs) |
| Serialization | kotlinx.serialization |
| Navigation | Navigation Compose (type-safe) |
| Architecture | MVVM + Repository, Kotlin Flow/StateFlow |
| Unit Tests | JUnit 5 Jupiter + MockK + Turbine |
| UI Tests | JUnit 4 + ComposeTestRule (via android-junit5 bridge) |

## Development Workflow

- **Branching**: `dev` for active feature development; `main` for release tags.
- **Verification**: Run `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` before pushing.
- **Version Bumps**: Set `versionCode` (+1) and `versionName` in `app/build.gradle.kts`.
- **Database Migrations**: Add explicit `Migration(N, N+1)` in `LazyDexDatabase.kt`. Keep Room schema export enabled.

## Key Commands

- `./gradlew testDebugUnitTest` — run all unit tests
- `./gradlew assembleDebug` — build debug APK
- `./gradlew lint` — run Android lint

## Context Routing

- **Reference Documentation**: `.agents/reference/`
  - `anilist-api.md` — AniList GraphQL queries, mutations, OAuth, rate limiting
  - `mihon-sync.md` — Tracker sync patterns and score conversion
  - `mihon-ui.md` — UI layout and component visual references

## Non-Goals

No accounts, no cloud sync, no social, no login/auth, no multi-device, no extension system, no push notifications, no widgets.
