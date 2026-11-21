# MALSync 2-Way Sync Architecture & LazyDex Reference

> **Source**: `E:\rockyxwall\04_Github\lazydex\.tmp\malsync\src` (`utils/syncHandler.ts`, `background/listSync.ts`, `_provider/AniList/*`, `_provider/Local/*`, `_provider/singleAbstract.ts`, `_provider/listAbstract.ts`)  
> **Related**: [`anilist-api.md`](anilist-api.md), [`mihon-sync.md`](mihon-sync.md), [`mihon-ui.md`](mihon-ui.md)  
> **Target Implementations**: `AnilistSyncEngine.kt`, `ThreeWayMergeResolver.kt`, `AnilistApi.kt`, `MediaTrackerSyncDialog.kt`

---

## 1. Executive Summary: What MALSync Actually Does

MALSync is a multi-provider browser extension and userscript that synchronizes anime and manga lists between multiple remote services (MyAnimeList, AniList, Kitsu, Simkl, Shikimori, MangaBaka) and a local storage provider.

### Core Architectural Insights from MALSync
1. **Strict Decoupling of List Sync vs. Metadata Overview**:
   - In MALSync's `AniList/list.ts`, list retrieval queries **NEVER request relational joins like `tags { ... }` or multi-KB text blobs like `description`**.
   - List sync only retrieves tracking attributes (`progress`, `status`, `score`, `dates`, `repeat`, `notes`) and minimal identifiers (`id`, `idMal`, `title`, `coverImage`, `bannerImage`, `total episodes/chapters/volumes`).
   - Deep metadata (HTML description, synonyms, characters, staff, reviews, recommendations) is queried **strictly on demand for a single item** via `MetaOverview` (`AniList/metaOverview.ts`).
2. **Master-Slave Synchronization Flow**:
   - One provider is elected as **Master** (e.g. MyAnimeList or Local Storage).
   - All other connected providers act as **Slaves** (e.g. AniList).
   - Diffs are calculated strictly unidirectionally (`Master -> Slave`).
3. **Pacing & Rate-Limit Safeguards**:
   - Full list synchronization in MALSync executes mutations sequentially with an explicit `3000ms` pause (`utils.wait(3000)`) between each `SaveMediaListEntry` call to guarantee total immunity from AniList's 90 req/min rate limit.
4. **Context-Aware Date Invariance (`fixDates`)**:
   - Automatic start/finish date population (`fixDates()`) is **strictly bypassed during `listSync`** (`if (this.getSyncMethod() === 'listSync') return;`). It only activates on `normal` interactive user reading/watching progress events.

---

## 2. MALSync List Query Architecture (`AniList/list.ts`)

MALSync retrieves the user's AniList list using a paginated GraphQL query with 100 items per page:

```graphql
query ($page: Int, $userName: String, $type: MediaType, $status: MediaListStatus, $sort: [MediaListSort]) {
  Page (page: $page, perPage: 100) {
    pageInfo {
      hasNextPage
    }
    mediaList (status: $status, type: $type, userName: $userName, sort: $sort) {
      status
      startedAt { year month day }
      completedAt { year month day }
      repeat
      score(format: POINT_100)
      progress
      progressVolumes
      notes
      media {
        siteUrl
        id
        idMal
        episodes
        chapters
        volumes
        status
        averageScore
        coverImage {
          large
          extraLarge
        }
        bannerImage
        title {
          userPreferred
        }
      }
    }
  }
}
```

### Why This Is Fast:
- **No `tags`**: AniList tags require joining the `media_tags` relational table (30–60 tags per item). Omitting tags prevents 3,000–6,000 joins per 100-item page.
- **No `description`**: Omitting HTML descriptions saves 100–400 KB of string parsing and transfer per page.
- **Payload size**: A 100-item page is only **~40–60 KB** and executes on AniList's database in **< 1.5 seconds**.

### Status Enumeration & The "All" List Pattern:
In MALSync definitions:
- `status.All = 7`
- When retrieving all entries, `variables.status` evaluates to `undefined`, which GraphQL interprets as no filter, returning all list entries across all user lists.
- Pagination runs in a `do { await this.getNext(); } while (!this.done)` loop until `pageInfo.hasNextPage == false`.

