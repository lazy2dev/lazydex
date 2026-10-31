# Phase 0 Foundation Plan v2 (Patched)

> **Status**: Current Active Work Target (supersedes v1)
> **Scope**: DB V5 migration (table rebuild), 2-tier Category → Type hierarchy with inheritance, capability flags, structure-only parts, event-sourced activity log (listening/reading time), soft deletes + active view, atomic repository rules, unified ItemDetailScreen.
> **Conventions**: all timestamps are **epoch milliseconds, UTC**. All IDs are UUID text, except fixed seed IDs. No hardcoded enums in code for categories; status-like strings are protected by SQL `CHECK`.

---

## 0. Changelog vs v1 (what was wrong, what changed)

| # | v1 problem | v2 decision |
|---|---|---|
| 1 | Types had `NOT NULL DEFAULT` values, so inheritance was undefined (Movie type got verb "Read") | Type columns are **nullable = inherit from parent**. Repository exposes `ResolvedCategoryConfig` |
| 2 | Unlimited depth, cascade delete, no unique names, deletion check ignored child types and tombstones | Max depth 2 (trigger), `ON DELETE RESTRICT`, unique names, delete check covers children + tombstones |
| 3 | Progress in both `media_items` and `media_parts` | `media_items.progress` is the single truth. Parts = **structure only** |
| 4 | Listening time derived from *current* speed (rewrites history), no reading math, undefined units | Time is **event-sourced** from `activity` with speed/rate/length snapshots; `lengthUnit` added |
| 5 | Migration crashed on unknown categories, bad `deletedAt`, no UUID fn, FK cascade risk, no backup | `Uncategorized` fallback, fixed seed IDs, deferred FKs, backup, `foreign_key_check`, tests |
| 6 | Items on a parent that has types had ambiguous filtering | Defined: parent chip = self + children; implicit "Unsorted" tier-2 chip |
| 7 | `progress INTEGER` can't hold ch. 12.5 or 1.5 hours | `progress REAL` |
| 8 | `sync_outbox` FK cascade would destroy unsynced changes | No FK on outbox; purge only after outbox drains |
| 9 | `activity` used AUTOINCREMENT while everything else is UUID | UUID `id` |
| 10 | `media_links.remoteId NOT NULL` with `''` hack | Nullable `remoteId`/`url`, hack removed |
| 11 | Missing indexes, missing CHECKs | Added (section 2.7) |
| 12 | "All queries filter deletedAt" is only a rule | `VIEW active_media_items`; DAOs read from it |
| 13 | No `updatedAt` on categories/parts/links (future sync pain) | Added now |

---

## 1. Architecture: Zero Enums, Capability-Driven, With Inheritance

### 1.1 Hierarchy (single table, strictly 2 levels)
- **Category** (`parentId IS NULL`): broad medium, e.g. `Novel`, `Manga`, `Anime`, `Movie`, `TV Series`, `Game`, `Uncategorized`.
- **Type** (`parentId = category.id`): optional subtype, e.g. `Light Novel`. A Type **cannot** have children (trigger-enforced).
- A category with **no types** holds items directly; Tier 2 auto-hides.
- A category **with** types may *also* hold items directly (legacy or unsorted items). These appear under an implicit **`Unsorted`** chip in Tier 2, shown only when their count is greater than 0.

### 1.2 Inheritance rule (the fix for v1 ambiguity)
For every configurable column, **a Type's `NULL` means "use the parent's value"**. A Category (top level) must have all columns filled (enforced by `CHECK`).

```
resolved(col) = type.col IS NOT NULL ? type.col : parent.col
```

The app never reads raw category rows for behavior. It reads one object:

```kotlin
data class ResolvedCategoryConfig(
    val categoryId: String,
    val topLevelId: String,
    val displayName: String,
    val icon: String,
    val unitName: String,
    val secondaryUnitName: String?,   // null = no secondary unit
    val actionVerb: String,
    val hasParts: Boolean,
    val hasCharEstimate: Boolean,
    val hasAudioSpeed: Boolean,
    val lengthUnit: LengthUnit        // CHARS or WORDS
)
```

`secondaryUnitName` is the one exception: a Type may need to say "no secondary unit" even if the parent has one. Use the sentinel `''` (empty string) = explicitly none; `NULL` = inherit.

### 1.3 Capability flags
- `hasParts`: structured breakdown (volumes, seasons, arcs).
- `hasCharEstimate`: enables `avgChapterLength` and total length calculation.
- `hasAudioSpeed`: enables the **Listen** mode toggle and playback speed. **The Listen toggle is only shown when the resolved `hasAudioSpeed = 1`.** No exceptions (this removes the v1 contradiction).

---

## 2. Database V5 Schema

