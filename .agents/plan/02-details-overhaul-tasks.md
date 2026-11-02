# Phase 2: Media Details Overhaul Implementation Tasks

> **Document**: `02-details-overhaul-tasks.md`  
> **Status**: Ready for Execution  
> **Target Surface**: `app/src/main/java/app/lazydex/ui/details`  
> **Execution Rule**: Execute tasks sequentially. Check off each box `[x]` upon verification.

---

### Phase 1: ViewModel & State Hardening

- [x] **Task 1.1: Extend `MediaDetailsUiState` with Loading & AniList Sync Properties**
  - **Files**: `app/src/main/java/app/lazydex/ui/details/MediaDetailsViewModel.kt`
  - **Action**: Add `isLoading: Boolean = false`, `isSyncing: Boolean = false`, `anilistMediaId: Long? = null`, `anilistSyncStatus: String? = null`, `anilistLastSynced: Long? = null`, `isAnilistLoggedIn: Boolean = false`, and `lastUpdated: Long = System.currentTimeMillis()`. Set `isLoading = true` in `initialize()` when `targetId != null` and clear when entity loads or is missing.
  - **Test/Verify**: `./gradlew testDebugUnitTest --tests "app.lazydex.ui.details.MediaDetailsViewModelTest"`

- [x] **Task 1.2: Fix False-Dirty Back-Press Bug**
  - **Files**: `app/src/main/java/app/lazydex/ui/details/MediaDetailsViewModel.kt`
  - **Action**: In `checkBackPressAllowed()`, guard `originalItem == null && !state.isNew`. If entity is still loading or uninitialized, do not flag `isDirty = true`. Allow clean back-press without popping "Discard Changes" alert.
  - **Test/Verify**: `./gradlew testDebugUnitTest --tests "app.lazydex.ui.details.MediaDetailsViewModelTest"`

- [x] **Task 1.3: Lag-Free Notes Debounced Persistence**
  - **Files**: `app/src/main/java/app/lazydex/ui/details/MediaDetailsViewModel.kt`
  - **Action**: Add `private val notesFlow = MutableStateFlow<String?>(null)`. In `init`, launch collector with `.debounce(500L).distinctUntilChanged()` to call `autoSave()`. Add `fun flushNotes()` to immediately write pending notes on back-press or exit. Remove synchronous `autoSave()` from per-keystroke `updateNotes()`.
  - **Test/Verify**: `./gradlew testDebugUnitTest --tests "app.lazydex.ui.details.MediaDetailsViewModelTest"`

- [x] **Task 1.4: Surface Scraper Errors to UI State**
  - **Files**: `app/src/main/java/app/lazydex/ui/details/MediaDetailsViewModel.kt`
  - **Action**: In `scrapeUrl()`, map scraper failure exceptions directly to `errorMsg` so `SnackbarHost` in Compose displays user-friendly error toast instead of silent spinner halt.
  - **Test/Verify**: `./gradlew testDebugUnitTest --tests "app.lazydex.ui.details.MediaDetailsViewModelTest"`

- [x] **Task 1.5: Wire AniList Link Observer & Push Sync**
  - **Files**: `app/src/main/java/app/lazydex/ui/details/MediaDetailsViewModel.kt`, `app/src/main/java/app/lazydex/di/Modules.kt`
  - **Action**: Inject `MediaLinkDao`, `AnilistSyncEngine`, and `AnilistTokenStore` into `MediaDetailsViewModel`. In `initialize()`, observe `mediaLinkDao.observeByMediaId(targetId)` to populate `anilistMediaId`, `anilistSyncStatus`, and `anilistLastSynced`. Add `fun syncWithAniList()` calling `anilistSyncEngine.pushDirtyItem(state.id)`.
  - **Test/Verify**: `./gradlew testDebugUnitTest --tests "app.lazydex.ui.details.MediaDetailsViewModelTest"`

---

### Phase 2: Action Row & AniList Sync Dialog (Komikku 1:1)

- [x] **Task 2.1: Re-architect `MediaActionRow.kt` to 4 Standard Buttons**
  - **Files**: `app/src/main/java/app/lazydex/ui/details/components/MediaActionRow.kt`
  - **Action**: Refactor component parameters to:
    - `inLibrary: Boolean`, `onLibraryClick: () -> Unit`, `onLibraryLongClick: () -> Unit` (Heart)
    - `lastUpdatedMillis: Long`, `onUpdatedClick: () -> Unit` (Hourglass/Schedule with relative formatted text e.g. "Updated 2d ago")
    - `isSynced: Boolean`, `onSyncClick: () -> Unit` (Sync icon)
    - `onEditClick: () -> Unit` (Pencil icon)
  - **Test/Verify**: `./gradlew assembleDebug`

