# LazyDex v0.0.3 — AniList OAuth Sync & Extended Metadata Architecture

> **Status**: ✅ **IMPLEMENTED & VERIFIED** (2026-07-23)
> **Tagline**: Two-way AniList sync, extended media metadata (format, duration, volumes, status, season), configurable 5-system rating architecture, and AniList-style local statistics calculation.
> **Prerequisite**: v0.0.2 codebase (Source interface, AniListSource, BackupProcessor merge logic).
> **Verification**: `./gradlew test` (100% Passed), `./gradlew assembleDebug` (Build Successful).

---

## 1. Executive Summary & Core Capabilities

- **Login Flow**: Mihon-style dedicated `TrackLoginActivity` capturing `lazydex://anilist-auth` deep links, parsing URI fragment `#access_token=...`, and validating CSRF `state` parameter before storing credentials in `AnilistTokenStore`.
- **Full Pull**: Paginated fetching of all user AniList entries using `$chunk` and `$perPage = 100` with 429 rate-limit backoff, deduplicating by `MediaListEntry.id`, with null-safe media DTO guards.
- **3-Tier Item Matching**: Matches local entries by:
  1. `anilistListEntryId == entry.id`
  2. `sourceUrl == "https://anilist.co/anime/$mediaId"` (or `manga/$mediaId`)
  3. Title + Local `MediaCategory` (using strict `TitleNormalizer` normalization and tie-breaking by `dateAdded DESC` on unlinked items indexed on `Dispatchers.Default`).
- **Recategorization Unbind**: Automatically unbinds AniList IDs (`anilistListEntryId = null`) if an item is moved to a non-syncable category (`GAME`, live-action `TV`, live-action `MOVIE`).
- **Conflict Resolution**: Timestamp-based with a **60-Second Clock Skew Buffer**:
  - If modification timestamps are within 60s of each other: applies deterministic merge (`maxOf(local.currentProgress, remote.progress)`).
  - Outside 60s buffer: strictly newer timestamp wins.
- **Remote Deletion Reconciliation**: Bound items missing from AniList remote payload (and not recently edited locally) are flagged with `syncPendingAction = "REMOTE_DELETED_PENDING_RESOLUTION"`, presenting a UI banner in Settings allowing the user to **Unlink (Keep Local)** or **Delete Locally**.
- **WorkManager Dynamic Delay**: When encountering HTTP 429 rate limits $> 60\text{s}$, `AnilistSyncQueueWorker` cancels execution and enqueues a replacement `OneTimeWorkRequest` with `.setInitialDelay(retryAfter, TimeUnit.SECONDS)`.
- **5-System Configurable Rating Architecture**: Stores score internally in SQLite DB as normalized `Int?` (0–100, `null` = unrated) to prevent float precision drift. Automatically syncs `ScoreFormat` preference (5-Star, 10-Pt, 10-Decimal, 100-Pt, 3-Smiley) with AniList profile on login.
- **Local Statistics**: Computes total progress, completed items, mean rating, category counts, and read/watch stats.

---

## 2. Architecture & Components Implemented

LazyDex replicates and extends Mihon's modular tracker structure:

| Layer | File Path | Implementation Detail |
|-------|-----------|-----------------------|
| Deep Link Activity | [TrackLoginActivity.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/main/java/app/lazydex/ui/auth/TrackLoginActivity.kt) | `singleTop` transparent activity handling OAuth redirect, parsing fragment tokens, and validating CSRF `state` |
| Tracker Service | [AnilistSyncManager.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/main/java/app/lazydex/data/anilist/AnilistSyncManager.kt) | 3-tier matcher, 60s skew-buffered conflict resolver, remote deletion reconciler, full pull & live push worker |
| API Client | [AnilistApi.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/main/java/app/lazydex/data/anilist/AnilistApi.kt) | GraphQL API supporting `Viewer`, paginated `MediaListCollection` (`$perPage = 100`), and `SaveMediaListEntry` |
| Auth Interceptor | [AnilistInterceptor.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/main/java/app/lazydex/data/anilist/AnilistInterceptor.kt) | Injects Bearer token header, catches HTTP 401, debounces user logout, and posts system notifications |
| Rate Limiter | [AnilistRateLimiter.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/main/java/app/lazydex/data/anilist/AnilistRateLimiter.kt) | Token bucket (85 req/min) with persistent `anilist_rate_limit_reset` timestamp tracking |
| Token Storage | [AnilistTokenStore.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/main/java/app/lazydex/data/anilist/AnilistTokenStore.kt) | Mutex-guarded EncryptedSharedPreferences with Keystore corruption recovery and Base64 fallback |
| Background Sync | [AnilistSyncQueueWorker.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/main/java/app/lazydex/data/anilist/AnilistSyncQueueWorker.kt) | WorkManager worker with dynamic long delay replacement requests and `Configuration.Provider` lazy initialization |

---

## 3. Database Migration: Room v2 → v3

Room Database version incremented from **2 to 3**.

### Entity Schema ([MediaItemEntity.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/main/java/app/lazydex/data/local/entity/MediaItemEntity.kt))