### 2.1 `categories`
```sql
CREATE TABLE categories (
    id                TEXT PRIMARY KEY,
    parentId          TEXT REFERENCES categories(id) ON DELETE RESTRICT,
    name              TEXT NOT NULL CHECK (length(trim(name)) > 0),

    -- nullable on Types (NULL = inherit). Required on top-level (see CHECK below).
    icon              TEXT,
    unitName          TEXT,     -- "Chapter", "Episode", "Minute", "Hour"
    secondaryUnitName TEXT,     -- "Volume", "Season"; '' = explicitly none; NULL = inherit
    actionVerb        TEXT,     -- "Read", "Watch", "Play"
    hasParts          INTEGER CHECK (hasParts IN (0,1)),
    hasCharEstimate   INTEGER CHECK (hasCharEstimate IN (0,1)),
    hasAudioSpeed     INTEGER CHECK (hasAudioSpeed IN (0,1)),
    lengthUnit        TEXT    CHECK (lengthUnit IN ('CHARS','WORDS')),

    sortOrder         INTEGER NOT NULL DEFAULT 0,
    createdAt         INTEGER NOT NULL,
    updatedAt         INTEGER NOT NULL,
    deletedAt         INTEGER,

    CHECK (id <> parentId),
    CHECK (
        parentId IS NOT NULL OR (
            icon IS NOT NULL AND unitName IS NOT NULL AND actionVerb IS NOT NULL
            AND hasParts IS NOT NULL AND hasCharEstimate IS NOT NULL
            AND hasAudioSpeed IS NOT NULL AND lengthUnit IS NOT NULL
        )
    )
);

CREATE INDEX index_categories_parentId  ON categories(parentId);
CREATE INDEX index_categories_sortOrder ON categories(sortOrder);

-- No duplicate names among siblings (case-insensitive)
CREATE UNIQUE INDEX index_categories_unique_name
    ON categories(COALESCE(parentId, ''), lower(name))
    WHERE deletedAt IS NULL;
```

**Depth triggers (max 2 levels, no cycles):**
```sql
CREATE TRIGGER trg_categories_depth_insert
BEFORE INSERT ON categories
WHEN NEW.parentId IS NOT NULL
 AND (SELECT parentId FROM categories WHERE id = NEW.parentId) IS NOT NULL
BEGIN
    SELECT RAISE(ABORT, 'categories: max depth is 2');
END;

CREATE TRIGGER trg_categories_depth_update
BEFORE UPDATE OF parentId ON categories
WHEN NEW.parentId IS NOT NULL
 AND (
      (SELECT parentId FROM categories WHERE id = NEW.parentId) IS NOT NULL
      OR EXISTS (SELECT 1 FROM categories WHERE parentId = NEW.id)  -- it already has children
 )
BEGIN
    SELECT RAISE(ABORT, 'categories: max depth is 2');
END;
```

> **Room note:** Room does not know about triggers or expression indexes. Create them in `RoomDatabase.Callback.onCreate` **and** in the V4→V5 `Migration`, not as entity `@Index`. Declare `active_media_items` with `@DatabaseView`.

#### Seed template (fixed IDs, fully editable)
Use these **exact same IDs** in both the fresh-DB seed and the migration (so both paths give an identical database).

| id | name | parent | icon | unit | secondary | verb | parts | charEst | audio | lengthUnit |
|---|---|---|---|---|---|---|---|---|---|---|
| `cat_uncategorized` | Uncategorized | NULL | folder | Item | `''` | Consume | 0 | 0 | 0 | WORDS |
| `cat_novel` | Novel | NULL | book | Chapter | `''` | Read | 0 | 1 | 1 | WORDS |
| `typ_novel_cn` | Web Novel (CN) | cat_novel | NULL | NULL | NULL | NULL | NULL | NULL | NULL | CHARS |
| `typ_novel_kr` | Web Novel (KR) | cat_novel | NULL | NULL | NULL | NULL | NULL | NULL | NULL | CHARS |
| `typ_novel_ln` | Light Novel | cat_novel | NULL | NULL | Volume | NULL | 1 | NULL | NULL | WORDS |
| `cat_manga` | Manga | NULL | image | Chapter | `''` | Read | 0 | 0 | 0 | WORDS |
| `typ_manga_jp` | Manga (JP) | cat_manga | NULL | NULL | Volume | NULL | 1 | NULL | NULL | NULL |
| `typ_manhwa` | Manhwa (KR) | cat_manga | NULL | NULL | NULL | NULL | NULL | NULL | NULL | NULL |
| `typ_manhua` | Manhua (CN) | cat_manga | NULL | NULL | NULL | NULL | NULL | NULL | NULL | NULL |
| `cat_anime` | Anime | NULL | tv | Episode | `''` | Watch | 0 | 0 | 0 | WORDS |
| `typ_anime_series` | Anime Series | cat_anime | NULL | NULL | Season | NULL | 1 | NULL | NULL | NULL |
| `typ_anime_movie` | Anime Movie | cat_anime | NULL | Minute | NULL | NULL | NULL | NULL | NULL | NULL |
| `typ_anime_ova` | OVA / Special | cat_anime | NULL | NULL | NULL | NULL | NULL | NULL | NULL | NULL |
| `cat_movie` | Movie | NULL | film | Minute | `''` | Watch | 0 | 0 | 0 | WORDS |
| `cat_tv` | TV Series | NULL | tv | Episode | Season | Watch | 1 | 0 | 0 | WORDS |
| `cat_game` | Game | NULL | gamepad | Hour | `''` | Play | 0 | 0 | 0 | WORDS |

