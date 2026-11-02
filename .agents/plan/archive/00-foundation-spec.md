# Phase 0 Foundation: UI & Sync UX Specification

> **Document**: `00-foundation-spec.md`  
> **Status**: Approved Specification (Spec-Driven Development)  
> **Complements**: `00-foundation-plan.md` (Architecture) & `00-foundation-tasks.md` (Execution)  
> **Conventions**: Adopts **AniList Terminology** project-wide:
> - **Type** (Top-level medium: Anime, Manga, Novel, Game, Movie)
> - **Format** (Subtype: TV, Movie, OVA, Manga, Light Novel, Web Novel, etc.)

---

## 1. Goal & User Stories

### 1.1 Goal
Provide seamless, lag-free local media tracking with automatic background AniList sync. Ensure that all UI components (feed cards, detail screens, settings) follow Komikku and Material 3 design standards with zero random or unstyled elements.

### 1.2 User Stories
- **US-1 (Tracking)**: *As a reader/viewer, I want to tap `+1` on a chapter or volume directly from my home feed, so that my progress increments instantly without lag.*
- **US-2 (Fractional Progress)**: *As a manga reader, I want to record fractional chapters (e.g. `12.5`), so that my reading log reflects special/omake releases.*
- **US-3 (AniList Sync)**: *As a user, I want my offline progress to sync to AniList automatically in the background, and I want a clear visual progress counter when doing a full sync in Settings.*
- **US-4 (Format Clarity)**: *As a user, I want clear Format badges (`Light Novel`, `Manhwa`, `TV`) and relevant counters so I never see irrelevant volume buttons on an Anime or movie card.*

### 1.3 Non-Goals
- No web scraping or chapter reading inside the tracker (handled by extensions later).
- No social feeds, reviews, or comments.
- No cloud accounts or third-party servers outside direct AniList/MAL APIs.

---

## 2. UI & UX Specification (Zero Random UI: Copied Directly from Komikku)

