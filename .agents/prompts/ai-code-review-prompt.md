# LazyDex — Implementation Plan (AI Audit/Review Only)

> **Task: Review this implementation plan and find ALL bugs, inconsistencies, missing edge cases, and design flaws. Do NOT build or generate any code. This is a review-only exercise.**
>
> **Audience**: This document is for an AI agent tasked with auditing and validating the LazyDex Android application implementation plan. The AI must be **merciless** — find every inconsistency, every missing edge case, every race condition, every performance trap, every deviation from best practices. Do not assume anything is correct. Verify everything. Question every decision below. If you find a flaw, flag it **and propose the proper correction**. Every flagged issue must include a concrete, actionable fix — not just a warning. All proposed solutions must strictly follow the architectural rules, conventions, and decisions documented in this plan (e.g., domain purity, normalize() enforcement, atomic SQL, epoch-0 default, etc.). Do not suggest workarounds that bypass or contradict these requirements.
>
> **Workflow**:
> 1. Read the entire plan from top to bottom
> 2. Cross-reference every section for contradictions and omissions
> 3. Verify every code snippet compiles logically (even if not runnable in this context)
> 4. Flag every issue with section reference, explanation, and concrete fix
> 5. Keep auditing — find MORE issues. Previous audits found 6+ issues; there are always more.

---

## 1. Project Overview

### Purpose
Local-only Android media tracker for tracking consumption progress across Novels, Manga, Anime, Games, Movies, and TV Shows. No accounts, no cloud, no social. Privacy-first.

### Core Features
1. Manual entry — URL scrape (novels) as auto-fill option inside the add form
   - MVP (v1.0): manual entry + URL scraping for novels. (Note: AniList/MyAnimeList search and sync are post-v1.0 future considerations and strictly out of scope for MVP).
2. Track progress (simple Int current + optional Int total; unit label derived from category)
3. Toggle status: 5 statuses (Reading/Watching/Playing, Completed, On Hold, Dropped, Plan to) — display label adapts to category
4. View all items in a filterable list (by category and status)
5. Tap item → UnifiedAddEditScreen (edit mode: title, cover, progress, status, alt titles, rating, notes, source URL, delete)
6. Edit and delete items — inline editing on UnifiedAddEditScreen (edit in place + save), delete with confirmation
7. Backup/restore data to `.lazydex` ZIP archive (containing JSON metadata and optional cover images directory) via SAF + auto-backup (scheduled)
8. Merge or overwrite on import
9. Dark theme (default) + Light toggle in settings + dynamic color (Monet, Android 12+) + fallback palette (older Android) + Amoled mode option
10. Settings screen — Data (export metadata or metadata + covers, import), Theme (dark/light toggle, amoled mode), Backup (auto-backup schedule), About (version, credits, licenses)

### Non-Goals
- No user accounts (v1.0 / MVP)
- No cloud sync (v1.0 / MVP)
- No social features
- No login/authentication (v1.0 / MVP)
- No multi-device support
- No extension/plugin system
- No push notifications
- No widget
- Note: AniList/MyAnimeList search and sync (OAuth) are planned as post-v1.0 features, not in-scope for the initial MVP release.

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
| Min SDK | **26** (Android 8.0) | Covers 95%+ devices | — |
| Target SDK | **34** | Latest stable | — |
| Package | **app.lazydex** | Short, clean, matches Mihon's `app.mihon` pattern | — |

### Testing Dual-Track Decision (Resolved Contradiction)

JUnit 5 (Jupiter) is the framework for **all unit tests** — ViewModels, Repository, Scraper, Backup. Compose UI tests (`createComposeRule()`) rely on JUnit4 infrastructure. These coexist via the `de.mannodermaus.android-junit5` plugin which enables Jupiter on Android while keeping JUnit4 rule support. The concrete setup:

- `test/` (unit tests): Jupiter API + engine + MockK + Turbine + Kotest assertions
- `androidTest/` (instrumented UI tests): JUnit4 runner + Compose UI test rules

Both live in the same Gradle module. The `android-junit5` plugin makes this work without conflict. Do NOT attempt to use Jupiter-only for Compose UI tests — `createComposeRule()` is hard-wired to JUnit4's `@Rule`/`@ClassRule` annotation model.

---

## 3. Architecture & Project Structure

```
app.lazydex/
├── LazyDexApp.kt                          # Application class (Koin init)
├── MainActivity.kt                        # Single Activity hosting Compose (~30 lines)
│
├── data/
│   ├── local/
│   │   ├── LazyDexDatabase.kt              # Room database (exportSchema = true, baseline tracked)
│   │   ├── entity/MediaItemEntity.kt       # Room entity
│   │   ├── dao/MediaItemDao.kt             # DAO with Flow queries + atomic increment/decrement
│   │   └── converter/Converters.kt         # Type converters
│   └── repository/
│       └── MediaRepositoryImpl.kt          # Implements domain repository
│
├── domain/
│   ├── model/
│   │   ├── MediaItem.kt                    # Domain model + normalize()
│   │   ├── MediaCategory.kt                # NOVEL, MANGA, ANIME, GAME, MOVIE, TV
│   │   └── UserStatus.kt                   # READING, WATCHING, PLAYING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO
│   └── repository/
│       └── MediaRepository.kt              # Interface (contract)
│
├── ui/
│   ├── theme/
│   │   ├── Theme.kt                        # Material3 theme with dynamic color
│   │   ├── Color.kt                        # Custom color palette
│   │   └── Type.kt                         # Typography
│   ├── navigation/
│   │   └── NavGraph.kt                     # NavHost + route definitions
│   ├── home/
│   │   ├── HomeScreen.kt                   # Main list + filter chips + FAB
│   │   └── HomeViewModel.kt
│   ├── addedit/
│   │   ├── UnifiedAddEditScreen.kt          # Single screen: Add + Edit modes
│   │   └── UnifiedAddEditViewModel.kt
│   ├── settings/
│   │   ├── SettingsScreen.kt               # Data, Theme, Backup, About
│   │   └── SettingsViewModel.kt
│   └── components/
│       ├── MediaCard.kt                    # Single item card (READ-ONLY)
│       ├── FilterBottomSheet.kt            # Category + status filter bottom sheet
│       ├── SortBottomSheet.kt              # Sort options bottom sheet
│       ├── CategoryBadge.kt                # Outline + icon badge (no color fill)
│       ├── StatusBadge.kt                  # Color-filled status pill
│       ├── StarRating.kt                   # Tappable 1.0–5.0 stars (half-star)
│       ├── AltTitleEditor.kt               # Dynamic list with swap/add/remove
│       ├── CoverImage.kt                   # Local path loading + gradient fallback
│       └── EmptyState.kt                   # Filter-aware placeholder
│
├── di/
│   └── Modules.kt                          # Koin modules (Room, Repository, ViewModels, Scraper)
│
├── scraper/
│   └── MetadataScraper.kt                  # OkHttp + Jsoup metadata extraction (NO WebView, uses UrlNormalizer)
│
├── util/
│   └── UrlNormalizer.kt                    # Single canonical URL normalizer (used by scraper + duplicate detection)
│
├── backup/
│   ├── BackupManager.kt                    # SAF read/write operations
│   └── BackupProcessor.kt                  # Serialize/deserialize/merge logic
│
└── res/
    ├── values/strings.xml                  # All user-facing strings
    ├── values/themes.xml                   # Minimal (splash screen only)
    └── drawable/                           # Vector icons, etc.
```

### Key Architectural Rules

1. **Domain layer has zero Android dependencies**. `MediaItem`, `MediaCategory`, `UserStatus`, `MediaRepository` interface — pure Kotlin. No Context, no Parcelable, no Android types.
2. **ViewModels never reference Fragment or Activity types**. No `context` in ViewModels. Use `SavedStateHandle` for nav args if needed.
3. **Repository is the single source of truth**. UI never reads from Room directly. Never caches data outside the repository.
4. **Scraper returns a domain model, not a parcelable**. `MetadataScraper` returns `ScrapedMetadata(title: String, imageUrl: String, alternativeTitles: List<String>)` — pure data.
5. **Screens are stateless Composables**. All state lives in ViewModels. Screens observe `StateFlow` and emit events.
6. **The Activity does nothing but set ComposeContent**. No logic in `MainActivity`.
7. **Every write path goes through `MediaItem.normalize()`** before persisting. This is the canonical invariant enforcer (progress capping, bounds, etc.) and CANNOT be bypassed by any caller including import.

---

## 4. Detailed Feature Specifications

### 4.1 Data Model

```kotlin
// domain/model/MediaCategory.kt
enum class MediaCategory(val displayName: String) {
    NOVEL("Novel"),
    MANGA("Manga"),
    ANIME("Anime"),
    GAME("Game"),
    MOVIE("Movie"),
    TV("TV");

    companion object {
        /** Case-insensitive lookup. Returns null for unknown values. */
        fun fromString(value: String): MediaCategory? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

// domain/model/UserStatus.kt
enum class UserStatus(val displayName: String) {
    READING("Reading"),
    WATCHING("Watching"),
    PLAYING("Playing"),
    COMPLETED("Completed"),
    ON_HOLD("On Hold"),
    DROPPED("Dropped"),
    PLAN_TO("Plan to");

    companion object {
        /** Case-insensitive lookup. Returns null for unknown values. */
        fun fromString(value: String): UserStatus? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

// domain/model/MediaItem.kt
data class MediaItem(
    val id: String,              // UUID string
    val category: MediaCategory,
    val title: String,
    val alternativeTitles: List<String> = emptyList(),  // Flexible list stored as JSON
    val sourceUrl: String?,      // Nullable — SQLite UNIQUE index treats NULLs as non-duplicates
    val coverImagePath: String,  // Local file path (not URL), empty if no cover
    val coverImageUrl: String?,  // Nullable original URL of the cover (used as fallback for download/restore)
    val currentProgress: Int,    // Always >= 0, and <= totalItems when total is non-null
    val totalItems: Int?,        // null = unknown/ongoing
    val userStatus: UserStatus,
    val rating: Double? = null,  // 1.0–5.0 stars, null = unrated
    val notes: String = "",      // User notes/annotations
    val lastUpdated: Long,       // System.currentTimeMillis()
    val dateAdded: Long          // System.currentTimeMillis() on creation (stable sort)
) {
    /**
     * Canonical normalization — run before EVERY write (add, update, import, merge).
     * - Trims whitespace from title, sourceUrl, coverImagePath, coverImageUrl
     * - Ensures title is never blank (defaults to "Untitled")
     * - Normalizes sourceUrl via UrlNormalizer
     * - Validates coverImageUrl has HTTP/HTTPS scheme, otherwise null
     * - Clamps currentProgress to [0, totalItems] when totalItems is non-null
     * - Clamps currentProgress >= 0 when totalItems is null
     * - Caps rating to 1.0–5.0 range
     * This is the SINGLE invariant enforcement point.
     */
    fun normalize(): MediaItem {
        val normalizedUrl = sourceUrl?.takeIf { it.isNotBlank() }?.let { UrlNormalizer.normalize(it) }
        val safeCoverUrl = coverImageUrl?.trim()?.takeIf {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        }
        val safeTotal = totalItems?.takeIf { it >= 0 }
        val safeProgress = when {
            currentProgress < 0 -> 0
            safeTotal != null && currentProgress > safeTotal -> safeTotal
            else -> currentProgress
        }
        val safeRating = rating?.coerceIn(1.0, 5.0)
        return copy(
            title = title.trim().ifBlank { "Untitled" },
            alternativeTitles = alternativeTitles.map { it.trim() }.filter { it.isNotBlank() },
            sourceUrl = normalizedUrl,
            coverImagePath = coverImagePath.trim(),
            coverImageUrl = safeCoverUrl,
            totalItems = safeTotal,
            currentProgress = safeProgress,
            rating = safeRating,
            notes = notes.trim()
        )
    }
}

// data/local/entity/MediaItemEntity.kt
@Entity(
    tableName = "media_items",
    indices = [Index(value = ["sourceUrl"], unique = true)]
)
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val category: String,            // Stored as uppercase string (e.g. "NOVEL"), no ambiguity
    val title: String,
    val alternativeTitles: String,   // JSON array: ["Alt 1", "Alt 2", ...]
    val sourceUrl: String?,          // Nullable — multiple NULLs allowed under UNIQUE index
    val coverImagePath: String,      // Local file path (not URL), empty if no cover
    val coverImageUrl: String?,      // Nullable original URL of the cover
    val currentProgress: Int,
    val totalItems: Int?,
    val userStatus: String,          // Stored as uppercase string (e.g. "READING")
    val rating: Double?,             // 1.0–5.0, null = unrated
    val notes: String,
    val lastUpdated: Long,
    val dateAdded: Long
)
```

**AI Verification Checklist**:
- `currentProgress` invariant enforced in `normalize()` — never below 0, never above totalItems when total is non-null
- `normalize()` must be called by repository on every write (add, update, setStatus, replaceAll). The repository is responsible, not the ViewModel
- **ID generation rule** (two distinct paths):
  - `repository.add()` (UI-entered items) generates a fresh UUID via `UUID.randomUUID()`. This is the **only** place new UUIDs are created. ViewModels never call `UUID.randomUUID()`.
  - **Import/merge** preserves the ID from the JSON file. A new UUID is generated ONLY if the imported item's `id` is missing, null, or empty (see 4.6 deserialization rules). This is critical: if import blindly generated new UUIDs, merge-by-ID would always treat imported items as new, creating duplicates.
- `totalItems` being null means "unknown" — progress can go arbitrarily high (within Int range)
- `lastUpdated` must be set on every mutation (add, edit, status change)
- `dateAdded` is set ONCE on creation (in `repository.add()`) and NEVER changed — provides stable sort order
- `rating` is nullable Double (1.0–5.0). `normalize()` caps with `coerceIn(1.0, 5.0)`
- `alternativeTitles` stored as JSON array string. TypeConverter converts to/from `List<String>`
- `coverImagePath` stores local file path (not URL). Empty string = no cover. Cover images downloaded to `{appInternalDir}/covers/{itemId}.{ext}`
- `coverImageUrl` stores original URL of the cover image. Normalized and validated to have http/https schemes.
- Entity ↔ Domain mapping uses `MediaCategory.fromString()` and `UserStatus.fromString()` — case-insensitive, defaults to null for unknown values → fail closed (reject the item or skip). The repository must wrap the mapping in try/catch inside `mapNotNull` to prevent a single corrupted row (e.g., from a future backup restore introducing "PODCAST") from crashing the entire Flow stream with a NullPointerException
- Room TypeConverters or column types? **Decision**: store enum strings directly as columns, use `@TypeConverters` only if you need reusable conversion. Since the DAO already works with raw strings, skip TypeConverters. The repository handles entity↔domain mapping. Alternative titles need a TypeConverter for `List<String>` ↔ JSON string.
- **`sourceUrl` has a SQLite UNIQUE index** (`@Entity(indices = [Index(value = ["sourceUrl"], unique = true)])`). **sourceUrl must be nullable (`String?`)** — SQLite treats empty strings `""` as equal under a UNIQUE index, so a second item with no URL would throw `SQLiteConstraintException`. Null values are never considered duplicates. This is a database-level safeguard — duplicate URL detection is NOT solely the UI's responsibility. The repository must catch `SQLiteConstraintException` and map it to a domain exception. See Section 4.4 for error handling.

### 4.2 Room Database

```kotlin
@Database(entities = [MediaItemEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class LazyDexDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao
}
```

**`exportSchema = true` Decision**: Schema JSON is tracked from v1 to enable `AutoMigration` for future versions. Configure `room.schemaLocation` in `app/build.gradle.kts` to output the schema file. Without this, v2+ migrations would require manual SQL instead of AutoMigration.

**AI Verification Checklist**:
- Database name: `"lazydex_db"` (constant)
- `fallbackToDestructiveMigration()` — acceptable for v1 since there are no migrations. Add a `Log.w()` on destructive migration so developers know data was wiped
- Room `.build()` is lazy — corruption surfaces on first query, not at construction. Wrap first DAO access in try/catch, not the builder call
- Never use `allowMainThreadQueries()` — all DB ops go through coroutines
- Enable WAL mode via `setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)` for better concurrent read/write

### 4.3 DAO

```kotlin
@Dao
interface MediaItemDao {
    // Reactive queries
    @Query("SELECT * FROM media_items ORDER BY dateAdded DESC")
    fun observeAll(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE category = :category ORDER BY dateAdded DESC")
    fun observeByCategory(category: String): Flow<List<MediaItemEntity>>

    /**
     * Filtered observation that supports category and a general UI StatusFilter mapping.
     * Uses a single query with IN clause to handle in-progress mappings.
     */
    @Query("""
        SELECT * FROM media_items 
        WHERE (:category IS NULL OR category = :category)
        AND (
            :filterType = 'ALL' OR 
            (:filterType = 'IN_PROGRESS' AND userStatus IN ('READING', 'WATCHING', 'PLAYING')) OR
            (:filterType = 'EXACT' AND userStatus = :exactStatus)
        )
        ORDER BY dateAdded DESC
    """)
    fun observeFiltered(category: String?, filterType: String, exactStatus: String?): Flow<List<MediaItemEntity>>

    // Sorting queries
    @Query("SELECT * FROM media_items ORDER BY dateAdded DESC")
    fun observeAllByDateAdded(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items ORDER BY lastUpdated DESC")
    fun observeAllByLastUpdated(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items ORDER BY title ASC")
    fun observeAllByTitle(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items ORDER BY CAST(currentProgress AS REAL) / CAST(COALESCE(totalItems, currentProgress) AS REAL) ASC")
    fun observeAllByProgress(): Flow<List<MediaItemEntity>>

    @Query("SELECT COUNT(*) FROM media_items")
    fun observeCount(): Flow<Int>

    // Reactive single-item observation (used by ViewModel)
    @Query("SELECT * FROM media_items WHERE id = :id")
    fun observeById(id: String): Flow<MediaItemEntity?>

    // One-shot reads
    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: String): MediaItemEntity?

    @Query("SELECT * FROM media_items")
    suspend fun getAll(): List<MediaItemEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM media_items WHERE sourceUrl = :url LIMIT 1)")
    suspend fun existsByUrl(url: String): Boolean

    @Upsert
    suspend fun upsert(item: MediaItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<MediaItemEntity>)

    /**
     * Atomic progress increment — uses single SQL UPDATE with MIN() capping.
     */
    @Query("""
        UPDATE media_items
        SET currentProgress = MIN(currentProgress + 1, COALESCE(totalItems, currentProgress + 1)),
            lastUpdated = :now
        WHERE id = :id
    """)
    suspend fun atomicIncrement(id: String, now: Long)

    /**
     * Atomic progress decrement — uses single SQL UPDATE with MAX() floor.
     */
    @Query("""
        UPDATE media_items
        SET currentProgress = MAX(currentProgress - 1, 0),
            lastUpdated = :now
        WHERE id = :id
    """)
    suspend fun atomicDecrement(id: String, now: Long)

    /**
     * Atomic status update.
     */
    @Query("""
        UPDATE media_items
        SET userStatus = :status,
            lastUpdated = :now
        WHERE id = :id
    """)
    suspend fun updateStatus(id: String, status: String, now: Long)

    // Deletes
    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM media_items")
    suspend fun deleteAll()

    /**
     * Atomic replaceAll — runs in a single transaction.
     * Prevents data loss if process dies between deleteAll and upsertAll.
     */
    @Transaction
    suspend fun replaceAll(items: List<MediaItemEntity>) {
        deleteAll()
        upsertAll(items)
    }
}
```

The `clearAndInsert` method above is wrong — `@Transaction` + `@Query` works for a single DELETE, not DELETE+INSERT. The correct approach is a default method in the DAO interface:

```kotlin
@Transaction
suspend fun replaceAll(items: List<MediaItemEntity>) {
    deleteAll()
    upsertAll(items)
}
```

Note: `@Transaction` on an interface default method works because Room generates the wrapper class. The function does NOT need to be `open`. The entire `deleteAll() + upsertAll()` is one atomic unit — if the upsert fails, SQLite rolls back the entire transaction including the delete. No data loss. Room's generated upsert binds one entity at a time per SQL statement, so there is no SQLite parameter limit issue regardless of list size. For read concurrency during a write, configure WAL mode via `setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)` on the Room builder.

**AI Verification Checklist**:
- `observeAll()` returns `Flow` — Room runs this on a background thread automatically
- `observeByCategory()` filters by category string — must match uppercase enum names ("NOVEL", "ANIME", etc.)
- `observeById()` returns `Flow<MediaItemEntity?>` — used by DetailViewModel to reactively observe the latest data from Room without passing the full domain model through navigation arguments
- `atomicIncrement` and `atomicDecrement` use single SQL UPDATE statements — no read-modify-write race condition possible. This makes the "rapid tap" race condition a non-issue. The user can tap [+] 100 times and every increment is individually atomic
- `updateStatus` is also a single atomic SQL UPDATE — no race condition
- `replaceAll` is annotated with `@Transaction` and calls `deleteAll()` + `upsertAll()` in the right order. If the process dies mid-transaction, Room rolls back — no data loss
- DAO methods must never be called from the main thread (Room enforces this by default with `suspend` and `Flow`)
- `@Upsert` (Room 2.5+) is used instead of `@Insert(onConflict = REPLACE)` to avoid deleting+reinserting the row (which changes the SQLite rowid). **However, this does NOT prevent false-positive Flow re-emissions** — Room's invalidation tracker fires at the *table* level, not the *row* level. ANY write to `media_items` causes ALL active Flow queries on that table to re-evaluate. The Repository layer applies `.distinctUntilChanged()` after domain mapping to suppress table-level noise (see Section 4.9).