Notes:
- Types were renamed (`Anime Series`, `Anime Movie`) so the name never duplicates the live-action top-level `Movie` / `TV Series`.
- `Manga` (parent) now has `hasParts = 0`; only the types that really have volumes set it to 1. v1 had the parent at 1 and some types at 0, which gave contradictory results.
- The category `Uncategorized` is a **safety net**. It can be renamed but not deleted while it holds items.

### 2.2 `media_items`
```sql
CREATE TABLE media_items (
    id                     TEXT PRIMARY KEY,
    categoryId             TEXT NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    title                  TEXT NOT NULL CHECK (length(trim(title)) > 0),
    alternativeTitles      TEXT NOT NULL DEFAULT '[]',
    description            TEXT NOT NULL DEFAULT '',
    author                 TEXT NOT NULL DEFAULT '',
    coverImagePath         TEXT NOT NULL DEFAULT '',
    coverImageUrl          TEXT,
    userStatus             TEXT NOT NULL
        CHECK (userStatus IN ('PLANNING','IN_PROGRESS','COMPLETED','ON_HOLD','DROPPED')),

    -- Progress (REAL: supports ch. 12.5 and 1.5 hours)
    progress               REAL NOT NULL DEFAULT 0 CHECK (progress >= 0),
    progressTotal          REAL CHECK (progressTotal IS NULL OR progressTotal >= 0),
    secondaryProgress      REAL NOT NULL DEFAULT 0 CHECK (secondaryProgress >= 0),
    secondaryProgressTotal REAL,

    -- Mode & calculation inputs
    actionOverride         TEXT CHECK (actionOverride IS NULL OR actionOverride IN ('Read','Listen')),
    avgChapterLength       INTEGER CHECK (avgChapterLength IS NULL OR avgChapterLength > 0),
    lengthUnitOverride     TEXT CHECK (lengthUnitOverride IS NULL OR lengthUnitOverride IN ('CHARS','WORDS')),
    playbackSpeed          REAL NOT NULL DEFAULT 1.0 CHECK (playbackSpeed > 0 AND playbackSpeed <= 4.0),

    rating                 REAL CHECK (rating IS NULL OR (rating >= 0 AND rating <= 10)),
    notes                  TEXT NOT NULL DEFAULT '',
    genres                 TEXT NOT NULL DEFAULT '[]',   -- JSON; junction table is a later phase
    tags                   TEXT NOT NULL DEFAULT '[]',   -- JSON; junction table is a later phase
    startDate              INTEGER,
    endDate                INTEGER,
    createdAt              INTEGER NOT NULL,
    updatedAt              INTEGER NOT NULL,
    deletedAt              INTEGER
);

CREATE INDEX index_media_items_category_status
    ON media_items(categoryId, userStatus) WHERE deletedAt IS NULL;
CREATE INDEX index_media_items_updatedAt ON media_items(updatedAt DESC);
CREATE INDEX index_media_items_deletedAt ON media_items(deletedAt);

-- The ONLY thing DAOs should read for UI lists/details
CREATE VIEW active_media_items AS
    SELECT * FROM media_items WHERE deletedAt IS NULL;
```

Changes:
- `lengthUnitOverride`: per-item override of the resolved `lengthUnit`. Needed because `avgChapterLength` is meaningless without a unit.
- `playbackSpeed` accepts any value in `(0, 4.0]`. The 5 chips (1.0 to 2.0) are shortcuts; the UI also allows a custom value (0.75x, 2.5x are common).
- `actionOverride = 'Listen'` is **only valid when the resolved `hasAudioSpeed = 1`**. The repository rejects it otherwise; the UI hides the toggle.

### 2.3 `media_parts` (structure only, no progress)
```sql
CREATE TABLE media_parts (
    id         TEXT PRIMARY KEY,
    mediaId    TEXT NOT NULL REFERENCES media_items(id) ON DELETE CASCADE,
    name       TEXT NOT NULL,                 -- "Volume 1", "Season 2", "Arc 1"
    sortOrder  INTEGER NOT NULL,
    totalUnits REAL CHECK (totalUnits IS NULL OR totalUnits > 0),  -- units inside this part
    createdAt  INTEGER NOT NULL,
    updatedAt  INTEGER NOT NULL,
    UNIQUE (mediaId, sortOrder)
);
CREATE INDEX index_media_parts_mediaId ON media_parts(mediaId);
```

