# LazyDex 0.0.1-beta — Full-Feature Usable MVP

> **Tagline**: Local-only Android media tracker. Works. Every field. Every screen. No excuses.
> **Philosophy**: This release ships everything a user needs to actually track their media consumption day-to-day. Nothing is stripped for speed — every field from the domain model, every screen from the flow, every interaction from the spec is present and functional. Polish, automation, and extras ship in 0.0.2-beta.

---

## 1. Data Layer

### Database
- [x] Room database (`LazyDexDatabase`) with `exportSchema = true`
- [x] Schema JSON tracked at `room.schemaLocation` for future AutoMigration
- [x] WAL journal mode enabled (`WRITE_AHEAD_LOGGING`)
- [x] `fallbackToDestructiveMigration()` with Log.w() on wipe
- [x] Database name: `"lazydex_db"`

### Entity
- [x] `MediaItemEntity` with all fields:
  - `id` (String, PrimaryKey)
  - `category` (String — uppercase enum name, e.g. `"NOVEL"`)
  - `title` (String)
  - `alternativeTitles` (String — JSON array)
  - `sourceUrl` (String?, nullable with UNIQUE index)
  - `coverImagePath` (String — local file path, empty = no cover)
  - `coverImageUrl` (String?, nullable original cover URL)
  - `currentProgress` (Int)
  - `totalItems` (Int?)
  - `userStatus` (String — uppercase enum name)
  - `rating` (Double?)
  - `notes` (String)
  - `lastUpdated` (Long)
  - `dateAdded` (Long)
- [x] `@Entity(indices = [Index(value = ["sourceUrl"], unique = true)])`
- [x] TypeConverter for `List<String>` ↔ JSON string (alternativeTitles)

### DAO (`MediaItemDao`)
- [x] `observeAll()` — Flow of all items, ordered by `dateAdded DESC`
- [x] `observeByCategory(category)` — Flow filtered by category
- [x] `observeFiltered(category, filterType, exactStatus)` — Flow filtered by category and StatusFilter mapping
- [x] `observeAllByDateAdded()` — explicit sort query
- [x] `observeAllByLastUpdated()` — sort by last updated
- [x] `observeAllByTitle()` — sort alphabetically
- [x] `observeAllByProgress()` — sort by progress %
- [x] `observeCount()` — Flow of item count
- [x] `observeById(id)` — Flow of single item (nullable)
- [x] `getById(id)` — one-shot suspend read
- [x] `getAll()` — one-shot suspend read all
- [x] `existsByUrl(url)` — SQLite-bound check
- [x] `upsert(item)` — `@Upsert` (Room 2.5+)
- [x] `upsertAll(items)` — batch upsert
- [x] `atomicIncrement(id, now)` — single SQL UPDATE with `MIN(currentProgress + 1, COALESCE(totalItems, …))`
- [x] `atomicDecrement(id, now)` — single SQL UPDATE with `MAX(currentProgress - 1, 0)`
- [x] `updateStatus(id, status, now)` — atomic status update
- [x] `deleteById(id)` — delete single item
- [x] `deleteAll()` — clear table
- [x] `replaceAll(items)` — `@Transaction` default method wrapping `deleteAll()` + `upsertAll()`

### Repository (`MediaRepository`)
- [x] Interface (`MediaRepository`) — contract with `observe*`, `observeFiltered`, `get*`, `existsByUrl`, `add`, `update`, `delete`, `incrementProgress`, `decrementProgress`, `setStatus`, `replaceAll`
- [x] Implementation (`MediaRepositoryImpl`)
- [x] **Every write path calls `MediaItem.normalize()`** before persisting:
  - [x] `add()` → `UUID.randomUUID()`, `lastUpdated = now`, `.normalize()`, upsert
  - [x] `update()` → `lastUpdated = now`, `.normalize()`, upsert
  - [x] `incrementProgress()` → delegates to `dao.atomicIncrement()` (no read needed)
  - [x] `decrementProgress()` → delegates to `dao.atomicDecrement()` (no read needed)
  - [x] `setStatus()` → delegates to `dao.updateStatus()` (no read needed)
  - [x] `replaceAll()` → map each item through `.normalize()` then `dao.replaceAll()`