The `rating` column stores `Int?` (0–100 integer, `null` = unrated) to prevent float precision drift:

```kotlin
@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["sourceUrl"], unique = true),
        Index(value = ["anilistListEntryId"])
    ]
)
data class MediaItemEntity(
    // ... core 19 columns ...
    @ColumnInfo(defaultValue = "0") val localUpdatedAt: Long = 0,
    @ColumnInfo(defaultValue = "NULL") val lastSyncedAt: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val anilistListEntryId: Long? = null,
    @ColumnInfo(defaultValue = "0") val isPrivate: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val mediaFormat: String? = null,
    @ColumnInfo(defaultValue = "NULL") val rawFormat: String? = null,
    @ColumnInfo(defaultValue = "NULL") val publishingStatus: String? = null,
    @ColumnInfo(defaultValue = "NULL") val season: String? = null,
    @ColumnInfo(defaultValue = "NULL") val totalVolumes: Int? = null,
    @ColumnInfo(defaultValue = "0") val progressVolumes: Int = 0,
    @ColumnInfo(defaultValue = "NULL") val durationMinutes: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val sourceMaterial: String? = null,
    @ColumnInfo(defaultValue = "0") val isAdult: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isDoujin: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val syncPendingAction: String? = null
)
```

### Migration Code (`Migration2To3` in [LazyDexDatabase.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/main/java/app/lazydex/data/local/LazyDexDatabase.kt))

Implements SQLite 4-step table rebuild (`media_items_new`), dynamically reading the legacy rating scale format from `SharedPreferences` to apply the exact conversion multiplier (`POINT_5` $\rightarrow \times 20$, `POINT_10` $\rightarrow \times 10$, etc.):

```kotlin
class Migration2To3(private val context: Context) : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val prefs = context.getSharedPreferences("lazydex_settings", Context.MODE_PRIVATE)
        val oldFormat = prefs.getString("score_format", "POINT_10_DECIMAL") ?: "POINT_10_DECIMAL"

        val multiplier = when (oldFormat) {
            "POINT_10_DECIMAL", "POINT_10" -> 10.0
            "POINT_5" -> 20.0
            "POINT_100" -> 1.0
            "POINT_3" -> 33.33
            else -> 10.0
        }

        db.beginTransaction()
        try {
            db.execSQL("""CREATE TABLE IF NOT EXISTS `media_items_new` ...""")
            db.execSQL("""
                INSERT INTO `media_items_new` ...
                SELECT ...,
                CASE 
                    WHEN rating IS NULL THEN NULL 
                    ELSE CAST(ROUND(rating * $multiplier) AS INTEGER) 
                END, ...
            """)
            db.execSQL("DROP TABLE `media_items` text")
            db.execSQL("ALTER TABLE `media_items_new` RENAME TO `media_items`")
            db.execSQL("CREATE UNIQUE INDEX ...")
            db.execSQL("CREATE INDEX ...")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
```

---

## 4. Configurable 5-System Rating Architecture

- **Internal Storage**: `rating: Int?` (0–100).
- **Format Converter**: [ScoreConverter.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/main/java/app/lazydex/util/ScoreConverter.kt) handles conversion between 0–100 integer scores, user display strings, UI input values, and format interval snapping:
  - `scoreToDisplay(scoreRaw, format)`
  - `uiToScoreRaw(value, format)`
  - `snapToFormatInterval(scoreRaw, format)`

```kotlin
enum class ScoreFormat(val displayName: String) {
    POINT_100("100-Point (1-100)"),
    POINT_10_DECIMAL("10-Point Decimal (1.0-10.0)"),
    POINT_10("10-Point Integer (1-10)"),
    POINT_5("5-Star (0.5-5.0 ★)"),
    POINT_3("3-Point Smiley (😦 😐 😊)")
}
```

---

## 5. Conflict Resolution & Matching Engine

### 60-Second Clock Skew Buffer ([AnilistSyncManager.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/main/java/app/lazydex/data/anilist/AnilistSyncManager.kt))

```kotlin
fun resolveSyncConflict(localTimeMs: Long, remoteTimeMs: Long): SyncDecision {
    val diff = abs(remoteTimeMs - localTimeMs)
    return when {
        diff < CLOCK_SKEW_BUFFER_MS -> SyncDecision.KeepLocal // Deterministic progress merge
        remoteTimeMs > localTimeMs -> SyncDecision.PullFromRemote
        localTimeMs > remoteTimeMs -> SyncDecision.PushToRemote
        else -> SyncDecision.KeepLocal
    }
}
```

### 3-Tier Matcher
1. **Tier 1**: Match by `anilistListEntryId` or `sourceUrl`.
2. **Tier 2**: Match by normalized title index computed on `Dispatchers.Default`.
3. **Tier 3**: Filter by category match, tie-breaking unlinked candidates by latest `dateAdded`.

---

## 6. UI & Settings Integration