---

## 3. The 2-Way Sync Engine (`utils/syncHandler.ts`)

### Phase 1: Parallel Fetch & Mapping (`retriveLists`)
```typescript
// Fetches Master and all Slave lists concurrently via Promise.all
const listOptions = await retriveLists(providerList, type, getList);
```
- Entries are indexed into a hash map keyed by canonical `malId`:
  ```typescript
  resultList[el.malId] = {
    diff: false,
    master: el,
    slaves: [slave1, slave2, ...]
  };
  ```

### Phase 2: Diff Generation (`changeCheck`)
Compares each slave entry against the master:

| Field | MALSync Rule (`changeCheck`) | Invariance / Clamping |
| :--- | :--- | :--- |
| **Score** | `slave.score !== master.score` | Sets `slave.diff.score = master.score`. |
| **Progress (Episodes / Chapters)** | `slave.watchedEp !== master.watchedEp` | **Completion Clamping**: If `master.status === COMPLETED`, clamps `slave.diff.watchedEp = slave.totalEp`. Otherwise `master.watchedEp`. |
| **Volumes (Manga)** | `slave.readVol !== master.readVol` | **Volume Clamping**: If `master.status === COMPLETED`, clamps `slave.diff.readVol = slave.totalVol`. Otherwise `master.readVol`. |
| **Status** | `normalizeStatus(slave.status) !== normalizeStatus(master.status)` | Normalizes `Considering` $\rightarrow$ `PlanToWatch`. If diff, sets `slave.diff.status`. |
| **Dates** | `slave.startDate !== master.startDate \|\| slave.finishDate !== master.finishDate` | Checked only if provider supports dates (`shouldCheckDates`). |
| **Rewatch Count** | `slave.rewatchCount !== master.rewatchCount` | Checked only if provider supports rewatch counter (`shouldCheckRewatchCount`). |

### Phase 3: Missing Check (`missingCheck`)
- Identifies items that exist in Master but do NOT exist in a Slave.
- Creates an outbound entry marked with `syncType`.
- **CRITICAL DIFFERENCE**: MALSync **NEVER deletes remote slave items** that do not exist in Master. It only pushes missing additions.

### Phase 4: Sequential Execution with Rate-Limit Pacing (`syncList`)
```typescript
export async function syncList(list, thisMissing) {
  // 1. Sync modified items
  for (const i in list) {
    const el = list[i];
    if (el.diff) {
      await syncListItem(el);
      el.diff = false;
    }
  }

  // 2. Sync missing items
  for (const miss of thisMissing) {
    await syncMissing(miss);
  }
}
```
Inside `syncItem`:
```typescript
singleClass.setSyncMethod('listSync');
return singleClass.update()
  .then(() => {
    // apply slave.diff values...
    return singleClass.sync();
  })
  .then(() => utils.wait(3000)) // 3-second throttle per mutation!
  .catch(async e => {
    await utils.wait(3000);
    throw e;
  });
```

---

## 4. Single Item Updates vs. Full List Sync (`singleAbstract.ts`)

MALSync defines two explicit operating modes via `syncMethod`:
1. `'normal'`: Interactive tracking updates (user reading/watching a chapter/episode).
2. `'listSync'`: Bulk list synchronization.

### Date Auto-Fill Guard (`fixDates`)
```typescript
private fixDates() {
  // During bulk listSync, NEVER automatically synthesize today's date!
  if (!this.supportsDates() || this.getSyncMethod() === 'listSync') {
    return;
  }

  const today = returnYYYYMMDD();
  if (this.getStartDate() === null && this._getStatus() === definitions.status.Watching && this._getEpisode() > 0) {
    this.setStartDate(today);
  }
  if (this.getFinishDate() === null && this._getStatus() === definitions.status.Completed) {
    this.setFinishDate(today);
    if (this.getStartDate() === null) {
      this.setStartDate(today);
    }
  }
}
```

---

## 5. Metadata Separation: `MetaOverview` vs. `UserList`