- [x] `add()` generates UUID — ViewModels never call `UUID.randomUUID()`
- [x] `lastUpdated` set on every mutation, `dateAdded` set once on creation
- [x] `sourceUrl` UNIQUE constraint errors caught → mapped to `DuplicateUrlException`
- [x] `replaceAll()` catches `SQLiteConstraintException` → mapped to `ImportFailedException`
- [x] Entity ↔ Domain mapping via `mapNotNull` with try/catch to prevent corrupted-row crash
- [x] `.distinctUntilChanged()` applied after domain mapping to suppress table-level Flow noise

---

## 2. Domain Layer (Pure Kotlin — Zero Android Dependencies)

### Models
- [x] `MediaCategory` enum: `NOVEL`, `MANGA`, `ANIME`, `GAME`, `MOVIE`, `TV` — each with `displayName`, case-insensitive `fromString()`
- [x] `UserStatus` enum: `READING`, `WATCHING`, `PLAYING`, `COMPLETED`, `ON_HOLD`, `DROPPED`, `PLAN_TO` — each with `displayName`, case-insensitive `fromString()`
- [x] `MediaItem` data class with all fields:
  - `id: String` (UUID)
  - `category: MediaCategory`
  - `title: String`
  - `alternativeTitles: List<String>`
  - `sourceUrl: String?`
  - `coverImagePath: String`
  - `coverImageUrl: String?`
  - `currentProgress: Int`
  - `totalItems: Int?`
  - `userStatus: UserStatus`
  - `rating: Double?`
  - `notes: String`
  - `lastUpdated: Long`
  - `dateAdded: Long`
- [x] `normalize()` method — **single invariant enforcement point**:
  - Trims whitespace from title, sourceUrl, coverImagePath, coverImageUrl, notes
  - Ensures title is never blank (defaults to "Untitled")
  - Filters blank alt titles
  - Clamps `currentProgress` to `[0, totalItems]` when total is non-null
  - Clamps `currentProgress >= 0` when total is null
  - Caps `rating` to `1.0–5.0` via `coerceIn`
  - Normalizes `sourceUrl` through `UrlNormalizer.normalize()`
  - Validates `coverImageUrl` scheme is HTTP/HTTPS, otherwise null

### Utilities
- [x] `UrlNormalizer` — canonical URL normalizer using OkHttp's `HttpUrl` (lowercase scheme+host, remove fragment, trim trailing slash)

---

## 3. UI Screens

### 3.1 HomeScreen — Read-Only List
- [x] TopAppBar: "LazyDex" title, [Filter] button, [Sort] button, Settings gear icon
- [x] Active filter pills (compact chips below toolbar) showing current category/status filter
- [x] `LazyColumn` of `MediaCard` items
- [x] `EmptyState` — filter-aware: "Nothing here yet" (no items) or "No items match your filters" (with Clear Filters action)
- [x] FAB [+] → navigates to UnifiedAddEditScreen in **add mode**
- [x] Tap card body → navigates to UnifiedAddEditScreen in **edit mode** (with item `id`)
- [x] Cards are **READ-ONLY** — no progress buttons, no long-press menus
- [x] `FilterBottomSheet` (ModalBottomSheet):
  - Category section: [All] [Novel] [Manga] [Anime] [Game] [Movie] [TV]
  - Status section: [All] [In Progress] [Completed] [On Hold] [Dropped] [Plan to] (mapped via StatusFilter)
  - [Clear Filters] button
- [x] `SortBottomSheet` (ModalBottomSheet):
  - Sort by: [Date Added] [Last Active] [Title] [Progress %]
  - Order: [↑ Ascending] [↓ Descending]
  - Current selection highlighted
- [x] State: `HomeUiState` with `items`, `selectedCategory`, `selectedStatus` (StatusFilter), `sortOrder`, `isLoading`
- [x] Default sort: `DATE_ADDED_DESC` (no teleporting)
- [x] ViewModel: `HomeViewModel` — observes filtered/sorted Flow from repository, maps StatusFilter to database status query

### 3.2 UnifiedAddEditScreen — Add + Edit Modes
- [x] **Add mode**: empty fields, URL scrape trigger visible, no delete button
- [x] **Edit mode**: pre-filled from DB via `observeById(id)`, delete button visible in toolbar
- [x] **URL field** (add mode) with [Auto-fill] button → triggers scrape
- [x] **Cover image** area — Coil async loading from local path, gradient+initials fallback
- [x] **Title field** — required, editable. Triggers error if blank on Save
- [x] **Alternative Titles** — dynamic list:
  - [↕] swap with main title
  - [×] remove
  - [+ Add Alternative Title] button