**Single source of truth rule:**
- `media_items.progress` is the only stored progress.
- A part's range is derived from cumulative `totalUnits` ordered by `sortOrder`:
  `part_i covers (sum(total_1..i-1), sum(total_1..i)]`.
- A part is **completed** iff `progress >= sum(total_1..i)`. Never stored.
- **Current part** = the first part not yet completed.
- `secondaryProgress` is **derived** (= number of completed parts + 0 if none) when parts exist; it is a manual counter only when the item has no part rows.
- Completing a part in the UI = setting `progress` to the end of that part (recorded as a bulk jump, `counted = 0`, see 2.5).
- If `hasParts = 1` but there are no part rows, show the flat counters (no empty breakdown).
- If any part has `totalUnits = NULL`, parts after it cannot be derived. The UI shows those parts as "unknown range" and disables per-part checkboxes after it.
- `progressTotal`, when parts exist and every `totalUnits` is known, is **derived** (= sum). If the user also typed a value, the sum wins and a mismatch hint is shown.

### 2.4 `media_links`
```sql
CREATE TABLE media_links (
    id             TEXT PRIMARY KEY,
    mediaId        TEXT NOT NULL REFERENCES media_items(id) ON DELETE CASCADE,
    kind           TEXT NOT NULL CHECK (kind IN ('SOURCE','METADATA','TRACKER')),
    providerId     TEXT NOT NULL,             -- 'anilist','royalroad','tmdb','igdb','tsundoku'
    remoteId       TEXT,                      -- NULL while PENDING_MATCH
    url            TEXT,
    state          TEXT NOT NULL DEFAULT 'LINKED'
        CHECK (state IN ('LINKED','PENDING_MATCH','NOT_FOUND','SKIPPED')),
    lastSyncedAt   INTEGER,
    syncedSnapshot TEXT NOT NULL DEFAULT '{}',
    createdAt      INTEGER NOT NULL,
    updatedAt      INTEGER NOT NULL,
    CHECK (state <> 'LINKED' OR remoteId IS NOT NULL OR url IS NOT NULL)
);

CREATE UNIQUE INDEX index_media_links_unique_provider ON media_links(mediaId, kind, providerId);
CREATE INDEX index_media_links_media  ON media_links(mediaId);
CREATE INDEX index_media_links_remote ON media_links(providerId, remoteId) WHERE remoteId IS NOT NULL;
```

### 2.5 `activity` (also the source of truth for time spent)
```sql
CREATE TABLE activity (
    id             TEXT PRIMARY KEY,          -- UUID (safe for future sync)
    mediaId        TEXT NOT NULL REFERENCES media_items(id) ON DELETE CASCADE,
    type           TEXT NOT NULL
        CHECK (type IN ('PROGRESS_UPDATE','STATUS_CHANGE','RATING','CREATED','DELETED','RESTORED')),
    oldValue       TEXT,
    newValue       TEXT NOT NULL,

    -- Only meaningful for PROGRESS_UPDATE
    delta          REAL,                      -- units consumed in this event (can be negative for undo)
    mode           TEXT CHECK (mode IS NULL OR mode IN ('READ','LISTEN','WATCH','PLAY','OTHER')),
    speed          REAL,                      -- playbackSpeed snapshot (1.0 for non-audio)
    lengthSnapshot INTEGER,                   -- avgChapterLength at that moment
    lengthUnit     TEXT CHECK (lengthUnit IS NULL OR lengthUnit IN ('CHARS','WORDS')),
    rateSnapshot   REAL,                      -- base rate (units/min) used at that moment
    counted        INTEGER NOT NULL DEFAULT 1 CHECK (counted IN (0,1)),  -- 0 = manual jump/import/part-complete

    timestamp      INTEGER NOT NULL
);
CREATE INDEX index_activity_media_time ON activity(mediaId, timestamp);
CREATE INDEX index_activity_time       ON activity(timestamp DESC);
```

**Activity rules**
- Every progress or status change writes a row **in the same transaction**.
- **Coalescing:** a `PROGRESS_UPDATE` for the same `mediaId` + `mode` within **30 seconds** of the previous row updates that row (`delta += d`, `newValue`, `timestamp`) instead of inserting a new one. This stops `[+]` tap spam from flooding the log.
- If coalescing makes `delta` exactly 0, delete the row.
- `counted = 0` for: typing an absolute progress value, completing a part, status-based bulk completion, imports and migration. These change `progress` but add **no time** to the stats.

### 2.6 `sync_outbox` (decoupled on purpose)
```sql
CREATE TABLE sync_outbox (
    id        TEXT PRIMARY KEY,
    mediaId   TEXT NOT NULL,                  -- NO foreign key: purging an item must never destroy unsynced changes
    action    TEXT NOT NULL,
    payload   TEXT NOT NULL,
    createdAt INTEGER NOT NULL
);
CREATE INDEX index_sync_outbox_createdAt ON sync_outbox(createdAt);
```
Tombstone purge job rule: **only purge `media_items` rows with `deletedAt` older than N days AND no outbox rows for that `mediaId`.**