| Characteristic | `UserList` (`AniList/list.ts`) | `MetaOverview` (`AniList/metaOverview.ts`) |
| :--- | :--- | :--- |
| **Purpose** | 2-way tracking sync and list mirroring | Rich media inspection / Details view |
| **Frequency** | Periodic background alarm (23h) or user manual trigger | Only when the user navigates to a specific media detail page |
| **Payload Complexity** | Flat, lean (IDs, titles, progress, status, score) | Deep nested graph (HTML descriptions, character edges, staff, reviews, recommendations) |
| **AniList Server Cost** | Low (~1.5s per 100 items, 0 relational joins) | High (single item lookup with multiple joins, ~1.0s) |

---

## 6. How LazyDex Synthesizes MALSync + Komikku

LazyDex combines MALSync's sync intelligence with Komikku's mobile tracker UI and Android architecture:

```
+-----------------------------------------------------------------------------------+
| LazyDex Architecture                                                              |
|                                                                                   |
|  [ Komikku UI Layer ]                                                             |
|   - MediaTrackerSyncDialog.kt (1:1 Komikku Track dialog: status, score, chapter)  |
|   - Interactive chapter/episode progress trigger -> pushDirtyItem()               |
|                                                                                   |
|  [ MALSync Engine Discipline ]                                                    |
|   - Lean List Retrieval (MediaListCollection without heavy relational joins)      |
|   - Completion Clamping (max progress on COMPLETED status)                        |
|   - Date Guard (fixDates only on interactive progress, not on bulk import)        |
|   - Rate-Limit Pacing (TokenBucketLimiter: 85 req/min, priority queues)           |
|                                                                                   |
|  [ LazyDex Local Master Core ]                                                    |
|   - Room SQLite Database (Master source of truth)                                 |
|   - ThreeWayMergeResolver.kt (Snapshot-based 3-way conflict resolution)           |
|   - Fractional Chapter Preservation (e.g. 12.5 local vs 12 remote integer)        |
|   - In-Flight Mutation Fence (dispatchedAt timestamp protects pending mutations)  |
+-----------------------------------------------------------------------------------+
```

### Architectural Comparison Matrix

| Dimension | MALSync (`.tmp/malsync`) | Komikku (Tachiyomi fork) | LazyDex Current & Target |
| :--- | :--- | :--- | :--- |
| **Sync Philosophy** | Master-Slave diffing | Last-write-wins single track | Local SQLite Master with 3-Way Snapshot Diff (`ThreeWayMergeResolver`) |
| **List Query** | `Page.mediaList` (100/page, no tags, no description) | Per-manga direct ID queries | `MediaListCollection` (chunked, lean metadata, fast) |
| **Interactive Tracking UI** | Web overlay / popup | `TrackInfoDialogHome.kt` (Material 3 bottom sheet) | `MediaTrackerSyncDialog.kt` (1:1 Komikku Material 3 bottom sheet) |
| **Progress Increment** | `checkSync` on web video/reader | Auto-tracks on chapter reading | Auto-tracks on reader finish via `pushDirtyItem` (`INTERACTIVE` priority) |
| **Rate Limiter** | `utils.wait(3000)` ad-hoc delays | OkHttp rate-limit interceptor | `TokenBucketLimiter` (token bucket with `INTERACTIVE` vs `BACKGROUND` priority) |
| **Deletions** | Never deletes remote items | Never deletes tracker items | `reconcileRemoteDeletions`: unlinks locally only after 100% verified complete sync |

---

## 7. Golden Rules for LazyDex Tracker Engineering

1. **Never Fetch Tags or Descriptions in List Queries**:
   - `MediaListCollection` is for **list tracking state**. Never request `tags { ... }` or `description` inside mass collection queries.
   - Fetch tags and description on demand when the user opens the Media Details screen or triggers a metadata refresh.
2. **Always Respect In-Flight Mutation Fences**:
   - If `localUserData.updatedAt > dispatchedAt`, do NOT overwrite the local record with the remote response echo.
3. **Preserve Fractional Chapter Reading**:
   - AniList stores integer progress (`12`). If LazyDex has `12.5`, preserve `12.5` locally if `local.toInt() == remote.progress`.
4. **Never Delete Local Media on Remote Unlink**:
   - If an item is deleted or unlinked on AniList, transition the local `media_links` row to `UNLINKED`. Never delete the user's local reading history, chapters, or files.