- [x] **Category chips** — all 6 categories, selectable (re-maps in-progress status on change)
- [x] **Status chips** — 5 category-adaptive status options, smart default:
  - Novel/Manga → Reading
  - Anime/Movie/TV → Watching
  - Game → Playing
- [x] **Star Rating** — tappable 1.0–5.0 stars (half-star increments), numeric display "4.5/5.0"
- [x] **Progress** / **Total** — number fields with safe Int parsing
- [x] **Validation**: red error text + disabled Save when progress > total
- [x] **Notes** — multiline text field
- [x] **Cover URL** field (optional, visible if scrape fails or manual entry) → triggers local download
- [x] **Source URL** field — editable, with [Open URL] button (launches browser)
- [x] **Save button** — disabled if validation fails:
  - Title blank → inline error
  - Progress > total → inline error
  - Invalid URL format → inline error
- [x] **Delete button** (edit mode only) → confirmation dialog → `repository.delete(id)` → pop back (sends navigation event)
- [x] **Discard changes dialog** on back press if unsaved edits exist
- [x] **State flow**: IDLE → SCRAPING → AUTO-FILLED → VALIDATING → SAVING → DONE (pop back)
- [x] Scrape flow: blocked fields (title, cover, alt titles, category) show spinner during scrape; active fields (progress, total, status, notes, rating) remain editable
- [x] ViewModel: `UnifiedAddEditViewModel` — fully hoists form state in a `MutableStateFlow` (no split-brain rememberSaveable)
- [x] `SavedStateHandle` for nav args (itemId for edit mode, null for add)

### 3.3 SettingsScreen
- [x] TopAppBar: "Settings" with back navigation
- [x] **Data section**:
  - [Export Backup] → Dialog: "Include Cover Images?" (Metadata Only or Metadata + Covers) → SAF CreateDocument (backup.lazydex) → package ZIP archive with backup.json and optional covers/ directory -> copy to SAF → delete temp
  - [Import Backup] → SAF OpenDocument (.lazydex) → copy to cache and unzip -> parse backup.json -> choice dialog: [Merge] or [Overwrite]
    - Merge: `BackupProcessor.merge(local, imported)` → `repository.replaceAll(merged)` -> copy covers to storage for new/overwritten items
    - Overwrite: press-and-hold 5–10s → `repository.replaceAll(imported)` -> restore all covers from ZIP
- [x] **Theme section**:
  - Theme Mode: [System] [Dark] [Light] selector (Dark default)
  - Amoled Mode: toggle (pure black backgrounds)
- [x] **About section**:
  - LazyDex v0.0.1-beta
  - "Built with Kotlin & Jetpack Compose"
  - [Open Source Licenses] → licenses screen/dialog
- [x] ViewModel: `SettingsViewModel` — theme prefs via DataStore, import/export orchestration (runs I/O on Dispatchers.IO, CPU tasks on Dispatchers.Default)
- [x] Error handling: export failure → Toast, import failure → dialog, corrupt backup → dialog, empty backup → dialog

---

## 4. Scraper

### MetadataScraper
- [x] Takes `OkHttpClient` as constructor parameter (injectable, testable)
- [x] `suspend fun scrape(url: String): Result<ScrapedMetadata>` wrapped in `withTimeout(30_000L)`
- [x] `ScrapedMetadata(title, imageUrl, alternativeTitles)`
- [x] URL validation:
  - Must start with `https://` — reject `http://` and non-http schemes
  - Max 2048 characters
  - Reject IP-based URLs (SSRF protection)
  - Reject known malicious patterns (javascript:, data:, file:)
  - `SafeDns` — custom OkHttp Dns resolver blocking private/local network addresses