### 2.7 `app_settings` (reading/listening rates)
```sql
CREATE TABLE app_settings (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
```
Defaults seeded (units per minute at 1.0x), all user-editable:

| key | default | meaning |
|---|---|---|
| `rate.READ.WORDS` | 240 | silent reading, words/min |
| `rate.READ.CHARS` | 500 | silent reading, chars/min (CN/KR/JP) |
| `rate.LISTEN.WORDS` | 150 | TTS / audiobook, words/min |
| `rate.LISTEN.CHARS` | 250 | TTS, chars/min |
| `activity.coalesceSeconds` | 30 | |
| `tombstone.retainDays` | 30 | |

---

## 3. Calculations (event-sourced)

### 3.1 Length estimates (need `hasCharEstimate = 1`, `avgChapterLength` set)
- Consumed = `progress × avgChapterLength`
- Projected total = `progressTotal × avgChapterLength` (show "—" when `progressTotal IS NULL`, e.g. ongoing series)
- Unit label comes from `lengthUnitOverride ?: resolved.lengthUnit` (chars or words).
- Example: 500 ch × 3,000 chars = **1,500,000 chars**.

### 3.2 Time spent (from the activity log, not from current settings)
```
minutes = Σ over PROGRESS_UPDATE where counted = 1 of
          delta × lengthSnapshot / (rateSnapshot × speed)
```
Group by `mode` to get **time read** vs **time listened** separately.

Why this is correct:
- Changing speed 1.0x → 2.0x only affects **new** events. History is not rewritten.
- Switching Read → Listen at chapter 300 does not relabel the first 300 chapters.
- Editing the rate in settings does not change past totals (each row stores `rateSnapshot`).
- Reading mode now has math too (`speed = 1.0`, READ rate).
- Items without `avgChapterLength` contribute no time (shown as "—", not 0).
- For Watch/Play categories (`unit = Minute` or `Hour`), time = `delta` converted to minutes (no rate math). Keep it simple: `mode = WATCH/PLAY`, `lengthSnapshot` NULL, minutes = `delta × (unit == Hour ? 60 : 1)`; for Episode units with no runtime data, time stays "—".

*Example:* 1,500,000 chars listened at 1.5x with rate 250 → `1,500,000 / (250 × 1.5)` = **4,000 min ≈ 66.7 h**.

### 3.3 Progress mutation rules
```sql
-- atomic increment with clamping (never read-modify-write in Kotlin)
UPDATE media_items
SET progress  = MAX(0, MIN(progress + :d, COALESCE(progressTotal, progress + :d))),
    updatedAt = :now
WHERE id = :id AND deletedAt IS NULL;
```
Then, in the same transaction, read the row back and apply:
1. First progress above 0 while `PLANNING` → `IN_PROGRESS`, set `startDate` if NULL.
2. `progressTotal` not NULL and `progress >= progressTotal` → `COMPLETED`, set `endDate`.
3. Decrement below total while `COMPLETED` → back to `IN_PROGRESS`, clear `endDate`.
4. Setting `COMPLETED` manually on an item with a known total → set `progress = progressTotal` (a `counted = 0` jump).
5. Write the activity row (with coalescing) using the **effective delta** after clamping, not the requested delta.

---

## 4. UI Architecture & Design System (Operate Mode)

### 4.1 Dex Screen: Adaptive Two-Tier Category Rail

```
+-------------------------------------------------------------+
|  TopAppBar: "LazyDex"               [Search] [Filter] [More]|
+-------------------------------------------------------------+
|  TIER 1 (Category Rail - Horizontal Scroll):                |
|  (● All)   ( Novel )   ( Manga )   ( Anime )   ( Movie ) [+]|
|   ↑ primary tonal pill                                      |
+ - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
|  TIER 2 (Type Sub-Rail - Animated SurfaceContainerLow):     |
|  [ All Novels ]  [ Web Novel CN ]  [ Light Novel ]  [ + Type ]
+-------------------------------------------------------------+
|  MEDIA FEED (LazyColumn / Adaptive Grid)                    |
|                                                             |
|  +-------------------------------------------------------+  |
|  | [Cover]  Lord of the Mysteries                         |  |
|  |          Web Novel (CN) · In Progress                 |  |
|  |          ---------------------------------            |  |
|  |          Ch. 420 of 1,432 (29%)                       |  |
|  |          [==========----------------------]           |  |
|  |          ~1.26M chars · 56h listened · 1.5x           |  |
|  |                                                       |  |
|  |          [ -1 ]  [  +1 Chapter  ]   [ Quick Listen ]  |  |
|  +-------------------------------------------------------+  |
|                                                             |
|  +-------------------------------------------------------+  |
|  | [Cover]  Overlord                                      |  |
|  |          Light Novel · In Progress                     |  |
|  |          ---------------------------------            |  |
|  |          Vol. 3, Ch. 12 of 14                         |  |
|  |          [=======================---------]           |  |
|  |                                                       |  |
|  |          [ -1 ]  [  +1 Chapter  ]   [ Next Vol ]      |  |
|  +-------------------------------------------------------+  |
+-------------------------------------------------------------+
```

