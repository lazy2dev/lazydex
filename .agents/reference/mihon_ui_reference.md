# Mihon / Komikku UI Visual Specification & Reference

This document provides a comprehensive graphical and structural reference of the Mihon/Komikku application UI screens based on the screenshots located in [.tmp/mihon-ss](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/.tmp/mihon-ss). These mockups, styling guidelines, and structural patterns serve as a design blueprint for the development of LazyDex UI features.

---

## Table of Contents
1. [Library & Navigation Shell](#1-library--navigation-shell)
   - [Library Main Screen](#library-main-screen)
   - [Browse Screen (Sources Tab)](#browse-screen-sources-tab)
   - [More Tab Menu](#more-tab-menu)
2. [Library Bottom Sheet Modals](#2-library-bottom-sheet-modals)
   - [Filter Tab](#filter-tab)
   - [Sort Tab](#sort-tab)
   - [Display Tab](#display-tab)
   - [Group Tab](#group-tab)
3. [Settings Hub & Sub-screens](#3-settings-hub--sub-screens)
   - [Settings Main Menu](#settings-main-menu)
   - [Appearance Settings](#appearance-settings)
   - [Tracking Settings](#tracking-settings)
   - [Data & Storage Settings](#data--storage-settings)
4. [Analytics & About](#4-analytics--about)
   - [Statistics Dashboard](#statistics-dashboard)
   - [About Screen](#about-screen)

---

## 1. Library & Navigation Shell

### Library Main Screen
* **Source Image**: [WhatsApp Image 2026-07-18 at 12.33.21 PM.jpeg](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/.tmp/mihon-ss/WhatsApp%20Image%202026-07-18%20at%2012.33.21%20PM.jpeg)

```
┌──────────────────────────────────────────────┐
│  Library  168        [Search] [Filter/Sort] ⋮│ ← Top Bar with count badge & action icons
├──────────────────────────────────────────────┤
│  Home  1     NmL  6     unisystem  6         │ ← Category tabs with counts
│  ──────────                                  │ ← Active tab underline
├──────────────────────────────────────────────┤
│  ┌──────────┐                                │
│  │ ┌──────┐ │                                │
│  │ │[Cover]│ │                                │
│  │ │      │ │                                │
│  │ └──────┘ │                                │
│  │ One-     │                                │
│  │ Punch Man│                                │
│  └──────────┘                                │ ← Media Grid (e.g. Compact Grid Layout)
│                                              │
│                                              │
├──────────────────────────────────────────────┤
│ [Library] [Updates] [History] [Browse] [More]│ ← Bottom Navigation Shell
│  Library   Updates   History   Browse   More │
└──────────────────────────────────────────────┘
```

#### UI Properties
- **Top Bar**: Shows app screen title "Library" and the total item count `168` inside a dark rounded pill. Action buttons on the right are Search `[Search]`, Filter/Sort sheet controller `[Filter/Sort]`, and Overflow menu (`⋮`).
- **Category Tabs**: Scrollable tab row containing custom label capsules (e.g., `Home 1`, `NmL 6`). The active tab is indicated by an accent color underline and brightened text.
- **Library Grid**: Displays media covers in a grid layout (columns adapt to screen width). Each item is encapsulated in a card showing the cover art and title text overlayed with a subtle bottom shadow gradient.
- **Bottom Navigation**: Five navigation destinations with outlined icons/text representations (`[Library]`, `[Updates]`, `[History]`, `[Browse]`, `[More]`) and text labels. Active destination (`Library`) uses a pill background indicator in secondary-accent color. The `Browse` tab displays a red circle indicator representing available extension updates.

---

### Browse Screen (Sources Tab)
* **Source Image**: [WhatsApp Image 2026-07-18 at 12.33.2515 PM.jpeg](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/.tmp/mihon-ss/WhatsApp%20Image%202026-07-18%20at%2012.33.2515%20PM.jpeg)

```
┌──────────────────────────────────────────────┐
│  Browse               [Search] [18+] [Filter]│ ← Top Bar with NSFW / Global actions
├──────────────────────────────────────────────┤
│    Sources      Feed      Extensions  1      │ ← Sub-tabs (with counts)
│  ───────────                                 │
├──────────────────────────────────────────────┤
│  [Search] Search for sources                 │ ← Rounded search input field
├──────────────────────────────────────────────┤
│  Last used                                   │
│  ┌───┐  Hentai20                             │
│  │ H │  [EN] English 18+                [Pin]│ ← Pinned Source row
│  └───┘                                       │
│  English                                     │
│  ┌───┐  AllPornComic                         │
│  │APC│  [EN] English 18+                [Pin]│
│  └───┘                                       │
├──────────────────────────────────────────────┤
│ [Library] [Updates] [History] [Browse] [More]│
└──────────────────────────────────────────────┘
```

#### UI Properties
- **Sub-navigation Tabs**: Provides quick transitions between `Sources`, `Feed`, `Extensions`, and `Migrate`.
- **Search Header**: Integrated text input box with a magnifier text-button `[Search]` to filter installed sources dynamically.
- **Source List**: Grouped list items split into "Last used" and "Language" categories.
- **Row Elements**: Each source contains an icon, title, subtitle (language/content flag, e.g. "[EN] English 18+"), and a pin indicator `[Pin]` on the far right to pin sources to the top of the list.

---

### More Tab Menu
* **Source Image**: [WhatsApp Image 2026-07-18 at 12.33.25 PM.jpeg](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/.tmp/mihon-ss/WhatsApp%20Image%202026-07-18%20at%2012.33.25%20PM.jpeg)

```
┌──────────────────────────────────────────────┐
│                                              │
│                    ╔═════╗                   │
│                    ║  C  ║                   │ ← Large App Logo Graphic
│                    ╚═════╝                   │
├──────────────────────────────────────────────┤
│  [Cloud] Downloaded only                 [o] │ ← Toggle switch row
│  [Glasses] Incognito mode                [o] │
├──────────────────────────────────────────────┤
│  [Download Queue] Download queue             │ ← Navigation list items
│  [Categories] Categories                     │
│  [Stats] Statistics                          │
│  [Warning] Library update errors             │
│  [Data/Storage] Data and storage             │
│  [Add] Batch Add                             │
├──────────────────────────────────────────────┤
│  [Settings] Settings                         │
│  [About] About                               │
└──────────────────────────────────────────────┘
```

#### UI Properties
- **Header Section**: Features the central app branding icon (the stylized book curve logo).
- **Global Toggles**: Two toggle switches at the top of the menu to change modes:
  - `Downloaded only` (marked with `[Cloud]`): Forces the library to show local items only.
  - `Incognito mode` (marked with `[Glasses]`): Disables reading history tracking temporarily.
- **Action Links**: Lists important utilities and secondary dashboards (e.g. `[Download Queue]`, `[Categories]`, `[Stats]`, `[Warning]`, `[Data/Storage]`, `[Add]`) separated by subtle horizontal dividers.

---

## 2. Library Bottom Sheet Modals

These four tabs are built within a consolidated, Notion-inspired `ModalBottomSheet` control.

### Filter Tab
* **Source Image**: [WhatsApp Image 2026-07-18 at 12.33.22 PM.jpeg](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/.tmp/mihon-ss/WhatsApp%20Image%202026-07-18%20at%2012.33.22%20PM.jpeg)

```
┌──────────────────────────────────────────────┐
│  Filter         Sort        Display    Group │ ← Sheet Tabs
│  ──────                                      │
├──────────────────────────────────────────────┤
│  [ ] Downloaded                              │ ← Multiple choice options
│  [ ] Unread                                  │
│  [ ] Started                                 │
│  [ ] Bookmarked                              │
│  [ ] Completed                               │
│  [ ] Lewd                                    │
│  [ ] Categories                         Edit │ ← Category filter item with inline action
│  [ ] Tracked                                 │
└──────────────────────────────────────────────┘
```

#### UI Properties
- **Filter Choices**: A list of checkboxes allowing multi-selection.
- **Inline Actions**: The `Categories` option has a text-button `Edit` on the right edge, allowing users to modify categories directly from the filter screen.

---

### Sort Tab
* **Source Image**: [WhatsApp Image 2026-07-18 at 12.33.23 PM.jpeg](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/.tmp/mihon-ss/WhatsApp%20Image%202026-07-18%20at%2012.33.23%20PM.jpeg)

```
┌──────────────────────────────────────────────┐
│  Filter         Sort        Display    Group │
│                 ────                         │
├──────────────────────────────────────────────┤
│      Alphabetically                          │
│      Total chapters                          │
│  ↓   Last read                               │ ← Active sort selection with direction indicator
│      Last update check                       │
│      Unread count                            │
│      Latest chapter                          │
│      Chapter fetch date                      │
│      Date added                              │
│      Tracker score                           │
│      Random                                  │
└──────────────────────────────────────────────┘
```

#### UI Properties
- **Active Selection**: Indicates the currently active sorting method (e.g. `Last read`).
- **Sort Direction**: Renders an arrow (`↓` or `↑`) on the left of the item text to indicate descending or ascending ordering. Tapping the active sort swaps the arrow direction.

---

### Display Tab
* **Source Image**: [WhatsApp Image 2026-07-18 at 12.33.24 PM.jpeg](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/.tmp/mihon-ss/WhatsApp%20Image%202026-07-18%20at%2012.33.24%20PM.jpeg)

```
┌──────────────────────────────────────────────┐
│  Filter         Sort        Display    Group │
│                             ───────          │
├──────────────────────────────────────────────┤
│  Display mode                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │ Compact  │  │Comforta. │  │   List   │    │ ← Grid segment options
│  └──────────┘  └──────────┘  └──────────┘    │
│                                              │
│  Items per row                          Auto │ ← Label + Value status
│  |----o----.----.----.----.----.----.----|   │ ← Slider selector
│                                              │
│  Overlay                                     │
│  [x] Downloaded chapters                     │
│  [x] Unread chapters                         │
└──────────────────────────────────────────────┘
```

#### UI Properties
- **Display Mode Selector**: Horizontal pill/button selector for switching layout styles (Compact, Comfortable, List, Cover-only, Panorama).
- **Items per Row Slider**: A slider track with tick marks ranging from minimum to maximum card counts per line, with an `Auto` selection option on the right.
- **Overlay & Tab Toggles**: Checkboxes controlling visible info labels overlaying the media cards (e.g. download count, unread count badge, source info).

---

### Group Tab
* **Source Image**: [WhatsApp Image 2026-07-18 at 12.33.27 PM.jpeg](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/.tmp/mihon-ss/WhatsApp%20Image%202026-07-18%20at%2012.33.27%20PM.jpeg)

```
┌──────────────────────────────────────────────┐
│  Filter         Sort        Display    Group │
│                                        ───── │
├──────────────────────────────────────────────┤
│  [Categories] Categories                     │
│  [Sources] Sources                           │
│  [Status] Status                             │
│  [Tracking Status] Tracking status           │
│  [Ungrouped] Ungrouped                       │
└──────────────────────────────────────────────┘
```

#### UI Properties
- **Grouping Configurations**: List options for grouping the library items by metadata fields (Categories, Sources, Reading Status, or Tracker Status).
- **Visual Cues**: Each row contains a text representation of the grouping icon (e.g., `[Categories]`, `[Sources]`, `[Status]`, `[Tracking Status]`, `[Ungrouped]`).

---

## 3. Settings Hub & Sub-screens

### Settings Main Menu
* **Source Image**: [WhatsApp Image 2026-07-18 at 12.33.12PM.jpeg](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/.tmp/mihon-ss/WhatsApp%20Image%202026-07-18%20at%2012.33.12PM.jpeg)

```
┌──────────────────────────────────────────────┐
│  ←  Settings                         [Search]│ ← Settings Top Bar
├──────────────────────────────────────────────┤
│  [Appearance] Appearance                     │
│      Theme, date & time format               │
├──────────────────────────────────────────────┤
│  [Library] Library                           │
│      Categories, global update, chapter swipe│
├──────────────────────────────────────────────┤
│  [Reader] Reader                             │
│      Reading mode, display, navigation       │
├──────────────────────────────────────────────┤
│  [Downloads] Downloads                       │
│      Automatic download, download ahead      │
├──────────────────────────────────────────────┤
│  [Tracking] Tracking                         │
│      One-way progress sync, enhanced sync    │
├──────────────────────────────────────────────┤
│  [Connections] Connections                   │
│      Discord, more to come..                 │
├──────────────────────────────────────────────┤
│  [Browse] Browse                             │
│      Sources, extensions, global search      │
├──────────────────────────────────────────────┤
│  [Data/Storage] Data and storage             │
│      Manual & automatic backups, storage space│
├──────────────────────────────────────────────┤
│  [Security] Security and privacy             │
│      App lock, secure screen                 │
├──────────────────────────────────────────────┤
│  [Advanced] Advanced                         │
│      Dump crash logs, battery optimizations  │
├──────────────────────────────────────────────┤
│  [About] About                               │
│      Komikku Stable 1.14.1                   │
└──────────────────────────────────────────────┘
```

#### UI Properties
- **Top Bar**: Back button arrow navigation, Title "Settings", and a search action `[Search]`.
- **Settings Rows**: Feature high-contrast colored icon labels (represented as text brackets like `[Appearance]`, `[Library]`, etc.), bold title labels, and lighter subtitles summarizing the details inside each sub-screen.

---

### Appearance Settings
* **Source Image**: [WhatsApp Image 2026-07-18 at 12.33.213 PM.jpeg](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/.tmp/mihon-ss/WhatsApp%20Image%202026-07-18%20at%2012.33.213%20PM.jpeg)

```
┌──────────────────────────────────────────────┐
│  ←  Appearance                               │
├──────────────────────────────────────────────┤
│  Theme                                       │
│  ┌──────────────┬──────────────┬──────────┐  │
│  │   ✓ System   │    Light     │   Dark   │  │ ← Theme toggle switch block
│  └──────────────┴──────────────┴──────────┘  │
│                                              │
│  ┌──────┐  ┌──────┐  ┌──────┐                │
│  │[Prev]│  │[Pr]✓ │  │[Prev]│                │
│  │      │  │      │  │      │                │
│  │Defau.│  │Dynam.│  │Custom│                │
│  └──────┘  └──────┘  └──────┘                │ ← Theme preset rows
│                                              │
│  Pure black dark mode                   [o]  │ ← Toggle switch (AMOLED Mode)
├──────────────────────────────────────────────┤
│  Manga Info                                  │ ← Section Header
│                                              │
│  Theme based on cover                   [x]  │
│                                              │
│  Cover based theme style                     │
│  Vibrant                                     │
└──────────────────────────────────────────────┘
```

#### UI Properties
- **Theme Segments**: Material segmented toggle control featuring options for `System` default, `Light` mode, or `Dark` mode.
- **Visual Previews**: Custom layout cards representing theme variants. The active variant (`Dynamic`) features a light border and a checkmark indicator (`✓`).
- **Dynamic Controls**: Toggle switch for `Pure black dark mode` (AMOLED background toggle) and cover-art palette extractors (`Theme based on cover`).

---

### Tracking Settings
* **Source Image**: [WhatsApp Image 2026-07-18 at 12.33.2514 PM.jpeg](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/.tmp/mihon-ss/WhatsApp%20Image%202026-07-18%20at%2012.33.2514%20PM.jpeg)

```
┌──────────────────────────────────────────────┐
│  ←  Tracking                           [Help]│
├──────────────────────────────────────────────┤
│  Open track menu on adding to library   [x]  │
│                                              │
│  Update progress after reading          [x]  │
│                                              │
│  Update progress when marked as read         │
│  Always                                      │
│                                              │
│  Auto sync progress from Trackers       [o]  │
├──────────────────────────────────────────────┤
│  Trackers                                    │
│                                              │
│  ┌─────┐  MyAnimeList                        │
│  │[MAL]│                                     │
│  └─────┘                                     │
│  ┌─────┐  AniList                            │
│  │ [AL]│                                  ✓  │ ← Active logged-in tracker
│  └─────┘                                     │
└──────────────────────────────────────────────┘
```

#### UI Properties
- **Switches & Options**: Switches for automating track updates on status changes, with text dialog entries (`Always`, `Never`) for prompt preferences.
- **Trackers Grid/List**: Lists third-party tracking services (MyAnimeList `[MAL]`, AniList `[AL]`, Kitsu `[Kitsu]`, etc.) with custom branding icon text. An active integration is indicated by a checkmark `✓` on the right side of the row.

---

### Data & Storage Settings
* **Source Images**: 
  - [WhatsApp Image 2026-07-18 at 12.33.29 PM.jpeg](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/.tmp/mihon-ss/WhatsApp%20Image%202026-07-18%20at%2012.33.29%20PM.jpeg) (Upper Section)
  - [WhatsApp Image 2026-07-18 at 12.33.10 PM.jpeg](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/.tmp/mihon-ss/WhatsApp%20Image%202026-07-18%20at%2012.33.10%20PM.jpeg) (Lower Section)

```
┌──────────────────────────────────────────────┐
│  ←  Data and storage                   [Help]│
├──────────────────────────────────────────────┤
│  Storage location                            │
│  /storage/emulated/0/Komikku                 │
├──────────────────────────────────────────────┤
│  Backup and restore                          │
│  ┌────────────────────┬───────────────────┐  │
│  │   Create backup    │  Restore backup   │  │ ← Outlined side-by-side buttons
│  └────────────────────┴───────────────────┘  │
│  Automatic backup frequency                  │
│  Every 12 hours                              │
│                                              │
│  Show restoring progress banner         [x]  │
├──────────────────────────────────────────────┤
│  Storage usage                               │
│  /storage/emulated/0                         │
│  ██████████████████████░░░░░░░░░░░░░░░░░░░░  │ ← Storage Progress Bar
│  Available: 24.12 GB / Total: 120 GB         │
├──────────────────────────────────────────────┤
│  Clear chapter cache                         │
│  Used: 78.65 MB                              │
│                                              │
│  Clear page preview cache                    │
│  Used: 31 B                                  │
└──────────────────────────────────────────────┘
```

#### UI Properties
- **Location Selector**: Configurable path link for reading/writing media files and automated backup exports.
- **Backup & Restore Buttons**: Two prominent side-by-side outlined actions.
- **Storage Metrics**: Visually renders horizontal colored progress bars mapping available disk capacity on internal vs. external card locations.
- **Cache Cleaners**: Actions with calculated size metrics (e.g. `Used: 78.65 MB`) allowing users to free up space instantly.

---

## 4. Analytics & About

### Statistics Dashboard
* **Source Image**: [WhatsApp Image 2026-07-18 at 12.33.28 PM.jpeg](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/.tmp/mihon-ss/WhatsApp%20Image%202026-07-18%20at%2012.33.28%20PM.jpeg)

```
┌──────────────────────────────────────────────┐
│  ←  Statistics                           ⋮   │
├──────────────────────────────────────────────┤
│  Overview                                    │
│  ┌────────────────────────────────────────┐  │
│  │    168           1d 14h           22   │  │
│  │ In library    Read duration   Completed│  │
│  │  [Library]       [Time]         [User] │  │
│  └────────────────────────────────────────┘  │ ← Card Layout
│  Entries                                     │
│  ┌────────────────────────────────────────┐  │
│  │     8              77              0   │  │
│  │  Global Upd     Started          Local │  │
│  └────────────────────────────────────────┘  │
│  Chapters                                    │
│  ┌────────────────────────────────────────┐  │
│  │   10197           2099           1430  │  │
│  │   Total           Read        Downloaded│  │
│  └────────────────────────────────────────┘  │
│  Trackers                                    │
│  ┌────────────────────────────────────────┐  │
│  │    123         6.88 [Star]         1   │  │
│  │  Tracked        Mean score       Used  │  │
│  └────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
```

#### UI Properties
- **Grid Layout**: Metric information is segmented into 4 main categories: Overview, Entries, Chapters, and Trackers.
- **Grid Cards**: Each section uses a subtle container background. Values are prominent, bold, and center-aligned on top of their labels. Supporting text icons provide visual accents to key telemetry metrics (e.g. `[Library]` for count, `[Time]` for reading time, `[User]` for completions, `[Star]` for ratings).

---

### About Screen
* **Source Image**: [WhatsApp Image 2026-07-18 at 12.33.2411 PM.jpeg](file:///e:/lazyman/rockyxwall/02_Codeing/01_Github/lazydex/.tmp/mihon-ss/WhatsApp%20Image%202026-07-18%20at%2012.33.2411%20PM.jpeg)

```
┌──────────────────────────────────────────────┐
│  ←  About                                    │
├──────────────────────────────────────────────┤
│                    ╔═════╗                   │
│                    ║  C  ║                   │ ← Logo Icon
│                    ╚═════╝                   │
├──────────────────────────────────────────────┤
│  Version                                     │
│  Stable 1.14.1 (7/18/26 12:43 AM)            │
├──────────────────────────────────────────────┤
│  Check for updates                           │
├──────────────────────────────────────────────┤
│  What's new                                  │
├──────────────────────────────────────────────┤
│  What's coming (soon)                        │
├──────────────────────────────────────────────┤
│  Help translate                              │
├──────────────────────────────────────────────┤
│  Open source licenses                        │
├──────────────────────────────────────────────┤
│  Privacy policy                              │
├──────────────────────────────────────────────┤
│          [Web]      [Discord]    [GitHub]    │ ← Social Links Row
└──────────────────────────────────────────────┘
```

#### UI Properties
- **Version Stamp**: Displays the build version, branch tag, and creation timestamp.
- **Links Rows**: Interactive options pointing to legal pages, contribution targets, and update checkers.
- **Footer Social Row**: A horizontal grid of text-based icon indicators centered at the bottom of the screen linking to the project webpage (`[Web]`), community server (`[Discord]`), and repository code base (`[GitHub]`).
