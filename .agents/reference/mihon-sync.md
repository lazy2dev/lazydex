# Mihon AniList Sync — Architecture Reference

> Extracted from `mihonapp/mihon` GitHub source (main branch, 2026).
> Purpose: Reference for replicating the same patterns in LazyDex.
> Main plan: [`v0.0.4.md`](../plans/v0.0.4.md) | API reference: [`anilist-api.md`](anilist-api.md) | DB migration: [`room-migration-v3.md`](room-migration-v3.md)

---

## 1. File Structure (in Mihon)

```
data/track/
├── BaseTracker.kt              # Abstract tracker: login/logout, credentials, bind/update/refresh
├── Tracker.kt                   # Interface: all tracker operations
├── TrackerManager.kt            # Registry of all trackers (Anilist, MyAnimeList, Kitsu, etc.)
├── DeletableTracker.kt          # Interface for trackers that support delete
├── Track.kt                     # Domain model: manga_id, remote_id, library_id, score, status, dates
│
└── anilist/
    ├── Anilist.kt               # Concrete tracker: status constants, login, bind, update, refresh, delete
    ├── AnilistApi.kt            # GraphQL client: all queries + mutations
    ├── AnilistInterceptor.kt    # OkHttp interceptor: Bearer token + User-Agent
    └── dto/
        ├── ALOAuth.kt           # Token: access_token, token_type, expires, expires_in
        ├── ALManga.kt           # ALManga (search result) + ALUserManga (user's list entry)
        ├── ALSearchItem.kt      # Search result DTO → toALManga()
        ├── ALFuzzyDate.kt       # Year/month/day → epoch millis
        ├── ALUser.kt            # Viewer + mediaListOptions + scoreFormat
        ├── ALSearchResult.kt    # Search response wrapper
        ├── ALAddMangaResult.kt  # SaveMediaListEntry response
        ├── ALCurrentUserResult.kt # Viewer query response
        └── ALUserListMangaQueryResult.kt # Media list fetch response
```

---

## 2. Key Architecture Decisions

### 2a Track Model is Separate from Manga

Mihon's `Track` interface is a **join entity** linking manga to tracking service:

```kotlin
interface Track : Serializable {
    var id: Long?
    var manga_id: Long
    var tracker_id: Long
    var remote_id: Long
    var library_id: Long?
    var title: String
    var last_chapter_read: Double
    var total_chapters: Long
    var score: Double
    var status: Long
    var started_reading_date: Long
    var finished_reading_date: Long
    var tracking_url: String
    var `private`: Boolean
}
```

**Why this matters**: A single manga can be tracked by multiple services. LazyDex maps each MediaItem to exactly one AniList entry, but the field design (remote_id, library_id, score/storage format, reading dates) is reused directly.

### 2b Tracker Interface

```kotlin
interface Tracker {
    val id: Long
    val name: String
    val client: OkHttpClient
    val supportsReadingDates: Boolean
    val supportsPrivateTracking: Boolean
    fun getStatusList(): List<Long>
    fun getStatus(status: Long): StringResource?
    fun getReadingStatus(): Long
    fun getRereadingStatus(): Long
    fun getCompletionStatus(): Long
    fun getScoreList(): List<String>
    fun indexToScore(index: Int): Double
    fun displayScore(track: DomainTrack): String
    suspend fun update(track: Track, didReadChapter: Boolean = false): Track
    suspend fun bind(track: Track, hasReadChapters: Boolean = false): Track
    suspend fun search(query: String): List<TrackSearch>
    suspend fun refresh(track: Track): Track
    suspend fun login(username: String, password: String)
    fun logout()
    val isLoggedIn: Boolean
    fun getUsername(): String
    fun getPassword(): String
    suspend fun register(item: Track, mangaId: Long)
    suspend fun setRemoteStatus(track: Track, status: Long)
    suspend fun setRemoteLastChapterRead(track: Track, chapterNumber: Int)
    suspend fun setRemoteScore(track: Track, scoreString: String)
}
```

### 2c Score Format

Mihon internally stores scores as **0-100** (integer). User preference converts display:
- `POINT_100` — 0–100
- `POINT_10` — 0–10 (×10 internally)
- `POINT_5` — 0–5 stars (0→0, 1→10, 2→30, 3→50, 4→70, 5→90)
- `POINT_3` — Smiley
- `POINT_10_DECIMAL` — 0.0–10.0

API always sends/receives `scoreRaw: Int` (0–100) via `score(format: POINT_100)` query.

### 2d LazyDex Differences from Mihon

| Aspect | Mihon | LazyDex |
|--------|-------|---------|
| Track model | Separate `Track` entity | Fields live directly on `MediaItem` |
| Sync unit | Per-manga (bind + update) | Bulk pull + bulk push + live single push |
| Token storage | Plain SharedPreferences | EncryptedSharedPreferences |
| Rate limiting | OkHttp extension | Manual token-bucket |
| Score format | 0-100 internal | 0-100 Int internal |
| Status | Long constants (1-6) | UserStatus enum |
| Categories | Only MANGA + ANIME | MANGA + ANIME + NOVEL + GAME + MOVIE + TV |
| Initial import | None (must bind per manga) | Full pull from MediaListCollection |