1. **Tier 1 (Main Categories Rail)**:
   - Horizontal scrolling row of Material 3 `FilterChip` components.
   - Includes `[All]`, user categories ordered by `sortOrder`, and trailing `[+ Category]` chip.
   - Selected chip uses `FilterChipDefaults.filterChipColors(selectedContainer = MaterialTheme.colorScheme.primaryContainer)`.
2. **Tier 2 (Adaptive Type Sub-Rail)**:
   - Housed inside `Surface(color = MaterialTheme.colorScheme.surfaceContainerLow)` with subtle bottom corner rounding (16.dp).
   - Animates in/out via `AnimatedVisibility(visible = hasTypes, enter = expandVertically(tween(200)), exit = shrinkVertically(tween(200)))`. Zero layout popping.
   - Displays `[All <Category>]`, child types, an implicit `[Unsorted]` chip (only when items exist directly on parent category with count > 0), and `[+ Type]`.
   - Collapses completely when the selected category has zero subtypes (e.g. `Movie`).
3. **Filter Semantics**:
   - Tier 1 chip $\rightarrow$ `categoryId IN (self + all child types)`.
   - Tier 2 type chip $\rightarrow$ `categoryId = type.id`.
   - `Unsorted` chip $\rightarrow$ `categoryId = category.id`.
4. **State Retention**:
   - Remembers the last selected subtype per category during the active session.
   - Defaults Tier 2 to `[All <Category>]` when switching top categories.
5. **Deletion Safety**:
   - Count `media_items WHERE categoryId IN (self + children)` including tombstones.
   - If count > 0, deletion is blocked; dialog presents **Reassign all** or **Purge tombstones** (only allowed when all remaining rows are tombstones and outbox is drained).
   - `RESTRICT` foreign key guarantees parent cannot be deleted while children exist.

---

### 4.2 Category & Type Creator BottomSheet

Replaces database terminology with human intent cards and progressive disclosure:

```
+-------------------------------------------------------------+
| [━] Drag Handle                                             |
| Create Category                                    [ Save ] |
+-------------------------------------------------------------+
| IDENTITY                                                    |
| Name:   [ Novel                             ]               |
| Icon:   ( [Book] IconPicker )   Color: ( [ Indigo ] Picker )|
+-------------------------------------------------------------+
| TRACKING LABELS                                             |
| Unit Name:   [ Chapter ]      Action Verb:  [ Read    ]     |
+-------------------------------------------------------------+
| CAPABILITIES & MODULES                                      |
|                                                             |
| [X] Volumes & Multi-Part Structure                          |
|     Secondary Unit: [ Volume ]                              |
|     "Enables tracking items split into volumes or seasons"  |
|                                                             |
| [X] Reading & Character Metrics                             |
|     Measure: (o) Characters (CN/KR/JP)   ( ) Words (EN)     |
|     "Estimates total length from average chapter size"      |
|                                                             |
| [X] Audio & TTS Playback Tracker                            |
|     "Enables speed selector and calculates hours listened"  |
+-------------------------------------------------------------+
```

#### Subtype Creation Flow:
- When opened via `[+ Type]`, top displays persistent badge: `Parent: Novel`.
- Every setting defaults to a compact **Inheritance Pill**:
  - Unit Name: `[ Inherit: "Chapter" ]` `[ Customize ]`
  - Action Verb: `[ Inherit: "Read" ]` `[ Customize ]`
  - Volumes: `[ Inherit: Disabled ]` `[ Customize ]`
  - Character Metrics: `[ Inherit: Enabled ]` `[ Customize ]`
- Tapping `[ Customize ]` seamlessly reveals the override input field. Null values in DB cleanly inherit parent settings.

---

### 4.3 Unified ItemDetailScreen & Tracking Console

All component rendering is driven by `ResolvedCategoryConfig`:

