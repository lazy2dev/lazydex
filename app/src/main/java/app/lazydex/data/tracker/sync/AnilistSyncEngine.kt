package app.lazydex.data.tracker.sync

import androidx.room.withTransaction
import app.lazydex.data.local.LazyDexDatabase
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.dao.MediaLinkDao
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.data.local.entity.MediaLinkEntity
import app.lazydex.data.local.entity.UserDataEntity
import app.lazydex.data.tracker.anilist.AnilistApi
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
    private val client: AnilistApi,
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

    fun mapToTypeAndFormat(
        format: String?,
        type: String,
        countryOfOrigin: String? = null
    ): Triple<String, String, String?> {
        val fmt = format?.uppercase() ?: ""
        val c = countryOfOrigin?.uppercase()
        val typ = type.uppercase()

        if (fmt == "NOVEL") {
            val novelFormat = when (c) {
                "CN", "KR" -> "WEB_NOVEL"
                else -> "LIGHT_NOVEL"
            }
            return Triple("NOVEL", novelFormat, c)
        }
        if (typ == "ANIME" || fmt in ANIME_FORMATS) {
            val animeFormat = when (fmt) {
                "MOVIE" -> "MOVIE"
                "OVA" -> "OVA"
                "ONA" -> "ONA"
                "SPECIAL" -> "SPECIAL"
                "TV_SHORT" -> "TV_SHORT"
                else -> "TV"
            }
            return Triple("ANIME", animeFormat, c)
        }
        val mangaFormat = when (fmt) {
            "ONE_SHOT" -> "ONE_SHOT"
            else -> "MANGA"
        }
        return Triple("MANGA", mangaFormat, c)
    }

    fun mapToTypeId(
        format: String?,
        type: String,
        countryOfOrigin: String? = null,
        tags: List<String> = emptyList()
    ): String {
        val fmt = format?.uppercase()
        val c = countryOfOrigin?.uppercase()
        val typ = type.uppercase()

        if (fmt == "NOVEL") {
            return when (c) {
                "CN" -> "typ_novel_cn"
                "KR" -> "typ_novel_kr"
                else -> "typ_novel_ln"
            }
        }
        if (typ == "ANIME" || fmt in ANIME_FORMATS) {
            return when (fmt) {
                "MOVIE" -> "typ_anime_movie"
                "OVA", "ONA", "SPECIAL" -> "typ_anime_ova"
                else -> "typ_anime_series"
            }
        }
        // Manga family
        return when (c) {
            "KR" -> "typ_manhwa"
            "CN", "TW" -> "typ_manhua"
            "JP" -> "typ_manga_jp"
            else -> {
                val tagLower = tags.map { it.lowercase() }
                when {
                    "manhwa" in tagLower || "webtoon" in tagLower -> "typ_manhwa"
                    "manhua" in tagLower -> "typ_manhua"
                    else -> "typ_manga_jp"
                }
            }
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
            // 1. Inbound import: process all chunks for MANGA and ANIME without mass uploading local DB
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

            isFullSyncComplete = true

            val now = timeProvider()
            _syncProgress.value = SyncProgress(
                isSyncing = false,
                current = pulledCount,
                total = pulledCount,
                lastSyncedAt = now,
                message = "Import completed"
            )

            SyncResult.Success(
                pushedCount = 0,
                pulledCount = pulledCount,
                unlinkedCount = 0
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
        val localMedia = mediaItemDao.getById(mediaId) ?: return@withContext false
        val localUserData = mediaItemDao.getUserData(mediaId) ?: UserDataEntity(mediaId = mediaId)
        val remoteMediaId = link.remoteId?.toIntOrNull() ?: return@withContext false

        val dispatchedAt = timeProvider()
        recordInFlight(localMedia.id, link.remoteId, dispatchedAt)

        val isManga = localMedia.type == "MANGA"
        val progressVolumesToSend = if (isManga) localUserData.secondaryProgress.toInt() else null

        try {
            val params = AnilistApi.SaveMediaListEntryParams(
                id = link.remoteMediaListId?.toIntOrNull(),
                mediaId = remoteMediaId,
                status = ThreeWayMergeResolver.lazyDexStatusToAniList(localUserData.userStatus, localUserData.repeat),
                progress = localUserData.progress.toInt(), // Section 6.3: Outbound truncates to integer
                progressVolumes = progressVolumesToSend,
                scoreRaw = localUserData.scoreRaw,
                isPrivate = localUserData.isPrivate,
                startedAt = if (localUserData.startYear != null) {
                    AniListFuzzyDateInput(localUserData.startYear, localUserData.startMonth, localUserData.startDay)
                } else null,
                completedAt = if (localUserData.endYear != null) {
                    AniListFuzzyDateInput(localUserData.endYear, localUserData.endMonth, localUserData.endDay)
                } else null
            )

            val saved = client.saveMediaListEntry(
                params = params,
                priority = TokenBucketLimiter.Priority.INTERACTIVE
            )

            // In-flight mutation fence check: was item modified locally while request was in transit?
            val currentUserData = mediaItemDao.getUserData(localMedia.id)
            if (currentUserData != null && (currentUserData.updatedAt ?: 0L) > dispatchedAt) {
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
                    localUpdatedAt = currentUserData?.updatedAt ?: dispatchedAt,
                    progress = localUserData.progress,
                    secondaryProgress = localUserData.secondaryProgress,
                    status = localUserData.userStatus,
                    scoreRaw = localUserData.scoreRaw,
                    repeat = localUserData.repeat,
                    isPrivate = localUserData.isPrivate,
                    notes = localUserData.notes
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
            clearInFlight(localMedia.id, link.remoteId)
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
     * Pure ID-based match:
     * - If remoteId already linked in DB: safe update (keep user status/score/notes, monotonic max progress, fill missing metadata).
     * - If remoteId not in DB: insert brand new item and link directly.
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
                val localUserData = mediaItemDao.getUserData(existingLink.mediaId)
                    ?: UserDataEntity(mediaId = existingLink.mediaId)
                if (localItem != null) {
                    val isAnime = type.uppercase() == "ANIME" || entry.media?.format in ANIME_FORMATS

                    // Monotonic progress merge (max) with fractional retention
                    val remoteProg = entry.progress?.toDouble() ?: 0.0
                    val resolvedProgress = when {
                        !tokenStore.autoSyncProgressFromTrackers -> localUserData.progress
                        entry.progress != null && localUserData.progress.toInt() == entry.progress -> localUserData.progress
                        remoteProg > localUserData.progress -> remoteProg
                        else -> localUserData.progress
                    }
                    val remoteVols = (entry.progressVolumes ?: 0).toDouble()
                    val resolvedSecondaryProgress = if (!tokenStore.autoSyncProgressFromTrackers || isAnime) {
                        localUserData.secondaryProgress
                    } else {
                        maxOf(localUserData.secondaryProgress, remoteVols)
                    }

                    // Safe retention: never overwrite local status, score, or notes if user has them
                    val resolvedStatus = if (localUserData.userStatus.isNotBlank() &&
                        localUserData.userStatus != "PLANNING" &&
                        localUserData.userStatus != "PLAN_TO"
                    ) {
                        localUserData.userStatus
                    } else {
                        entry.status?.let { ThreeWayMergeResolver.anilistStatusToLazyDex(it) } ?: localUserData.userStatus
                    }
                    val resolvedScore = localUserData.scoreRaw ?: (if (entry.scoreRaw == 0) null else entry.scoreRaw)
                    val resolvedNotes = if (localUserData.notes.isNotBlank()) localUserData.notes else (entry.notes ?: "")
                    val resolvedRepeat = maxOf(localUserData.repeat, entry.repeat ?: 0)
                    val resolvedPrivate = localUserData.isPrivate

                    // Metadata fill: update totals and missing metadata
                    val updatedProgressTotal = (if (isAnime) entry.media?.episodes else entry.media?.chapters)?.toDouble() ?: localItem.progressTotal
                    val updatedSecondaryProgressTotal = if (isAnime) null else (entry.media?.volumes?.toDouble() ?: localItem.secondaryProgressTotal)
                    val updatedCoverImageUrl = if (localItem.coverImageUrl.isNullOrBlank()) (entry.media?.coverImage?.best ?: "") else localItem.coverImageUrl
                    val updatedBannerImage = localItem.bannerImage ?: entry.media?.bannerImage
                    val updatedTitleEnglish = localItem.titleEnglish ?: entry.media?.title?.english
                    val updatedTitleRomaji = localItem.titleRomaji ?: entry.media?.title?.romaji
                    val updatedTitleNative = localItem.titleNative ?: entry.media?.title?.nativeTitle
                    val updatedIsAdult = localItem.isAdult || (entry.media?.isAdult ?: false)

                    val updatedGenres = if (localItem.genres.isBlank() || localItem.genres == "[]") {
                        if (entry.media?.genres.isNullOrEmpty()) "[]" else Json.encodeToString(entry.media!!.genres)
                    } else {
                        localItem.genres
                    }
                    val incomingTags = entry.media?.tags?.map { it.name }
                    val updatedTags = if (localItem.tags.isBlank() || localItem.tags == "[]") {
                        if (incomingTags.isNullOrEmpty()) "[]" else Json.encodeToString(incomingTags)
                    } else {
                        localItem.tags
                    }
                    val sanitizedDesc = sanitizeDescription(entry.media?.description)
                    val updatedDescription = if (localItem.description.isBlank()) sanitizedDesc else localItem.description

                    val updatedStartYear = localUserData.startYear ?: entry.startedAt?.year ?: entry.media?.startDate?.year
                    val updatedStartMonth = localUserData.startMonth ?: entry.startedAt?.month ?: entry.media?.startDate?.month
                    val updatedStartDay = localUserData.startDay ?: entry.startedAt?.day ?: entry.media?.startDate?.day
                    val updatedEndYear = localUserData.endYear ?: entry.completedAt?.year
                    val updatedEndMonth = localUserData.endMonth ?: entry.completedAt?.month
                    val updatedEndDay = localUserData.endDay ?: entry.completedAt?.day
                    val (resolvedType, resolvedFormat, resolvedCountry) = mapToTypeAndFormat(
                        entry.media?.format,
                        type,
                        entry.media?.countryOfOrigin
                    )

                    val updatedMediaItem = localItem.copy(
                        type = resolvedType,
                        format = resolvedFormat,
                        countryOfOrigin = resolvedCountry ?: localItem.countryOfOrigin,
                        durationMinutes = entry.media?.duration ?: localItem.durationMinutes,
                        progressTotal = updatedProgressTotal,
                        secondaryProgressTotal = updatedSecondaryProgressTotal,
                        coverImageUrl = updatedCoverImageUrl,
                        bannerImage = updatedBannerImage,
                        description = updatedDescription,
                        titleEnglish = updatedTitleEnglish,
                        titleRomaji = updatedTitleRomaji,
                        titleNative = updatedTitleNative,
                        isAdult = updatedIsAdult,
                        genres = updatedGenres,
                        tags = updatedTags
                    )
                    mediaItemDao.upsert(updatedMediaItem)

                    val remoteUpdatedMs = entry.updatedAt?.let { if (it > 100_000_000_000L) it else it * 1000L }
                    val remoteProgressIncreased = (resolvedProgress > localUserData.progress + 1e-6) ||
                        (!isAnime && resolvedSecondaryProgress > localUserData.secondaryProgress + 1e-6)
                    val updatedProgressIncreasedAt = if (remoteProgressIncreased) {
                        maxOf(localUserData.progressIncreasedAt ?: 0L, remoteUpdatedMs ?: now)
                    } else {
                        localUserData.progressIncreasedAt
                    }
                    val updatedUserDataUpdatedAt = if (remoteProgressIncreased ||
                        resolvedStatus != localUserData.userStatus ||
                        resolvedScore != localUserData.scoreRaw ||
                        resolvedNotes != localUserData.notes
                    ) {
                        maxOf(localUserData.updatedAt ?: 0L, remoteUpdatedMs ?: now)
                    } else {
                        localUserData.updatedAt
                    }

                    val updatedUserData = localUserData.copy(
                        userStatus = resolvedStatus,
                        progress = resolvedProgress,
                        secondaryProgress = if (isAnime) 0.0 else resolvedSecondaryProgress,
                        scoreRaw = resolvedScore,
                        repeat = resolvedRepeat,
                        isPrivate = resolvedPrivate,
                        notes = resolvedNotes,
                        startYear = updatedStartYear,
                        startMonth = updatedStartMonth,
                        startDay = updatedStartDay,
                        endYear = updatedEndYear,
                        endMonth = updatedEndMonth,
                        endDay = updatedEndDay,
                        updatedAt = updatedUserDataUpdatedAt,
                        progressIncreasedAt = updatedProgressIncreasedAt
                    )
                    mediaItemDao.upsertUserData(updatedUserData)

                    val updatedLink = existingLink.copy(
                        remoteMediaListId = entry.id.toString(),
                        syncStatus = ThreeWayMergeResolver.SYNC_STATUS_SYNCED,
                        state = "LINKED",
                        lastSyncedAt = now,
                        updatedAt = now
                    )
                    mediaLinkDao.upsert(updatedLink)
                }
            } else {
                // New item from remote (pure ID-based insert)
                val newMediaId = UUID.randomUUID().toString()
                val (itemType, itemFormat, itemCountry) = mapToTypeAndFormat(
                    entry.media?.format,
                    type,
                    entry.media?.countryOfOrigin
                )
                val userStatus = ThreeWayMergeResolver.anilistStatusToLazyDex(entry.status)
                val progress = entry.progress?.toDouble() ?: 0.0
                val isAnime = itemType == "ANIME" || entry.media?.format in ANIME_FORMATS
                val secondaryProgress = if (isAnime) 0.0 else (entry.progressVolumes?.toDouble() ?: 0.0)
                val progressTotal = (if (isAnime) entry.media?.episodes else entry.media?.chapters)?.toDouble()
                val secondaryProgressTotal = if (isAnime) null else entry.media?.volumes?.toDouble()
                val scoreRaw = if (entry.scoreRaw == 0) null else entry.scoreRaw
                val repeat = entry.repeat ?: 0
                val isPrivate = entry.isPrivate ?: false
                val notes = entry.notes ?: ""
                val title = entry.media?.title?.best?.ifBlank { "Unknown" } ?: "Unknown"
                val titleEnglish = entry.media?.title?.english
                val titleRomaji = entry.media?.title?.romaji
                val titleNative = entry.media?.title?.nativeTitle
                val coverImageUrl = entry.media?.coverImage?.best ?: ""
                val bannerImage = entry.media?.bannerImage
                val genres = if (entry.media?.genres.isNullOrEmpty()) "[]" else Json.encodeToString(entry.media!!.genres)
                val isAdult = entry.media?.isAdult ?: false

                val remoteUpdatedMs = entry.updatedAt?.let { if (it > 100_000_000_000L) it else it * 1000L }
                val fallbackFuzzyDate = fuzzyDateToMillis(entry.completedAt?.year, entry.completedAt?.month, entry.completedAt?.day)
                    ?: fuzzyDateToMillis(entry.startedAt?.year, entry.startedAt?.month, entry.startedAt?.day)

                val hasProgress = progress > 0.0 || (!isAnime && secondaryProgress > 0.0)
                val initialProgressIncreasedAt = if (hasProgress) (remoteUpdatedMs ?: fallbackFuzzyDate) else null
                val initialUserDataUpdatedAt = remoteUpdatedMs ?: fallbackFuzzyDate ?: now

                val newItem = MediaItemEntity(
                    id = newMediaId,
                    type = itemType,
                    format = itemFormat,
                    countryOfOrigin = itemCountry,
                    title = title,
                    titleEnglish = titleEnglish,
                    titleRomaji = titleRomaji,
                    titleNative = titleNative,
                    coverImagePath = "",
                    coverImageUrl = coverImageUrl,
                    bannerImage = bannerImage,
                    description = sanitizeDescription(entry.media?.description),
                    author = "",
                    progressTotal = progressTotal,
                    secondaryProgressTotal = secondaryProgressTotal,
                    genres = genres,
                    tags = Json.encodeToString(entry.media?.tags?.map { it.name } ?: emptyList()),
                    isAdult = isAdult,
                    durationMinutes = entry.media?.duration,
                    avgChapterLength = null,
                    lengthUnitOverride = null,
                    playbackSpeed = 1.0,
                    createdAt = now,
                    updatedAt = remoteUpdatedMs ?: now,
                    deletedAt = null
                )
                mediaItemDao.upsert(newItem)

                val newUserData = UserDataEntity(
                    mediaId = newMediaId,
                    userStatus = userStatus,
                    progress = progress,
                    secondaryProgress = secondaryProgress,
                    scoreRaw = scoreRaw,
                    repeat = repeat,
                    notes = notes,
                    isPrivate = isPrivate,
                    startYear = entry.startedAt?.year ?: entry.media?.startDate?.year,
                    startMonth = entry.startedAt?.month ?: entry.media?.startDate?.month,
                    startDay = entry.startedAt?.day ?: entry.media?.startDate?.day,
                    endYear = entry.completedAt?.year,
                    endMonth = entry.completedAt?.month,
                    endDay = entry.completedAt?.day,
                    updatedAt = initialUserDataUpdatedAt,
                    progressIncreasedAt = initialProgressIncreasedAt
                )
                mediaItemDao.upsertUserData(newUserData)

                val snapshot = SyncSnapshot(
                    remoteUpdatedAt = entry.updatedAt?.let { if (it > 100_000_000_000L) it / 1000L else it } ?: (now / 1000L),
                    localUpdatedAt = initialUserDataUpdatedAt,
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

    private fun fuzzyDateToMillis(year: Int?, month: Int?, day: Int?): Long? {
        if (year == null || year <= 0) return null
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(java.util.Calendar.YEAR, year)
        cal.set(java.util.Calendar.MONTH, (month?.coerceIn(1, 12) ?: 1) - 1)
        cal.set(java.util.Calendar.DAY_OF_MONTH, day?.coerceIn(1, 31) ?: 1)
        return cal.timeInMillis
    }

    private fun sanitizeDescription(html: String?): String {
        if (html.isNullOrBlank()) return ""
        return html
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("</?p>"), "\n")
            .replace(Regex("<[^>]*>"), "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#039;", "'")
            .trim()
    }
}

