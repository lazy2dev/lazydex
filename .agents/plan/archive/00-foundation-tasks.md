# Phase 0 Foundation: Implementation Tasks

> **Document**: `00-foundation-tasks.md`  
> **Status**: Ready for Execution (`writing-plans` Discipline)  
> **Architecture Spec**: `00-foundation-plan.md`  
> **UI/UX Spec**: `00-foundation-spec.md`  
> **Verification Command**: `./gradlew testDebugUnitTest`

---

## Phase 1: Database Migration V6 & Entity Refactor

- [x] **Task 1.1: Write Failing `MigrationV6Test.kt`**
  - **Files**: `app/src/test/java/app/lazydex/data/local/MigrationV6Test.kt`
  - **Action**: Create unit test using SQLite in-memory JDBC + `SupportSQLiteDatabase` proxy. Seed realistic V5 database with 5-star ratings (e.g. 4.5), epoch dates, legacy source links, and `media_parts`. Assert failure before migration implementation.
  - **Test/Verify**: `./gradlew testDebugUnitTest --tests "app.lazydex.data.local.MigrationV6Test"`

- [x] **Task 1.2: Implement `MIGRATION_5_6` in `LazyDexDatabase.kt`**
  - **Files**: `app/src/main/java/app/lazydex/data/local/LazyDexDatabase.kt`
  - **Action**: Add `MIGRATION_5_6` object. Rename `categories.hasParts` $\rightarrow$ `hasSecondaryProgress`. Add `remoteMediaListId` and `syncStatus` to `media_links`. Swap `media_items` table with `scoreRaw = ROUND(rating * 20)` and `strftime` dates. Aggregate `media_parts` into `secondaryProgressTotal` and drop table. Bump database version to 6.
  - **Test/Verify**: `./gradlew testDebugUnitTest --tests "app.lazydex.data.local.MigrationV6Test"`

- [x] **Task 1.3: Update Entity & View Definitions**
  - **Files**:
    - `app/src/main/java/app/lazydex/data/local/entity/MediaItemEntity.kt`
    - `app/src/main/java/app/lazydex/data/local/entity/CategoryEntity.kt`
    - `app/src/main/java/app/lazydex/data/local/entity/MediaLinkEntity.kt`
    - `app/src/main/java/app/lazydex/data/local/entity/ActiveMediaItemView.kt`
  - **Action**: Align entity fields: `scoreRaw: Int?`, `titleRomaji`, `titleEnglish`, `titleNative`, `tags: String = "[]"`, `genres: String = "[]"`, `hasSecondaryProgress`, `startYear..endDay`. Retire `MediaPartEntity.kt`.
  - **Test/Verify**: `./gradlew testDebugUnitTest`

- [x] **Task 1.4: Update DAOs & Fix Rating Query Breaks**
  - **Files**:
    - `app/src/main/java/app/lazydex/data/local/dao/MediaItemDao.kt`
    - `app/src/main/java/app/lazydex/data/local/dao/MediaLinkDao.kt`
  - **Action**: Update `getStats()` to query `AVG(scoreRaw) / 20.0` instead of `rating`. Add `getDirtyLinks(providerId: String)` query to `MediaLinkDao`. Remove `mediaPartDao` references.
  - **Test/Verify**: `./gradlew testDebugUnitTest`

---

## Phase 2: Network, Token Bucket & AniList Client

- [x] **Task 2.1: Implement `AnilistTokenStore`**
  - **Files**:
    - `app/src/main/java/app/lazydex/data/tracker/anilist/AnilistTokenStore.kt`
    - `app/src/test/java/app/lazydex/data/tracker/anilist/AnilistTokenStoreTest.kt`
  - **Action**: Securely store OAuth access token, user ID, username, and adult content preferences using `EncryptedSharedPreferences`.
  - **Test/Verify**: `./gradlew testDebugUnitTest --tests "app.lazydex.data.tracker.anilist.AnilistTokenStoreTest"`

- [x] **Task 2.2: Implement Two-Tier `TokenBucketLimiter`**
  - **Files**:
    - `app/src/main/java/app/lazydex/data/tracker/anilist/TokenBucketLimiter.kt`
    - `app/src/test/java/app/lazydex/data/tracker/anilist/TokenBucketLimiterTest.kt`
  - **Action**: Implement coroutine token bucket with 60 req/min baseline and 15 reserved tokens for interactive user taps. Test with `StandardTestDispatcher` and virtual clock.
  - **Test/Verify**: `./gradlew testDebugUnitTest --tests "app.lazydex.data.tracker.anilist.TokenBucketLimiterTest"`

- [x] **Task 2.3: Implement `AnilistGraphQLClient` & DTOs**
  - **Files**:
    - `app/src/main/java/app/lazydex/data/tracker/anilist/AnilistGraphQLClient.kt`
    - `app/src/main/java/app/lazydex/data/tracker/anilist/dto/AniListDtos.kt`
    - `app/src/test/java/app/lazydex/data/tracker/anilist/AnilistGraphQLClientTest.kt`
  - **Action**: Implement OkHttp GraphQL executor for `MediaListCollection` query and `SaveMediaListEntry` mutation. Add deduplication for custom lists via `distinctBy { it.media.id }`.
  - **Test/Verify**: `./gradlew testDebugUnitTest --tests "app.lazydex.data.tracker.anilist.AnilistGraphQLClientTest"`