All UI screens and dialogs are copied directly from the tested Material 3 implementations in [`.tmp/komikku`](file:///e:/rockyxwall/04_Github/lazydex/.tmp/komikku) and adapted surgically to LazyDex's data model:

### 2.1 Screen 1: Tracker Settings (`SettingsTrackingScreen.kt`)
- **Direct Source to Copy**:
  - Screen: [`.tmp/komikku/.../SettingsTrackingScreen.kt`](file:///e:/rockyxwall/04_Github/lazydex/.tmp/komikku/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTrackingScreen.kt)
  - Widget: [`.tmp/komikku/.../TrackingPreferenceWidget.kt`](file:///e:/rockyxwall/04_Github/lazydex/.tmp/komikku/app/src/main/java/eu/kanade/presentation/more/settings/widget/TrackingPreferenceWidget.kt)
  - Logos: [`.tmp/komikku/.../TrackLogoIcon.kt`](file:///e:/rockyxwall/04_Github/lazydex/.tmp/komikku/app/src/main/java/eu/kanade/presentation/track/components/TrackLogoIcon.kt)
- **Layout & Interactions**:

```
┌──────────────────────────────────────────────────────────┐
│ ←  Tracking Settings                                     │
├──────────────────────────────────────────────────────────┤
│ SERVICES                                                 │
│ ┌──────────────────────────────────────────────────────┐ │
│ │ [AL] AniList                                         │ │
│ │      Logged in as: LazyReader                        │ │
│ │      [ Log Out ]                                     │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                          │
│ SYNC & REFRESH                                           │
│ ┌──────────────────────────────────────────────────────┐ │
│ │ Sync Library with AniList                            │ │
│ │ [ Sync Now ]                                         │ │
│ │                                                      │ │
│ │ ── Progress (MAL-Sync style) ─────────────────────── │ │
│ │ Syncing items: 45 / 350 items                        │ │
│ │ [===================>                          ] 12% │ │
│ └──────────────────────────────────────────────────────┘ │
│                                                          │
│ AUTOMATION                                               │
│ [X] Background Sync (Daily on Wi-Fi)                     │
│ [X] Strict Adult Content Filter (Viewer settings)        │
└──────────────────────────────────────────────────────────┘
```

#### States:
1. **Logged Out**: Displays `[ Log In with AniList ]` button. Tapping opens Chrome Custom Tab OAuth (`lazydex://anilist-auth`).
2. **Logged In**: Displays username, avatar, and active status.
3. **Syncing**: "Sync Now" button is disabled; displays live progress bar `X / Total items` and percentage (MAL-Sync style).
4. **Error**: Displays inline error banner with retry option (e.g. `Rate limit reached: retrying in 45s`).

---

### 2.2 Screen 2: Feed Media Card (`MediaCard.kt`)
Enhanced Material 3 Card retaining existing dimensions and layout:

```
┌──────────────────────────────────────────────────────────┐
│ ┌──────┐  Solo Leveling                                  │
│ │Cover │  [ Manhwa ] [ In Progress ]                     │
│ │      │                                                 │
│ │      │  Ch. 179 / 200                        [ + 1 ]   │
│ │      │  Vol. 4 / 8                                     │
│ └──────┘                                                 │
└──────────────────────────────────────────────────────────┘
```

#### Dual Stepper Rules:
- **Primary Counter (Chapters/Episodes)**: Always visible. Tapping `[+1]` immediately increments chapter.
- **Secondary Counter (Volumes)**:
  - **Visible ONLY** if the item's **Format** has `hasSecondaryProgress == 1` (e.g. Manga, Light Novel).
  - **Hidden completely** if `hasSecondaryProgress == 0` (e.g. Anime, Movie, standard Web Novel). No ugly `Vol: —` placeholders.
  - Tapping the Volume text opens a quick dialog to step or set volume.
- **Fractional Progress & Direct Input**:
  - Tapping directly on the chapter number (e.g. `179`) opens the compact number picker dialog copied from Komikku's [`.tmp/komikku/.../TrackInfoDialogSelector.kt`](file:///e:/rockyxwall/04_Github/lazydex/.tmp/komikku/app/src/main/java/eu/kanade/presentation/track/TrackInfoDialogSelector.kt), allowing user to type exact decimals (e.g. `179.5`).
- **Sync Status Affordance**:
  - If item has offline edits awaiting sync (`syncStatus == 'DIRTY_LOCAL'`), a subtle dot or sync icon appears next to the title. Disappears once synced.

---

### 2.3 Screen 3: Detail & Edit (`UnifiedAddEditScreen.kt`)
Header backdrop, localized titles, and rich metadata chips:

```
┌──────────────────────────────────────────────────────────┐
│ [ Back ]                                        [ Save ] │
│ ┌──────────────────────────────────────────────────────┐ │
│ │                     BANNER ART                       │ │
│ │   ┌──────┐  The Beginning After the End              │ │
│ │   │Cover │  Romaji: The Beginning After the End      │ │
│ │   │      │  Native: 끝이 아닌 시작                   │ │
│ └─  └──────┘ ──────────────────────────────────────────┘ │
│                                                          │
│ TRACKING                                                 │
│ Status:   [ In Progress ▾ ]    Score: [ ★★★★☆ (85/100) ] │
│ Chapter:  [- 1]   175 / ?   [+ 1]                        │
│ Volume:   [- 1]     3 / ?   [+ 1]   (If Format has vols) │
│                                                          │
│ METADATA & TAXONOMY                                      │
│ Type:   Manga           Format: Manhwa                   │
│ Genres: [ Action ] [ Fantasy ] [ Reincarnation ]         │
│ Tags:   [ Magic ] [ Cultivation ] [ Swordmanship ]       │
│                                                          │
│ READING SPEED ESTIMATE                                   │
│ ⏱ ~18.5 hours remaining (based on 240 words/min)        │
└──────────────────────────────────────────────────────────┘
```

---

## 3. Data & State Contracts

### 3.1 Tracking Settings State
```kotlin
data class TrackingSettingsUiState(
    val isLoggedIn: Boolean = false,
    val username: String? = null,
    val avatarUrl: String? = null,
    val isSyncing: Boolean = false,
    val syncCurrent: Int = 0,
    val syncTotal: Int = 0,
    val syncError: String? = null,
    val backgroundSyncEnabled: Boolean = true
)
```

### 3.2 Media Card State
```kotlin
data class MediaCardUiState(
    val id: String,
    val title: String,
    val formatName: String,
    val coverPath: String,
    val coverUrl: String?,
    val progress: Double,
    val progressTotal: Double?,
    val secondaryProgress: Double,
    val secondaryProgressTotal: Double?,
    val hasSecondaryProgress: Boolean,
    val isDirtyLocal: Boolean,
    val scoreRaw: Int?
)
```

---

## 4. Acceptance Criteria (BDD)

### AC-1: Stepper Visibility
- **Given** an Anime entry (`hasSecondaryProgress == 0`),
- **When** the card renders in `DexScreen`,
- **Then** only Episode progress `Ep. X / Y [+1]` is shown; Volume controls are completely hidden.

### AC-2: Offline Tap & Sync Queue
- **Given** device is in airplane mode,
- **When** user taps `+1` on a chapter,
- **Then** local progress increments immediately (<16ms), `media_links.syncStatus` becomes `DIRTY_LOCAL`, and a subtle pending sync indicator appears.
- **When** network reconnects,
- **Then** worker pushes mutation and clears `DIRTY_LOCAL`.

### AC-3: Fractional Chapter Input
- **Given** a manga currently on Chapter 12,
- **When** user taps the chapter number text `12`,
- **Then** a number dialog opens allowing entry of `12.5`, updating progress to `12.5`.

### AC-4: Settings Live Sync Progress
- **Given** user triggers "Sync Now" in `Settings -> Tracking`,
- **When** sync runs,
- **Then** the screen displays live progress `Syncing: X / Total items` and disables duplicate taps until completed.