- [x] **Task 2.2: Implement Komikku 1:1 `MediaTrackerSyncDialog.kt`**
  - **Files**: `app/src/main/java/app/lazydex/ui/details/components/MediaTrackerSyncDialog.kt`
  - **Action**: Create dialog matching Komikku `TrackInfoDialogHome.kt`:
    - Linked State: Tracker icon, remote title, overflow menu (Open in browser, Copy link, Unlink), 3x2 matrix showing remote Status, Chapters, Score, Start Date, Finish Date, and a "Sync Now" button.
    - Unlinked State: "Not Linked" status, AniList ID manual input or search button, and Link action.
    - Logged-out State: "Connect to AniList" prompt with shortcut to Settings.
  - **Test/Verify**: `./gradlew assembleDebug`

---

### Phase 3: Hero Header & Metadata Edit Sheet

- [x] **Task 3.1: Polish `MediaInfoHeader.kt` Backdrop & Cover Scrim**
  - **Files**: `app/src/main/java/app/lazydex/ui/details/components/MediaInfoHeader.kt`
  - **Action**: Support local disk `coverImagePath` in blurred hero backdrop model (fallback if `bannerUrl` and `coverImageUrl` are empty). Show both format badge AND total chapters in the secondary row alongside host name (remove mutual exclusivity).
  - **Test/Verify**: `./gradlew assembleDebug`

- [x] **Task 3.2: Relocate URL Scraper into `EditMetadataSheetContent`**
  - **Files**: `app/src/main/java/app/lazydex/ui/details/MediaDetailsScreen.kt`
  - **Action**: Remove permanent top scraper banner from `MediaDetailsScreen`. Move URL text field and "Fetch" button into top of `EditMetadataSheetContent`. Add Type/Format dropdown picker in the sheet.
  - **Test/Verify**: `./gradlew assembleDebug`

- [x] **Task 3.3: Clean `MediaDescriptionSection.kt` Copy & Chip Interactions**
  - **Files**: `app/src/main/java/app/lazydex/ui/details/components/MediaDescriptionSection.kt`
  - **Action**: Replace `"No description available. Tap category or webview to edit."` with `"No description provided."`. Remove click hijacking from genre/tag chips. Center subtle expand/collapse chevron.
  - **Test/Verify**: `./gradlew assembleDebug`

---

### Phase 4: Tracking Matrix & Category-Aware Selectors

- [x] **Task 4.1: Convert `MediaTrackingSection.kt` to Two-Line Labeled Cells**
  - **Files**: `app/src/main/java/app/lazydex/ui/details/components/MediaTrackingSection.kt`
  - **Action**: Update `TrackMatrixCell` to render two lines:
    - Line 1: `labelSmall` in `onSurfaceVariant` (`STATUS`, `PROGRESS`, `SCORE`, `STARTED`, `FINISHED`).
    - Line 2: `titleSmall` bold value (`Reading`, `Ch. 12 / 100`, `4.5 ★`, `Oct 12, 2026`).
    - Host local `remember { mutableStateOf(notes) }` for lag-free typing, feeding ViewModel's debounced flow.
  - **Test/Verify**: `./gradlew assembleDebug`

- [x] **Task 4.2: Category-Aware `TrackStatusSelector` & Preset Rating Controls**
  - **Files**: `app/src/main/java/app/lazydex/ui/details/components/MediaTrackSelectors.kt`
  - **Action**: In `TrackStatusSelector`, filter visible statuses based on `MediaCategory` (Novels/Manga: Reading, Completed, On Hold, Dropped, Plan to Read; Anime/TV: Watching...; Games: Playing...). In `TrackScoreSelector`, add 5 star preset chips (1★, 2★, 3★, 4★, 5★) above slider. Fix `DatePickerDialog` dismiss button row.
  - **Test/Verify**: `./gradlew assembleDebug`

---

### Phase 5: Loading Skeleton & Verification

- [x] **Task 5.1: Create `MediaDetailsSkeleton.kt` Placeholder**
  - **Files**: `app/src/main/java/app/lazydex/ui/details/components/MediaDetailsSkeleton.kt`
  - **Action**: Build pulsing Material 3 skeleton composable representing hero cover, title lines, action button capsules, and tracking matrix card.
  - **Test/Verify**: `./gradlew assembleDebug`

- [x] **Task 5.2: Screen Assembly & Verification**
  - **Files**: `app/src/main/java/app/lazydex/ui/details/MediaDetailsScreen.kt`
  - **Action**: Wire `isLoading` to render `MediaDetailsSkeleton`. Connect `MediaActionRow` buttons to `MediaTrackerSyncDialog` and `EditMetadataSheetContent`. Remove redundant "Not in Library" surface card.
  - **Test/Verify**: `./gradlew testDebugUnitTest` and `./gradlew assembleDebug`

---

### Phase 6: Final Verification Gate

- [x] **Task 6.1: Run Full Test Suite & Assemble APK**
  - **Command**: `./gradlew testDebugUnitTest`
  - **Command**: `./gradlew assembleDebug`