---

## Phase 3: State-Based Sync Engine & Conflict Resolution

- [x] **Task 3.1: Pure 3-Way Merge Resolver**
  - **Files**:
    - `app/src/main/java/app/lazydex/data/tracker/sync/ThreeWayMergeResolver.kt`
    - `app/src/test/java/app/lazydex/data/tracker/sync/ThreeWayMergeResolverTest.kt`
  - **Action**: Implement pure domain function comparing `local`, `remote`, and `syncedSnapshot` timestamps. Add unit tests for clean remote edits, clean local edits, concurrent updates, and monotonic progress merge.
  - **Test/Verify**: `./gradlew testDebugUnitTest --tests "app.lazydex.data.tracker.sync.ThreeWayMergeResolverTest"`

- [x] **Task 3.2: Implement `AnilistSyncEngine`**
  - **Files**:
    - `app/src/main/java/app/lazydex/data/tracker/sync/AnilistSyncEngine.kt`
    - `app/src/test/java/app/lazydex/data/tracker/sync/AnilistSyncEngineTest.kt`
  - **Action**: Implement chunk batching (50–100 items with `yield()`), in-flight mutation fence, and `isFullSyncComplete` guard to prevent partial pagination unlinking.
  - **Test/Verify**: `./gradlew testDebugUnitTest --tests "app.lazydex.data.tracker.sync.AnilistSyncEngineTest"`

- [x] **Task 3.3: Wire Local Progress Taps to `DIRTY_LOCAL`**
  - **Files**:
    - `app/src/main/java/app/lazydex/data/repository/MediaRepositoryImpl.kt`
  - **Action**: When `incrementProgress` or status change is called, mark corresponding `media_links.syncStatus = 'DIRTY_LOCAL'`. Trigger asynchronous background sync push if online.
  - **Test/Verify**: `./gradlew testDebugUnitTest --tests "app.lazydex.data.repository.ProgressMutationRulesTest"`

---

## Phase 4: UI & Settings (Copy Directly from Komikku)

- [x] **Task 4.1: Port Settings Tracking Screen & Preference Widget**
  - **Source Files to Copy**:
    - `.tmp/komikku/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTrackingScreen.kt`
    - `.tmp/komikku/app/src/main/java/eu/kanade/presentation/more/settings/widget/TrackingPreferenceWidget.kt`
    - `.tmp/komikku/app/src/main/java/eu/kanade/presentation/track/components/TrackLogoIcon.kt`
  - **Target Files**:
    - `app/src/main/java/app/lazydex/ui/settings/SettingsTrackingScreen.kt`
    - `app/src/main/java/app/lazydex/ui/settings/components/TrackingPreferenceWidget.kt`
    - `app/src/main/java/app/lazydex/ui/settings/SettingsViewModel.kt`
  - **Action**: Copy Komikku's tested Material 3 tracking screen and widgets directly. Adapt for AniList login card, "Sync Library" button with MAL-Sync live progress bar (`X / Total items`), and background sync toggle. Add navigation route to `SettingsMainScreen.kt`.
  - **Test/Verify**: `./gradlew assembleDebug`

- [x] **Task 4.2: Port Komikku Stepper & Progress Dialog**
  - **Source Files to Copy**:
    - `.tmp/komikku/app/src/main/java/eu/kanade/presentation/track/TrackInfoDialogSelector.kt`
    - `.tmp/komikku/app/src/main/java/eu/kanade/presentation/track/TrackInfoDialogHome.kt`
  - **Target Files**:
    - `app/src/main/java/app/lazydex/ui/components/TrackProgressDialog.kt`
  - **Action**: Copy Komikku's number picker and stepper dialog for direct chapter/volume selection and fractional decimal input (e.g. `12.5`).
  - **Test/Verify**: `./gradlew assembleDebug`

- [x] **Task 4.3: Update `MediaCard.kt` Dual Steppers & Badges**
  - **Files**:
    - `app/src/main/java/app/lazydex/ui/components/MediaCard.kt`
  - **Action**: Add Format badge (`Manhwa`, `Light Novel`, `TV`). Show Volume counter ONLY when `hasSecondaryProgress == true`. Connect tap on chapter text to `TrackProgressDialog`.
  - **Test/Verify**: `./gradlew assembleDebug`

- [x] **Task 4.4: Update `UnifiedAddEditScreen.kt` Metadata & Chips**
  - **Files**:
    - `app/src/main/java/app/lazydex/ui/addedit/UnifiedAddEditScreen.kt`
  - **Action**: Render banner art, localized titles (Romaji, Native), Type/Format pickers, and JSON tag/genre chip clouds.
  - **Test/Verify**: `./gradlew assembleDebug`

---

## Phase 5: End-to-End Verification Gate

- [x] **Task 5.1: Full Unit & Regression Test Suite**
  - **Files**: All test files
  - **Action**: Run all tests across Room, Sync, Math, and Extensions.
  - **Test/Verify**: `./gradlew testDebugUnitTest` (Must pass with 0 errors).

- [x] **Task 5.2: Assemble Debug APK**
  - **Files**: Full project
  - **Action**: Compile complete debug build to verify no missing imports, compiler warnings, or Room schema issues.
  - **Test/Verify**: `./gradlew assembleDebug` (Build SUCCESSFUL).