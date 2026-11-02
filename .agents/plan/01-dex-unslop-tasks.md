# Phase 1 Tasks: Dex Screen UI Unslop & Komikku Alignment

> **Document**: `01-dex-unslop-tasks.md`  
> **Status**: Completed (`writing-plans` Discipline)  
> **Architecture Spec**: `01-dex-unslop-plan.md`  
> **Source Reference**: `.tmp/komikku/app/src/main/java/eu/kanade/presentation/`  
> **Verification Command**: `./gradlew testDebugUnitTest` and `./gradlew assembleDebug`

---

## Phase 1: Navigation Shell & FAB Consolidation

- [x] **Task 1.1: Re-label Bottom Navigation Tab to 'Browse'**
  - **Files**: `app/src/main/java/app/lazydex/ui/navigation/MainShellScreen.kt`
  - **Action**: Replace center tab #3 icon with `Icons.Default.Explore` (or `Icons.Outlined.Explore`), label with `"Browse"`, and contentDescription with `"Browse"`.
  - **Verify**: `./gradlew assembleDebug`

- [x] **Task 1.2: Convert Extended FAB to Compact Circular FAB on DexScreen**
  - **Files**: `app/src/main/java/app/lazydex/ui/dex/DexScreen.kt`
  - **Action**: Replace `ExtendedFloatingActionButton` with standard `FloatingActionButton(onClick = onNavigateToAddItem) { Icon(Icons.Default.Add, contentDescription = "Add Item") }`.
  - **Verify**: `./gradlew assembleDebug`

---

## Phase 2: MediaCard Cleanup (Komikku Read-Only Model)

- [x] **Task 2.1: Strip Inline Steppers & Editing Dialogs from MediaCard**
  - **Files**: `app/src/main/java/app/lazydex/ui/components/MediaCard.kt`
  - **Action**:
    - Remove `+1` increment buttons and callbacks (`onIncrementProgress`).
    - Remove `TrackProgressDialog` dialog states and tap handlers (`showPrimaryDialog`, `showSecondaryDialog`).
    - Card has single unified `clickable(onClick = onClick)`.
  - **Verify**: `./gradlew assembleDebug`

- [x] **Task 2.2: Implement Komikku Restrained Badges on Covers**
  - **Files**: `app/src/main/java/app/lazydex/ui/components/MediaCard.kt`
  - **Action**:
    - Update `CardStartBadges`: show single progress badge pill (`formatProgress(displayProgress)`) at `TopStart` when `showProgressBadge == true`. Remove format badge from grid cover.
    - Update `CardEndBadges`: replace score badge and sync badge on grid cover with a subtle 8dp circular status dot in `TopEnd`.
    - Tokenize bottom gradient scrim with `MaterialTheme.colorScheme.scrim`.
    - Set title style to `MaterialTheme.typography.titleSmall`.
  - **Verify**: `./gradlew assembleDebug`

---

## Phase 3: Taxonomy & Rail Streamlining (AniList Terminology)

- [x] **Task 3.1: Add TopAppBar Overflow Menu for Type/Format Creation**
  - **Files**: `app/src/main/java/app/lazydex/ui/dex/DexScreen.kt`
  - **Action**: Add 3-dots overflow `IconButton` in `TopAppBar` actions. Render `DropdownMenu` with two items:
    1. `"New Type"` $\rightarrow$ opens `CategoryCreatorSheet(parentCategory = null)`
    2. `"New Format"` $\rightarrow$ opens `CategoryCreatorSheet(parentCategory = activeParent)`
  - **Verify**: `./gradlew assembleDebug`

- [x] **Task 3.2: Remove '+ Category' and '+ Type' AssistChips from Browse Rails**
  - **Files**: `app/src/main/java/app/lazydex/ui/dex/DexScreen.kt`
  - **Action**: Remove `AssistChip` for `+ Category` from Tier 1 rail, and remove `AssistChip` for `+ Type` from Tier 2 rail. Rails now display only `FilterChip` items for Types and Formats.
  - **Verify**: `./gradlew assembleDebug`

---

## Phase 4: Komikku SearchToolbar & ViewModel Pipeline

- [x] **Task 4.1: Write Failing `DexViewModelTest.kt`**
  - **Files**: `app/src/test/java/app/lazydex/ui/dex/DexViewModelTest.kt`
  - **Action**: Create JUnit 5 test class using MockK and Turbine. Test `searchQuery` flow:
    - Empty query returns all items.
    - Query matching title filters correctly.
    - Query matching author filters correctly.
    - Whitespace trimming and case-insensitive matching.
  - **Verify**: `./gradlew testDebugUnitTest --tests "app.lazydex.ui.dex.DexViewModelTest"` (Fails until implementation).

- [x] **Task 4.2: Implement Debounced Search Pipeline in DexViewModel**
  - **Files**: `app/src/main/java/app/lazydex/ui/dex/DexViewModel.kt`
  - **Action**:
    - Add `searchQuery = MutableStateFlow<String?>(null)` and `fun setSearchQuery(query: String?)`.
    - Apply `.debounce(250).distinctUntilChanged()` on `searchQuery`.
    - Filter items in-memory on `Dispatchers.Default` using query against `title`, `author`, `titleEnglish`, `titleRomaji`.
  - **Verify**: `./gradlew testDebugUnitTest --tests "app.lazydex.ui.dex.DexViewModelTest"` (Passes with 0 errors).

- [x] **Task 4.3: Port Komikku SearchToolbar into DexScreen**
  - **Files**: `app/src/main/java/app/lazydex/ui/dex/DexScreen.kt`
  - **Action**:
    - Adopt Komikku's `SearchToolbar` pattern (`AppBar.kt#L310`):
      - When `searchQuery == null`, show `"Dex"` title, Search icon, Filter icon, and Overflow menu.
      - When `searchQuery != null`, show Back button (clears search to null), `BasicTextField` with focus requester, and Clear button.
    - Connect text changes directly to `viewModel.setSearchQuery(it)`.
  - **Verify**: `./gradlew assembleDebug`

---

## Phase 5: Verification & Polish

- [x] **Task 5.1: Run Full Unit Test Suite**
  - **Action**: Run `./gradlew testDebugUnitTest` across all modules. Verify 0 failures.
  - **Verify**: `./gradlew testDebugUnitTest`

- [x] **Task 5.2: Assemble Debug Build**
  - **Action**: Run `./gradlew assembleDebug` to guarantee clean compilation.
  - **Verify**: `./gradlew assembleDebug`
