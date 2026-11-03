# MALSync 2-Way Sync Model Reference

> Source: `malsync/src/utils/syncHandler.ts`, `malsync/src/background/listSync.ts`  
> Related: [`anilist-api.md`](anilist-api.md), [`mihon-sync.md`](mihon-sync.md)

---

## 1. Core Architecture

MALSync handles multi-provider list synchronization using a **Master-Slave Diff Model**:
- **Master**: The authoritative source (in LazyDex: local SQLite database / user device).
- **Slave(s)**: Remote tracker services (AniList, MyAnimeList, Kitsu, Simkl).
- **Sync Operations**:
  1. **Fetch & Map**: Retrieve lists from all providers and index by canonical media identifier.
  2. **Diff Generation (`changeCheck`)**: Compare each tracked attribute between Master and Slave.
  3. **Missing Check (`missingCheck`)**: Identify entries present in Master but absent in Slave (and vice versa).
  4. **Batch Execution (`syncList`)**: Dispatch updates for diffs and mutations for missing entries.

```
+--------------------+        generateSync()        +----------------------+
| Local MediaItem DB | <--------------------------> | Remote Tracker Sync  |
|     (Master)       |                              |   (Slave: AniList)   |
+--------------------+                              +----------------------+
          |                                                    |
          v                                                    v
   LocalState (t_local)                              RemoteState (t_remote)
          \                                                    /
           \---> ThreeWayMergeResolver(Snapshot: t_snap) <----/
                                   |
                                   v
                         Merged Domain State
               (Monotonic progress, status resolution)
```

---

## 2. Field Comparison & Diff Rules (`changeCheck`)

| Attribute | Comparison Rule | Conflict Resolution |
|---|---|---|
| **Progress / Episodes** | `slave.watchedEp !== master.watchedEp` | If `master.status == COMPLETED`, clamp to `slave.totalEp`. Otherwise advance to highest watched episode. |
| **Volumes** | `slave.readVol !== master.readVol` | If `master.status == COMPLETED`, clamp to `slave.totalVol`. Otherwise take `master.readVol`. |
| **Status** | `normalize(slave.status) !== normalize(master.status)` | Map to normalized lifecycle state (`CURRENT`, `COMPLETED`, `PAUSED`, `DROPPED`, `PLANNING`). |
| **Score** | `slave.score !== master.score` | Normalized to 100-point integer scale (`POINT_100`). |
| **Dates** | `startDate`, `finishDate` | Checked only if provider supports date inputs (AniList does via `FuzzyDateInput`). |
| **Rewatch Count** | `slave.rewatchCount !== master.rewatchCount` | Integer counter incremented on repeated completion cycles. |

### Special Invariance Rules:
1. **Completion Clamping**: When marking `COMPLETED`, progress automatically advances to `progressTotal` (if total is known).
2. **Fractional Progress Preservation**: In LazyDex, fractional chapter reading (e.g. `12.5`) is preserved locally even when remote provider only supports integer progress (`12`).
3. **In-Flight Mutation Fence**: If local timestamp `updatedAt > dispatchedAt`, incoming remote echoes from previous in-flight HTTP requests are ignored.

---

## 3. Missing Item Reconciliation (`missingCheck`)

1. **Inbound Missing (Remote exists, Local missing)**:
   - Check local database for matching title (`TitleMatcher`).
   - If local media exists without tracker: create `MediaLinkEntity(state = "SUGGESTED")` to prevent duplicate library cards.
   - If no local media exists: insert `MediaItemEntity` and `MediaLinkEntity(state = "LINKED")`.

2. **Outbound Missing (Local exists, Remote missing)**:
   - Newly added library item marked `DIRTY_LOCAL`.
   - Engine calls `SaveMediaListEntry(mediaId: remoteId, status: ..., progress: ...)` with `INTERACTIVE` priority.

3. **Remote Deletions (`reconcileRemoteDeletions`)**:
   - Run strictly **only after all chunks complete** without errors.
   - Items removed remotely on AniList are transitioned to `MediaLinkEntity(state = "UNLINKED")`.
   - **Local media files, chapters, and history are NEVER deleted.**

---

## 4. MALSync + Komikku Hybrid in LazyDex

LazyDex combines MALSync's sync intelligence with Komikku's mobile runtime efficiency:

| Dimension | MALSync Strategy | Komikku Strategy | LazyDex Implementation |
|---|---|---|---|
| **2-Way Merge** | Master-Slave diffing | Last-write-wins | `ThreeWayMergeResolver.kt` (3-way snapshot diffing + monotonic progress) |
| **Thread Management** | Web Extension Alarms | Non-blocking Coroutines (`Dispatchers.IO`) | Kotlin Coroutines + `yield()` every 50 DB rows to release SQLite locks |
| **Rate Limiting** | Ad-hoc delay loops | OkHttp `RateLimit` interceptor (85 req/min) | `TokenBucketLimiter.kt` (60 capacity, 60 refill/min, priority queues) |
| **Pagination** | `Page(page, perPage: 100)` | Direct per-manga API calls | `MediaListCollection(chunk: $chunk)` in 500-item chunks |
| **Periodic Sync** | `chrome.alarms` 23hr | Scheduled worker | `WorkManager` background daily job on unmetered Wi-Fi |

