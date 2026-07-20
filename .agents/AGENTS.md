## 1. Project Overview

### Purpose
Local-only Android media tracker for tracking consumption progress across Novels, Anime, Manga, and Games. No accounts, no cloud, no social. Privacy-first.

### Core Features
- Add media items via URL scraping (metadata auto-fill: title, cover image, alternative titles) or manual entry
- Track progress (current chapter/episode/volume) with optional total, auto-complete status when progress = total
- Unified Add/Edit/Detail screen (AniList-style single screen for both adding and editing)
- Status workflow: Reading/Watching/Playing → Completed → On Hold → Dropped → Plan to (category-adaptive)
- User rating: 1.0–5.0 stars (half-star increments)
- Alternative titles: flexible list stored as JSON, swap-to-main UI
- Cover images downloaded to local storage (not URL-based), named by item ID
- View all items in a READ-ONLY filterable list (cards show info only — no progress buttons)
- Filter + Sort buttons in toolbar (Notion-inspired: ModalBottomSheet for each)
- Multiple sort options: Last Active, Title, Progress %, Date Added
- Color strategy: status gets color badges, category gets neutral outline + icon
- Filter-aware empty state
- Tap card → unified detail screen for editing
- Delete with confirmation dialog, back-navigation with discard warning
- Progress clamping: red error text + disabled Save when progress > total
- Backup/restore data to JSON files via Storage Access Framework (SAF)
- Merge or overwrite on import (overwrite requires press-and-hold 5–10s)
- Auto-backup via WorkManager with SAF folder picker
- Dark theme (default) with WTR-LAB-inspired color palette, AMOLED mode, dynamic color (Monet)
- Settings screen (export, import, theme, auto-backup, about)

### Knowledge Graph (graphify)

This project uses [graphify](https://github.com/safishamsi/graphify) to build a persistent knowledge graph of the entire codebase + .agents docs.

- **Run after any code/doc change:** `/graphify` — rebuilds graph, regenerates HTML + report
- **Incremental update:** `/graphify --update` — re-extracts only changed files (much cheaper)
- **Ask questions via graph:** `/graphify query "how does X connect to Y"`
- **Explore paths:** `/graphify path "di Modules" "LazyDexDatabase"`
- **Explain a node:** `/graphify explain "MediaItem"`

Outputs land in `graphify-out/`:
- `graph.html` — interactive visualization (open in browser, no server needed)
- `GRAPH_REPORT.md` — audit report with god nodes, communities, surprising connections
- `graph.json` — raw graph data for programmatic access

The graph is your map of the codebase. Run `/graphify` before starting any task to understand what you're touching, and run `/graphify --update` after making changes to keep the map fresh. The graph is **persistent** — relationships survive across sessions, so you can ask questions weeks later without re-reading files.

### Non-Goals
- No user accounts
- No cloud sync
- No social features
- No login/authentication
- No multi-device support
- No extension/plugin system
- No push notifications
- No widget

---

## 2. Tech Stack Decisions

| Layer | Decision | Justification | Alternative Considered |
|-------|----------|---------------|----------------------|
| Language | **Kotlin** | Industry standard for Android | — |
| UI Framework | **Jetpack Compose + Material3** | Modern declarative UI, dynamic color, less boilerplate | Classic XML (rejected: more code, no dynamic theming) |
| DI | **Koin** | Lightweight, no annotation processing, fast builds | Hilt (rejected: slower builds, overkill for this size), Injekt (rejected: poorly documented) |
| Database | **Room** | Official Android SQLite wrapper, Flow support, migrations | SQLDelight (rejected: multiplatform overkill), SharedPreferences (rejected: data loss risk, no queries) |
| Networking | **OkHttp 4.x** | Industry standard, proven | Ktor (rejected: more complex for simple GET) |
| HTML Parsing | **Jsoup** | Mature, Kotlin-compatible | — |
| Image Loading | **Coil v3** | Kotlin-native, Compose support, OkHttp integration | Glide (rejected: Java-first, less Compose-native) |
| Serialization | **kotlinx.serialization** | Compile-time, no reflection, Kotlin-native | Gson (rejected: reflection-based, slower, no compile-time safety) |
| Navigation | **Navigation Compose** | Official Google solution, type-safe | Voyager (rejected: third-party, less support) |
| Architecture | **MVVM + Repository pattern** | Clean separation, testable | — |
| State Mgmt | **Kotlin Flow → StateFlow** | Reactive, lifecycle-aware | LiveData (rejected: less flexible, not Flow-compatible) |
| **Unit Testing** | **JUnit 5 (Jupiter) + MockK + Turbine** | All ViewModel, Repository, Scraper tests use Jupiter | — |
| **Compose UI Testing** | **JUnit 4 (via `ui-test-junit4`) + ComposeTestRule** | `createComposeRule()` only exists for JUnit4. Instrumented UI tests run under JUnit4 runtime via the `de.mannodermaus.android-junit5` Gradle plugin's backwards-compatible runner bridge. Pure unit tests stay on Jupiter. | — |
| Build | **Gradle version catalog** (`libs.versions.toml`) | Centralized dependency management | — |
| Min SDK | **26** (Android 8.0) | Matches old codebase, covers 95%+ devices | — |
| Target SDK | **34** | Latest stable | — |
| Package | **app.lazydex** | Short, clean, matches Mihon's `app.mihon` pattern | — |