- **[DataAndStorageScreen.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/main/java/app/lazydex/ui/settings/DataAndStorageScreen.kt)**:
  - **AniList Tracking Card**: Displays connection status, username, score scale picker, manual "Sync Now" button, and "Disconnect".
  - **Remote Deletion Warning Banner**: Displays items deleted remotely on AniList with **Unlink (Keep Local)** and **Delete** buttons.
  - **Runtime Permission**: Requests `POST_NOTIFICATIONS` permission on Android 13+ before launching auth flow.

---

## 7. Verification & Tests

### Automated Unit Tests ([AnilistSyncConflictTest.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/test/java/app/lazydex/data/anilist/AnilistSyncConflictTest.kt), [ScoreConverterTest.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/test/java/app/lazydex/util/ScoreConverterTest.kt), [TitleNormalizerTest.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/test/java/app/lazydex/util/TitleNormalizerTest.kt))

Ran `./gradlew test`:
- **Result**: `BUILD SUCCESSFUL` (19/19 tests passed).

### Application Build (`./gradlew assembleDebug`)

Ran `./gradlew assembleDebug`:
- **Result**: `BUILD SUCCESSFUL`.

---

## 8. Komikku Extended UI Framework Adaptation & Migration Plan

> **Status**: 🟢 **PHASES 1–3 IMPLEMENTED & VERIFIED**
> **Goal**: Port Komikku's extended UI framework (`.tmp/komikku`) into LazyDex with 100% visual parity while respecting local-only tracker constraints.

### Phase 1: Theme Engine & Porting Foundations ✅
- Ported Komikku's 20+ dynamic theme schemes into LazyDex theme system (`AppTheme`, `ThemeMode`, palette generators).
- Integrated Amoled pure dark mode and Material3 color schemes.

### Phase 2: Direct-Port Komikku Details UI Composables ✅
- **[KomikkuMangaInfoHeader.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/main/java/app/lazydex/ui/components/KomikkuMangaInfoHeader.kt)** (from `.tmp/komikku/.../MangaInfoHeader.kt`):
  - Blurred cover backdrop (`blur(7.dp).alpha(0.25f)` with vertical gradient fade).
  - Poster cover art (2:3 aspect ratio `100.dp x 150.dp`, 8.dp rounded corners, 6.dp shadow, tap-to-zoom).
  - `SelectionContainer` title and author subtitle stack.
  - Category badge, status badge, and star rating.
  - Action row buttons (`Category`, `Status`, `Tracker`, `Webview`).
- **[KomikkuNamespaceTags.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/main/java/app/lazydex/ui/components/KomikkuNamespaceTags.kt)** (from `.tmp/komikku/.../NamespaceTags.kt`):
  - `FlowRow` chip container supporting namespace-grouped tags (`genre`, `studio`, `author`).
- **[KomikkuCollapsibleBox.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/main/java/app/lazydex/ui/components/KomikkuCollapsibleBox.kt)** (from `.tmp/komikku/.../CollapsibleBox.kt`):
  - Expandable synopsis container with height animation.
- **[KomikkuMangaNotesSection.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/main/java/app/lazydex/ui/components/KomikkuMangaNotesSection.kt)** (from `.tmp/komikku/.../MangaNotesSection.kt`):
  - Clean personal notes section with markdown display.
- **[KomikkuMangaCoverDialog.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/main/java/app/lazydex/ui/components/KomikkuMangaCoverDialog.kt)** (from `.tmp/komikku/.../MangaCoverDialog.kt`):
  - Full-screen cover image zoom preview dialog.

### Phase 3: 1:1 In-Place Presentation & Edit Interaction Model ✅
- **Clean Presentation Styling**: Screen stays 100% clean with zero boxy `OutlinedTextField` inputs cluttering the screen in both Read Mode and Edit Mode.
- **Top Bar Controls**:
  - Read Mode: `← Back` | Title (scrolled alpha) | `[✎ Edit]` button | `⋮ Overflow Menu`.
  - Edit Mode: `[Cancel]` button | Title (scrolled alpha) | `[Save]` button.
- **Popup Edit Modals**: Tapping any field in Edit Mode (Title, Author, Progress, Synopsis, Tags, Notes) pops up a clean inline edit dialog modal.
- **No Duplicate Button Labels**: Action row buttons (`Category`, `Status`, `Tracker`, `Webview`) trigger selection menus cleanly without duplicating badge text.
- **[TrackerBottomSheet.kt](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/app/src/main/java/app/lazydex/ui/components/TrackerBottomSheet.kt)**: Ported directly from Komikku's `TrackInfoDialogHome`:
  - 2-grid card container (`surfaceContainerHighest`) with vertical/horizontal dividers for `Status`, `Progress`, `Score`, `Volumes`, and `Visibility`.

### Phase 4: Bulk Selection, FastScroller & Navigation Composables ⏳
- Integrate Komikku's `VerticalFastScroller` across library lists.
- Port Komikku's `AdaptiveSheet` and `WheelPicker` for quick category/status selection.
- Implement multi-selection bulk action toolbar for library grid/list views.

### Phase 5: Final Parity Audit & Release Build ⏳
- Conduct end-to-end UI parity audit against `.tmp/komikku`.
- Verify performance, memory footprint, and compile release APK with `./gradlew assembleRelease`.