```
+-------------------------------------------------------------+
| [<- Back]              [ Hero Cover Backdrop ]       [Edit] |
|                        [ Poster Thumbnail    ]              |
|                        Lord of the Mysteries                |
|                        Cuttlefish That Loves Diving         |
|                        [ Novel ] [ Web Novel (CN) ]         |
+-------------------------------------------------------------+
| CONSUMPTION CONSOLE                                         |
|                                                             |
| Mode Switcher (Shown only if hasAudioSpeed = 1):            |
| [   📖 Reading   ]    [   🎧 Listening (TTS)   ]            |
|                                                             |
| Playback Speed (Active when in Listening mode):             |
| ( 1.0x )   ( 1.25x )   (● 1.5x )   ( 1.75x )   ( 2.0x )     |
|                                                             |
| Progress:                                                   |
| [- 5]  [ - 1 ]     Chapter 420 / 1,432     [ + 1 ]  [+ 5]   |
| [==================---------------------------------------] |
|                                                             |
| Stats Dashboard:                                            |
| +---------------------------------------------------------+ |
| | Time Listened | Chars Consumed | Total Projected Length | |
| |    56.0 hrs   |    1.26M chars |     4.29M chars        | |
| +---------------------------------------------------------+ |
|  Avg Chapter Length: [ 3,000 ] chars/ch                     |
+-------------------------------------------------------------+
| VOLUME / SEASON BREAKDOWN (Accordion - if hasParts = 1)     |
|                                                             |
| > Volume 1: Clown (Ch. 1 - 213)                 [✓ Complete]|
| > Volume 2: Faceless (Ch. 214 - 482)            [ 74% Done ]|
|   Progress inside Vol 2: Ch. 420 / 482                      |
| > Volume 3: Traveler (Ch. 483 - 732)            [  Pending ]|
+-------------------------------------------------------------+
```

1. **Mode Switcher**: Segmented pill toggles `Read` vs `Listen`. Hidden if `hasAudioSpeed = 0`.
2. **Audio & Speed Controls**:
   - Quick preset chips (`1.0x`, `1.25x`, `1.5x`, `1.75x`, `2.0x`) plus custom numerical field (e.g. `2.5x`).
   - Time spent & projected remaining time calculate live from `activity` event snapshots.
3. **Progress Console & Ergonomics**:
   - Primary `+1` / `-1` and `+5` / `-5` buttons have minimum touch target of 48.dp.
   - Haptic feedback triggered on taps via `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.TextHandleMove)`.
   - Rapid taps coalesce inside 30 seconds into a single `activity` log row.
   - Snackbar surfaces after tap: *"Updated to Chapter 421"* with `[ Undo ]` action (3s window).
4. **Volume Breakdown Accordion**:
   - If item has `media_parts`, lists volumes with their derived chapter spans (`sum(total_1..i-1)` to `sum(total_1..i)`).
   - Tapping volume completion checkbox sets `progress` to the end of that part with `counted = 0` (no fake hours added to listening stats).
   - If no parts rows exist, flat primary and secondary counters are shown.
5. **Category Switching Resilience**:
   - When user changes item's category or subtype, existing parts, custom avg lengths, and audio speeds are **preserved in DB but hidden in UI**. Reappears cleanly if switched back.

---

## 5. Migration V4 → V5 (safe table rebuild)

**Pre-flight (Kotlin, before the SQL):**
1. Copy the DB file (`.db`, `-wal`, `-shm`) to `backups/pre_v5_<timestamp>.db`. Keep the last 2 backups.
2. Inspect V4: does any other V4 table reference `media_items`? If yes, those tables must be rebuilt in the same migration (otherwise the drop will cascade/violate).

