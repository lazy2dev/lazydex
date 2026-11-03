package app.lazydex.data.tracker.sync

import androidx.room.withTransaction
import app.lazydex.data.local.LazyDexDatabase
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.dao.MediaLinkDao
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.data.local.entity.MediaLinkEntity
import app.lazydex.data.tracker.anilist.AnilistGraphQLClient
import app.lazydex.data.tracker.anilist.AnilistTokenStore
import app.lazydex.data.tracker.anilist.TokenBucketLimiter
import app.lazydex.data.tracker.anilist.dto.AniListFuzzyDateInput
import app.lazydex.data.tracker.anilist.dto.AniListMediaListEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class SyncProgress(
    val isSyncing: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val message: String? = null,
    val lastSyncedAt: Long? = null,
    val error: String? = null
)

sealed class SyncResult {
    data class Success(
        val pushedCount: Int,
        val pulledCount: Int,
        val unlinkedCount: Int
    ) : SyncResult()

    data class Error(val message: String, val cause: Throwable? = null) : SyncResult()
    object NotLoggedIn : SyncResult()
}

/**
 * 2-way lag-free synchronization engine for AniList tracking.
 * Implements chunk batching (50 items per transaction with yield), in-flight mutation fence,
 * and remote deletion reconciliation guard.
 */