### 4.4 Repository

```kotlin
interface MediaRepository {
    /** Reactive observation — UI bindings */
    fun observeAll(): Flow<List<MediaItem>>
    fun observeByCategory(category: MediaCategory): Flow<List<MediaItem>>
    fun observeFiltered(category: MediaCategory?, statusFilter: StatusFilter): Flow<List<MediaItem>>
    fun observeById(id: String): Flow<MediaItem?>  // Single-item observation for UnifiedAddEditViewModel

    /** One-shot reads */
    suspend fun getById(id: String): MediaItem?
    suspend fun getAll(): List<MediaItem>   // For backup export
    suspend fun existsByUrl(url: String): Boolean  // SQLite-bound, near-zero memory

    /** Writes — ALL go through MediaItem.normalize() before persisting */
    suspend fun add(item: MediaItem): MediaItem           // generates id + sets lastUpdated
    suspend fun update(item: MediaItem)                    // caller provides full item
    suspend fun delete(id: String)

    /** Atomic operations (delegated to DAO-level SQL) */
    suspend fun incrementProgress(id: String)
    suspend fun decrementProgress(id: String)
    suspend fun setStatus(id: String, status: UserStatus)

    /** Bulk operations — atomic via @Transaction */
    suspend fun replaceAll(items: List<MediaItem>)         // clears and inserts atomically
}
```

**Implementation Contract** (every write path explicitly listed — no exceptions):
- `add()` → `item.copy(id = UUID.randomUUID().toString(), lastUpdated = System.currentTimeMillis()).normalize()` → `dao.upsert(entity)`
- `update()` → `item.copy(lastUpdated = System.currentTimeMillis()).normalize()` → `dao.upsert(entity)`. **This includes the Edit screen**: user manually types progress=500, total=100 → `normalize()` clamps progress to 100 before DB write. The repository does NOT trust that ViewModel data is already valid.
- `incrementProgress()` → `dao.atomicIncrement(id, System.currentTimeMillis())` — no read needed, SQL handles capping
- `decrementProgress()` → `dao.atomicDecrement(id, System.currentTimeMillis())` — no read needed, SQL handles flooring
- `setStatus()` → `dao.updateStatus(id, status.name, System.currentTimeMillis())`
- `replaceAll()` → normalizes every item, then calls `dao.replaceAll(normalizedEntities)`

**Single Canonical URL Normalizer** — used by ALL code paths (scraper, duplicate detection, validation). There is exactly one normalizer. If it changes, every consumer changes with it. No drift permitted.

```kotlin
// util/UrlNormalizer.kt
import okhttp3.HttpUrl

object UrlNormalizer {
    /**
     * Canonical URL normalization for duplicate detection + scrape validation.
     * Uses OkHttp's HttpUrl to parse and normalize, which handles encoding spaces
     * and general web-scale URL quirks natively without throwing URISyntaxException.
     * - Lowercase scheme + host
     * - Trim whitespace
     * - Remove trailing slash
     * - Strip fragment (#...)
     */
    fun normalize(url: String): String {
        val httpUrl = HttpUrl.parse(url.trim()) ?: return url.trim()
        return httpUrl.newBuilder()
            .fragment(null)
            .build()
            .toString()
            .removeSuffix("/")
    }
}
```

The scraper in 4.5 calls `UrlNormalizer.normalize(url)` for its normalization step. The duplicate detection in the repository calls the same `UrlNormalizer.normalize(url)` on both the new URL and all existing `sourceUrl` values. Two different implementations would drift — this must be one file.

**AI Verification Checklist**:
- `add()` generates UUID and sets lastUpdated — this is the **only** place UUIDs are generated. ViewModels never call `UUID.randomUUID()`
- `normalize()` is called in the repository on every write path — NOT in the ViewModel, NOT in the UI
- `replaceAll()` normalizes every item individually — this handles the import-bypass gap. Even if a corrupted backup has `currentProgress = -5`, it gets clamped to 0 before hitting the DB
- Atomic increment/decrement means NO debounce in the ViewModel. "Rapid tap" is safe.
- Optimistic locking (stale-edit detection) is NOT implemented for v1. See 4.10.5 for rationale
- Error handling: repository wraps DB exceptions and rethrows as `DataAccessException` (a custom sealed class or RuntimeException subclass). Alternatively, use Kotlin `Result<T>` for all write operations
- **UNIQUE constraint on `sourceUrl`**: The entity has a SQLite UNIQUE index on `sourceUrl`. If the repository's `add()`, `update()`, or `replaceAll()` triggers a `SQLiteConstraintException` (e.g., an edge case bypasses the UI-level duplicate check), the repository must catch it and map it to a `DuplicateUrlException` domain exception. Do NOT let raw constraint violations propagate to the ViewModel.
- **`replaceAll()` exception handling**: `replaceAll()` must catch `SQLiteConstraintException` and map it to an `ImportFailedException` so the ViewModel can surface a user-friendly error during import instead of crashing:
  ```kotlin
  override suspend fun replaceAll(items: List<MediaItem>) = withContext(Dispatchers.IO) {
      try {
          val entities = items.map { it.normalize().toEntity() }
          dao.replaceAll(entities)
      } catch (e: SQLiteConstraintException) {
          throw ImportFailedException("Import failed: duplicate source URL detected", e)
      } catch (e: Exception) {
          throw ImportFailedException("Import failed due to database error", e)
      }
  }
  ```

### 4.5 Metadata Scraper

```kotlin
class MetadataScraper(private val okHttpClient: OkHttpClient) {
    suspend fun scrape(url: String): Result<ScrapedMetadata> = withTimeout(30_000L) {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val call = okHttpClient.newCall(request)
                
                currentCoroutineContext()[Job]?.invokeOnCompletion {
                    call.cancel()
                }

                call.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                    val body = response.body ?: throw IOException("Empty body")
                    
                    // Check Content-Type subtype: assume html if missing, permit xhtml+xml and html
                    val subtype = body.contentType()?.subtype ?: "html"
                    if (!subtype.contains("html", ignoreCase = true) && !subtype.contains("xml", ignoreCase = true)) {
                        throw IOException("URL does not point to an HTML page (got $subtype)")
                    }

                    // Check Content-Length header first — fast rejection for known-large
                    val contentLength = body.contentLength()
                    if (contentLength > MAX_SCRAPE_BYTES) throw IOException("File too large")
                    
                    // Use custom SizeLimitedSource to read safely without buffering the entire body
                    val limitedSource = SizeLimitedSource(body.source(), MAX_SCRAPE_BYTES).buffer()
                    val doc = withContext(Dispatchers.Default) {
                        // Pass null for charset to allow Jsoup to sniff the true encoding from headers/<meta> tags (prevents mojibake)
                        Jsoup.parse(limitedSource.inputStream(), null, url)
                    }
                    
                    val title = extractTitle(doc)
                    val imageUrl = extractImageUrl(doc)
                    val alternativeTitles = extractAlternativeTitles(doc)
                    Result.success(ScrapedMetadata(title, imageUrl, alternativeTitles))
                }
            } catch (e: TimeoutCancellationException) {
                Result.failure(Exception("Request timed out. The site may be slow or unreachable.", e))
            } catch (e: Exception) {
                if (e is IOException && e.message == "Canceled" || !currentCoroutineContext().isActive) {
                    throw CancellationException("Scrape cancelled", e)
                }
                Result.failure(e)
            }
        }
    }

    data class ScrapedMetadata(
        val title: String,
        val imageUrl: String,
        val alternativeTitles: List<String> = emptyList()
    )
}

private const val MAX_SCRAPE_BYTES = 5L * 1024 * 1024

class SizeLimitedSource(delegate: Source, private val maxBytes: Long) : ForwardingSource(delegate) {
    private var totalRead = 0L
    override fun read(sink: Buffer, byteCount: Long): Long {
        val result = super.read(sink, byteCount)
        if (result != -1L) {
            totalRead += result
            if (totalRead > maxBytes) throw IOException("Response exceeded $maxBytes bytes")
        }
        return result
    }
}
```

**Scraping Strategy**:
1. Validate URL format before making request
2. Normalize URL via `UrlNormalizer.normalize(url)`
3. GET the page with OkHttp (custom User-Agent, 15s read timeout)
4. Parse HTML with Jsoup
5. Extract `og:title` or `twitter:title` or `<title>` (in that priority)
6. Extract `og:image` or `twitter:image` or first `<img>` (in that priority)
7. For `og:image` with relative URL: resolve to absolute using `Jsoup`'s `absUrl()`
8. For `og:image` scheme: **keep as-is**. Do NOT upgrade http→https. Do NOT HEAD-check. Coil will handle connection errors on its own retry/fallback. Blind-upgrade breaks http-only images. HEAD-check adds an extra round-trip and many servers 405/block HEAD even when GET works.
9. Return `Result.success` or `Result.failure` with clear error message