- [x] Normalize URL via `UrlNormalizer.normalize(url)` before request
- [x] OkHttp config: connect 10s, read 15s, write 10s, follow redirects=true, max 5, modern mobile User-Agent, no cookie jar
- [x] `withContext(Dispatchers.IO)` for network, `withContext(Dispatchers.Default)` for Jsoup.parse()
- [x] Size limit: abort if >5MB via custom Okio `SizeLimitedSource` throwing `IOException`
- [x] Charset: Pass `null` to Jsoup parser to allow sniffing encoding automatically (prevents mojibake)
- [x] Extract: `og:title` > `twitter:title` > `<title>`
- [x] Extract: `og:image` > `twitter:image` > first `<img>` (resolve relative via `absUrl()`)
- [x] Keep `og:image` scheme as-is (do not upgrade http→https)
- [x] Fallback: empty string if no title/image found (don't crash)

---

## 5. Backup System

### Serialization/Deserialization
- [x] `BackupProcessor` — kotlinx.serialization, `ignoreUnknownKeys = true`, case-insensitive enums
- [x] `BackupEnvelopeDto(schemaVersion, items)` — versioned envelope
- [x] `MediaItemBackupDto` — all fields nullable (robust deserialization) including `coverImageUrl`, `notes`, and `dateAdded`
- [x] `serialize(items)`: domain → DTO → JSON
- [x] `deserialize(json)`: JSON → envelope → DTO → domain (with sanitization)
- [x] Deserialization rules:
  - Schema version missing → default 1
  - Schema version > 1 → reject with message
  - Items missing/null → return empty list
  - Item missing `id` → generate UUID
  - Item missing `title` → reject that item
  - Bad category → reject item
  - Bad userStatus → default to category-appropriate status
  - Negative/null progress → clamp to 0
  - Negative total → treat as null
  - Missing/null/0 lastUpdated → epoch 0 (NOT import time)
  - Missing/null/0 dateAdded → default to current system time
  - Empty file → throw
  - File > 50MB → abort

### BackupManager
- [x] `export()`: packages backup.json metadata and optional local covers/ folder into `.lazydex` ZIP archive
- [x] `import()`: unzips `.lazydex` file, extracts backup.json, and provides temporary covers directory for restoration
- [x] Temp files cleaned up safely via `try/finally` block to prevent storage leaks
- [x] Use `"wt"` mode (write+truncate) for SAF output stream to prevent trailing garbage

### Merge Logic (`BackupProcessor.merge()`)
- [x] Keyed by BOTH `id` and normalized `sourceUrl`. Group by normalized URL first, resolve conflicts, and force the winning model to adopt the local database's `id`.
- [x] Conflicts: newer `lastUpdated` wins
- [x] Tie: local wins (deterministic)
- [x] Items only in local → keep as-is
- [x] Items only in imported:
  - Preserve valid historical `lastUpdated` if > 0L
  - Stamp with `System.currentTimeMillis() - index` only if timestamp is 0/null/negative
- [x] Use `LinkedHashMap` for deterministic ordering
- [x] All merged items normalized before return
- [x] Cover image restore/overwrite is tied to conflict resolution: if imported metadata wins or is new, extract and copy its cover from ZIP; otherwise, keep local cover.

---

## 6. Theme

- [x] Dark theme as default
- [x] Light theme toggle (via DataStore preference)
- [x] System theme option (follow device setting)
- [x] Amoled mode (pure black `#000000` backgrounds when dark + amoled enabled)
- [x] Dynamic color (Monet) on Android 12+ (SDK 31+)
- [x] Fallback custom palette for older Android:
  - Background: `#1b1d23`
  - Cards: `#1f2129`
  - Primary: `#5795d9`
  - Accent: `#fd7e14`
- [x] Status colors (reserved for status badges only):
  - In Progress: `#5795d9` (primary blue)
  - Completed: `#22c55e` (green)
  - On Hold: `#eab308` (yellow)
  - Dropped: `#ef4444` (red)
  - Plan to: `#6b7280` (gray)
- [x] Status pill backgrounds: `color.copy(alpha = 0.15f)`
- [x] Category badges: neutral outline + icon (no color fill)
- [x] `CategoryBadge` component: outline + icon per category
- [x] `StatusBadge` component: color-filled pill

---

## 7. Shared Components

- [x] `MediaCard` — cover image (Coil), title, status badge, category badge, rating stars, progress text, last updated relative time, onTap callback
- [x] `FilterBottomSheet` — category + status selection, Clear Filters
- [x] `SortBottomSheet` — sort by + order, current selection highlighted
- [x] `CategoryBadge` — outline + icon (Book for Novel/Manga, TV for Anime/TV, Gamepad for Game, Film for Movie)
- [x] `StatusBadge` — color-filled pill with status-adaptive color
- [x] `StarRating` — tappable 1.0–5.0, half-star precision, numeric display, interactive toggle
- [x] `AltTitleEditor` — dynamic list, swap/remove/add, integrated with main title
- [x] `CoverImage` — Coil async from local path, gradient + first-letter initials fallback
- [x] `EmptyState` — configurable message, action label, action callback, filter-aware variant

---

## 8. Navigation

- [x] Single-Activity (`MainActivity`) — only sets ComposeContent, zero logic
- [x] Navigation Compose 2.8 with type-safe routes
- [x] Routes: `Home`, `AddEdit(itemId: String?)`, `Settings`
- [x] Back navigation with discard-changes dialog (UnifiedAddEditScreen)
- [x] Delete → pop back to Home
- [x] Save → pop back to Home

---

## 9. DI (Koin)

- [x] `LazyDexApp` — Application class, Koin init
- [x] Room module: database, DAO
- [x] Repository module: `MediaRepositoryImpl` bound to `MediaRepository` interface
- [x] ViewModel modules: `HomeViewModel`, `UnifiedAddEditViewModel`, `SettingsViewModel`
- [x] Scraper module: `OkHttpClient` + `MetadataScraper`
- [x] Backup module: `BackupProcessor`, `BackupManager`

---

## 10. Architecture & Conventions

- [x] MVVM + Repository pattern
- [x] State management: Kotlin Flow → StateFlow (no LiveData)
- [x] Domain layer: zero Android dependencies (no Context, no Parcelable)
- [x] ViewModels: no Fragment/Activity/Context references; `SavedStateHandle` for nav args
- [x] Repository: single source of truth; UI never reads Room directly
- [x] Screens: stateless Composables observing `StateFlow` and emitting events
- [x] Every write path goes through `MediaItem.normalize()`
- [x] `replaceAll()` is atomic (`@Transaction`)
- [x] No `allowMainThreadQueries()` — all DB ops through coroutines

---

## Known Limitations (Accept for 0.0.1-beta)

1. **No optimistic locking** — last save wins if two screens edit the same item
2. **No auto-backup** — backup is manual only (WorkManager auto-backup deferred to 0.0.2)
3. **No AniList/MAL integration** — URL scraping only for metadata auto-fill (OAuth sync is post-v1.0)
4. **No CI/CD configuration** — build manually or via Android Studio
5. **No unit/instrumented tests** — deferred to 0.0.2 to ship working app faster
6. **No ProGuard/R8 rules** — default Android optimization only
7. **Single language** — English `strings.xml` only (i18n deferred)
8. **No accessibility audit** — basic content descriptions may be incomplete
9. **Rating color interpolation** — not implemented; stars use default star color (deferred)

---

## 11. Build Configuration

- [x] Language: Kotlin
- [x] Min SDK: 26, Target SDK: 34
- [x] Package: `app.lazydex`
- [x] Gradle version catalog (`libs.versions.toml`)
- [x] Dependencies:
  - Jetpack Compose + Material3
  - Navigation Compose 2.8
  - Room (with KSP)
  - Koin
  - OkHttp 4.x
  - Jsoup
  - Coil v3
  - kotlinx.serialization
  - DataStore Preferences
  - WorkManager (declared, not wired for auto-backup yet)

---

## 12. File Structure

```
app.lazydex/
├── LazyDexApp.kt
├── MainActivity.kt
├── data/
│   ├── local/
│   │   ├── LazyDexDatabase.kt
│   │   ├── entity/MediaItemEntity.kt
│   │   ├── dao/MediaItemDao.kt
│   │   └── converter/Converters.kt
│   └── repository/
│       └── MediaRepositoryImpl.kt
├── domain/
│   ├── model/
│   │   ├── MediaItem.kt
│   │   ├── MediaCategory.kt
│   │   └── UserStatus.kt
│   └── repository/
│       └── MediaRepository.kt
├── ui/
│   ├── theme/
│   │   ├── Theme.kt
│   │   ├── Color.kt
│   │   └── Type.kt
│   ├── navigation/NavGraph.kt
│   ├── home/
│   │   ├── HomeScreen.kt
│   │   └── HomeViewModel.kt
│   ├── addedit/
│   │   ├── UnifiedAddEditScreen.kt
│   │   └── UnifiedAddEditViewModel.kt
│   ├── settings/
│   │   ├── SettingsScreen.kt
│   │   └── SettingsViewModel.kt
│   └── components/
│       ├── MediaCard.kt
│       ├── FilterBottomSheet.kt
│       ├── SortBottomSheet.kt
│       ├── CategoryBadge.kt
│       ├── StatusBadge.kt
│       ├── StarRating.kt
│       ├── AltTitleEditor.kt
│       ├── CoverImage.kt
│       └── EmptyState.kt
├── di/Modules.kt
├── scraper/MetadataScraper.kt
├── util/UrlNormalizer.kt
├── backup/
│   ├── BackupManager.kt
│   └── BackupProcessor.kt
└── res/
    ├── values/strings.xml
    ├── values/themes.xml
    └── drawable/
```