open class AnilistSyncEngine(
    private val client: AnilistGraphQLClient,
    private val db: LazyDexDatabase? = null,
    private val mediaItemDao: MediaItemDao,
    private val mediaLinkDao: MediaLinkDao,
    private val tokenStore: AnilistTokenStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val timeProvider: () -> Long = System::currentTimeMillis,
    private val transactionRunner: (suspend (suspend () -> Unit) -> Unit)? = null
) {
    companion object {
        const val PROVIDER_ID = "anilist"
        const val DEFAULT_BATCH_SIZE = 50
        val ANIME_FORMATS = setOf("TV", "TV_SHORT", "MOVIE", "SPECIAL", "OVA", "ONA", "MUSIC")
        val TRACKED_TYPES = listOf("MANGA", "ANIME")
    }

    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    private val inFlightMutations = ConcurrentHashMap<String, Long>()

    fun recordInFlight(mediaId: String, remoteId: String? = null, timestamp: Long = timeProvider()) {
        inFlightMutations[mediaId] = timestamp
        if (remoteId != null) {
            inFlightMutations[remoteId] = timestamp
        }
    }

    fun clearInFlight(mediaId: String, remoteId: String? = null) {
        inFlightMutations.remove(mediaId)
        if (remoteId != null) {
            inFlightMutations.remove(remoteId)
        }
    }

    fun getInFlightDispatchedAt(mediaId: String, remoteId: String? = null): Long? {
        return inFlightMutations[mediaId] ?: remoteId?.let { inFlightMutations[it] }
    }

    private suspend fun executeTransaction(block: suspend () -> Unit) {
        when {
            transactionRunner != null -> transactionRunner.invoke(block)
            db != null -> db.withTransaction { block() }
            else -> block()
        }
    }

    fun mapToCategoryId(format: String?, type: String): String {
        return when {
            format?.uppercase() == "NOVEL" -> "cat_novel"
            type.uppercase() == "ANIME" || format?.uppercase() in ANIME_FORMATS -> "cat_anime"
            else -> "cat_manga"
        }
    }

    /**
     * Executes a full 2-way sync across both MANGA and ANIME collections.
     * Reconciles remote deletions strictly ONLY when all chunk pages complete without errors.
     */
    suspend fun syncAll(): SyncResult = withContext(ioDispatcher) {
        if (!tokenStore.isLoggedIn || tokenStore.userId == null) {
            return@withContext SyncResult.NotLoggedIn
        }
        val userId = tokenStore.userId!!

        _syncProgress.value = SyncProgress(
            isSyncing = true,
            current = 0,
            total = 0,
            message = "Starting sync..."
        )

        var pushedCount = 0
        var pulledCount = 0
        var unlinkedCount = 0
        var isFullSyncComplete = false
        val seenRemoteIds = mutableSetOf<String>()

        try {
            // 1. Push pending dirty local changes before pulling
            pushedCount = pushDirtyLinks()

            // 2. Inbound sync: process all chunks for MANGA and ANIME
            for (type in TRACKED_TYPES) {
                var chunk = 1
                var hasNext = true
                while (hasNext) {
                    _syncProgress.update { it.copy(message = "Fetching $type chunk $chunk...") }
                    val (entries, hasNextChunk) = client.getDeduplicatedEntries(
                        userId = userId,
                        type = type,
                        chunk = chunk,
                        priority = TokenBucketLimiter.Priority.BACKGROUND
                    )
                    hasNext = hasNextChunk
                    chunk++

                    pulledCount += entries.size
                    _syncProgress.update { it.copy(total = it.total + entries.size) }

                    // Chunk batching (50 items per db transaction block)
                    for (batch in entries.chunked(batchSize)) {
                        executeTransaction {
                            processInboundBatch(batch, type, seenRemoteIds)
                        }
                        _syncProgress.update { it.copy(current = it.current + batch.size) }
                        yield() // Yield SQLite write lock to pending interactive taps
                    }
                }
            }

            // Successfully processed all chunks without exception
            isFullSyncComplete = true

            // 3. Remote deletion reconciliation guard:
            // Runs strictly ONLY when isFullSyncComplete == true
            if (isFullSyncComplete) {
                _syncProgress.update { it.copy(message = "Reconciling remote deletions...") }
                unlinkedCount = reconcileRemoteDeletions(seenRemoteIds)
            }

            // 4. Push any concurrent merges that resulted in DIRTY_LOCAL
            val additionalPushed = pushDirtyLinks()
            val totalPushed = pushedCount + additionalPushed

            val now = timeProvider()
            _syncProgress.value = SyncProgress(
                isSyncing = false,
                current = pulledCount,
                total = pulledCount,
                lastSyncedAt = now,
                message = "Sync completed"
            )

            SyncResult.Success(
                pushedCount = totalPushed,
                pulledCount = pulledCount,
                unlinkedCount = unlinkedCount
            )
        } catch (e: Throwable) {
            val errorMsg = e.message ?: "Sync failed"
            _syncProgress.value = SyncProgress(
                isSyncing = false,
                error = errorMsg
            )
            SyncResult.Error(errorMsg, e)
        }
    }

    /**
     * Pushes a single local media item to AniList if marked DIRTY_LOCAL or newly linked.
     * Uses INTERACTIVE priority and applies the in-flight mutation fence.
     */
    open suspend fun pushDirtyItem(mediaId: String): Boolean = withContext(ioDispatcher) {
        if (!tokenStore.isLoggedIn) return@withContext false

        val links = mediaLinkDao.getByMediaId(mediaId)
        val link = links.firstOrNull { it.providerId == PROVIDER_ID && it.state == "LINKED" }
            ?: return@withContext false
        val localItem = mediaItemDao.getById(mediaId) ?: return@withContext false
        val remoteMediaId = link.remoteId?.toIntOrNull() ?: return@withContext false

        val dispatchedAt = timeProvider()
        recordInFlight(localItem.id, link.remoteId, dispatchedAt)

        try {
            val params = AnilistGraphQLClient.SaveMediaListEntryParams(
                id = link.remoteMediaListId?.toIntOrNull(),
                mediaId = remoteMediaId,
                status = ThreeWayMergeResolver.lazyDexStatusToAniList(localItem.userStatus, localItem.repeat),
                progress = localItem.progress.toInt(), // Section 6.3: Outbound truncates to integer
                progressVolumes = localItem.secondaryProgress.toInt(),
                scoreRaw = localItem.scoreRaw,
                isPrivate = localItem.isPrivate,
                startedAt = if (localItem.startYear != null) {
                    AniListFuzzyDateInput(localItem.startYear, localItem.startMonth, localItem.startDay)
                } else null,
                completedAt = if (localItem.endYear != null) {
                    AniListFuzzyDateInput(localItem.endYear, localItem.endMonth, localItem.endDay)
                } else null
            )

            val saved = client.saveMediaListEntry(
                params = params,
                priority = TokenBucketLimiter.Priority.INTERACTIVE
            )

            // In-flight mutation fence check: was item modified locally while request was in transit?
            val currentLocal = mediaItemDao.getById(localItem.id)
            if (currentLocal != null && currentLocal.updatedAt > dispatchedAt) {
                // Modified locally during in-flight request: preserve DIRTY_LOCAL
                if (link.remoteMediaListId == null && saved.id > 0) {
                    mediaLinkDao.upsert(
                        link.copy(
                            remoteMediaListId = saved.id.toString(),
                            updatedAt = dispatchedAt
                        )
                    )
                }
            } else {
                // Clean round trip: update snapshot and mark SYNCED
                val newSnapshot = SyncSnapshot(
                    remoteUpdatedAt = saved.updatedAt ?: (dispatchedAt / 1000L),
                    localUpdatedAt = localItem.updatedAt,
                    progress = localItem.progress,
                    secondaryProgress = localItem.secondaryProgress,
                    status = localItem.userStatus,
                    scoreRaw = localItem.scoreRaw,
                    repeat = localItem.repeat,
                    isPrivate = localItem.isPrivate,
                    notes = localItem.notes
                )
                mediaLinkDao.upsert(
                    link.copy(
                        remoteMediaListId = saved.id.toString(),
                        syncStatus = ThreeWayMergeResolver.SYNC_STATUS_SYNCED,
                        lastSyncedAt = dispatchedAt,
                        syncedSnapshot = newSnapshot.toJson(),
                        updatedAt = dispatchedAt
                    )
                )
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            clearInFlight(localItem.id, link.remoteId)
        }
    }

    /**
     * Pushes all local entries marked DIRTY_LOCAL to AniList.
     */
    suspend fun pushDirtyLinks(): Int = withContext(ioDispatcher) {
        if (!tokenStore.isLoggedIn) return@withContext 0
        val dirtyLinks = mediaLinkDao.getDirtyLinks(PROVIDER_ID)
        var count = 0
        for (link in dirtyLinks) {
            if (pushDirtyItem(link.mediaId)) {
                count++
            }
        }
        count
    }

    /**
     * Reconciles remote deletions when full sync is complete.
     * Items missing on AniList are marked UNLINKED. Local progress and reading history are never deleted.
     */
    suspend fun reconcileRemoteDeletions(seenRemoteIds: Set<String>): Int = withContext(ioDispatcher) {
        val linkedLinks = mediaLinkDao.getByProviderAndState(PROVIDER_ID, "LINKED")
        val toUnlink = mutableListOf<MediaLinkEntity>()
        val now = timeProvider()
        for (link in linkedLinks) {
            val remoteId = link.remoteId ?: continue
            if (!seenRemoteIds.contains(remoteId)) {
                toUnlink.add(
                    link.copy(
                        state = "UNLINKED",
                        syncStatus = ThreeWayMergeResolver.SYNC_STATUS_SYNCED,
                        updatedAt = now
                    )
                )
            }
        }
        if (toUnlink.isNotEmpty()) {
            mediaLinkDao.upsertAll(toUnlink)
        }
        toUnlink.size
    }

    /**
     * Ingests a single sub-batch of entries inside an active transaction.
     */
    private suspend fun processInboundBatch(
        batch: List<AniListMediaListEntry>,
        type: String,
        seenRemoteIds: MutableSet<String>
    ) {
        val now = timeProvider()
        for (entry in batch) {
            val remoteId = entry.resolvedMediaId?.toString() ?: continue
            seenRemoteIds.add(remoteId)

            val existingLink = mediaLinkDao.getByRemoteId(PROVIDER_ID, remoteId)
            if (existingLink != null) {
                val localItem = mediaItemDao.getById(existingLink.mediaId)
                if (localItem != null) {
                    val snapshot = SyncSnapshot.fromJson(existingLink.syncedSnapshot)
                    val localState = LocalState.from(localItem)
                    val remoteState = RemoteState.from(entry)
                    val inFlightDispatchedAt = getInFlightDispatchedAt(localItem.id, remoteId)

                    val mergeResult = ThreeWayMergeResolver.resolve(
                        local = localState,
                        remote = remoteState,
                        snapshot = snapshot,
                        dispatchedAt = inFlightDispatchedAt
                    )

                    val isAnime = type.uppercase() == "ANIME" || entry.media?.format in ANIME_FORMATS
                    val updatedProgressTotal = (if (isAnime) entry.media?.episodes else entry.media?.chapters)?.toDouble() ?: localItem.progressTotal
                    val updatedSecondaryProgressTotal = entry.media?.volumes?.toDouble() ?: localItem.secondaryProgressTotal
                    val updatedCoverImageUrl = entry.media?.coverImage?.best ?: localItem.coverImageUrl
                    val updatedBannerImage = entry.media?.bannerImage ?: localItem.bannerImage
                    val updatedTitleEnglish = entry.media?.title?.english ?: localItem.titleEnglish
                    val updatedTitleRomaji = entry.media?.title?.romaji ?: localItem.titleRomaji
                    val updatedTitleNative = entry.media?.title?.nativeTitle ?: localItem.titleNative
                    val updatedIsAdult = entry.media?.isAdult ?: localItem.isAdult
                    val updatedGenres = if (entry.media?.genres.isNullOrEmpty()) localItem.genres else Json.encodeToString(entry.media!!.genres)

                    val updatedStartYear = localItem.startYear ?: entry.startedAt?.year
                    val updatedStartMonth = localItem.startMonth ?: entry.startedAt?.month
                    val updatedStartDay = localItem.startDay ?: entry.startedAt?.day
                    val updatedEndYear = localItem.endYear ?: entry.completedAt?.year
                    val updatedEndMonth = localItem.endMonth ?: entry.completedAt?.month
                    val updatedEndDay = localItem.endDay ?: entry.completedAt?.day

                    val updatedItem = localItem.copy(
                        userStatus = mergeResult.mergedState.userStatus,
                        progress = mergeResult.mergedState.progress,
                        secondaryProgress = mergeResult.mergedState.secondaryProgress,
                        scoreRaw = mergeResult.mergedState.scoreRaw,
                        repeat = mergeResult.mergedState.repeat,
                        isPrivate = mergeResult.mergedState.isPrivate,
                        notes = mergeResult.mergedState.notes,
                        progressTotal = updatedProgressTotal,
                        secondaryProgressTotal = updatedSecondaryProgressTotal,
                        coverImageUrl = updatedCoverImageUrl,
                        bannerImage = updatedBannerImage,
                        titleEnglish = updatedTitleEnglish,
                        titleRomaji = updatedTitleRomaji,
                        titleNative = updatedTitleNative,
                        isAdult = updatedIsAdult,
                        genres = updatedGenres,
                        startYear = updatedStartYear,
                        startMonth = updatedStartMonth,
                        startDay = updatedStartDay,
                        endYear = updatedEndYear,
                        endMonth = updatedEndMonth,
                        endDay = updatedEndDay,
                        updatedAt = mergeResult.mergedState.updatedAt
                    )
                    mediaItemDao.upsert(updatedItem)

                    val updatedLink = existingLink.copy(
                        remoteMediaListId = entry.id.toString(),
                        syncStatus = mergeResult.syncStatus,
                        state = "LINKED",
                        lastSyncedAt = now,
                        syncedSnapshot = mergeResult.newSnapshot.toJson(),
                        updatedAt = now
                    )
                    mediaLinkDao.upsert(updatedLink)
                }
            } else {
                val categoryId = mapToCategoryId(entry.media?.format, type)
                val targetTitles = listOfNotNull(
                    entry.media?.title?.userPreferred,
                    entry.media?.title?.english,
                    entry.media?.title?.romaji,
                    entry.media?.title?.nativeTitle
                )

                // Check for existing local item with matching title & category family that lacks an active AniList link
                val localCandidates = mediaItemDao.getActiveByCategoryId(categoryId)
                val matchingLocal = localCandidates.firstOrNull { localItem ->
                    val hasExistingLink = mediaLinkDao.getByMediaId(localItem.id)
                        .any { it.providerId == PROVIDER_ID && it.state == "LINKED" }
                    if (hasExistingLink) return@firstOrNull false

                    val localTitles = listOfNotNull(
                        localItem.title,
                        localItem.titleEnglish,
                        localItem.titleRomaji,
                        localItem.titleNative
                    )
                    TitleMatcher.isMatch(localTitles, targetTitles)
                }

                if (matchingLocal != null) {
                    // In-app Merge Suggester: Attach suggested link to existing local item without creating duplicate card
                    val snapshot = SyncSnapshot(
                        remoteUpdatedAt = entry.updatedAt?.let { if (it > 100_000_000_000L) it / 1000L else it } ?: (now / 1000L),
                        localUpdatedAt = matchingLocal.updatedAt,
                        progress = entry.progress?.toDouble() ?: 0.0,
                        secondaryProgress = entry.progressVolumes?.toDouble() ?: 0.0,
                        status = ThreeWayMergeResolver.anilistStatusToLazyDex(entry.status),
                        scoreRaw = if (entry.scoreRaw == 0) null else entry.scoreRaw,
                        repeat = entry.repeat ?: 0,
                        isPrivate = entry.isPrivate ?: false,
                        notes = entry.notes ?: ""
                    )
                    val suggestedLink = MediaLinkEntity(
                        id = UUID.randomUUID().toString(),
                        mediaId = matchingLocal.id,
                        kind = "TRACKER",
                        providerId = PROVIDER_ID,
                        remoteId = remoteId,
                        remoteMediaListId = entry.id.toString(),
                        url = "https://anilist.co/${type.lowercase()}/$remoteId",
                        state = "SUGGESTED",
                        syncStatus = ThreeWayMergeResolver.SYNC_STATUS_SYNCED,
                        lastSyncedAt = now,
                        syncedSnapshot = snapshot.toJson(),
                        createdAt = now,
                        updatedAt = now
                    )
                    mediaLinkDao.upsert(suggestedLink)
                } else {
                    // New item from remote (no matching local item exists)
                    val newMediaId = UUID.randomUUID().toString()
                val categoryId = mapToCategoryId(entry.media?.format, type)
                val userStatus = ThreeWayMergeResolver.anilistStatusToLazyDex(entry.status)
                val progress = entry.progress?.toDouble() ?: 0.0
                val secondaryProgress = entry.progressVolumes?.toDouble() ?: 0.0
                val isAnime = type.uppercase() == "ANIME" || entry.media?.format in ANIME_FORMATS
                val progressTotal = (if (isAnime) entry.media?.episodes else entry.media?.chapters)?.toDouble()
                val secondaryProgressTotal = entry.media?.volumes?.toDouble()
                val scoreRaw = if (entry.scoreRaw == 0) null else entry.scoreRaw
                val repeat = entry.repeat ?: 0
                val isPrivate = entry.isPrivate ?: false
                val notes = entry.notes ?: ""
                val title = entry.media?.title?.best?.ifBlank { "Unknown" } ?: "Unknown"
                val titleEnglish = entry.media?.title?.english
                val titleRomaji = entry.media?.title?.romaji
                val titleNative = entry.media?.title?.nativeTitle
                val coverImageUrl = entry.media?.coverImage?.best
                val bannerImage = entry.media?.bannerImage
                val genres = if (entry.media?.genres.isNullOrEmpty()) "[]" else Json.encodeToString(entry.media!!.genres)
                val isAdult = entry.media?.isAdult ?: false
                val entryUpdatedAt = entry.updatedAt?.let { if (it > 100_000_000_000L) it else it * 1000L } ?: now

                val newItem = MediaItemEntity(
                    id = newMediaId,
                    categoryId = categoryId,
                    title = title,
                    titleEnglish = titleEnglish,
                    titleRomaji = titleRomaji,
                    titleNative = titleNative,
                    coverImagePath = "",
                    coverImageUrl = coverImageUrl,
                    bannerImage = bannerImage,
                    description = "", // Bulk ingestion omits heavy HTML description
                    author = "",
                    userStatus = userStatus,
                    progress = progress,
                    progressTotal = progressTotal,
                    secondaryProgress = secondaryProgress,
                    secondaryProgressTotal = secondaryProgressTotal,
                    scoreRaw = scoreRaw,
                    repeat = repeat,
                    notes = notes,
                    genres = genres,
                    tags = "[]", // Bulk ingestion omits heavy tag metadata
                    isPrivate = isPrivate,
                    isAdult = isAdult,
                    startYear = entry.startedAt?.year,
                    startMonth = entry.startedAt?.month,
                    startDay = entry.startedAt?.day,
                    endYear = entry.completedAt?.year,
                    endMonth = entry.completedAt?.month,
                    endDay = entry.completedAt?.day,
                    durationMinutes = entry.media?.duration,
                    avgChapterLength = null,
                    lengthUnitOverride = null,
                    playbackSpeed = 1.0,
                    createdAt = now,
                    updatedAt = entryUpdatedAt,
                    deletedAt = null
                )
                mediaItemDao.upsert(newItem)

                val snapshot = SyncSnapshot(
                    remoteUpdatedAt = entry.updatedAt?.let { if (it > 100_000_000_000L) it / 1000L else it } ?: (now / 1000L),
                    localUpdatedAt = entryUpdatedAt,
                    progress = progress,
                    secondaryProgress = secondaryProgress,
                    status = userStatus,
                    scoreRaw = scoreRaw,
                    repeat = repeat,
                    isPrivate = isPrivate,
                    notes = notes
                )

                val newLink = MediaLinkEntity(
                    id = UUID.randomUUID().toString(),
                    mediaId = newMediaId,
                    kind = "TRACKER",
                    providerId = PROVIDER_ID,
                    remoteId = remoteId,
                    remoteMediaListId = entry.id.toString(),
                    url = "https://anilist.co/${type.lowercase()}/$remoteId",
                    state = "LINKED",
                    syncStatus = ThreeWayMergeResolver.SYNC_STATUS_SYNCED,
                    lastSyncedAt = now,
                    syncedSnapshot = snapshot.toJson(),
                    createdAt = now,
                    updatedAt = now
                )
                mediaLinkDao.upsert(newLink)
                }
            }
        }
    }
}

