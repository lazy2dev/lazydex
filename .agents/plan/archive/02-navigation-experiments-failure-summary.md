# Navigation Experiments Failure & Retrospective Summary

> **Context**: Between Commit 4 (`ff552cc`, stable V5 baseline) and the latest session, three iterations of category-based bottom navigation were attempted. This document analyzes what each experiment attempted, the architectural and UX flaws that emerged, and the lessons learned.

---

## 1. Commit 3 (`695585f`): First Pinned Nav & Modal Sheet Attempt

### What was attempted:
* Replaced the standard 5-tab navigation bar (`Dex`, `Stats`, `Add`, `History`, `Settings`) with 4 pinned category tabs + 1 `Library` tab.
* Created `CategoryPickerSheet.kt` and moved category management into `CategoryManagerScreen.kt` in Settings.
* Persisted pinned categories inside `ThemePreferences` (`DataStore`).
* Removed Home screen category rails and moved subtypes into the Filter sheet (`DexSettingsSheet`).

### Why it failed / Flaws:
1. **Device Crash on Older Emulator**:
   * Storing category navigation state in `DataStore` preferences created asynchronous timing conflicts with Room database category initialization. On cold start or older Android API levels (e.g., API 28 emulator), race conditions triggered unhandled crashes.
2. **Split Source of Truth**:
   * Categories lived in Room SQLite (`categories` table), but pinned tab preferences lived in DataStore (`ThemePreferences`). Deleting or renaming a category in SQLite did not reliably invalidate or sanitize DataStore, causing broken foreign-key references.
3. **Hidden / Buried Category Creation**:
   * Creating a new category required leaving the Home screen, navigating into `Settings -> Categories`, creating it, and navigating back. Users found this workflow fragmented and tedious.
4. **Loss of Primary Destination Access**:
   * Ousting Stats, History, and Settings from the bottom bar forced them into sub-menus or floating actions, drastically increasing tap friction for primary app areas.

---

## 2. Commit 2 (`318d639`): Hold-Click Library with Inline Scrollable Box

### What was attempted:
* Attempted to solve the discoverability and navigation problem by adding a hold-click (long-press) gesture to the `Library` bottom bar icon.
* Long-pressing opened an inline horizontal scrollable box (`ScrollableCategoryBox`) directly overlapping or inside the bottom navigation container, displaying buttons for all categories.

### Why it failed / Flaws:
1. **Zero Discoverability (Hidden Gesture)**:
   * The long-press gesture had no visual affordance or hint. First-time users or casual users would never discover that holding the tab opened additional categories.
2. **Ergonomic Collisions & Clipping**:
   * Stacking a scrollable row directly on top of the bottom navigation bar caused frequent touch conflict with the Android system gesture bar (navigation pill / 3-button nav) and obstructed the Home screen Floating Action Button (`+ Add`).
3. **Material 3 Guideline Violation**:
   * Material Design 3 strictly mandates that bottom navigation bars have 3 to 5 persistent, non-scrollable destinations. Nesting horizontal scrolling inside a navigation bar violated platform standards and produced jank on lower-end devices.
4. **No Taxonomy Management**:
   * The scrollable box was purely a viewer/filter; it provided no way to add, reorder, or edit categories on the fly.

---

## 3. Experiment 1 (Post-318d639 Uncommitted): 5-Item M3 Dynamic Switcher & Strict "No All" State

### What was attempted:
* Refined the bottom bar to exactly 5 Material 3 items: 4 pinned categories + Slot 5 `Library ▾` dynamic switcher with visible downward chevron (`▾`).
* Single tap or long-press on Slot 5 opened `CategorySelectorSheet.kt` featuring:
  * Category list with item count badges and pin status.
  * Sticky `+ New category` button at the bottom.
  * Settings shortcut gear in header.
* Replaced DataStore with `app_settings` in Room SQLite via `AppSettingDao` (zero schema migration, single source of truth).
* Enforced user constraint: **Strictly NO "All" view state** anywhere.
* Moved Stats, History, Browse, and Settings into a TopAppBar overflow menu (`MoreVert`).

### Why it was discarded / Flaws:
1. **High Cognitive Load & App Identity Shift**:
   * By dedicating the entire bottom navigation bar exclusively to categories (e.g. Novel, Manga, Anime, Movie, Library), LazyDex felt less like a comprehensive media tracker and more like a single-purpose tab viewer.
   * Users lost 1-tap muscle memory for `Stats`, `History`, `Browse`, and `Settings`. Having to open an overflow menu (`...`) for core features degraded everyday usability.
2. **Friction of Strict "No All" State**:
   * Removing the combined/All library view completely prevented users from viewing a unified feed of recently updated items across different media types (e.g., catching up on both a novel chapter and an anime episode).
3. **Over-Engineering vs. The Working Baseline**:
   * Commit 4 (`ff552cc`) already had a fully functioning, highly responsive **Two-Tier Category Rail** on the Home screen (Tier 1: Categories + Tier 2: Animated Subtypes) paired with standard 5-tab bottom navigation (`Dex`, `Stats`, `Add`, `History`, `Settings`).
   * The bottom bar experiments introduced significant architectural churn for marginal aesthetic gain, ultimately proving inferior to the clean, proven two-tier baseline.

---

## 4. Architectural Decision: Ground Truth Baseline

* **Active Baseline**: Commit 4 (`ff552cc`, v0.4.0) is the canonical foundation.
* **Navigation Standard**:
  * **Bottom Navigation Bar**: 5 primary app destinations (`Dex`, `Stats`, `Add`, `History`, `Settings`).
  * **Dex Feed Filtering**: Top-of-screen two-tier horizontal rails (Categories + Subtypes) with smooth expandable visibility, plus the tune/filter sheet for advanced criteria (status, genres, tags, sort).
  * **Category Taxonomy**: Dynamic Room V5 database structure (`categories` table with parent-child relationships, resolved configurations, event-sourced tracking, and time metrics).