**URL Validation Rules**:
- Must start with `https://` (reject `http://` and non-http schemes)
- Must be a valid URL (use OkHttp's `HttpUrl.parse()` to check, encoding spaces gracefully)
- Max length: 2048 characters (standard URL max)
- Reject IP-address-based URLs (security: no `https://192.168.x.x` or `https://10.x.x.x`)
- Reject known malicious patterns (e.g., URLs containing JavaScript schemes, `data:` URIs, `file://`)
- **SSRF Protection**: Simple URL string matching is insufficient — it misses loopback addresses, secondary local ranges (e.g. `172.16.x.x`), IPv6 locals (`::1`, `fe80::`), and is vulnerable to DNS rebinding (a public domain resolving to a local IP). Implement a custom OkHttp `Dns` resolver as the sole SSRF defense — it blocks private networks at the DNS lookup level, which catches ALL the above cases including rebinding. Do NOT rely on URL pattern matching alone:
  ```kotlin
  class SafeDns : Dns {
      override fun lookup(hostname: String): List<InetAddress> {
          val addresses = Dns.SYSTEM.lookup(hostname)
          for (address in addresses) {
              if (address.isLoopbackAddress || 
                  address.isSiteLocalAddress || 
                  address.isLinkLocalAddress || 
                  address.isAnyLocalAddress) {
                  throw IOException("Access to local/private network addresses is blocked")
              }
          }
          return addresses
      }
  }
  ```
- Normalize before duplicate check: lowercase scheme + host, trim trailing slashes, strip fragments

**Edge Cases & Errors the AI MUST Handle**:
- URL is empty or blank
- URL is malformed (no dots, spaces, invalid characters)
- URL is `http://` not `https://` — reject with "Only HTTPS URLs are supported"
- URL is a localhost/intranet IP — reject with "Only public URLs are supported"
- URL points to a binary file (PDF, ZIP, image) — the page returns non-HTML (caught by Content-Type subtype validation)
- Network timeout (15s read timeout, but site takes 30s) — caught by coroutine `withTimeout(30_000L)` and throws `TimeoutCancellationException` which is mapped to a timeout error Result
- DNS resolution failure
- SSL handshake failure (expired cert, wrong host)
- Server returns 4xx or 5xx
- Server returns empty body
- Server returns malformed HTML (no `<head>`, no `<title>`, unclosed tags)
- Page is behind a login wall (returns login page HTML, not real content)
- Page requires cookies/accept headers (some sites return 403 without proper headers)
- Page uses Cloudflare/DDOS protection (returns challenge page)
- Page redirects multiple times (OkHttp follows redirects by default, but set max redirects to 5)
- Page is extremely large (>5MB) — abort and fail at the Okio source level via `SizeLimitedSource` throwing `IOException` to prevent memory exhaustion / OOM
- `og:title` exists but is empty string
- `og:image` exists but is a relative URL — resolve to absolute using `Jsoup`'s `absUrl("content")`
- `og:image` scheme is `http://` — keep as-is. Coil handles http images. Do NOT try to upgrade to https.
- Title extracted is garbage (e.g., "404 Not Found", "Access Denied", "Error") — heuristic rejection check
- Multiple languages in title (e.g., English primary with Japanese subtitle) — keep as-is
- Character encoding issues (ISO-8859-1 vs UTF-8 pages) — Jsoup handles this automatically when charset is null
- JavaScript-rendered pages (Jsoup can't execute JS — this is intentional. If site requires JS, scraping fails gracefully. NO WebView fallback.)
- Rate limiting / 429 Too Many Requests — the scraper returns the failure message. No automatic retry.

**AI MUST verify**:
- OkHttp client is configured with prop### 4.6 Backup System

```kotlin
@Serializable
data class BackupEnvelopeDto(
    val schemaVersion: Int = 1,
    val items: List<MediaItemBackupDto>? = null
)

@Serializable
data class MediaItemBackupDto(
    val id: String? = null,
    val category: String? = null,
    val title: String? = null,
    val alternativeTitles: List<String>? = null,
    val sourceUrl: String? = null,
    val coverImageUrl: String? = null,
    val currentProgress: Int? = null,
    val totalItems: Int? = null,
    val userStatus: String? = null,
    val rating: Double? = null,
    val notes: String? = null,
    val lastUpdated: Long? = null,
    val dateAdded: Long? = null
)

object BackupProcessor {
    private val backupJson = Json {
        ignoreUnknownKeys = true
        decodeEnumsCaseInsensitive = true
    }

    suspend fun serialize(items: List<MediaItem>): String = withContext(Dispatchers.Default) {
        val envelope = BackupEnvelopeDto(items = items.map { it.toDto() })
        backupJson.encodeToString(envelope)
    }

    suspend fun deserialize(json: String): List<MediaItem> = withContext(Dispatchers.Default) {
        val envelope = backupJson.decodeFromString<BackupEnvelopeDto>(json)
        envelope.items?.mapNotNull { dto -> dto.toDomain() } ?: emptyList()
    }

    suspend fun merge(local: List<MediaItem>, imported: List<MediaItem>): List<MediaItem> =
        withContext(Dispatchers.Default) {
            val localById = local.associateBy { it.id }
            val localByUrl = local.filter { it.sourceUrl != null }.associateBy { it.sourceUrl }
            val result = LinkedHashMap<String, MediaItem>()
            local.forEach { result[it.id] = it }
            
            imported.forEachIndexed { index, item ->
                // Check duplicate by URL first, then by ID
                val existingLocal = localById[item.id] ?: item.sourceUrl?.let { localByUrl[it] }
                
                when {
                    existingLocal == null -> {
                        // Pure addition: preserve timestamp if > 0, otherwise stagger
                        val finalTime = if (item.lastUpdated > 0L) item.lastUpdated
                                        else System.currentTimeMillis() - index
                        result[item.id] = item.copy(lastUpdated = finalTime).normalize()
                    }
                    item.lastUpdated > existingLocal.lastUpdated -> {
                        // Imported item is newer: overwrite local (keep local ID if matched by URL)
                        result[existingLocal.id] = item.copy(id = existingLocal.id).normalize()
                    }
                }
            }
            result.values.toList()
        }

    private fun MediaItem.toDto() = MediaItemBackupDto(
        id = id,
        category = category.name,
        title = title,
        alternativeTitles = alternativeTitles.ifEmpty { null },
        sourceUrl = sourceUrl,
        coverImageUrl = coverImageUrl,
        currentProgress = currentProgress,
        totalItems = totalItems,
        userStatus = userStatus.name,
        rating = rating,
        notes = notes.ifBlank { null },
        lastUpdated = lastUpdated,
        dateAdded = dateAdded
    )
}

private fun MediaItemBackupDto.toDomain(): MediaItem? {
    val safeTitle = title?.takeIf { it.isNotBlank() } ?: return null
    val safeCategory = category?.let { MediaCategory.fromString(it) } ?: return null
    val safeStatus = userStatus?.let { UserStatus.fromString(it) } ?: inProgressStatusFor(safeCategory)
    val safeProgress = maxOf(currentProgress ?: 0, 0)
    val safeTotal = totalItems?.takeIf { it >= 0 }
    val safeLastUpdated = if (lastUpdated != null && lastUpdated > 0L) lastUpdated else 0L
    val safeDateAdded = if (dateAdded != null && dateAdded > 0L) dateAdded else System.currentTimeMillis()
    return MediaItem(
        id = id.takeIf { !it.isNullOrBlank() } ?: UUID.randomUUID().toString(),
        category = safeCategory,
        title = safeTitle,
        alternativeTitles = alternativeTitles ?: emptyList(),
        sourceUrl = sourceUrl?.trim(),
        coverImagePath = "",  // Will be populated during cover extraction if zip matches
        coverImageUrl = coverImageUrl?.trim(),
        currentProgress = safeProgress,
        totalItems = safeTotal,
        userStatus = safeStatus,
        rating = rating?.coerceIn(1.0, 5.0),
        notes = notes?.trim() ?: "",
        lastUpdated = safeLastUpdated,
        dateAdded = safeDateAdded
    ).normalize()
}

object BackupManager {
    /**
     * Exports backup data to a .lazydex ZIP file at the target SAF URI.
     * Packages the backup.json metadata and optionally the local covers/ folder.
     */
    suspend fun export(
        context: Context,
        uri: Uri,
        items: List<MediaItem>,
        includeCovers: Boolean,
        localCoversDir: File
    ) = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "backup_${UUID.randomUUID()}.tmp")
        try {
            ZipOutputStream(tempFile.outputStream().buffered()).use { zipOut ->
                // Write backup.json
                zipOut.putNextEntry(ZipEntry("backup.json"))
                val jsonMetadata = BackupProcessor.serialize(items)
                zipOut.write(jsonMetadata.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // Write covers if selected
                if (includeCovers && localCoversDir.exists() && localCoversDir.isDirectory) {
                    val coverFiles = localCoversDir.listFiles() ?: emptyArray()
                    for (file in coverFiles) {
                        if (file.isFile) {
                            zipOut.putNextEntry(ZipEntry("covers/${file.name}"))
                            file.inputStream().use { it.copyTo(zipOut) }
                            zipOut.closeEntry()
                        }
                    }
                }
            }

            // Copy the finalized ZIP to the SAF target
            context.contentResolver.openOutputStream(uri, "wt")?.use { safOut ->
                tempFile.inputStream().use { it.copyTo(safOut) }
            } ?: throw IOException("Could not open output stream for $uri")
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    /**
     * Imports a .lazydex ZIP file, extracting backup.json and any covers to a temporary directory.
     * Returns the list of parsed MediaItems, and calls the block with the temporary covers directory.
     */
    suspend fun import(
        context: Context,
        uri: Uri,
        onImportSuccess: suspend (items: List<MediaItem>, tempCoversDir: File?) -> Unit
    ) = withContext(Dispatchers.IO) {
        val tempZipFile = File(context.cacheDir, "import_${UUID.randomUUID()}.tmp")
        val tempExtractDir = File(context.cacheDir, "extracted_${UUID.randomUUID()}")
        tempExtractDir.mkdirs()

        try {
            // Copy from SAF to local temp file
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempZipFile.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("Could not open input stream for $uri")

            // Unzip file
            var jsonString: String? = null
            val tempCoversDir = File(tempExtractDir, "covers")
            
            ZipInputStream(tempZipFile.inputStream().buffered()).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "backup.json" -> {
                            jsonString = zipIn.bufferedReader(Charsets.UTF_8).readText()
                        }
                        entry.name.startsWith("covers/") && !entry.isDirectory -> {
                            tempCoversDir.mkdirs()
                            val filename = entry.name.substringAfter("covers/")
                            val targetFile = File(tempCoversDir, filename)
                            targetFile.outputStream().use { zipIn.copyTo(it) }
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            val json = jsonString ?: throw IllegalArgumentException("Missing backup.json inside ZIP")
            val items = BackupProcessor.deserialize(json)
            
            // Execute the import success block while the temp covers are available
            onImportSuccess(items, tempCoversDir.takeIf { it.exists() })

        } finally {
            if (tempZipFile.exists()) tempZipFile.delete()
            tempExtractDir.deleteRecursively()
        }
    }
}
```

**Backup JSON Schema** (contained inside `backup.json` inside the `.lazydex` ZIP):
```json
{
    "schemaVersion": 1,
    "items": [
        {
            "id": "uuid-string",
            "category": "NOVEL",
            "title": "Example",
            "alternativeTitles": ["Alt 1", "Alt 2"],
            "sourceUrl": "https://...",
            "coverImageUrl": "https://...",
            "currentProgress": 42,
            "totalItems": 100,
            "userStatus": "READING",
            "rating": 4.5,
            "notes": "My notes",
            "lastUpdated": 1700000000000,
            "dateAdded": 1700000000000
        }
    ]
}
```

**Backup Format Robustness**:
- Backups may omit `schemaVersion` entirely. **Decision**: `schemaVersion` defaults to `1` when missing. Do NOT reject files missing `schemaVersion`.
- Backups may omit `lastUpdated`. **Decision**: `lastUpdated` defaults to **epoch 0** (`0L`) when missing, null, or 0. NOT import time. Rationale: if untimestamped imported items got `System.currentTimeMillis()`, they'd be newer than every local item, causing merge ("keep newest") to silently clobber genuinely newer local edits. Epoch 0 means local always wins conflicts against untimestamped imports — safe default. Merge tiebreak (`local wins when timestamps equal`) then also favors local for any imported items that were also stamped 0.
- `category` may be stored as `NOVEL`, `Novel`, or `novel` — handle case-insensitively
- `userStatus` similarly may vary — handle case-insensitively
- `totalItems`, `rating`, and `notes` are nullable — ensure kotlinx.serialization handles nullable fields correctly

**Deserialization Rules**:
- `schemaVersion` missing → treat as `1`
- `schemaVersion` > `1` → reject with "Unsupported backup schema version: X" (future-proofing)
- `schemaVersion` is present but not an integer → reject as malformed
- `items` field missing or null → return empty list (don't crash)
- Individual item with missing `id` → generate a new UUID (don't reject the whole import)
- Individual item with missing `title` → reject that item (data is corrupted)
- `category` string doesn't match any `MediaCategory` value → reject that item
- `userStatus` string doesn't match any `UserStatus` value → default to category-adaptive status
- `currentProgress` negative or null → clamp to 0 during deserialization
- `totalItems` negative → treat as `null`
- `lastUpdated` is 0, negative, or missing → set to epoch 0 (0L). NOT `System.currentTimeMillis()`. Rationale: stamping imported items with import time makes them newer than every local item, so merge ("keep newest") silently clobbers genuine local edits. Epoch 0 is the safe default — local always wins conflicts against untimestamped imports. Note: `merge()` will correct pure-addition items (no local counterpart) to `System.currentTimeMillis()` — see merge logic below. Deserialization itself stays conservative (epoch 0) because it has no knowledge of which items have local counterparts.
- Empty file or blank string → throw `IllegalArgumentException("Backup file is empty")`
- File > 50MB → abort with "Backup file is too large"

**Export Rules**:
- Temp file write must be wrapped in `try/finally` block to guarantee deletion on failure or coroutine cancellation (prevents storage leaks).
- Package backup as `.lazydex` ZIP archive. Include optional cover images from local directory into `covers/` subdirectory.
- Copy temp file to SAF target URI via ContentResolver's `openOutputStream(uri, "wt")`.

**Merge Logic**:
- Deduplicates by BOTH `id` and normalized `sourceUrl`. Group by normalized URL first, resolve conflicts, and force the winning model to adopt the local database's `id`.
- Tie-break: Newer timestamp wins; local wins on tie.
- Cover image restore/overwrite is tied to the conflict resolution decision: If imported item wins or is a new addition, copy/restore its cover image from the ZIP to local storage. Otherwise, keep the local cover image.

**Auto-backup**: WorkManager scheduled daily/weekly. Writes to a temporary file in app-internal storage first, packages the ZIP (including covers if enabled), and only copies the completed ZIP to the SAF directory once it is fully validated (prevents partial-write corruption in the user's folder). Keeps last 30 files, deletes older.

**AI Verification Checklist**:
- `replaceAll` imports through repository — goes through `normalize()` (invariant enforcement)
- Serialization uses kotlinx.serialization with `@SerialName` annotations if field names differ from Kotlin property names
- Test round-trip: serialize → deserialize → serialize → compare
- Test with actual backup file

### 4.7 UI Screens

#### HomeScreen (Read-Only List)
- TopAppBar with title "LazyDex", [Filter] and [Sort] buttons, and settings gear icon
- Active filter pills shown as compact chips below toolbar
- LazyColumn of MediaCards, or EmptyState when list is empty (filter-aware)
- FAB: + icon to open UnifiedAddEditScreen in add mode
- Each card shows: cover image (async via Coil from local path), title, status badge (color-filled), category badge (outline + icon), rating stars, progress text (current/total), last updated relative time
- Cards are READ-ONLY — no progress buttons on cards. No long-press context menu
- Tap card body → navigate to UnifiedAddEditScreen in edit mode with item `id`
- Filter bottom sheet contains: Category section [All] [Novel] [Manga] [Anime] [Game] [Movie] [TV] and Status section [All] [Reading] [Watching] [Playing] [Completed] [On Hold] [Dropped] [Plan to]

**State Management**:
```kotlin
enum class StatusFilter(val displayName: String) {
    ALL("All"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    ON_HOLD("On Hold"),
    DROPPED("Dropped"),
    PLAN_TO("Plan to")
}

data class HomeUiState(
    val items: List<MediaItem> = emptyList(),
    val selectedCategory: MediaCategory? = null,  // null = "All"
    val selectedStatus: StatusFilter = StatusFilter.ALL,
    val sortOrder: SortOrder = SortOrder.DATE_ADDED_DESC,
    val isLoading: Boolean = true
)

enum class SortOrder {
    DATE_ADDED_DESC,
    LAST_UPDATED_DESC,
    TITLE_ASC,
    PROGRESS_ASC
}

sealed interface HomeAction {
    data class SelectCategory(val category: MediaCategory?) : HomeAction
    data class SelectStatus(val status: StatusFilter) : HomeAction
    data class SelectSort(val sortOrder: SortOrder) : HomeAction
    data class OpenDetail(val itemId: String) : HomeAction
}

sealed interface HomeUiEvent {
    data class OpenItem(val url: String) : HomeUiEvent
}
```

**Filtering Strategy**: Uses `combine` on `StateFlow<MediaCategory?>`, `StateFlow<StatusFilter>`, and `StateFlow<SortOrder>` to let SQLite do the work:

```kotlin
class HomeViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val selectedCategoryFlow = MutableStateFlow<MediaCategory?>(null)
    private val selectedStatusFlow = MutableStateFlow<StatusFilter>(StatusFilter.ALL)
    private val sortOrderFlow = MutableStateFlow(SortOrder.DATE_ADDED_DESC)

    val uiState: StateFlow<HomeUiState> = combine(
        selectedCategoryFlow, selectedStatusFlow, sortOrderFlow
    ) { category, status, sort -> Triple(category, status, sort) }
        .flatMapLatest { (category, status, sort) ->
            repository.observeFiltered(category, status)
                .map { items ->
                    HomeUiState(
                        items = items,
                        selectedCategory = category,
                        selectedStatus = status,
                        sortOrder = sort,
                        isLoading = false
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun selectCategory(category: MediaCategory?) { selectedCategoryFlow.value = category }
    fun selectStatus(status: StatusFilter) { selectedStatusFlow.value = status }
    fun selectSort(sort: SortOrder) { sortOrderFlow.value = sort }

    // One-shot event channel for navigation/intents
    private val _events = Channel<HomeUiEvent>(Channel.BUFFERED)
    val events: Flow<HomeUiEvent> = _events.receiveAsFlow()

    fun openItem(url: String) {
        viewModelScope.launch {
            _events.send(HomeUiEvent.OpenItem(url))
        }
    }
}
```

**AI Verification Checklist**:
- Cards are READ-ONLY. No increment/decrement buttons. No long-press context menu.
- Tap card → navigate to UnifiedAddEditScreen (edit mode) with item `id`. Card click never starts an Activity — it navigates within the app.
- [Filter] button opens FilterBottomSheet (ModalBottomSheet) with category + status selection
- [Sort] button opens SortBottomSheet (ModalBottomSheet) with sort order + direction
- Filter/sort state persisted in ViewModel `StateFlow`s — survives config changes
- Empty state is filter-aware: if filters active and no matches → "No items match your filters. [Clear Filters]"
- Cover image loaded from local `coverImagePath` via Coil. Gradient+initials fallback if path empty.
- Relative time display: calculate at render time. Acceptable for "5m ago" to become "6m ago".
- FAB click: navigate to UnifiedAddEditScreen in add mode.

#### UnifiedAddEditScreen (Full-Screen Form — Add + Edit modes)

Single screen handles both adding new items (empty fields, scrape available) and editing existing items (pre-filled from DB, delete button visible in edit mode only).

**Fields** (add mode):
- Cover image display (local path via Coil, gradient+initials fallback)
- URL input + [Auto-fill] button (add mode only; scrapes title, cover, alternative titles, category)
- Title text field (blocked during scrape)
- Alternative titles dynamic list: [↕] swap with main, [×] remove, [+ Add]
- Category chips (Novel, Manga, Anime, Game, Movie, TV)
- Status chips (category-adaptive: 1 in-progress + Completed, On Hold, Dropped, Plan to)
- Rating: 5 tappable stars (half-star precision, 1.0–5.0)
- Progress (number) + Total (number, optional)
- Notes text field (optional)
- Source URL (editable) with [Open URL] button
- [Save] button (disabled if validation fails)
- [Delete] button (edit mode only, with confirmation)

**Flow** (add mode):
1. User enters URL → taps [Auto-fill]
2. Blocked during scrape: Title, Cover Image, Alternative Titles, Category show spinner
3. Active during scrape: Progress, Total, Status, Notes, Rating remain editable
4. Success → scraped values filled into blocked fields. Scraped value replaces user-typed text if different. If scrape finds nothing → user's text preserved.
5. Auto-detect: if progress = total → auto-set status to Completed
6. Failure → inline error "Could not auto-fill, please enter manually", blocked fields re-enabled

**Validation Rules** (shared by Add and Edit):
- Title is required (non-empty, trim whitespace)
- Category must be selected (default to Novel)
- URL is optional but if provided must be valid HTTPS
- Progress >= 0, must be ≤ total if total is set → red error text, Save disabled
- Total must be >= 0 if provided (null = unknown)
- Rating 1.0–5.0, optional (null = unrated)
- Similar title detection: check if main/alt title matches existing item → "Similar title exists" warning
- **Add mode**: block save if progress > total
- **Edit mode**: clamp progress on blur (cap to total), show inline hint ("Capped to N"). Do NOT block save. `normalize()` is the authoritative enforcement.

**Status Derivation** (smart default, fully editable):
- Novel/Manga → **Reading**
- Anime/Movie/TV → **Watching**
- Game → **Playing**

**AI Verification Checklist**:
- Scrape is cancellable via ViewModelScope — if user dismisses screen during scrape, coroutine is cancelled
- If scrape is in progress and user manually edits an active field, don't overwrite their edit when scrape succeeds
- Form state fully hoisted in ViewModel's MutableStateFlow (no `rememberSaveable` split-brain)
- Cover image displayed from local `coverImagePath` via Coil; gradient+initials fallback if path empty
- No cover URL text field — cover comes from scrape download or is not set
- Duplicate URL check: normalize via `UrlNormalizer.normalize()`, then `repository.existsByUrl(normalizedUrl)` — single SQLite EXISTS query. Show warning: "This URL is already tracked"
- Progress/Total text fields accept only numeric input (`KeyboardType.Number`). **CRITICAL: Safe Int parsing required**. For `currentProgress`: `text.toLongOrNull()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0`. For `totalItems`: `text.takeIf { it.isNotBlank() }?.toLongOrNull()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt()` — empty input stays `null`.
- URL validation on both scrape tap AND submit (user might skip scraping and enter URL manually)
- Back press + unsaved changes → discard confirmation dialog: "Discard unsaved changes? [Keep Editing] [Discard]"

#### UnifiedAddEditScreen — Edit Mode (previously named DetailScreen)

The same UnifiedAddEditScreen serves edit mode. Pre-fills all fields from DB, adds a [Delete] button in the toolbar.

**Edit-mode differences from add mode**:
- Toolbar title: "Edit Media" (vs "Add Media")
- Fields pre-populated from database via `repository.observeById(id).take(1)` → draft `MutableStateFlow`
- URL input hidden (scrape is add-only)
- Delete button visible in toolbar (with confirmation dialog)
- Category change remaps in-progress status (READING↔WATCHING↔PLAYING) but leaves COMPLETED/ON_HOLD/DROPPED/PLAN_TO untouched

**Full edit-mode fields**:
- Cover image preview (local path via Coil, gradient+initials fallback)
- Title (editable)
- Alternative titles: dynamic list with [↕] swap, [×] remove, [+ Add]
- Category chips (editable — changing category remaps in-progress status)
- Status chips (category-adaptive: 1 in-progress + Completed, On Hold, Dropped, Plan to)
- Rating: 5 tappable stars (half-star precision)
- Progress + Total (editable; clamp on blur with "Capped to N" hint)
- Notes (editable)
- Source URL (editable) with [Open URL] button
- [Save] button
- [Delete] button (confirmation: "Delete 'Title'? This cannot be undone.")

**AI Verification Checklist**:
- Editing the source URL is allowed. No stale-data warning needed.
- Screen identified by passing **only `id` (String)** as nav argument — NOT the full `MediaItem`.
- The route argument is `AddEditRoute(val id: String?)` (a `@Serializable` data class; null = add mode).
- ViewModel extracts route via `savedStateHandle.toRoute<AddEditRoute>()` (Nav 2.8 type-safe API).
- ViewModel initializes draft state from `repository.observeById(id).take(1)` — independent MutableStateFlow not reactive to Room re-emissions (prevents TextField clobbering).
- On save, calls `repository.update(item.copy(lastUpdated = System.currentTimeMillis()).normalize())`.
- Editing sourceUrl to match another item → `DuplicateUrlException` caught, inline error: "This URL is already tracked by another item."
- Must update `lastUpdated` on save (done by repository).
- Delete shows confirmation dialog, then `repository.delete(id)` → emit navigation event to pop back.
- Discard unsaved changes on back press with confirmation dialog: "Discard unsaved changes? [Keep Editing] [Discard]"
- No optimistic locking for v1. Last save wins.
- **Clamping feedback**: If `normalize()` clamps progress, show inline hint "Capped to N" on blur AND when save completes (ViewModel emits snackbar event). Prevents confusion when user taps Save without dismissing keyboard.
- **Category-Changed Status Remap**: When user changes category (e.g. Anime→Novel), only READING/WATCHING/PLAYING get remapped to the new category's in-progress equivalent. COMPLETED/ON_HOLD/DROPPED/PLAN_TO are category-agnostic and stay untouched.

#### SettingsScreen
- Toolbar "Settings" with back arrow
- Sections:
  - **Data**: Export button, Import button
  - **Theme**: Dark/Light toggle, Amoled mode toggle
  - **Backup**: Auto-backup schedule setting
  - **About**: App name + version string, "Built with Kotlin & Jetpack Compose", Open source licenses

**Export Flow**:
1. Tap Export → SAF CreateDocument launcher (suggest "lazydex_backup.json")
2. User picks location → ViewModel calls `repository.getAll()` + `BackupProcessor.serialize()` + `BackupManager.export()`
3. Show success toast or error dialog

**Import Flow**:
1. Tap Import → SAF OpenDocument launcher (filter: application/json)
2. User picks file → ViewModel calls `BackupManager.import()` + `BackupProcessor.deserialize()`
3. Show import choice dialog: Merge or Overwrite
4. Merge: call `BackupProcessor.merge(local, imported)` then `repository.replaceAll(merged)`
5. Overwrite: call `repository.replaceAll(imported)`
6. Show success toast or error dialog

**AI Verification Checklist**:
- SAF launchers are registered in the Activity/Screen (via `rememberLauncherForActivityResult`), not ViewModel — they need ActivityResultRegistry
- Export/Import I/O runs on `Dispatchers.IO`
- `BackupProcessor.serialize/deserialize` runs on `Dispatchers.Default` (CPU-bound)
- Error handling: if export fails (no space, permission denied), show user-friendly toast. Don't crash.
- If import file is corrupted (invalid JSON, wrong schema), show error dialog, don't silently fail
- Merge + replaceAll is atomic via `@Transaction` — if merge computation fails, DB is untouched
- Overwrite + replaceAll is atomic — if any step fails, DB rolls back. No data loss.
- Version string: use `BuildConfig.VERSION_NAME` — never hardcode. Alternative: `context.packageManager.getPackageInfo(context.packageName, 0).versionName`
- Write export to temp file first, then copy to target URI (prevents partial file)
- Settings screen is NOT a bottom sheet — it's a full-screen destination in NavHost

### 4.8 Theme

```kotlin
@Composable
fun LazyDexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,  // Android 12+
    amoledMode: Boolean = false,   // True black backgrounds
    content: @Composable () -> Unit
)
```

**Requirements**:
- Dynamic color on Android 12+ (Monet / Material You)
- Fallback to custom light/dark palettes on older versions
- Dark mode is default (WTR-LAB inspired), toggle in settings
- Amoled mode option: uses pure black (#000000) backgrounds when enabled
- All Material3 components used consistently (MaterialCard, Material3 buttons, etc.)
- Surface colors, background colors, card colors follow Material3 spec — with WTR-LAB palette:
  - Background: `#1b1d23` (dark), `#f2f3f4` (light)
  - Card surface: `#1f2129` (dark), `#ffffff` (light)
  - Toolbar: `#212529` (dark)
  - Primary: `#5795d9` (dark), `#4288c9` (light)
  - Accent: `#fd7e14` (progress, highlights)
- **Color strategy**: Status gets color badges. Category gets neutral outline + Material icon. Rationale: 6 categories × 5 statuses = 30 color combos is visual noise. Status colors have universal associations (green=completed, red=dropped). Category icons are scannable without color memory.
- Status colors (Reading/Watching/Playing=primary blue, Completed=`#22c55e`, On Hold=`#eab308`, Dropped=`#ef4444`, Plan to=`#6b7280`)
- Category representation: outline-style badge with Material icon (Book for Novel/Manga, Gamepad for Game, Film for Movie, TV for Anime/TV)
- Badge backgrounds should be semi-transparent versions of the badge text color (`color.copy(alpha = 0.15f)`)
- Rating colors: 1.0=`#ef4444` → 3.0=`#eab308` → 5.0=`#22c55e` with interpolation
- See `DESIGN.md` for complete theme mapping including typography (Nunito Sans + JetBrains Mono) and spacing

**AI Verification Checklist**:
- `dynamicColor` must check `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` before using `dynamicLightColorScheme()` / `dynamicDarkColorScheme()`
- Fallback palettes must have sufficient contrast (WCAG AA: 4.5:1 for normal text, 3:1 for large text)
- Category and status colors must be accessible
- Use `MaterialTheme.colorScheme` for all standard colors — never hardcode values in components
- Surface colors in dark mode: use dark gray (#1E1E1E or similar), never pure black (#000000) to avoid eye strain on AMOLED. Pure black is acceptable only if the user explicitly chooses an "AMOLED dark" mode (amoledMode = true)
- Badge background: use `Color.copy(alpha = 0.15f)` of the text color for a pill-shaped background

### 4.9 Error Handling Strategy

| Layer | Error Type | Handling |
|-------|-----------|----------|
| Repository | DB corruption on first query | Catch `SQLException` on first `dao` access. Show error dialog with "Reset database" option |
| Repository | Query failure | Log error, rethrow as `DataAccessException` |
| Repository | Progress invariant violation | Handled by `normalize()` — impossible to persist bad data |
| Scraper | Network timeout | Return `Result.failure("Request timed out. The site may be slow or unreachable.")` |
| Scraper | Invalid URL | Return `Result.failure("Invalid URL format. Enter a full https:// URL.")` |
| Scraper | Non-HTTP response | Return `Result.failure("Server returned an unexpected response.")` |
| Backup | File read error | Log error, show toast "Could not read backup file" |
| Backup | Parse error | Show dialog "Backup file appears to be corrupted or from a newer version" |
| Backup | File write error | Show toast "Could not save backup. Check storage space." |
| UI | Network on main thread | Should never happen — all IO is coroutine-dispatched |
| UI | Navigation with deleted item | Catch in ViewModel, navigate back if item no longer exists |

**Important: DB corruption detection + concurrency safety** — `Room.databaseBuilder().build()` is **lazy**. It does NOT open the database or verify integrity at construction time. The first actual query (DAO call) triggers the open, and that's where corruption surfaces. Therefore, use the `onStart` Flow operator to wrap the corruption check around reactive queries — this cleanly separates the suspending check from the Flow return:

```kotlin
class MediaRepositoryImpl(
    private val dao: MediaItemDao
) : MediaRepository {

    override fun observeAll(): Flow<List<MediaItem>> {
        return dao.observeAll()
            .map { entities ->
                entities.mapNotNull { entity ->
                    runCatching { entity.toDomain() }.getOrNull()
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .catch { e ->
                // Catches upstream DB query failures (e.g. SQLiteDatabaseCorruptException)
                // that per-row try/catch in .map {} cannot reach
                Log.e("MediaRepository", "DB query failed (possible corruption)", e)
                emit(emptyList())
            }
    }

    override fun observeByCategory(category: MediaCategory): Flow<List<MediaItem>> {
        return dao.observeByCategory(category.name)
            .map { entities ->
                entities.mapNotNull { entity ->
                    runCatching { entity.toDomain() }.getOrNull()
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .catch { e ->
                Log.e("MediaRepository", "DB query failed (possible corruption)", e)
                emit(emptyList())
            }
    }

    override fun observeFiltered(category: MediaCategory?, statusFilter: StatusFilter): Flow<List<MediaItem>> {
        val catName = category?.name
        val (filterType, exactStatus) = when (statusFilter) {
            StatusFilter.ALL -> "ALL" to null
            StatusFilter.IN_PROGRESS -> "IN_PROGRESS" to null
            StatusFilter.COMPLETED -> "EXACT" to UserStatus.COMPLETED.name
            StatusFilter.ON_HOLD -> "EXACT" to UserStatus.ON_HOLD.name
            StatusFilter.DROPPED -> "EXACT" to UserStatus.DROPPED.name
            StatusFilter.PLAN_TO -> "EXACT" to UserStatus.PLAN_TO.name
        }
        return dao.observeFiltered(catName, filterType, exactStatus)
            .map { entities ->
                entities.mapNotNull { entity ->
                    runCatching { entity.toDomain() }.getOrNull()
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .catch { e ->
                Log.e("MediaRepository", "DB query failed (possible corruption)", e)
                emit(emptyList())
            }
    }

    override fun observeById(id: String): Flow<MediaItem?> {
        return dao.observeById(id)
            .map { entity ->
                runCatching { entity?.toDomain() }.getOrNull()
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .catch { e ->
                Log.e("MediaRepository", "DB query failed (possible corruption)", e)
                emit(null)
            }
    }
}
```

Note 1: Separate DB corruption detection via `ensureDbHealthy()` is removed. Room opens the database lazily — corruption surfaces naturally on the first actual query and throws `SQLiteDatabaseCorruptException`. Catch it at the application entry point or during regular query subscriptions rather than adding a redundant `SELECT 1` health check.

Note 2: The per-row `runCatching` inside `.map {}` only catches domain mapping failures (e.g. invalid enum values). It does NOT catch upstream database query exceptions like `SQLiteDatabaseCorruptException`. The `.catch` operator on the Flow chain is required to handle those upstream failures gracefully — without it, a corrupt database would crash the entire Flow stream and the app.

**Global Error Principles**:
- Never crash with an unhandled exception. Every coroutine should have a try/catch or a `CoroutineExceptionHandler`.
- User-facing errors must be human-readable, not stack traces.
- Log technical details with `Log.e()`.
- If the app enters an unrecoverable state (DB won't open, data corrupted), surface a "Something went wrong" screen with a "Reset app" button that calls `context.deleteDatabase("lazydex_db")` and restarts.

### 4.10 Edge Cases & Race Conditions

These are **not** exhaustive. The AI must consider every scenario below and any it can think of:

1. **Rapid increment/decrement**: User taps [+] 20 times quickly. **Mitigated by atomic SQL**: each increment is `UPDATE media_items SET currentProgress = MIN(currentProgress + 1, ...)`. No read-modify-write race. No debounce needed. Do NOT add debounce — it would drop legitimate taps.

2. **Configuration change during scrape**: User rotates phone while a scrape is in progress. The ViewModel survives (ViewModelScope). The Compose UI is rebuilt. The loading state persists via `StateFlow` → `collectAsStateWithLifecycle()`.

3. **Process death during backup**: Android kills the app while exporting. On relaunch, state is restored from Room (which was never modified during export). Safe.

4. **Import overwrite + process death**: User imports, chooses "Overwrite". The DAO's `@Transaction replaceAll()` runs `deleteAll()` + `upsertAll()` atomically. If process dies mid-transaction, Room rolls back. No data loss.

5. **Two screens editing the same item**: User opens Edit screen, then goes home and opens the same item again via the list. The first Edit screen still has stale data. On save, it overwrites the other change. **Decision for v1**: Last save wins. No optimistic locking. This is acceptable for a single-user local app. If implementing locking later, add `expectedLastUpdated: Long` parameter to `repository.update()` and check in DAO: `UPDATE ... WHERE id = :id AND lastUpdated = :expectedLastUpdated`. If 0 rows affected, throw `StaleDataException`.

6. **Scraping the same URL twice**: User adds URL A, it scrapes successfully. User adds URL A again. The duplicate check (normalized URL comparison) prevents adding. If first scrape is still in progress, the form fields are disabled — second tap is blocked.

7. **Very long titles**: Some sites have very long `<title>` tags (100+ characters). Handle with `maxLines = 2` and `ellipsis = TextUtils.TruncateAt.END` in the card. Room has no practical string length limit.

8. **Very large lists**: 10,000 items. Room handles this fine. LazyColumn handles this fine (view recycling). Backup JSON will be large (~5-10MB). Serialization and I/O will take a few seconds. Show a progress indicator during export/import. For v1, a simple progress dialog is sufficient. Do NOT try to stream JSON for v1 — keep it simple.

9. **Malicious URL injection**: URL like `https://evil.com/"; DROP TABLE media_items; --`. Room uses parameterized queries by default — no SQL injection. URL opened via `Intent.ACTION_VIEW` is URL-encoded by Android. Safe.

10. **Cover image URL that returns a redirect to a malicious site**: Coil handles redirects. Verify that Coil doesn't follow redirects to `file://` or `content://` schemes. This is a Coil security concern — check Coil's redirect policy. If needed, add a custom `Interceptor` to OkHttp client that rejects redirects to non-http schemes.

11. **Empty database on first launch**: Show empty state with friendly message: "Nothing here yet. Tap + to add your first item." If filters are active, show: "No items match your filters. [Clear Filters]"

11b. **Cover image download failure**: Scrape extracts og:image URL but download fails. Show gradient+icon fallback in UI. User can provide alternative URL in edit mode to trigger re-download. Local file path stored in Room (empty string if never downloaded).

11c. **Alternative title swap**: When user taps [↕] on an alt title, current main title moves to position 0 of alt list, selected alt becomes main. Database updated atomically with `normalize()`.

11d. **Progress = total auto-complete**: When user sets `currentProgress = totalItems` and total is non-null, ViewModel automatically sets status to `COMPLETED`. User can override this by manually changing status.

12. **System locale / RTL**: App supports RTL layouts (`supportsRtl="true"` in manifest). Compose handles this with `LocalLayoutDirection`. All paddings use `Start`/`End` not `Left`/`Right`.

13. **Input method / keyboard**: Progress/Total fields show numeric keyboard (`KeyboardType.Number`). Forms handle IME actions (Next → Next → Done). Test on devices without hardware keyboard.

14. **Accessibility**: All interactive elements must have content descriptions (`.contentDescription()` in Compose). Buttons must be at least 48dp touch target. Color must not be the only differentiator — category and status badges use text + icon + color.

---

## 5. Implementation Order

The AI MUST follow this order. Each phase depends on the previous one.

### Phase 1: Project Scaffold
- [ ] Create `build.gradle.kts` (project-level) with version catalog
- [ ] Create `settings.gradle.kts`
- [ ] Create `gradle/libs.versions.toml` with ALL dependencies (including MockWebServer, Kotest assertions, android-junit5 plugin, Compose UI test JUnit4)
- [ ] Create `app/build.gradle.kts` with all plugins (compose, KSP, kotlinx.serialization, android-junit5) and dependencies
- [ ] Create `gradle.properties` (without hardcoded JDK path)
- [ ] Create `proguard-rules.pro` with kotlinx.serialization keep rules
- [ ] Update `gradle-wrapper.properties` to Gradle 8.10.2 (AGP 8.7.x requires Gradle 8.9+ — Gradle 8.7.3 is NOT compatible)
- [ ] Create `local.properties` template (or document requirement)
- [ ] Run `./gradlew assembleDebug` to verify build
- [ ] **AI must verify**: Build succeeds, no dependency conflicts, no missing files, KSP + Room + Kotlin 2.0 compatible

### Phase 2: Data Layer
- [ ] Create `domain/model/` — MediaItem, MediaCategory, UserStatus (with `fromString()` companions, `normalize()`)
- [ ] Create `domain/repository/MediaRepository.kt` interface
- [ ] Create `data/local/entity/MediaItemEntity.kt`
- [ ] Create `data/local/dao/MediaItemDao.kt` (with atomic increment/decrement, `@Transaction replaceAll`)
- [ ] Create `data/local/LazyDexDatabase.kt` (exportSchema = true, schemaLocation configured, WAL mode)
- [ ] Create `data/local/converter/Converters.kt` — TypeConverter for `List<String>` ↔ JSON string (alternative titles)
- [ ] Create `data/repository/MediaRepositoryImpl.kt` (normalize() on every write, UUID in add only)
- [ ] Create `util/UrlNormalizer.kt` (single canonical normalizer, used by repository + scraper)
- [ ] Write tests for MediaRepositoryImpl (insert, read, update, delete, atomic increment/decrement, replaceAll transaction, normalize enforcement)
- [ ] **AI must verify**: All DAO methods work, Flow emissions are correct, atomic SQL prevents race conditions, @Transaction prevents partial updates

### Phase 3: DI Setup
- [ ] Create `di/Modules.kt` — Room module, Repository module, Scraper module, ViewModel modules, Coil ImageLoader module
- [ ] **Coil disk cache hardening**: Configure Coil's `ImageLoader` with a 250MB disk cache and `respectCacheHeaders(false)`. Manga/anime aggregator sites aggressively rotate CDN URLs and use signed expiring tokens. Without aggressive caching, users opening the app offline a month later will find half their cover images broken. The disk cache prevents standard OS eviction from clearing cover images:
  ```kotlin
  single {
      ImageLoader.Builder(androidContext())
          .diskCache {
              DiskCache.Builder()
                  .directory(androidContext().cacheDir.resolve("image_cache"))
                  .maxSizeBytes(250L * 1024 * 1024) // 250MB hard limit
                  .build()
          }
          .respectCacheHeaders(false) // Force cache regardless of site headers
          .build()
  }
  ```
- [ ] Create `LazyDexApp.kt` — Application class with Koin `startKoin()`, implements `SingletonImageLoader.Factory` to register the custom Coil `ImageLoader`
- [ ] Register Application class in AndroidManifest.xml
- [ ] **AI must verify**: Koin starts without errors, all modules load, no circular dependencies, `SingletonImageLoader.Factory` returns the Koin-injected `ImageLoader`

### Phase 4: Theme
- [ ] Create `ui/theme/Color.kt` — custom palette, category colors, status colors
- [ ] Create `ui/theme/Type.kt` — typography scale
- [ ] Create `ui/theme/Theme.kt` — LazyDexTheme with dynamic color + fallback
- [ ] **AI must verify**: Theme compiles, dynamic color works on Android 12+ emulator, fallback works on Android 8-11, WCAG contrast ratios met

### Phase 5: Core UI Components
- [ ] Create `ui/components/MediaCard.kt` (READ-ONLY — no buttons, covers local path)
- [ ] Create `ui/components/FilterBottomSheet.kt` (category + status selection)
- [ ] Create `ui/components/SortBottomSheet.kt` (sort order selection)
- [ ] Create `ui/components/CategoryBadge.kt` (outline + icon, no color fill)
- [ ] Create `ui/components/StatusBadge.kt` (color-filled pill)
- [ ] Create `ui/components/StarRating.kt` (tappable 1.0–5.0 with half-stars)
- [ ] Create `ui/components/AltTitleEditor.kt` (dynamic list with swap/add/remove)
- [ ] Create `ui/components/CoverImage.kt` (local path loading + gradient fallback)
- [ ] Create `ui/components/EmptyState.kt` (filter-aware messages)
- [ ] **AI must verify**: Each component is reusable, accepts proper parameters, handles edge cases (empty strings, null values, long text, RTL)

### Phase 6: Home Screen
- [ ] Create `ui/home/HomeViewModel.kt`
- [ ] Create `ui/home/HomeScreen.kt`
- [ ] Wire filter chips, list, FAB, settings gear
- [ ] Handle user interactions: tap card → navigate to UnifiedAddEditScreen (edit mode), tap FAB → UnifiedAddEditScreen (add mode), tap Filter → FilterBottomSheet, tap Sort → SortBottomSheet, tap gear → SettingsScreen
- [ ] **AI must verify**: Cards are READ-ONLY (no increment/decrement/status-toggle on cards), all interactions work, loading states are correct, filter-aware empty state, config change survives, one-shot events use Channel

### Phase 7: Scraper
- [ ] Create `scraper/MetadataScraper.kt` + tests (MockWebServer for HTTP responses)
- [ ] Update `ScrapedMetadata` to include `alternativeTitles: List<String>`, `imageUrl: String`
- [ ] Handle scrape flow (loading, success, failure with inline error)
- [ ] **AI must verify**: Scrape works with real URLs, extracts title/og:image/alt titles, scrape cancellation works

### Phase 8: Unified Add/Edit Screen
- [ ] Create `ui/addedit/UnifiedAddEditViewModel.kt`
- [ ] Create `ui/addedit/UnifiedAddEditScreen.kt`
- [ ] Single screen handles both add mode (empty + scrape) and edit mode (pre-filled + delete)
- [ ] Add mode: URL input + Auto-fill button, scrape fills blocked fields (title, cover, alts, category)
- [ ] Edit mode: pre-populate from DB via `observeById(id).take(1)` → draft `MutableStateFlow`
- [ ] Category chips: Novel, Manga, Anime, Game, Movie, TV (editable; category-change remaps in-progress status)
- [ ] Status chips: category-adaptive (1 in-progress + Completed, On Hold, Dropped, Plan to)
- [ ] Alternative titles: dynamic list with swap-to-main, add, remove
- [ ] Rating: 5 tappable stars (half-star precision)
- [ ] Cover image: display from local path, gradient fallback; optional cover URL field for re-download if scrape fails
- [ ] Form validation: title required, progress ≤ total (red error + disable Save)
- [ ] Back navigation with unsaved changes → confirmation dialog: "Discard unsaved changes? [Keep Editing] [Discard]"
- [ ] Delete with confirmation dialog (edit mode only), emit navigation event after deletion
- [ ] Save calls `repository.update()` (edit) or `repository.add()` (add)
- [ ] Source URL field (editable) + [Open URL] button to launch external browser
- [ ] **AI must verify**: Both modes work, form state preserved on config change (hoisted in ViewModel MutableStateFlow), validation correct, no UUID generation in ViewModel, back button shows dialog, scrape blocks only conflicting fields, category-change remaps in-progress status, DuplicateUrlException caught with inline error

### Phase 9: Settings Screen
- [ ] Create `backup/BackupProcessor.kt` + tests (round-trip, merge, schema version default 1, case-insensitive enums)
- [ ] Create `backup/BackupManager.kt`
- [ ] Create `ui/settings/SettingsViewModel.kt`
- [ ] Create `ui/settings/SettingsScreen.kt`
- [ ] Handle SAF export/import launchers (in Composable, not ViewModel)
- [ ] Import conflict dialog (merge vs overwrite)
- [ ] Theme section: dark/light toggle, amoled mode toggle
- [ ] Backup section: auto-backup schedule setting
- [ ] About section with version info, credits, open source licenses
- [ ] **AI must verify**: Export creates valid JSON, import reads backup format, schemaVersion missing defaults to 1, merge is deterministic, overwrite is atomic via @Transaction, error cases (corrupt file, empty file, permissions) handled, theme toggles persist, auto-backup schedule works

### Phase 10: Navigation
- [ ] Create `ui/navigation/NavGraph.kt`
- [ ] Wire all screens together (Home, UnifiedAddEdit with optional itemId arg for edit mode, Settings)
- [ ] Handle up/back navigation correctly
- [ ] UnifiedAddEdit has two modes: add mode (no nav arg) and edit mode (itemId nav arg)
- [ ] **AI must verify**: All routes defined, navigation type-safe (Kotlin serialization plugin for nav args), back stack correct, no duplicate destinations, bottom sheet replaced by full-screen UnifiedAddEdit

### Phase 11: Polish & Testing
- [ ] Write ViewModel unit tests (MockK for repository, verify state changes, one-shot events)
- [ ] Write scraper integration tests (MockWebServer)
- [ ] Write backup processor tests (round-trip, merge edge cases, backward compat)
- [ ] Write Compose UI tests (createComposeRule, JUnit4 runner) — at least one per screen
- [ ] Accessibility audit
- [ ] Performance profiling (DB queries, list scrolling, image loading)
- [ ] **AI must verify**: Test coverage is meaningful, tests are deterministic, UI tests run on JUnit4 runner not Jupiter, no flaky tests, performance acceptable

---

## 6. Build Configuration Details

### Dependencies (libs.versions.toml)

```toml
[versions]
kotlin = "2.0.21"
agp = "8.7.3"
compose-bom = "2024.12.01"
room = "2.6.1"
koin = "4.0.0"
okhttp = "4.12.0"
jsoup = "1.18.3"
coil = "3.0.4"
kotlinx-serialization = "1.7.3"
navigation-compose = "2.8.5"
lifecycle = "2.8.7"
coroutines = "1.9.0"
ksp = "2.0.21-1.0.28"
junit5 = "5.11.4"
android-junit5 = "1.11.4.0"
mockk = "1.13.14"
turbine = "1.2.0"
kotest-assertions = "5.9.1"
mockwebserver = "4.12.0"
compose-ui-test = "1.7.6"
androidx-test-ext = "1.2.1"
androidx-test-runner = "1.6.2"
datastore = "1.1.1"
work = "2.10.0"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
compose-activity = { group = "androidx.activity", name = "activity-compose", version = "1.9.3" }
compose-navigation = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation-compose" }

# Compose UI Test (JUnit4 runtime — for instrumented UI tests only)
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4", version.ref = "compose-ui-test" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest", version.ref = "compose-ui-test" }

# AndroidX Instrumentation Test (required to boot AndroidJUnitRunner and createComposeRule())
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "androidx-test-ext" }
androidx-test-runner = { group = "androidx.test", name = "runner", version.ref = "androidx-test-runner" }

# Lifecycle
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }

# Room
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Koin
koin-core = { group = "io.insert-koin", name = "koin-core", version.ref = "koin" }
koin-android = { group = "io.insert-koin", name = "koin-android", version.ref = "koin" }
koin-compose = { group = "io.insert-koin", name = "koin-compose", version.ref = "koin" }

# Networking
okhttp-core = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
jsoup = { group = "org.jsoup", name = "jsoup", version.ref = "jsoup" }

# Image Loading
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }

# Serialization
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinx-serialization" }

# Coroutines
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }

# Unit Testing (JUnit5 / Jupiter)
junit5-api = { group = "org.junit.jupiter", name = "junit-jupiter-api", version.ref = "junit5" }
junit5-engine = { group = "org.junit.jupiter", name = "junit-jupiter-engine", version.ref = "junit5" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
kotest-assertions-core = { group = "io.kotest", name = "kotest-assertions-core", version.ref = "kotest-assertions" }
mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "mockwebserver" }
coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

# DataStore
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version = "1.1.1" }

# WorkManager
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version = "2.10.0" }
koin-androidx-workmanager = { group = "io.insert-koin", name = "koin-androidx-workmanager", version.ref = "koin" }

# Debug
leakcanary = { group = "com.squareup.leakcanary", name = "leakcanary-android", version = "2.14" }

[bundles]
compose = ["compose-ui", "compose-ui-graphics", "compose-ui-tooling-preview", "compose-material3", "compose-material-icons"]
compose-ui-test = ["compose-ui-test-junit4", "compose-ui-test-manifest"]
android-instrumentation-test = ["androidx-test-ext-junit", "androidx-test-runner", "compose-ui-test-junit4"]
room = ["room-runtime", "room-ktx"]
koin = ["koin-core", "koin-android", "koin-compose"]
unit-test = ["junit5-api", "junit5-engine", "mockk", "turbine", "kotest-assertions-core", "coroutines-test"]
```

### Gradle Plugins (app/build.gradle.kts)
```kotlin
plugins {
    id("com.android.application") version "8.7.3"
    kotlin("android") version "2.0.21"
    kotlin("plugin.compose") version "2.0.21"          // Kotlin 2.0+ compose compiler
    kotlin("plugin.serialization") version "2.0.21"
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
    id("de.mannodermaus.android-junit5") version "1.11.4.0"  // JUnit5 on Android
    // Note: junit5-ktx is a library dependency, not a plugin — add in dependencies {} block if needed
}
```

### Build Configuration Checklist
- Kotlin 2.0+: use `kotlin("plugin.compose")` plugin (not the separate Compose compiler Gradle plugin)
- Room: use KSP (`ksp(libs.room.compiler)`), not KAPT
- kotlinx.serialization: add `kotlin("plugin.serialization")`
- JUnit5 unit tests: use `@Test` from `org.junit.jupiter.api` in `src/test/`
- Compose UI tests: use `@Test` from `org.junit` (JUnit4) in `src/androidTest/` with `createComposeRule()`
- The `de.mannodermaus.android-junit5` plugin makes Jupiter work in `test/` while keeping JUnit4 rule support for `androidTest/`. Do NOT use Jupiter annotations in `androidTest/`.
- `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` in `defaultConfig` for instrumented UI tests
- `testOptions { unitTests.all { useJUnitPlatform() } }` for unit test module
- `proguard-rules.pro`: must contain `-keepattributes *Annotation*`, `-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }`, and keep rules for all `@Serializable` classes
- `buildFeatures.buildConfig = true` for version info in Settings
- `room.schemaLocation` configured from v1 for future AutoMigration support (exportSchema = true)
- LeakCanary: add `debugImplementation(libs.leakcanary)` in dependencies — never ship to release builds

---

## 7. Testing Strategy

### Dual-Track Testing Model (Resolved)

| Test Type | Runtime | Framework | Location |
|-----------|---------|-----------|----------|
| Unit tests (ViewModel, Repository, Scraper, Backup) | JUnit5 (Jupiter) | Jupiter API + MockK + Turbine + Kotest assertions | `src/test/` |
| Compose UI tests (screen rendering, user interaction) | JUnit4 | `createComposeRule()` + `ComposeTestRule` | `src/androidTest/` |
| Scraper integration | JUnit5 (Jupiter) | OkHttp MockWebServer | `src/test/` |

Both tracks coexist via the `de.mannodermaus.android-junit5` plugin. The plugin registers Jupiter as the test engine for `src/test/` while preserving JUnit4 rule infrastructure for `src/androidTest/`. The `androidTest/` source set uses JUnit4's `@RunWith(AndroidJUnit4::class)` runner.

### Unit Tests (Required)
| Class | What to Test | Framework |
|-------|-------------|-----------|
| MediaRepositoryImpl | CRUD, atomic increment/decrement bounds, @Transaction replaceAll, normalize enforcement, duplicate URL detection | JUnit5 + MockK (mock DAO) + Turbine (test Flow) |
| BackupProcessor | Serialize/deserialize round-trip, merge logic, schemaVersion defaults to 1, case-insensitive enums, invalid JSON, empty list | JUnit5 |
| MetadataScraper | URL validation, successful scrape, various error responses (timeout, 404, empty body, malformed HTML) | JUnit5 + MockWebServer |
| HomeViewModel | Filtering, increment/decrement events, state mapping, one-shot events via Channel | JUnit5 + MockK + Turbine |
| UnifiedAddEditViewModel | Form validation (both add + edit modes), scrape flow states, alt title swap, rating, cover download, discard dialog trigger, delete | JUnit5 + MockK + Turbine |

### Compose UI Tests (Instrumented, Nice to Have)
- Compose UI tests for key flows: add item form validation, edit item save, filter list interaction, sort interaction
- Use `createComposeRule()` + `ComposeTestRule` in `src/androidTest/`
- Run with JUnit4 `@RunWith(AndroidJUnit4::class)`
- Test files: `HomeScreenTest.kt`, `UnifiedAddEditScreenTest.kt`, `SettingsScreenTest.kt`

### AI Verification Checklist
- Tests are deterministic (no flakiness) — use `TestCoroutineDispatcher` or `StandardTestDispatcher` for coroutine-based tests
- Turbine tests have timeout to prevent hanging (`turbineScope { ... }` has built-in timeout)
- MockK verifies interactions correctly (`verify { ... }`)
- `createComposeRule()` tests are in `src/androidTest/` (not `src/test/`) and use JUnit4 annotations
- MockWebServer is started/stopped in `@BeforeEach`/`@AfterEach` to isolate test state
- Backup tests test with actual old-format JSON strings (missing schemaVersion, different casing)

---

## 8. Common Mistakes & Anti-Patterns — THE AI MUST AVOID

1. **Don't put Android Context in ViewModel**. ViewModel should be pure. Use `Application` context via `AndroidViewModel` only if absolutely necessary (e.g., system services), but prefer injecting dependencies.

2. **Don't use `GlobalScope`**. Ever. Use `viewModelScope` in ViewModels, `lifecycleScope` in Compose, or create a scoped coroutine.

3. **Don't call Room on main thread**. Room enforces this by default. Only `Flow` queries are safe to observe on main thread (Room switches to background internally).

4. **Don't use `StateFlow` with initial value for one-shot events** (navigation, toasts). Use `Channel<UiEvent>` with `receiveAsFlow()` or `SharedFlow` with `replay = 0`.

5. **Don't add debounce/serialize rapid increment/decrement**. The DAO uses atomic SQL `UPDATE` — no race condition exists. Debounce would lose legitimate taps.

6. **Don't hardcode strings**. Every user-facing string goes in `strings.xml`. Use `stringResource()` in Compose.

7. **Don't use `remember` for state that should survive config changes**. Use `collectAsStateWithLifecycle()` with a ViewModel Flow, or `rememberSaveable` for simple UI state.

8. **Don't use `mutableStateOf` in the ViewModel outside of `StateFlow`**. ViewModels should expose `StateFlow<UiState>`, not mutable state objects.

9. **Don't ignore ProGuard/R8**. The `proguard-rules.pro` must exist with kotlinx.serialization keep rules.

10. **Don't nest `NavHost` unnecessarily**. Single-level navigation is fine for this app (5 screens + dialogs).

11. **Don't use `by lazy` or `lateinit var` in ViewModels for observable state**. Use `MutableStateFlow` initialized in `init`.

12. **Don't use `requireActivity()` or `LocalContext.current` in ViewModel code**.

13. **Don't chain Flows without cancellation handling**. If you `flatMapLatest` in a ViewModel, ensure the upstream Flow is properly scoped to `viewModelScope`.

14. **Don't assume `System.currentTimeMillis()` is monotonic**. It can go backward if the user changes system time. For ordering, this is acceptable for v1. Document the behavior.

15. **Don't use `@OptIn` for experimental APIs without understanding the risk**.

16. **Don't create memory leaks with Coil**. Image loading is scoped to the Composable lifecycle by default with `AsyncImage`.

17. **Don't ignore the Android back button**. Navigation Compose handles it automatically.

18. **Don't use `LaunchedEffect` with infinite loop** (e.g., `while(true)` without delay). Use `snapshotFlow` or periodic `delay()`.

19. **Don't use `@Composable` functions that do side effects** (network calls, DB writes) directly. Trigger from events.

20. **Don't use JUnit5 annotations in `src/androidTest/`**. Compose UI tests run under JUnit4. Jupiter annotations will be ignored.

21. **Don't use `@TypeConverters` for enums without also testing the edge case**. Store enums as uppercase strings directly. Don't over-engineer with TypeConverters until you need a second column type.

22. **Don't call `Room.databaseBuilder().build()` and assume DB is healthy**. Corruption surfaces on first query. Wrap the first DAO call.

23. **Don't blindly upgrade `og:image` from `http://` to `https://`** or add a HEAD check. Keep the URL as-scraped. Coil handles http images fine. HEAD checks add latency and many servers reject them.

24. **Don't reject backup files missing `schemaVersion`**. Default to version 1 so old backups load correctly.

---

## 9. Pre-Implementation Q&A (Verified Answers)

These questions have been audited and resolved. The answers below are definitive:

1. **Gradle 8.7.3 vs AGP 8.7.3**: NOT compatible. AGP 8.7.x requires Gradle 8.9+. Use **Gradle 8.10.2** (or latest 8.x).
2. **KSP 2.0.21-1.0.28**: Correct. KSP versions are strictly coupled to Kotlin compiler. `-1.0.28` is the exact release for Kotlin `2.0.21`.
3. **android-junit5 1.11.4.0 + AGP 8.7.3**: Yes, the `1.11.x` series works with AGP 8.x.
4. **Coil 3.x network dependency**: Yes, `coil-network-okhttp` is required separately — Coil 3.0 decoupled networking from the core library.
5. **material3 only or material2 too?**: Strictly **material3 only**. Mixing bloats APK, fragments theming, and causes conflicting typography/color systems.
6. **Type-safe nav args?**: Yes. Navigation 2.8.x makes type-safe navigation via Kotlin Serialization the official standard. Use `@Serializable` route classes.
7. **Room WAL mode?**: Yes. `JournalMode.WRITE_AHEAD_LOGGING` allows concurrent reads via Flow while writes are in progress.
8. **Minimum OkHttp version**: **4.12.0** — compatible with both Coil 3.x and Jsoup without conflict.
9. **LeakCanary in debug?**: Yes. In an offline-first app, the biggest crash risks are memory leaks from un-scoped coroutines or retained Compose contexts. Add as `debugImplementation`.
10. **App launcher icons**: Android Adaptive Icons (`<adaptive-icon>`) in `mipmap-anydpi-v26`. Pure XML vector drawables for both foreground and background. No PNGs.
11. **No browser installed**: Wrap `context.startActivity()` in try/catch for `ActivityNotFoundException`. Show Toast: "No web browser found."
12. **Merge order**: Local first, imported second. A `LinkedHashMap` keyed by UUID naturally appends new imported items after existing local ones, preserving deterministic order before DB re-sorts by `lastUpdated`.
13. **coil-network-okhttp + OkHttp 4.12.0**: Yes, compatible. Coil 3.x supports OkHttp 4.x and 5.x seamlessly.
14. **android-junit5 vs compose/KSP plugins**: No conflict. KSP generates code, Compose plugin transforms IR, JUnit5 hooks testing runtime — different build phases.
15. **@SerialName for backup JSON**: **No**. The backup format uses `"id"`, not `"_id"`. Keep the Kotlin property as `val id: String` without renaming.

---

---

## Appendix B: File Manifest

The final project should contain these files (and only these):

```
app/
├── build.gradle.kts
├── proguard-rules.pro
├── src/
│   ├── main/
│   │   ├── AndroidManifest.xml
│   │   ├── kotlin/app/lazydex/
│   │   │   ├── LazyDexApp.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── data/
│   │   │   │   ├── local/
│   │   │   │   │   ├── LazyDexDatabase.kt
│   │   │   │   │   ├── entity/MediaItemEntity.kt
│   │   │   │   │   ├── dao/MediaItemDao.kt
│   │   │   │   │   ├── converter/Converters.kt
│   │   │   │   └── repository/MediaRepositoryImpl.kt
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   │   ├── MediaItem.kt
│   │   │   │   │   ├── MediaCategory.kt
│   │   │   │   │   └── UserStatus.kt
│   │   │   │   └── repository/MediaRepository.kt
│   │   │   ├── ui/
│   │   │   │   ├── theme/
│   │   │   │   │   ├── Theme.kt
│   │   │   │   │   ├── Color.kt
│   │   │   │   │   └── Type.kt
│   │   │   │   ├── navigation/NavGraph.kt
│   │   │   │   ├── home/
│   │   │   │   │   ├── HomeScreen.kt
│   │   │   │   │   └── HomeViewModel.kt
│   │   │   │   ├── addedit/
│   │   │   │   │   ├── UnifiedAddEditScreen.kt
│   │   │   │   │   └── UnifiedAddEditViewModel.kt
│   │   │   │   ├── settings/
│   │   │   │   │   ├── SettingsScreen.kt
│   │   │   │   │   └── SettingsViewModel.kt
│   │   │   │   └── components/
│   │   │   │       ├── MediaCard.kt
│   │   │   │       ├── FilterBottomSheet.kt
│   │   │   │       ├── SortBottomSheet.kt
│   │   │   │       ├── CategoryBadge.kt
│   │   │   │       ├── StatusBadge.kt
│   │   │   │       ├── StarRating.kt
│   │   │   │       ├── AltTitleEditor.kt
│   │   │   │       ├── CoverImage.kt
│   │   │   │       └── EmptyState.kt
│   │   │   ├── di/Modules.kt
│   │   │   ├── scraper/MetadataScraper.kt
│   │   │   ├── util/UrlNormalizer.kt
│   │   │   ├── backup/
│   │   │   │   ├── BackupManager.kt
│   │   │   │   └── BackupProcessor.kt
│   │   └── res/
│   │       ├── values/strings.xml
│   │       ├── values/themes.xml
│   │       ├── drawable/ (app icon, etc.)
│   │       └── mipmap-*/ (launcher icons)
│   ├── test/kotlin/app/lazydex/
│   │   ├── data/repository/MediaRepositoryImplTest.kt
│   │   ├── scraper/MetadataScraperTest.kt
│   │   ├── backup/BackupProcessorTest.kt
│   │   ├── ui/home/HomeViewModelTest.kt
│   │   └── ui/addedit/UnifiedAddEditViewModelTest.kt
│   └── androidTest/kotlin/app/lazydex/
│       └── ui/ (Compose UI tests)
│           ├── HomeScreenTest.kt
│           ├── UnifiedAddEditScreenTest.kt
│           └── SettingsScreenTest.kt
gradle/
├── libs.versions.toml
└── wrapper/
    ├── gradle-wrapper.jar
    └── gradle-wrapper.properties
build.gradle.kts
settings.gradle.kts
gradle.properties
gradlew
gradlew.bat
.gitignore
```

**Note**: The `schemas/` directory is tracked from v1 (`exportSchema = true`) to enable AutoMigration for future Room migrations. Add `room.schemaLocation` in `app/build.gradle.kts`.

---

> **Final Instruction to AI**: Do not proceed until you have audited every section above. If you find ambiguities, contradictions, missing edge cases, or design flaws, flag them and propose corrections. This is a review-only document — do NOT build, generate, or write any code. Be merciless and keep finding more issues.

---

# LazyDex Implementation Plan — Audit & Validation Report

An exhaustive audit of the [implementation plan](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/.agents/implementation_plan.md) was performed to detect logical inconsistencies, runtime pitfalls, compile errors, and potential AI hallucinations. Below is the list of issues identified, along with explanations and concrete fixes.

---

## 1. Dependency & System Architecture

### 1.1 Coil 3 Custom ImageLoader Ignored by `AsyncImage`
* **Issue**: The plan defines a custom `ImageLoader` in the Koin DI module (with a 250MB cache and ignore-cache-headers config) but fails to register it as Coil's global singleton. By default, Coil's `AsyncImage` retrieves the image loader using `SingletonImageLoader.get(context)`. Without global registration, `AsyncImage` will fallback to its default image loader, completely ignoring the custom cache settings.
* **Reason**: Coil 3 requires setting the custom image loader as the singleton or implementing `SingletonImageLoader.Factory` in the `Application` class.
* **Fix**: Have `LazyDexApp` implement `SingletonImageLoader.Factory` and retrieve the Koin-injected `ImageLoader`:
  ```kotlin
  class LazyDexApp : Application(), SingletonImageLoader.Factory {
      override fun newImageLoader(context: PlatformContext): ImageLoader {
          return get<ImageLoader>() // Injected from Koin
      }
  }
  ```

### 1.2 CPU-Bound Suspend Functions Run on Main Thread
* **Issue**: `BackupProcessor.serialize()` and `BackupProcessor.deserialize()` are marked as `suspend` but call synchronous, CPU-heavy functions (`Json.encodeToString` and `Json.decodeFromString`) without wrapping them in `withContext(Dispatchers.Default)`.
* **Reason**: Marking a function as `suspend` does not automatically move it to a background thread. If launched from a standard `viewModelScope` (which runs on the Main thread), they will run on the main thread and block the UI.
* **Fix**: Force the execution onto `Dispatchers.Default`:
  ```kotlin
  object BackupProcessor {
      suspend fun serialize(items: List<MediaItem>): String = withContext(Dispatchers.Default) {
          Json.encodeToString(BackupEnvelope(items = items))
      }

      suspend fun deserialize(json: String): List<MediaItem> = withContext(Dispatchers.Default) {
          Json.decodeFromString<BackupEnvelope>(json).items
      }
  }
  ```

---

## 2. Backup & Serialization

### 2.1 Deserialization Failures / Crash on Missing Fields
* **Issue**: The plan directly deserializes JSON into `List<MediaItem>` (where fields like `currentProgress: Int`, `id: String`, `lastUpdated: Long` are non-nullable and primitive). If a backup is missing any of these fields, kotlinx.serialization will throw a `SerializationException` and fail the entire import. This violates the rules stating that a missing `id` should generate a new UUID and a missing `lastUpdated` should default to `0L`.
* **Reason**: Strict domain models cannot handle missing or malformed primitive fields directly during JSON decoding without throwing exceptions.
* **Fix**: Define a separate `@Serializable` Data Transfer Object (DTO) for backups, decode into the DTO, and then manually map and validate/sanitize into the domain model:
  ```kotlin
  @Serializable
  data class MediaItemBackupDto(
      val id: String? = null,
      val category: String? = null,
      val title: String? = null,
      val sourceUrl: String? = null,
      val coverImageUrl: String? = null,
      val currentProgress: Int? = null,
      val totalItems: Int? = null,
      val userStatus: String? = null,
      val lastUpdated: Long? = null
  )
  ```

### 2.2 Case-Sensitive Enum Deserialization Crashes
* **Issue**: The plan states that `category` (stored as `Novel`, `novel`, etc.) and `userStatus` (stored as `Reading`, etc.) should be parsed case-insensitively. However, kotlinx.serialization's default companion `Json` instance is strictly case-sensitive for enums and will crash the import on any non-uppercase enum values.
* **Reason**: Standard JSON decoding does not apply case-insensitivity or ignore unknown keys unless explicitly configured.
* **Fix**: Create a configured `Json` instance in `BackupProcessor`:
  ```kotlin
  private val backupJson = Json {
      ignoreUnknownKeys = true
      decodeEnumsCaseInsensitive = true
  }
  // Use backupJson.decodeFromString(...) instead of global Json.decodeFromString
  ```

### 2.3 Merge Timestamp Contradiction (Data Loss Risk)
* **Issue**: In the description of the merge logic, the plan states that imported items should preserve their historical timestamps, using `System.currentTimeMillis() - index` only when they lack one. In the checklist, it says the merge must overwrite pure-additions with `System.currentTimeMillis()` (~ now) and verify that item C gets `≈ now`.
* **Reason**: Overwriting pure-additions with the current timestamp will wipe out the historical timeline of all imported items during a fresh install/restore.
* **Fix**: The checklist test must be updated to expect historical timestamps to be preserved for pure-additions if they exist in the backup, and only use staggered current time if the backup lacked the field.

---

## 3. Data & DAO Layer

### 3.1 Room SQLite Parameter Limits Hallucination
* **Issue**: The plan states that Room list operations (like `upsertAll(items)`) will crash SQLite on API <= 30 if the list has more than 100 items (because 500 items with 9 columns would produce 4,500 bound variables, exceeding the 999 SQLite limit). It mandates chunking inserts in batches of 100.
* **Reason**: This is a hallucination of Room's internals. Room's generated insert/upsert helper compiles to a loop that binds a *single* entity at a time and executes the prepared statement once per row. It never binds all parameters of all rows at once.
* **Fix**: The chunking logic is unnecessary. A direct `upsertAll(items)` call is fully supported and won't crash on parameter limits.

### 3.2 Redundant & Over-engineered Database Health Check
* **Issue**: The plan uses `ensureDbHealthy()` executing `SELECT 1` on every Flow start to detect DB corruption.
* **Reason**: Room opens the database lazily. If the database is corrupt, Room's open helper will fail and throw a `SQLiteDatabaseCorruptException` during the actual query execution (e.g. `observeAll()`). Executing a separate `SELECT 1` query first adds unnecessary JNI and database overhead on every subscription.
* **Fix**: Remove `ensureDbHealthy()` entirely. Catch standard `SQLException` or `SQLiteDatabaseCorruptException` during regular query subscriptions or at the application entry point.

### 3.3 MediaItem.normalize() URL Inconsistency
* **Issue**: `MediaItem.normalize()` only trims the `sourceUrl` (`sourceUrl = sourceUrl.trim()`), but duplicate detection is designed to match normalized URLs (lowercase host/scheme, stripped trailing slashes, stripped fragments).
* **Reason**: Because the URL stored in the DB is not fully normalized, the SQLite UNIQUE index on `sourceUrl` will not prevent duplicates that differ slightly (e.g., `https://site.com/item/` vs `https://site.com/item`).
* **Fix**: Make `MediaItem.normalize()` call `UrlNormalizer.normalize(sourceUrl)` before copy:
  ```kotlin
  fun normalize(): MediaItem {
      // ...
      return copy(
          // ...
          sourceUrl = UrlNormalizer.normalize(sourceUrl),
          // ...
      )
  }
  ```

### 3.4 UNIQUE Index + Empty String Duplicates (sourceUrl Nullable)
* **Issue**: `sourceUrl` is declared as non-nullable `String` with a default of `""` (empty string). SQLite's UNIQUE index treats empty strings as equal — adding a second item without a URL throws `SQLiteConstraintException`.
* **Reason**: SQLite considers all empty-string values equal under a UNIQUE constraint. Multiple NULL values are always permitted.
* **Fix**: Change `sourceUrl` to nullable `String?` in both the domain model and the entity. The normalizer strips blanks to null; the entity column allows nulls:
  ```kotlin
  // domain
  val sourceUrl: String?,
  
  // entity  
  val sourceUrl: String?,
  
  // normalize()
  val normalizedUrl = sourceUrl?.takeIf { it.isNotBlank() }?.let { UrlNormalizer.normalize(it) }
  return copy(sourceUrl = normalizedUrl, ...)
  ```

### 3.5 Bulk Import Exception Handling in replaceAll
* **Issue**: `replaceAll()` in the repository does not catch `SQLiteConstraintException` or other database errors. If the imported list contains duplicates (same normalized URL), the transaction throws and crashes the coroutine.
* **Reason**: The DAO's `@Transaction replaceAll()` runs `deleteAll()` + `upsertAll()` atomically. A constraint violation in `upsertAll` rolls back the transaction and surfaces an unhandled exception to the ViewModel.
* **Fix**: Wrap `dao.replaceAll()` in a try/catch in the repository and throw a domain-level `ImportFailedException`:
  ```kotlin
  override suspend fun replaceAll(items: List<MediaItem>) = withContext(Dispatchers.IO) {
      try {
          val entities = items.map { it.normalize().toEntity() }
          dao.replaceAll(entities)
      } catch (e: SQLiteConstraintException) {
          throw ImportFailedException("Import failed: duplicate source URL detected", e)
      } catch (e: Exception) {
          throw ImportFailedException("Import failed due to database error", e)
      }
  }
  ```

---

## 4. UI & State Management

### 4.1 HomeViewModel State Mapping / Filter Selection Reset
* **Issue**: The flow mapping in `HomeViewModel.uiState` is:
  ```kotlin
  val uiState: StateFlow<HomeUiState> = selectedCategoryFlow
      .flatMapLatest { category -> ... }
      .map { items -> HomeUiState(items = items, isLoading = false) }
  ```
  This map is placed outside of `flatMapLatest`, meaning the `category` variable is out of scope. Because it is omitted, `selectedCategory` in `HomeUiState` falls back to its default value (`null` = "All").
* **Reason**: Whenever the list changes or a filter is clicked, the UI state will tell Compose that the active filter is "All", visually resetting the filter chip highlights.
* **Fix**: Map the items to `HomeUiState` inside `flatMapLatest` to preserve the active category:
  ```kotlin
  val uiState: StateFlow<HomeUiState> = selectedCategoryFlow
      .flatMapLatest { category ->
          val flow = if (category == null) repository.observeAll()
                     else repository.observeByCategory(category)
          flow.map { items ->
              HomeUiState(items = items, selectedCategory = category, isLoading = false)
          }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())
  ```

### 4.2 Missing `observeById` Implementation in Repository
* **Issue**: The `MediaRepository` interface defines `observeById(id: String): Flow<MediaItem?>`, but `MediaRepositoryImpl` (lines 925-989) fails to implement this method entirely.
* **Reason**: The developer will experience a compile error for missing interface implementations.
* **Fix**: Implement the method in `MediaRepositoryImpl`:
  ```kotlin
  override fun observeById(id: String): Flow<MediaItem?> {
      return dao.observeById(id)
          .map { entity ->
              try {
                  entity?.toDomain()
              } catch (e: Exception) {
                  null
              }
          }
          .distinctUntilChanged()
          .flowOn(Dispatchers.Default)
  }
  ```

### 4.3 TotalItems Input Clamping / Empty Value Bug
* **Issue**: The plan suggests safe Int parsing for form inputs: `text.toLongOrNull()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0`.
* **Reason**: While this works for `currentProgress`, if applied to `totalItems` (which is optional and can be `null`), an empty input will parse to `0` instead of `null`. If `totalItems` becomes `0`, the progress (if > 0) will be incorrectly clamped to `0`.
* **Fix**: Allow the parsed result to be nullable for `totalItems`:
  ```kotlin
  val total = text.takeIf { it.isNotBlank() }
      ?.toLongOrNull()
      ?.coerceAtMost(Int.MAX_VALUE.toLong())
      ?.toInt()
  ```

### 4.4 DetailScreen Duplicate-URL Error Path
* **Issue**: If a user edits the `sourceUrl` in DetailScreen to match an existing item's URL, the repository's `update()` triggers a `SQLiteConstraintException` from the UNIQUE index. The ViewModel has no handling for this, so the exception crashes the coroutine.
* **Reason**: The repository's `DuplicateUrlException` was defined in the interface but the DetailScreen implementation did not catch it.
* **Fix**: Add error handling in `DetailViewModel.saveChanges()` and surface an inline error message:
  ```kotlin
  fun saveChanges(updatedItem: MediaItem) {
      viewModelScope.launch {
          try {
              _errorMessage.value = null
              repository.update(updatedItem)
          } catch (e: DuplicateUrlException) {
              _errorMessage.value = "This URL is already tracked by another item."
          } catch (e: Exception) {
              _errorMessage.value = "An error occurred while saving."
          }
      }
  }
  ```

---

## 5. Scraping & Networking

### 5.1 IP/SSRF Blocklist is Insufficient — DNS-Level Protection Required
* **Issue**: The plan's URL validation rules reject IP-based URL strings (`https://192.168.x.x`, `https://10.x.x.x`) via pattern matching. This misses loopback addresses (127.x.x.x), secondary private ranges (172.16.x.x), link-local (169.254.x.x), IPv6 local addresses (`::1`, `fe80::`), and is completely vulnerable to DNS rebinding attacks where a public domain resolves to a local IP.
* **Reason**: String-matching IP addresses in URLs is a fragile denylist approach that cannot cover all local address variants and cannot detect DNS rebinding.
* **Fix**: Implement a custom OkHttp `Dns` resolver as the sole SSRF defense. This catches ALL private/local network addresses at the DNS resolution layer (before any HTTP request is made), including rebinding:
  ```kotlin
  class SafeDns : Dns {
      override fun lookup(hostname: String): List<InetAddress> {
          val addresses = Dns.SYSTEM.lookup(hostname)
          for (address in addresses) {
              if (address.isLoopbackAddress || 
                  address.isSiteLocalAddress || 
                  address.isLinkLocalAddress || 
                  address.isAnyLocalAddress) {
                  throw IOException("Access to local/private network addresses is blocked")
              }
          }
          return addresses
      }
  }
  ```

### 5.2 CPU-Bound Parsing Executing on OkHttp Thread
* **Issue**: In `MetadataScraper.scrape()`, the plan implements `suspendCancellableCoroutine` and executes `Jsoup.parse(boundedStream, ...)` directly inside the OkHttp callback thread (`onResponse`).
* **Reason**: This directly contradicts the plan's own rule: *"Jsoup.parse() is CPU-bound and MUST run on Dispatchers.Default, NOT on OkHttp's IO dispatcher pool."*
* **Fix**: Perform the scrape using `withContext` and a synchronous call with custom cancellation listener, then run `Jsoup.parse` on `Dispatchers.Default`:
  ```kotlin
  suspend fun scrape(url: String): Result<ScrapedMetadata> = withContext(Dispatchers.IO) {
      try {
          val request = Request.Builder().url(url).build()
          val call = okHttpClient.newCall(request)
          
          currentCoroutineContext()[Job]?.invokeOnCompletion {
              call.cancel()
          }

          call.execute().use { response ->
              if (!response.isSuccessful) throw IOException("Unexpected code $response")
              val body = response.body ?: throw IOException("Empty body")
              
              val limitStream = BoundedInputStream(body.byteStream(), MAX_SCRAPE_BYTES)
              val doc = withContext(Dispatchers.Default) {
                  Jsoup.parse(limitStream, "UTF-8", url)
              }
              
              val title = extractTitle(doc)
              val imageUrl = extractImageUrl(doc)
              Result.success(ScrapedMetadata(title, imageUrl))
          }
      } catch (e: Exception) {
          Result.failure(e)
      }
  }
  ```

---

## 6. Flow & Reactive Error Handling

### 6.1 Flow Error Handling for DB Corruption — Missing `.catch` Operator
* **Issue**: The reactive `observe*()` methods in `MediaRepositoryImpl` use per-row `try/catch` (or `runCatching`) inside `.map {}` to handle domain mapping failures (e.g., invalid enum values from a corrupted row). However, this does NOT catch upstream database query exceptions such as `SQLiteDatabaseCorruptException`, which occurs when Room attempts to open or query a corrupt database file. These exceptions propagate through the Flow chain and crash the collector (the ViewModel/UI).
* **Reason**: `SQLiteDatabaseCorruptException` is thrown by Room's internal query execution before the `.map {}` operator ever runs. The per-row try/catch is inside `.map {}` and only catches exceptions during row-to-domain conversion. Upstream FlowSource errors bypass it entirely.
* **Fix**: Add the `.catch` operator **after** `flowOn(Dispatchers.Default)` to act as the final safety net for all upstream errors, emitting a safe fallback value instead of crashing:
  ```kotlin
  override fun observeAll(): Flow<List<MediaItem>> {
      return dao.observeAll()
          .map { entities ->
              entities.mapNotNull { entity ->
                  runCatching { entity.toDomain() }.getOrNull()
              }
          }
          .distinctUntilChanged()
          .flowOn(Dispatchers.Default)
          .catch { e ->
              Log.e("MediaRepository", "DB query failed (possible corruption)", e)
              emit(emptyList())
          }
  }
  ```
   The `.catch` operator catches ALL exceptions upstream of it in the chain — both mapping failures (if `runCatching` were removed) AND database query failures.

---

## 7. Continued Audit — Additional Issues Found

### 7.1 Package Name Contradiction
* **Issue**: Section 2 declares `Package: app.lazydex` ("matches Mihon's `app.mihon` pattern"), and Section 3's project tree root is `app.lazydex/`. But **Appendix B's File Manifest** puts every source set under `kotlin/com/rockyxwall/lazydex/` (main, test, and androidTest). Two different, incompatible package names.
* **Fix**: Standardize on `app.lazydex` and correct all three Appendix B paths from `kotlin/com/rockyxwall/lazydex/...` to `kotlin/app/lazydex/...`.

### 7.2 Auto-Backup Feature Has No Dependency, No Class, No Wiring
* **Issue**: Auto-backup (Core Feature #7, SettingsScreen "Backup" section, Phase 9 checklist "auto-backup schedule works") is described only as "Optional scheduled backup via WorkManager." But:
  - `libs.versions.toml` has no `androidx-work`/`work-runtime-ktx` entry.
  - No `AutoBackupWorker.kt` exists in the Section 3 architecture tree or Appendix B manifest.
  - `di/Modules.kt` has no Worker/WorkManager registration.
  - No enqueue policy (`KEEP`/`REPLACE`), constraints, or interval logic is specified.
* **Fix**: Add `androidx-work-runtime-ktx` to the version catalog, create `backup/AutoBackupWorker.kt` (a `CoroutineWorker` that calls `repository.getAll()` → `BackupProcessor.serialize()` → writes to app-internal storage with a timestamped filename), register it via Koin's `WorkManagerFactory` (or a manual `WorkerFactory`), and add both files to the manifest.

### 7.3 Stale Enum Value Comments in Architecture Tree
* **Issue**: Section 3's tree comments say `MediaCategory.kt # NOVEL, ANIME, MANGA, GAME` (4 values) and `UserStatus.kt # READING, COMPLETED` (2 values). The actual Section 4.1 enums have 6 categories (adds MOVIE, TV) and 7 statuses.
* **Fix**: Update the tree comments to list all values, or drop the inline value lists entirely so they can't drift out of sync with the canonical definition.

### 7.4 Coil's Image Loader Has No SSRF Protection
* **Issue**: `MetadataScraper` uses a custom `SafeDns` to block private/loopback/link-local addresses and rebinding (Section 4.5). But the Phase 3 Koin `ImageLoader` for Coil (`AsyncImage` cover images) is built with a plain OkHttp backend — no `SafeDns`. `coverImageUrl` validation (Section 4.7 AddItemScreen) only requires "valid URL (http/https)," with none of `sourceUrl`'s IP/scheme denylist rules. A user-entered or scraped `og:image` pointing at an internal address (e.g. a cloud metadata endpoint) will be fetched by Coil unguarded.
* **Fix**: Build one shared `OkHttpClient` with `SafeDns` in the DI network module, inject it into both `MetadataScraper` and Coil's `OkHttpNetworkFetcherFactory(callFactory = { safeClient })`, and tighten `coverImageUrl` validation to match `sourceUrl`'s scheme rules.

### 7.5 Duplicate/Shadowed `BackupEnvelopeDto`
* **Issue**: Section 4.6 declares `private data class BackupEnvelopeDto` at file top level (lines 632-637), then declares **another** `private data class BackupEnvelopeDto` nested inside `object BackupProcessor` (lines 658-662) with the identical name. The nested one shadows the top-level one for `serialize()`/`deserialize()`, leaving the top-level declaration dead code and the "canonical" version ambiguous.
* **Fix**: Delete the nested duplicate inside `BackupProcessor`; keep the single top-level `BackupEnvelopeDto`.

### 7.6 `BackupProcessor.merge()` Has No Function Body — Won't Compile
* **Issue**: Inside `object BackupProcessor` (a concrete object, not an interface/abstract class), `merge()` is declared as:
  ```kotlin
  suspend fun merge(local: List<MediaItem>, imported: List<MediaItem>): List<MediaItem>
  ```
  with no `= ...` or `{ }` body (line 674). A concrete object cannot contain body-less function declarations — this is a hard compile error.
* **Fix**: Implement it per the plan's own merge rules (local wins ties, pure-imports keep historical timestamps, LinkedHashMap for deterministic order):
  ```kotlin
  suspend fun merge(local: List<MediaItem>, imported: List<MediaItem>): List<MediaItem> =
      withContext(Dispatchers.Default) {
          val localById = local.associateBy { it.id }
          val result = LinkedHashMap<String, MediaItem>()
          local.forEach { result[it.id] = it }
          imported.forEachIndexed { index, item ->
              val existingLocal = localById[item.id]
              when {
                  existingLocal == null -> {
                      val finalTime = if (item.lastUpdated > 0L) item.lastUpdated
                                      else System.currentTimeMillis() - index
                      result[item.id] = item.copy(lastUpdated = finalTime).normalize()
                  }
                  item.lastUpdated > existingLocal.lastUpdated -> result[item.id] = item.normalize()
              }
          }
          result.values.toList()
      }
  ```

### 7.7 `normalize()` Doesn't Enforce "Title Required"
* **Issue**: Section 4.4 states validation rules ("Title is required") apply to both Add and Edit, and stresses "the repository does NOT trust that ViewModel data is already valid" — but that guarantee is only actually implemented for progress/total clamping. `normalize()` only trims the title (line 231); it never rejects or defaults a blank one. A user can clear the title on DetailScreen and save, and `update() → normalize()` will silently persist `title = ""`.
* **Fix**: Either make `normalize()` fall back to a placeholder (e.g. `title.trim().ifBlank { "Untitled" }`), or have `DetailViewModel.saveChanges()` validate non-blank title client-side and surface an inline error before calling `repository.update()`, matching the pattern already used for duplicate-URL errors.

### 7.8 DetailViewModel Has No Event Channel for External Deletion
* **Issue**: The error-handling table (4.9) says "Navigation with deleted item → Catch in ViewModel, navigate back if item no longer exists," but `observeById(id)` transitioning to `null` is never wired to anything. HomeViewModel established a `Channel<HomeEvent>` pattern for one-shot navigation side effects (Section 4.7, Anti-Pattern #4); DetailViewModel has no equivalent.
* **Fix**: Add `Channel<DetailEvent>` to DetailViewModel, emit `DetailEvent.ItemDeletedExternally` when the observed item flips non-null → null, and have DetailScreen collect it to call `popBackStack()`.

### 7.9 AddItemViewModel Never Catches `DuplicateUrlException`
* **Issue**: Add-flow duplicate protection relies solely on the pre-submit `existsByUrl()` check (4.7), which is a TOCTOU race — a duplicate can still slip through by the time `repository.add()` runs (e.g. rapid double-tap before the button disables). DetailViewModel got a `try/catch` for this exact exception (Fix 4.4); AddItemViewModel's save path was never given the same treatment, so the race would crash the coroutine instead of showing a friendly error.
* **Fix**: Wrap `repository.add()` in AddItemViewModel with the same `catch (e: DuplicateUrlException)` pattern used in DetailViewModel.

---

## 8. Continued Audit — Additional Issues (Round 3)

### 8.1 `notes` Field Silently Dropped by Backup Export/Import (Data Loss)
* **Issue**: `MediaItem.notes` (4.1) and `MediaItemEntity.notes` both exist and are user-facing ("Notes field (optional)" in AddItemScreen, "Notes field (editable)" in DetailScreen). But `MediaItemBackupDto`, the JSON schema example, `MediaItem.toDto()`, and `MediaItemBackupDto.toDomain()` in 4.6 all omit `notes` entirely.
* **Reason**: Every export silently discards the user's notes, and every import resets `notes` back to `""` (the domain model's default) regardless of what was on disk before. This is real, silent user data loss on the most basic Export→Import round trip — worse than the timestamp bug already caught in 2.3/7.6, since there's no fallback recovery at all.
* **Fix**: Add `notes` to all four places:
  ```kotlin
  @Serializable
  private data class MediaItemBackupDto(
      // ...existing fields...
      val notes: String? = null
  )

  private fun MediaItem.toDto() = MediaItemBackupDto(
      // ...existing fields...
      notes = notes
  )

  private fun MediaItemBackupDto.toDomain(): MediaItem? {
      // ...
      return MediaItem(
          // ...existing fields...
          notes = notes?.trim() ?: ""
      ).normalize()
  }
  ```
  Also add `"notes": "..."` to the documented JSON schema, and include a round-trip test that specifically asserts `notes` survives serialize→deserialize.

### 8.2 Default Theme Contradicts Itself
* **Issue**: Core Features #9 states **"Dark theme (default)"**. Section 4.8 states **"Dark mode follows system setting by default"**, and the `LazyDexTheme` signature backs the system-following claim: `darkTheme: Boolean = isSystemInDarkTheme()`. These describe two different first-launch behaviors — a user on a light-mode device gets light theme under 4.8's rule, but dark theme under Core Features' rule.
* **Fix**: Pick one and make it explicit in code, not just prose. Recommended: introduce a persisted `ThemeMode { SYSTEM, LIGHT, DARK }` (DataStore/SharedPreferences-backed), initialize it to `DARK` on first launch (satisfying Core Features #9), and let "follow system" be an explicit option the user opts into via the toggle — rather than the unconfigured default.

### 8.3 UserStatus/Category "Adaptive Label" Design Is Incoherent, and DetailScreen's Status Editor Is Missing 2 of 7 Values
* **Issue**: Core Features #3 claims 5 conceptual statuses where "display label adapts to category," but 4.1's actual `UserStatus` enum hardcodes 7 separate values (READING, WATCHING, PLAYING as distinct entries, each with its own fixed `displayName` — nothing adapts). Worse, DetailScreen's spec (4.7) literally lists the status editor as **"dropdown/chips with all 5 statuses (Reading, Completed, On Hold, Dropped, Plan to)"** — omitting WATCHING and PLAYING outright. As written, an Anime or Game item can never be set to its correct in-progress status through the UI. Separately, the corrupted-import fallback in 4.6 (`userStatus` string doesn't match → default to `READING`) ignores category too, so a mangled Anime backup row silently becomes "Reading" instead of "Watching."
* **Fix**: Add a category-aware helper and use it everywhere a status list or default is needed:
  ```kotlin
  fun inProgressStatusFor(category: MediaCategory): UserStatus = when (category) {
      MediaCategory.NOVEL, MediaCategory.MANGA -> UserStatus.READING
      MediaCategory.ANIME, MediaCategory.TV, MediaCategory.MOVIE -> UserStatus.WATCHING
      MediaCategory.GAME -> UserStatus.PLAYING
  }

  fun availableStatuses(category: MediaCategory): List<UserStatus> = listOf(
      inProgressStatusFor(category),
      UserStatus.COMPLETED, UserStatus.ON_HOLD, UserStatus.DROPPED, UserStatus.PLAN_TO
  )
  ```
  Use `availableStatuses(category)` to populate DetailScreen/AddItemScreen status chips (fixing the missing options), and use `inProgressStatusFor(category)` as the fallback in `MediaItemBackupDto.toDomain()` instead of the hardcoded `READING`.

### 8.4 HomeScreen's Status Filter Chips Have No Backing Implementation
* **Issue**: 4.7 describes "Secondary filter chips (optional): status filter — [All] [Reading] [Completed] [On Hold] [Dropped] [Plan to]." But `HomeUiState` has no `selectedStatus` field, `HomeEvent` has no `SelectStatus` case, and the DAO (4.3) only offers `observeByCategory` — there's no query or repository method that filters by status, or by category+status together.
* **Fix**: Add a `selectedStatus: UserStatus?` to `HomeUiState`, a `SelectStatus` case to the input-action model, a DAO query (e.g. `observeByCategoryAndStatus`), and combine both filters in the ViewModel:
  ```kotlin
  combine(selectedCategoryFlow, selectedStatusFlow) { cat, status -> cat to status }
      .flatMapLatest { (cat, status) -> repository.observeFiltered(cat, status) }
  ```

### 8.5 `HomeEvent` Conflates Inbound Actions and Outbound One-Shot Events
* **Issue**: `HomeEvent` (4.7) is declared as one sealed interface containing both things the UI sends *to* the ViewModel (`Increment`, `Decrement`, `ToggleStatus`, `SelectCategory`, `DeleteItem`, `OpenDetail`) and the thing the ViewModel sends *out* to the UI via `Channel<HomeEvent>` — but the shown code only ever sends `OpenItem` through that channel. This contradicts the plan's own Anti-Pattern #4 guidance (one-shot events should be their own dedicated type) and leaves the other six variants as dead weight on the outbound `events: Flow<HomeEvent>` type.
* **Fix**: Split into two types — `HomeAction` (inbound, handled via ViewModel methods or a single `onAction()`) and `HomeUiEvent` (outbound one-shot, containing only `OpenItem` and similar navigation/toast events), each with its own `Channel`.

### 8.6 10MB Backup Ceiling Leaves No Headroom Over the Plan's Own Size Estimate
* **Issue**: 4.6's Deserialization Rules reject any backup file over 10MB. But Edge Case #8 in 4.10 already estimates a 10,000-item library produces a "~5-10MB" backup — before notes text is even counted (see 8.1). A real power user's own valid export, once notes are correctly included per the 8.1 fix, can easily exceed the reject threshold, making their own backup unimportable back into the same app.
* **Fix**: Raise the ceiling well past the estimated normal case (e.g. 50–100MB — it's plain JSON, no compression) or size the limit relative to available device storage rather than a fixed constant.

### 8.7 Auto-Backup Has No Retention Policy or Restore Path
* **Issue**: Even with the `AutoBackupWorker` added per Fix 7.2, nothing caps how many timestamped backup files accumulate in app-internal storage over time — daily backups run forever with no cleanup. Settings also only wires manual SAF import; there's no UI to browse or restore from an auto-backup file, so the feature can write backups but never read them back.
* **Fix**: Have the Worker delete files beyond a retention window (e.g. keep last 30) at the end of each run, and add a "Restore from auto-backup" list in Settings that feeds a selected file through the existing `BackupManager.import()` + merge/overwrite flow.

---

## 9. Continued Audit — Additional Issues (Round 4)

### 9.1 Scraper Size-Limit Technique Is Broken, Contradicts Its Own Code Sample, and Uses an Undeclared Dependency
* **Issue**: Section 4.5's prose mandates: *"Always enforce a hard byte limit at the Okio source level via `body.source().peek().readByteString(5 * 1024 * 1024)`."* But `Okio`'s `readByteString(byteCount)` reads **exactly** `byteCount` bytes and throws `EOFException` if the source has fewer — it is not a "read up to N bytes, then stop" cap. As written, this would throw on nearly every real HTML page (almost all are well under 5MB), breaking scraping entirely rather than protecting against oversized ones.
* Separately, the actual code sample in the same section abandons this technique and instead wraps `body.byteStream()` in `BoundedInputStream(...)` — a class from `org.apache.commons:commons-io`, which is **not present anywhere** in `libs.versions.toml`. The code also references `MAX_SCRAPE_BYTES`, which is never defined. So the plan proposes two different, mutually exclusive size-limiting mechanisms, and the one actually used in code won't compile.
* **Fix**: Pick one real mechanism and use it consistently. Recommended: a small `ForwardingSource` that counts bytes read and throws once a limit is exceeded, fed to Jsoup via an `InputStream` bridge — no extra dependency needed:
  ```kotlin
  private const val MAX_SCRAPE_BYTES = 5L * 1024 * 1024

  class SizeLimitedSource(delegate: Source, private val maxBytes: Long) : ForwardingSource(delegate) {
      private var totalRead = 0L
      override fun read(sink: Buffer, byteCount: Long): Long {
          val result = super.read(sink, byteCount)
          if (result != -1L) {
              totalRead += result
              if (totalRead > maxBytes) throw IOException("Response exceeded $maxBytes bytes")
          }
          return result
      }
  }
  // usage: Jsoup.parse(SizeLimitedSource(body.source(), MAX_SCRAPE_BYTES).buffer().inputStream(), "UTF-8", url)
  ```
  Drop the `BoundedInputStream` reference and the `peek().readByteString()` line entirely.

### 9.2 Auto-Backup Worker Will Crash: WorkManager's Default Init Races Koin Startup
* **Issue**: Fix 7.2 adds `AutoBackupWorker` (a `CoroutineWorker`) that needs the Koin-provided `MediaRepository`/`BackupProcessor`. By default, Android's `WorkManagerInitializer` (an `androidx.startup` `ContentProvider`) runs **before** `Application.onCreate()`. If `LazyDexApp.onCreate()` is where `startKoin()` runs, WorkManager may construct `AutoBackupWorker` via reflection before Koin has any modules loaded, throwing `ClosedScopeException` / `NoBeanDefFoundException` the first time the worker fires.
* **Reason**: This is unaddressed in Fix 7.2, which only specifies "register it via Koin's WorkManagerFactory" without sequencing.
* **Fix**: Disable default WorkManager auto-init and initialize it manually after Koin, via a custom `WorkerFactory`:
  ```xml
  <!-- AndroidManifest.xml -->
  <provider android:name="androidx.startup.InitializationProvider" android:authorities="${applicationId}.androidx-startup" tools:node="merge">
      <meta-data android:name="androidx.work.WorkManagerInitializer" android:value="androidx.startup" tools:node="remove" />
  </provider>
  ```
  ```kotlin
  class LazyDexApp : Application(), SingletonImageLoader.Factory, Configuration.Provider {
      override fun onCreate() {
          super.onCreate()
          startKoin { androidContext(this@LazyDexApp); modules(appModules) }
          WorkManager.initialize(this, workManagerConfiguration)
      }
      override val workManagerConfiguration: Configuration
          get() = Configuration.Builder().setWorkerFactory(get<KoinWorkerFactory>()).build()
  }
  ```

### 9.3 Category Change on an Existing Item Doesn't Reconcile an Incompatible `userStatus`
* **Issue**: Fix 8.3 correctly scopes `availableStatuses(category)` per category (e.g. Anime → WATCHING, not READING). But DetailScreen (4.7) allows editing category on an *existing* item. If a user changes an item's category from Anime (status = WATCHING) to Novel, WATCHING is no longer in `availableStatuses(NOVEL)`, leaving the item in a state the UI itself says shouldn't exist — and neither the original plan nor Fix 8.3 defines what happens to the stored status.
* **Fix**: On category change in `DetailViewModel`, remap only the three "in-progress" values (READING/WATCHING/PLAYING) to the new category's equivalent; leave COMPLETED/ON_HOLD/DROPPED/PLAN_TO untouched since they aren't category-specific:
  ```kotlin
  fun onCategoryChanged(newCategory: MediaCategory) {
      val inProgress = setOf(UserStatus.READING, UserStatus.WATCHING, UserStatus.PLAYING)
      val newStatus = if (currentStatus in inProgress) inProgressStatusFor(newCategory) else currentStatus
      // update UI state with newCategory + newStatus
  }
  ```

### 9.4 ViewModel Error State Uses Raw Hardcoded Strings, Violating Anti-Pattern #6
* **Issue**: Fixes 4.4 and 7.9 have `DetailViewModel`/`AddItemViewModel` set `_errorMessage.value = "This URL is already tracked by another item."` — a literal English string in a `StateFlow`. Anti-Pattern #6 requires all user-facing strings in `strings.xml` via `stringResource()`, but `stringResource()` only works inside `@Composable` scope, and Architectural Rule #2 forbids Context in ViewModels. As written, the fixes satisfy neither rule.
* **Fix**: Have the ViewModel expose a resource ID (or sealed `UiText` type), resolved to text only at the Composable layer:
  ```kotlin
  sealed interface UiText {
      data class Resource(@StringRes val resId: Int) : UiText
      @Composable fun resolve(): String = when (this) {
          is Resource -> stringResource(resId)
      }
  }
  // ViewModel: _errorMessage.value = UiText.Resource(R.string.error_duplicate_url)
  // Screen: errorMessage?.resolve()?.let { Text(it) }
  ```

### 9.5 Temp Backup File Name Collision Between Manual Export and Auto-Backup Worker
* **Issue**: Section 4.6's Export Rules write a fixed-name temp file, `File(context.cacheDir, "temp_backup.json")`, before copying to the SAF target. Fix 7.2's `AutoBackupWorker` runs on a WorkManager schedule and independently serializes+writes backups. If a scheduled auto-backup fires while the user is mid-export in Settings, both paths can write to the same `cacheDir` filename concurrently, corrupting one or both temp files.
* **Fix**: Use a unique temp filename per operation (e.g. `UUID.randomUUID()` or a caller-scoped subdirectory), not a shared constant:
  ```kotlin
  val tempFile = File(context.cacheDir, "backup_${UUID.randomUUID()}.json.tmp")
  ```

### 9.6 `BackupManager` / `BackupProcessor` Signatures Contradict Their Own Described Flow
* **Issue**: Section 4.6 declares:
  ```kotlin
  suspend fun export(context: Context, uri: Uri, items: List<MediaItem>)
  suspend fun import(context: Context, uri: Uri): List<MediaItem>
  ```
  i.e., `BackupManager` takes/returns domain objects directly. But the SettingsScreen flow (4.9) describes them as separate steps from serialization: *"ViewModel calls `repository.getAll()` + `BackupProcessor.serialize()` + `BackupManager.export()`"* and *"`BackupManager.import()` + `BackupProcessor.deserialize()`"* — implying `BackupManager` handles only raw JSON I/O and `BackupProcessor` handles the `List<MediaItem> ⇄ String` conversion, which only works if `export()` takes a `String` and `import()` returns a `String`. The declared signatures and the narrated call sequence can't both be correct.
* **Fix**: Make `BackupManager` a pure file-I/O layer over SAF, operating on `String`, matching the flow description:
  ```kotlin
  object BackupManager {
      suspend fun export(context: Context, uri: Uri, json: String)
      suspend fun import(context: Context, uri: Uri): String
  }
  // SettingsViewModel:
  BackupManager.export(context, uri, BackupProcessor.serialize(repository.getAll()))
  val items = BackupProcessor.deserialize(BackupManager.import(context, uri))
  ```

---

## 10. Core Architectural & Runtime Fatal Flaws (Round 5)

### 10.1 The "Single Source of Truth" Invariant Contradiction

* **Issue**: Section 3 (Rule 7) and Section 4.4 explicitly mandate: *"Every write path goes through `MediaItem.normalize()` before persisting... CANNOT be bypassed by any caller."* However, Section 4.3 and 4.10 (#1) mandate that `incrementProgress` and `decrementProgress` use **atomic raw SQL updates** (`UPDATE media_items SET currentProgress = MIN(...)`). The checklist at line 262 even says `normalize()` must run on *"increment, decrement, setStatus"*.
* **Reason**: A raw SQL update directly manipulates the SQLite database, entirely bypassing the Kotlin domain layer and `MediaItem.normalize()`. The plan claims that "SQL handles capping," but if a new invariant is added to `normalize()` in the future (e.g., "if progress equals total, set status to COMPLETED"), the atomic SQL will silently violate it because the SQL query wasn't updated. The checklist and the implementation contract are mutually exclusive.
* **Fix**: You cannot have both without an exception. You must formally amend Architectural Rule 7:
> *"Rule 7: Whole-item writes (add, update, replaceAll) MUST go through `MediaItem.normalize()`. Atomic partial updates (increment, decrement, setStatus) are strictly exempt from `normalize()`, provided their raw SQL queries directly mirror the bound-clamping logic of the domain model."*

### 10.2 The OOM (Out of Memory) Trap in Backup Export

* **Issue**: Fix 8.6 raised the backup size ceiling to 50–100MB to accommodate power users. Fix 9.6 redefined `BackupManager` to pass the entire backup file as a single String: `suspend fun export(context: Context, uri: Uri, json: String)`.
* **Reason**: Loading a 50MB JSON payload into a Kotlin `String` requires 100MB of heap (due to UTF-16 character encoding). Combined with the ~100MB memory footprint of the `List<MediaItem>` domain objects and the serialization buffer, this single operation will spike the app's heap usage to 200MB+. On low/mid-tier Android devices, this will instantly trigger an `OutOfMemoryError` and crash the app during export/import.
* **Fix**: Completely eliminate the intermediate `String`. `BackupProcessor` and `BackupManager` must operate directly on streams using `kotlinx.serialization`'s streaming API (requires `@OptIn(ExperimentalSerializationApi::class)`). This also eliminates the temp-file approach from Export Rules since streaming cannot leave partial files:
```kotlin
object BackupManager {
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun export(context: Context, uri: Uri, items: List<MediaItem>) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            val envelope = BackupEnvelopeDto(items = items.map { it.toDto() })
            Json.encodeToStream(envelope, output)
        } ?: throw IOException("Could not open output stream for $uri")
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun import(context: Context, uri: Uri): List<MediaItem> = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            Json.decodeFromStream<BackupEnvelopeDto>(input).items?.mapNotNull { it.toDomain() } ?: emptyList()
        } ?: emptyList()
    }
}
```

### 10.3 Coroutine Cancellation Swallowed by Scraper

* **Issue**: In `MetadataScraper.scrape()` (Section 4.5 code sample, line 622), the network call is wrapped in a generic try/catch:
```kotlin
catch (e: Exception) { Result.failure(e) }
```
* **Reason**: In Kotlin coroutines, cancellation is communicated by throwing a `CancellationException` (which extends `RuntimeException` extends `Exception`). If the user dismisses the AddItem bottom sheet while the OkHttp request is hanging, `viewModelScope` cancels the coroutine. The generic `catch (e: Exception)` intercepts the `CancellationException`, swallows the cancellation, and incorrectly returns a `Result.failure()` to the ViewModel, breaking structured concurrency and potentially causing the ViewModel to process a failure state for a destroyed screen.
* **Fix**: You must explicitly rethrow `CancellationException` before the generic catch:
```kotlin
} catch (e: CancellationException) {
    throw e // Crucial: allow coroutine to cancel
} catch (e: Exception) {
    Result.failure(e)
}
```

### 10.4 I/O Threading Hallucination in Jsoup

* **Issue**: Section 4.5 mandates: *"Jsoup.parse() is CPU-bound and MUST run on Dispatchers.Default, NOT on OkHttp's IO dispatcher pool."* The code passes an `InputStream` (via the `SizeLimitedSource` stream from Fix 9.1) directly to `Jsoup.parse(InputStream, ...)`.
* **Reason**: This is factually incorrect for the implemented code. When Jsoup parses from a stream, it performs **blocking I/O reads** from the OkHttp socket as it builds the DOM tree — the network content has not been fully buffered into memory. Running blocking socket I/O on `Dispatchers.Default` (which has a thread count equal to CPU cores, often just 2-8 threads) will immediately starve the thread pool if the network is slow, since every blocked thread consumes a core-limited slot.
* **Fix**: Remove the `withContext(Dispatchers.Default)` wrapper around `Jsoup.parse`. The entire read-and-parse operation from the `InputStream` MUST remain on `Dispatchers.IO`:
```kotlin
// Inside withContext(Dispatchers.IO) { ... }:
val doc = Jsoup.parse(limitStream, "UTF-8", url) // No Dispatchers.Default switch
val title = extractTitle(doc)
val imageUrl = extractImageUrl(doc)
```

### 10.5 Missing DataStore Dependency for Theme Persistence

* **Issue**: Fix 8.2 resolved a theme default contradiction by mandating a persisted `ThemeMode` backed by "DataStore/SharedPreferences". However, `androidx.datastore:datastore-preferences` is completely absent from the `libs.versions.toml` in Section 6. Neither DataStore nor SharedPreferences is listed anywhere in the dependency catalog.
* **Reason**: A feature was dictated by the fix but the required build dependency was ignored, resulting in missing imports when the project is scaffolded.
* **Fix**: Add the DataStore dependency to `libs.versions.toml` and register a `ThemePreferences` singleton in `di/Modules.kt`:
```toml
[versions]
datastore = "1.1.1"

[libraries]
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
```

### 10.6 AddItemScreen Category/Status Desync

* **Issue**: Fix 8.3 instituted strict rules for statuses based on category (e.g., Anime → WATCHING, Game → PLAYING). However, Section 4.7's `AddItemScreen` spec (lines 874–891) lists the form fields as URL, Category, Title, Cover URL, Progress, Total, Notes — **no Status selector**. If a user adds an Anime via URL auto-fill and hits Save, the ViewModel has no source for the item's status. Without a defined default, the item would get `READING` (the domain model default), violating the category-status mapping established in Fix 8.3.
* **Reason**: The AddItem flow was never updated to respect the new category-aware status constraints introduced by Fix 8.3.
* **Fix**: AddItemViewModel must dynamically derive and assign the correct initial status based on the selected category when creating a new item, and AddItemScreen should show the auto-derived status as a read-only label to keep the form simple:
```kotlin
// In AddItemViewModel:
val initialStatus = inProgressStatusFor(selectedCategory)
val newItem = MediaItem(
    category = selectedCategory,
    userStatus = initialStatus,
    // ... other fields
)
```

### 10.7 Target SDK 34 Edge-to-Edge System UI Overlap

* **Issue**: The plan sets `Target SDK 34` and uses Jetpack Compose Material 3 but never mentions `enableEdgeToEdge()`, `WindowInsets`, or `Modifier.windowInsetsPadding()` anywhere.
* **Reason**: On API 35+ (which the plan's stated target SDK 34 inherits behavior changes from), edge-to-edge rendering is enforced by default. Without handling insets, the `LazyColumn` items will draw underneath the transparent navigation bar, and the `ModalBottomSheet`'s save button in AddItemScreen will overlap with the gesture navigation handle.
* **Fix**:
1. Call `enableEdgeToEdge()` in `MainActivity.onCreate()` before `setContent {}`.
2. Ensure `HomeScreen` and `DetailScreen` use Material 3 `Scaffold` (which automatically consumes insets for content area).
3. Explicitly apply `Modifier.windowInsetsPadding(WindowInsets.navigationBars)` to content inside `ModalBottomSheet` in AddItemScreen.

### 10.8 DetailScreen Progress Clamping UI Illusion

* **Issue**: Section 4.7 states: *"DetailScreen: Client-side clamp progress on blur (cap to total), show inline hint ('Capped to 100')."* The plan relies on focus-loss (blur) to trigger the clamp and warning.
* **Reason**: Compose TextFields do not natively trigger "on blur" events without custom `FocusManager` handling. If a user types "150" (where total is 100) and taps the "Save" button in the toolbar *without dismissing the keyboard*, the field never loses focus. The ViewModel receives "150", `normalize()` silently caps it to "100" in Room, and the user never sees the UI warning — they see their entered "150" briefly before the save refreshes with "100" and no explanation.
* **Fix**: The client-side clamping and warning feedback must also be evaluated explicitly during the `saveChanges()` event in `DetailViewModel`, not just relying on UI focus-loss. If the ViewModel detects a clamp was required during save, it can proceed with the save but should emit a toast or snackbar event:
```kotlin
fun saveChanges(updatedItem: MediaItem) {
    val clamped = updatedItem.currentProgress > (updatedItem.totalItems ?: Int.MAX_VALUE)
    val normalized = updatedItem.copy(lastUpdated = System.currentTimeMillis()).normalize()
    viewModelScope.launch {
        repository.update(normalized)
        if (clamped) _events.send(SaveEvent.ProgressCapped)
    }
}
```

---

## 11. Continued Audit — Additional Issues (Round 6)

### 11.1 The "Teleporting Item" UX Disaster (List Sorting vs. Rapid Tapping)

* **Location**: Section 4.3 (DAO `ORDER BY lastUpdated DESC`) & Section 4.7 (HomeScreen Interactions)
* **Issue**: The DAO mandates sorting the Home list by `lastUpdated DESC`. When a user taps the `[+]` button to increment progress, the atomic SQL updates `lastUpdated` to the current timestamp. Room immediately invalidates the table, the Flow emits the new list, and the item instantly teleports to the top (index 0) of the list. If the user is rapidly tapping `[+]` three times, the first tap moves the item, and their second and third taps will accidentally hit *whatever item slid into its place*. This renders inline progress tracking unusable.
* **Fix**: Separate creation time from modification time to stabilize the UI:
  1. Add `val dateAdded: Long` to `MediaItem` and `MediaItemEntity` (defaulting to `System.currentTimeMillis()` on creation).
  2. Change the DAO queries to `ORDER BY dateAdded DESC` (or `title ASC`).
  3. Keep `lastUpdated` strictly for background conflict resolution in `BackupProcessor.merge()`.

### 11.2 TextField Clobbering on the Detail Screen

* **Location**: Section 4.7 (DetailScreen Inline Editing) & Fix 4.2
* **Issue**: The plan states the DetailScreen allows inline editing with a "Save" button, and Fix 4.2 binds the screen to `repository.observeById(id)`. If the screen's TextFields are bound to this reactive DB Flow, any background DB invalidation (or even a delayed first emission) will instantly overwrite the user's uncommitted typing with the stale database state.
* **Fix**: `DetailViewModel` must read the database state exactly *once* (e.g., `repository.observeById(id).take(1)`) to initialize an independent `MutableStateFlow<MediaItem>` (the draft state). The UI binds to the draft state, and only writes back to Room when the user clicks Save.

### 11.3 The `exportSchema = false` Trap (Migration Dead-End)

* **Location**: Section 4.2 (Room Database)
* **Issue**: The plan explicitly mandates `exportSchema = false` for v1.0 because "there are zero migrations." Room's `AutoMigration` feature requires the schema JSON of the *previous* version to generate the migration path. If v1.0 ships to production with `exportSchema = false`, you will permanently lack the v1.0 baseline. When you eventually build v2.0, AutoMigration will fail, forcing you to write raw, manual SQL migrations for every database change for the rest of the app's lifecycle.
* **Fix**: Change `exportSchema = true` immediately. Configure `room.schemaLocation` in the `app/build.gradle.kts` file so the v1.0 schema is tracked in version control from day one.

### 11.4 Empty Backup Import Wipes Database Silently

* **Location**: Section 4.3 (DAO `replaceAll`), Section 4.6 (Deserialization)
* **Issue**: If a user accidentally imports an empty backup file, or a malformed file that safely falls back to `[]` per the deserialization rules, the `SettingsViewModel` passes the empty list to `repository.replaceAll()`. The `@Transaction` executes `deleteAll()` (wiping the entire local database) followed by `upsertAll(emptyList)` (doing nothing). The user's entire library is instantly deleted with no warning.
* **Fix**: Add a hard guard in `BackupProcessor.deserialize()` or `SettingsViewModel` that throws an exception if the parsed list is empty:
```kotlin
val items = envelope.items?.mapNotNull { it.toDomain() } ?: emptyList()
if (items.isEmpty()) throw ImportFailedException("Backup file contains no valid items.")
return items
```

### 11.5 Merge Logic Blind Spot: `sourceUrl` Collisions

* **Location**: Section 4.6 (Merge Logic), Fix 7.6
* **Issue**: Fix 7.6 implements merge logic deduplicating *exclusively* by `id` (UUID). Scenario: A user uninstalls the app, reinstalls it, and manually re-adds a novel they are reading (generating ID = A, URL = "https://x"). Later, they import an old backup containing that same novel (ID = B, URL = "https://x"). The merge function sees different IDs and includes *both* in the final list. When passed to `dao.replaceAll()`, the `sourceUrl` UNIQUE index is violated, throwing a `SQLiteConstraintException` and crashing the entire import.
* **Fix**: `BackupProcessor.merge()` must deduplicate by BOTH `id` and normalized `sourceUrl`. If an imported item has a matching URL but a different ID compared to a local item, they must be merged into a single entity (preserving the local `id` to maintain DB integrity).

### 11.6 Scraper Blindly Downloads Binary Files (OOM/CPU Waste)

* **Location**: Section 4.5 (Metadata Scraper), Fix 9.1
* **Issue**: Fix 9.1 limits the download size to 5MB, but never checks the `Content-Type`. If a user pastes a URL to a 4MB `.zip`, `.mp4`, or `.png` file, OkHttp will fully download the binary payload and pass it to `Jsoup.parse()`. Jsoup will attempt to parse 4MB of raw binary data as UTF-8 HTML strings, resulting in gibberish, wasted bandwidth, and a massive CPU spike.
* **Fix**: In `MetadataScraper`, immediately after checking `!response.isSuccessful`, verify the content type before reading the body stream:
```kotlin
val contentType = response.body?.contentType()
if (contentType?.subtype != "html") {
    throw IOException("URL does not point to an HTML page")
}
```

### 11.7 Navigation 2.8 Type-Safety Paradigm Mismatch

* **Location**: Section 4.7 (DetailScreen Route Argument)
* **Issue**: The plan states the ViewModel should extract the route argument via `SavedStateHandle.getStateFlow<String>("id", "")`. In Navigation Compose 2.8 (which introduced the `@Serializable` type-safe routes specified in the tech stack), route arguments are obfuscated in the bundle and must be decoded through the serialization engine. Using raw string keys to dig into the `SavedStateHandle` circumvents the type-safe routing and will fail to resolve the argument.
* **Fix**: Use the official Nav 2.8 extraction API in `DetailViewModel`:
```kotlin
val route = savedStateHandle.toRoute<DetailRoute>()
val itemId = route.id
```

### 11.8 Dynamic Color Overrides AMOLED Mode

* **Location**: Section 4.8 (Theme)
* **Issue**: The plan mandates supporting both `dynamicColor` (Material You) and `amoledMode` (True Black backgrounds). However, `dynamicDarkColorScheme(context)` returns an immutable, OS-generated palette (which uses dark grays, not pure black). If `amoledMode` is enabled, the plan provides no instruction to override the OS dynamic colors, resulting in the AMOLED setting being silently ignored on Android 12+ devices.
* **Fix**: In `Theme.kt`, explicitly copy and mutate the background/surface colors *after* retrieving the base scheme:
```kotlin
val baseColor = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
} else {
    if (darkTheme) DarkColorScheme else LightColorScheme
}

val finalColorScheme = if (darkTheme && amoledMode) {
    baseColor.copy(background = Color.Black, surface = Color.Black)
} else {
    baseColor
}
```

---

## 12. Continued Audit — Critical Issues & Regressions (Round 7)

### 12.1 Backup Export Permanently Leaks Temp Files on Failure

* **Location**: Section 4.6 (Export Rules), Fix 9.5
* **Issue**: Fix 9.5 mandates creating unique temp files for exports: `val tempFile = File(context.cacheDir, "backup_${UUID.randomUUID()}.json.tmp")`. However, it relies on the success path ("Delete the temporary file after successful transfer") to clean it up. If the SAF output stream fails, if `Json.encodeToStream` throws an exception, or if the coroutine is cancelled, the temp file is orphaned. At 5MB–100MB per file, failed exports will rapidly exhaust device storage until the OS intervenes.
* **Fix**: The temp file write must be wrapped in a `try/finally` block to guarantee deletion regardless of exceptions or coroutine cancellation:
```kotlin
val tempFile = File(context.cacheDir, "backup_${UUID.randomUUID()}.json.tmp")
try {
    // Write to tempFile, copy to SAF uri...
} finally {
    if (tempFile.exists()) tempFile.delete()
}
```
*(Note: If Fix 10.2's streaming API entirely bypasses the temp file by writing directly to the `contentResolver` output stream, the temp file steps must be formally stricken from the plan).*

### 12.2 Missing `dateAdded` in Backup Schema Destroys List Order on Import

* **Location**: Fix 11.1, Section 4.6 (Backup System)
* **Issue**: Fix 11.1 solved the "Teleporting Item" UI bug by separating modification time (`lastUpdated`) from creation time (`dateAdded`), and changed the DAO to `ORDER BY dateAdded DESC`. However, `dateAdded` was **never added to `MediaItemBackupDto`**.
* **Reason**: When a user exports their library, `dateAdded` is silently dropped. When they import it back, the domain mapping fallback will assign `System.currentTimeMillis()` as the `dateAdded` for *every* item. The user's entire historical sorting order is permanently destroyed on import.
* **Fix**: Add `dateAdded` to `MediaItemBackupDto`, `toDto()`, and `toDomain()`:
```kotlin
@Serializable
private data class MediaItemBackupDto(
    // ...
    val dateAdded: Long? = null
)
// In toDomain():
val safeDateAdded = if (dateAdded != null && dateAdded > 0L) dateAdded else System.currentTimeMillis()
```

### 12.3 Status Filter Chips Are Completely Broken for Anime and Games

* **Location**: Section 4.7 (HomeScreen), Fix 8.3, Fix 8.4
* **Issue**: The UI spec dictates a "Reading" filter chip to show in-progress items. Fix 8.3 correctly split the underlying domain status into `READING` (Novels), `WATCHING` (Anime), and `PLAYING` (Games). If Fix 8.4's DAO query blindly filters by `userStatus = :status` using the UI's selected chip (e.g., `UserStatus.READING`), **Anime and Games will instantly vanish** from the list because their statuses are `WATCHING` and `PLAYING`.
* **Fix**: The UI filter state must not map 1:1 to a single enum value. Create a conceptual `StatusFilter` enum for the UI, and map it to a SQL `IN (...)` clause in the DAO:
```kotlin
// UI Layer
enum class StatusFilter { ALL, IN_PROGRESS, COMPLETED, ON_HOLD, DROPPED, PLAN_TO }

// DAO
@Query("""
    SELECT * FROM media_items 
    WHERE (:category IS NULL OR category = :category)
    AND (
        :filterType = 'ALL' OR 
        (:filterType = 'IN_PROGRESS' AND userStatus IN ('READING', 'WATCHING', 'PLAYING')) OR
        (:filterType = 'EXACT' AND userStatus = :exactStatus)
    )
    ORDER BY dateAdded DESC
""")
fun observeFiltered(category: String?, filterType: String, exactStatus: String?): Flow<List<MediaItemEntity>>
```

### 12.4 OkHttp Coroutine Cancellation Is Swallowed, Falsely Triggering UI Errors

* **Location**: Section 4.5 (Metadata Scraper), Fix 10.3
* **Issue**: The scraper wraps the network call with `currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }`. When the coroutine is cancelled (e.g., user closes the bottom sheet), OkHttp interrupts the socket and `call.execute()` throws an `IOException` (e.g., "Socket closed" or "Canceled").
* **Reason**: Fix 10.3 added `catch (e: CancellationException) { throw e }`, but **OkHttp does not throw `CancellationException`**. It throws `IOException`. The outer block catches the `IOException` and returns `Result.failure(e)`. The ViewModel receives a failure instead of a cancellation, triggering error states (toasts/snackbars) for a screen the user already closed.
* **Fix**: Explicitly check coroutine status or the specific OkHttp cancellation exception before returning a failure:
```kotlin
} catch (e: Exception) {
    if (e is IOException && e.message == "Canceled" || !currentCoroutineContext().isActive) {
        throw CancellationException("Scrape cancelled", e)
    }
    Result.failure(e)
}
```

### 12.5 `sourceUrl` Merge Deduplication ID-Swap Crashes the Database

* **Location**: Fix 11.5, Section 4.6 (Merge Logic)
* **Issue**: Fix 11.5 mandates deduplicating imports by BOTH `id` and `sourceUrl`. If an imported item has the same URL as a local item but a *different ID*, the plan says to "merge into a single entity (preserving the local id)". However, if the imported item is newer and "wins", simply taking the imported item and mapping its ID to the local ID leaves the *original* imported ID unaccounted for. If the JSON contained *other* items that somehow referenced it, or if it isn't properly removed from the imported pool, Room's `upsertAll` will crash with a `SQLiteConstraintException` because two items in the merged list still share the same `sourceUrl`.
* **Fix**: The merge logic must aggressively group by normalized URL *first*, reconcile conflicts to a single winning domain model, and explicitly force the winning model to adopt the local database's `id`:
```kotlin
// Inside BackupProcessor.merge:
val localByUrl = local.associateBy { it.sourceUrl }
// ... loop over imported ...
val url = item.sourceUrl
if (url != null && localByUrl.containsKey(url)) {
    val localMatch = localByUrl[url]!!
    val winner = if (item.lastUpdated > localMatch.lastUpdated) item else localMatch
    // CRITICAL: Force the local ID to prevent UNIQUE constraint crashes
    result[localMatch.id] = winner.copy(id = localMatch.id).normalize()
}
```

### 12.6 Scraper Content-Type Check Crashes on Valid XHTML and Null Headers

* **Location**: Fix 11.6
* **Issue**: Fix 11.6 added `if (contentType?.subtype != "html") throw IOException(...)` to prevent downloading binary files.
* **Reason**:
  1. `contentType` can be null (servers omit it). If null, `null != "html"` evaluates to `true`, and the scraper crashes on a perfectly valid webpage.
  2. Many modern/legacy novel sites serve `application/xhtml+xml`. The subtype is `xhtml+xml`. `xhtml+xml != html` evaluates to `true`, rejecting valid text content.
* **Fix**: Make the check permissive for HTML/XML and handle nulls safely:
```kotlin
val subtype = response.body?.contentType()?.subtype ?: "html" // Assume HTML if missing
if (!subtype.contains("html") && !subtype.contains("xml")) {
    throw IOException("URL does not point to an HTML page (got $subtype)")
}
```

### 12.7 Scraper Vulnerable to "Slowloris" Hanging

* **Location**: Section 4.5 (Metadata Scraper), Section 6 (OkHttp config)
* **Issue**: The plan configures OkHttp with a `readTimeout` of 15 seconds. `readTimeout` only measures the maximum time *between* bytes arriving. If a malicious or tarpitted site returns 1 byte every 14 seconds, OkHttp will never time out. A 5MB file could take 20 hours to download, permanently hanging the coroutine and leaking memory.
* **Fix**: Enforce an absolute wall-clock timeout on the entire scrape operation using Kotlin Coroutines:
```kotlin
suspend fun scrape(url: String): Result<ScrapedMetadata> = withTimeout(30_000L) {
    withContext(Dispatchers.IO) {
        // OkHttp logic here...
    }
}
```

### 12.8 AddItemScreen Form State Hoisting Contradiction (Split-Brain UI)

* **Location**: Section 4.7 (AddItemScreen flow)
* **Issue**: The plan states that the UI survives rotation using `rememberSaveable` for form state. Simultaneously, it states the ViewModel completes the async scrape and "auto-fills Title and Cover URL fields."
* **Reason**: If the TextFields are backed by local `rememberSaveable` state in the Compose function, the ViewModel cannot inject the scraped data into them without convoluted side-effects (`LaunchedEffect`). This creates a split-brain architecture where the ViewModel holds scrape results but the UI holds user input.
* **Fix**: The ViewModel MUST fully hoist the form state. Delete `rememberSaveable` from the UI. The ViewModel exposes a `MutableStateFlow<AddItemFormState>`, and the TextFields read/write directly to it. When the scrape succeeds, the ViewModel procedurally updates the Flow, instantly reflecting in the UI.

### 12.9 SSRF/URI Validation Bypass in the Domain Model

* **Location**: Section 4.1 (MediaItem), Section 4.4 (UrlNormalizer)
* **Issue**: `UrlNormalizer` is wrapped in a `try/catch` that falls back to `url.trim()` if URI parsing fails. `MediaItem.normalize()` trusts this output completely. Furthermore, `coverImageUrl` has NO scheme validation at the domain level.
* **Reason**: If a malicious backup JSON or an edge-case UI bug injects `sourceUrl = "javascript:alert(1)"` or `coverImageUrl = "file:///data/data/app.lazydex/databases/lazydex_db"`, the normalizer simply trims it, the repository saves it, and Coil attempts to execute/read it. The plan explicitly stated `normalize()` is the "canonical invariant enforcer", but it fails to enforce basic URL safety.
* **Fix**: `MediaItem.normalize()` must enforce strict HTTP/HTTPS schemes and drop invalid URIs to `null` (for source) or `""` (for cover), rather than blindly trusting the normalizer's fallback:
```kotlin
// In MediaItem.normalize():
val safeCover = coverImageUrl.trim().takeIf { 
    it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) 
} ?: ""
```

### 12.10 Material 3 `ModalBottomSheet` Double-Padding Hallucination

* **Location**: Fix 10.7
* **Issue**: Fix 10.7 dictates applying `Modifier.windowInsetsPadding(WindowInsets.navigationBars)` to the content inside `ModalBottomSheet` in AddItemScreen.
* **Reason**: In Jetpack Compose Material 3, `ModalBottomSheet` *automatically* consumes and applies window insets for the navigation bar to ensure it sits above system gestures. Applying explicit navigation bar padding to its child content will result in **double padding**, causing the bottom sheet's content to float awkwardly 48-100dp above the bottom of the sheet itself.
* **Fix**: Omit `windowInsetsPadding(WindowInsets.navigationBars)` inside `ModalBottomSheet`. Rely on M3's native inset handling for the sheet, and only apply it manually to full-screen `Scaffold` configurations if needed.

---

## 13. Continued Audit — Critical Issues & Regressions (Round 8)

### 13.1 Catastrophic Data Loss on Export Overwrite (SAF Streaming Trap)

* **Location**: Fix 10.2 (OOM Fix via Streaming API), Section 4.6 (Export Rules)
* **Issue**: Fix 10.2 removed the intermediate temporary file during export to prevent memory exhaustion, choosing instead to stream the JSON directly to the SAF `Uri`. If a user selects their *existing* backup file to overwrite it, Android's `openOutputStream(uri, "wt")` immediately truncates the existing file to 0 bytes. If serialization fails halfway through (e.g., due to a `SerializationException`, a malformed item, or the OS killing the app in the background), the stream closes. The user's only valid backup is now permanently destroyed, replaced by a corrupted, half-written JSON file.
* **Reason**: Direct-to-SAF streaming violates atomicity. You cannot safely overwrite a mission-critical file in place when the data generation process is vulnerable to interruption.
* **Fix**: Combine the memory efficiency of Fix 10.2 (streaming) with the atomicity of Fix 12.1 (temp files). Stream the JSON to a local temp file first. Only after serialization completes successfully, open the SAF `Uri` and copy the stream:
```kotlin
@OptIn(ExperimentalSerializationApi::class)
suspend fun export(context: Context, uri: Uri, items: List<MediaItem>) = withContext(Dispatchers.IO) {
    val tempFile = File(context.cacheDir, "backup_${UUID.randomUUID()}.tmp")
    try {
        tempFile.outputStream().use { fileOut ->
            val envelope = BackupEnvelopeDto(items = items.map { it.toDto() })
            Json.encodeToStream(envelope, fileOut)
        }
        context.contentResolver.openOutputStream(uri, "wt")?.use { safOut ->
            tempFile.inputStream().use { it.copyTo(safOut) }
        } ?: throw IOException("Could not open output stream for $uri")
    } finally {
        if (tempFile.exists()) tempFile.delete()
    }
}
```

### 13.2 Jsoup Charset Forcing Corrupts Non-UTF-8 Sites (Manga/Novel Scraper)

* **Location**: Section 4.5 (Metadata Scraper), scraper code sample
* **Issue**: The code snippet passes `"UTF-8"` hardcoded into the parser: `Jsoup.parse(limitStream, "UTF-8", url)` (line 615).
* **Reason**: Passing a hardcoded charset explicitly disables Jsoup's built-in algorithm that sniffs the HTML `<meta charset>` tag. Since many Japanese manga and web novel sites (which this app specifically targets) are legacy platforms serving `Shift-JIS`, `EUC-JP`, or `UTF-16`, forcing UTF-8 will silently garble the extracted titles into mojibake (unreadable characters).
* **Fix**: Pass `null` as the charset parameter. This instructs Jsoup to read HTTP headers and HTML `<meta>` tags to detect the true encoding automatically:
```kotlin
val doc = Jsoup.parse(limitStream, null, url)
```

### 13.3 Network Timeout Silently Swallowed (UI Hangs)

* **Location**: Section 4.5 (Scraper Error Handling), Fix 12.4
* **Issue**: `scrape()` wraps OkHttp with `withTimeout(30_000L)`. When this times out, Kotlin throws a `TimeoutCancellationException` inside the coroutine. Because this exception extends `CancellationException`, Fix 12.4's catch block evaluates `!currentCoroutineContext().isActive` as true (since `withTimeout` cancels the coroutine before throwing) and incorrectly rethrows it as `CancellationException("Scrape cancelled", e)`.
* **Reason**: The ViewModel intercepts this as a standard coroutine cancellation (e.g., the user navigating away) and stops processing entirely. The UI hangs indefinitely with `isLoading = true`, completely ignoring the plan's mandate to display: "Request timed out. The site may be slow..."
* **Fix**: Catch `TimeoutCancellationException` explicitly *before* evaluating general coroutine cancellation, mapping it to a `Result.failure()` so the ViewModel receives the error state:
```kotlin
} catch (e: TimeoutCancellationException) {
    Result.failure(Exception("Request timed out. The site may be slow or unreachable.", e))
} catch (e: Exception) {
    if (e is IOException && e.message == "Canceled" || !currentCoroutineContext().isActive) {
        throw CancellationException("Scrape cancelled", e)
    }
    Result.failure(e)
}
```

### 13.4 Ghost Item Trap on DetailScreen Deletion

* **Location**: Section 4.7 (DetailScreen Inline Editing), Fix 11.2
* **Issue**: The user taps "Delete" in the DetailScreen toolbar, confirms the dialog, and the ViewModel calls `repository.delete(id)`. However, the plan fails to emit any navigation event to pop the backstack.
* **Reason**: Fix 11.2 changed DetailScreen to use an isolated draft state (`take(1)` from Room) rather than a live reactive stream. After deletion, the draft state is untouched and the screen has no way to detect the item is gone. The user remains trapped on a screen rendering a "ghost item" that no longer exists in the database.
* **Fix**: `DetailViewModel` must emit a navigation event immediately after the deletion succeeds. The UI observes this event and closes the screen:
```kotlin
fun deleteItem() {
    viewModelScope.launch {
        repository.delete(itemId)
        _events.send(DetailEvent.NavigateUp)
    }
}
```

### 13.5 Missing Koin-WorkManager Dependency (Compilation Failure)

* **Location**: Fix 9.2 (Auto-Backup Worker Initialization), Section 6 (Dependencies)
* **Issue**: Fix 9.2 wires up `AutoBackupWorker` using `Configuration.Builder().setWorkerFactory(get<KoinWorkerFactory>()).build()`. However, `KoinWorkerFactory` is part of the `koin-androidx-workmanager` artifact, which is completely missing from `libs.versions.toml`.
* **Reason**: Attempting to reference `KoinWorkerFactory` results in a hard compilation failure during Phase 3.
* **Fix**: Add the missing dependency to `libs.versions.toml`:
```toml
koin-androidx-workmanager = { group = "io.insert-koin", name = "koin-androidx-workmanager", version.ref = "koin" }
```
Ensure it is included in the `koin` bundle, and declare `workManagerFactory()` inside the Koin module setup in `Modules.kt`.

### 13.6 UrlNormalizer URI Syntax Crashes on Unencoded Spaces

* **Location**: Section 4.4 (UrlNormalizer), Fix 12.9
* **Issue**: `UrlNormalizer` uses `java.net.URI(url.trim())`. If a user pastes a URL containing unencoded spaces (e.g., `https://example.com/my page`), `URI()` throws a `URISyntaxException`.
* **Reason**: The `catch` block blindly falls back to `url.trim()`. Fix 12.9's domain scheme check only verifies the URL starts with `"http"`, meaning the un-normalized, space-containing URL bypasses deduplication, gets saved in the database, and will later cause OkHttp to crash when the scraper attempts to call it.
* **Fix**: Replace `java.net.URI` with OkHttp's `HttpUrl.parse()`, which gracefully encodes spaces and handles web-scale URL quirks natively:
```kotlin
fun normalize(url: String): String {
    val httpUrl = HttpUrl.parse(url.trim()) ?: return url.trim()
    return httpUrl.newBuilder().fragment(null).build().toString().removeSuffix("/")
}
```

### 13.7 Empty State Flashing (UI Race Condition)

* **Location**: Section 4.7 (HomeScreen State Management)
* **Issue**: The plan dictates: "LazyColumn of MediaCards, or EmptyState when list is empty". `HomeUiState` initializes with `isLoading = true` and `items = emptyList()`.
* **Reason**: If the Composable merely checks `if (uiState.items.isEmpty()) { EmptyState() }`, the app will aggressively flash the "Nothing here yet" placeholder graphic for several frames on every cold boot before the Room database has time to execute its first background query and populate the list.
* **Fix**: The Compose UI must explicitly guard the empty state behind the loading flag to prevent the flicker:
```kotlin
if (!uiState.isLoading && uiState.items.isEmpty()) {
    EmptyState()
} else if (uiState.isLoading) {
    // Show skeleton loaders or CircularProgressIndicator
} else {
    // LazyColumn
}
```
