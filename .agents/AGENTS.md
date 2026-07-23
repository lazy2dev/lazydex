# LazyDex

Local-only Android media tracker for Novels, Anime, Manga, Games, Movies, TV. No accounts, no cloud, no social.

## Tech Stack

| Layer | Choice |
|-------|--------|
| Language | Kotlin |
| UI | Jetpack Compose + Material3 |
| DI | Koin (no annotation processing) |
| DB | Room SQLite (v2, Flow, WAL) |
| Networking | OkHttp 4.x + Jsoup |
| Images | Coil v3 (local files, not URLs) |
| Serialization | kotlinx.serialization |
| Navigation | Navigation Compose (type-safe) |
| Architecture | MVVM + Repository, Kotlin Flow/StateFlow |
| Unit Tests | JUnit 5 Jupiter + MockK + Turbine |
| UI Tests | JUnit 4 + ComposeTestRule (via android-junit5 bridge) |

## Commands

- `./gradlew test` — unit tests
- `./gradlew assembleDebug` — debug APK
- `./gradlew lint` — Android lint

## Knowledge Graph (graphify)

- `/graphify` — rebuild full graph
- `/graphify --update` — incremental re-extract
- `/graphify query "X to Y"` — ask relationship questions
- Output: `graphify-out/graph.html`, `graph.json`, `GRAPH_REPORT.md`

## Context Routing

- **Design specs**: `.agents/design/` — per-screen specs, components, theme, navigation
- **Active plans**: `.agents/plans/` — current implementation plans
- **Reference**: `.agents/reference/` — external UI references
- **Archived**: `.agents/archived/` — completed/superseded docs
- **Knowledge graph**: `graphify-out/` — run `/graphify` to rebuild

## Non-Goals

No accounts, no cloud sync, no social, no login/auth, no multi-device, no extension system, no push notifications, no widgets.