**SQL (inside the Room migration transaction):**
```sql
PRAGMA defer_foreign_keys = ON;   -- foreign_keys pragma itself cannot be changed inside a transaction

-- 1. categories + triggers + indexes (section 2.1)
-- 2. seed rows with the FIXED IDs from the seed table (INSERT with explicit ids)

-- 3. new table
CREATE TABLE media_items_new ( /* section 2.2 definition */ );

-- 4. copy, with defensive mapping
INSERT INTO media_items_new (
    id, categoryId, title, alternativeTitles, description, author,
    coverImagePath, coverImageUrl, userStatus,
    progress, progressTotal, secondaryProgress, secondaryProgressTotal,
    actionOverride, avgChapterLength, lengthUnitOverride, playbackSpeed,
    rating, notes, genres, tags, startDate, endDate,
    createdAt, updatedAt, deletedAt
)
SELECT
    id,
    COALESCE(
      CASE upper(trim(category))
        WHEN 'NOVEL'        THEN 'cat_novel'
        WHEN 'LIGHT_NOVEL'  THEN 'typ_novel_ln'
        WHEN 'WEB_NOVEL'    THEN 'cat_novel'
        WHEN 'MANGA'        THEN 'cat_manga'
        WHEN 'MANHWA'       THEN 'typ_manhwa'
        WHEN 'MANHUA'       THEN 'typ_manhua'
        WHEN 'ANIME'        THEN 'cat_anime'
        WHEN 'MOVIE'        THEN 'cat_movie'
        WHEN 'TV'           THEN 'cat_tv'
        WHEN 'TV_SERIES'    THEN 'cat_tv'
        WHEN 'GAME'         THEN 'cat_game'
      END,
      'cat_uncategorized'              -- unknown / NULL / typo never crashes the migration
    ),
    title, COALESCE(alternativeTitles,'[]'), COALESCE(description,''), COALESCE(author,''),
    COALESCE(coverImagePath,''), coverImageUrl,
    CASE WHEN userStatus IN ('PLANNING','IN_PROGRESS','COMPLETED','ON_HOLD','DROPPED')
         THEN userStatus ELSE 'PLANNING' END,
    COALESCE(currentProgress, 0), totalItems, 0, NULL,
    NULL, NULL, NULL, 1.0,
    rating, COALESCE(notes,''), COALESCE(genres,'[]'), COALESCE(tags,'[]'),
    startDate, endDate,
    createdAt, updatedAt,
    CASE WHEN isDeleted = 1 THEN COALESCE(updatedAt, createdAt) END   -- real timestamp, not "1"
FROM media_items;
-- (adjust column names to the real V4 schema; verify before shipping)

-- 5. swap
DROP TABLE media_items;
ALTER TABLE media_items_new RENAME TO media_items;

-- 6. new tables: media_parts, media_links, activity, sync_outbox, app_settings (+ seed settings)

-- 7. legacy sourceUrl -> media_links
INSERT INTO media_links (id, mediaId, kind, providerId, remoteId, url, state,
                         syncedSnapshot, createdAt, updatedAt)
SELECT lower(hex(randomblob(16))), id, 'SOURCE', 'manual', NULL, sourceUrl, 'LINKED',
       '{}', createdAt, updatedAt
FROM media_items_old_source   -- read sourceUrl BEFORE step 5 from the old table, or keep a temp copy
WHERE sourceUrl IS NOT NULL AND trim(sourceUrl) <> '';

-- 8. indexes + view + triggers (re-create everything after the rename)
-- 9. one CREATED activity row per migrated item, counted = 0
INSERT INTO activity (id, mediaId, type, newValue, counted, timestamp)
SELECT lower(hex(randomblob(16))), id, 'CREATED', 'migrated_v5', 0, createdAt FROM media_items;

PRAGMA foreign_key_check;   -- run from Kotlin after the migration; any row returned = fail loudly in debug builds
```
Order fix: **step 7 must read `sourceUrl` before the old table is dropped** (create `CREATE TEMP TABLE legacy_source AS SELECT id, sourceUrl, createdAt, updatedAt FROM media_items WHERE sourceUrl IS NOT NULL` first, then insert from the temp table).

**Migration tests (`MigrationTestHelper`) must cover:**
1. Empty V4 database.
2. Rows with an unknown category string, NULL category, lower-case category.
3. `isDeleted = 1` rows (expect non-null `deletedAt`, hidden in `active_media_items`).
4. NULL / empty / duplicate `sourceUrl`.
5. Fresh-install DB vs migrated DB: schema, seed IDs and triggers are identical.
6. `PRAGMA foreign_key_check` returns zero rows; `PRAGMA integrity_check` is `ok`.
7. Row counts before = after.

---

## 6. Repository & Transaction Rules
- All reads go through `active_media_items` (or explicitly named `*_includingDeleted` queries used only by purge/delete-check code).
- `MediaRepositoryImpl` runs every progress increment, part completion, status change, mode switch, and rating change inside one `db.withTransaction { }`.
- Progress uses the atomic SQL in 3.3 (never read-modify-write in Kotlin), then applies status rules and writes the activity row (with coalescing) in the same transaction.
- Every mutation also appends a `sync_outbox` row in that same transaction.
- `getResolvedConfig(categoryId)` is the only way the UI learns behavior; it merges Type over Category and is cached/invalidated on category edits.
- Soft delete = set `deletedAt`, write a `DELETED` activity row; restore clears it and writes `RESTORED`.
- Category delete = the check in 4.1, done in a transaction with the delete itself.
- Backup/export must include `categories` (definitions), `app_settings`, and everything else, otherwise restored items lose their taxonomy.

## 7. Known Deferred Items (accepted for Phase 0)
- `genres` / `tags` stay JSON text (not indexable). Plan junction tables `item_genres`, `item_tags` in a later phase.
- Runtime/episode-length data for accurate Watch time on Episode units.
- Real multi-device sync conflict resolution (the `updatedAt`, UUID ids and outbox are in place for it).

## 8. Definition of Done
- [ ] V5 schema created by fresh install and by migration gives the same result.
- [ ] Migration tests (section 5) pass.
- [ ] `ResolvedCategoryConfig` unit tests: type overrides, type inherits, `secondaryUnitName = ''` override.
- [ ] Trigger tests: depth > 2 rejected, duplicate sibling name rejected, delete with children or items rejected.
- [ ] Progress tests: clamp, status transitions, coalescing, undo (negative delta), `counted = 0` jumps.
- [ ] Time-math tests: speed change mid-history does not alter old totals; read vs listen separated.
- [ ] No DAO query reads `media_items` directly outside the explicit includingDeleted paths.