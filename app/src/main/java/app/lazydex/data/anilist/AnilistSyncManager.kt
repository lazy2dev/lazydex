package app.lazydex.data.anilist

import android.util.Log
import app.lazydex.data.anilist.model.ScoreFormat
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.UserStatus
import app.lazydex.util.ScoreConverter
import app.lazydex.util.TitleNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID
import kotlin.math.abs

enum class SyncDecision {
    PullFromRemote,
    PushToRemote,
    KeepLocal
}

data class SyncReportItem(
    val title: String,
    val isNewImport: Boolean,
    val oldProgress: Int? = null,
    val newProgress: Int? = null,
    val oldStatus: String? = null,
    val newStatus: String? = null,
    val coverUrl: String? = null
)

data class AnilistSyncReport(
    val totalScanned: Int = 0,
    val importedCount: Int = 0,
    val updatedCount: Int = 0,
    val items: List<SyncReportItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

sealed class SyncProgressState {
    object Idle : SyncProgressState()
    data class Fetching(val type: String, val page: Int, val itemsFetched: Int) : SyncProgressState()
    data class Analyzing(val totalRemoteItems: Int) : SyncProgressState()
    data class Syncing(val current: Int, val total: Int, val currentItemTitle: String) : SyncProgressState()
    data class Completed(val report: AnilistSyncReport) : SyncProgressState()
    data class Error(val message: String) : SyncProgressState()
}

open class AnilistSyncManager(
    private val api: AnilistApi,
    private val tokenStore: AnilistTokenStore,
    private val dao: MediaItemDao,
    private val localCoversDir: File? = null,
    private val httpClient: OkHttpClient? = null
) {

    companion object {
        private const val TAG = "AnilistSyncManager"
        const val CLOCK_SKEW_BUFFER_MS = 60_000L

        fun resolveSyncConflict(localTimeMs: Long, remoteTimeMs: Long): SyncDecision {
            val diff = abs(remoteTimeMs - localTimeMs)
            return when {
                diff < CLOCK_SKEW_BUFFER_MS -> SyncDecision.KeepLocal
                remoteTimeMs > localTimeMs -> SyncDecision.PullFromRemote
                localTimeMs > remoteTimeMs -> SyncDecision.PushToRemote
                else -> SyncDecision.KeepLocal
            }
        }
    }

    open suspend fun loginWithToken(token: String, expiresInSeconds: Long = 31536000L) {
        tokenStore.saveToken(token, expiresInSeconds)
        val viewer = api.getViewer()
        val userFormat = ScoreFormat.fromString(viewer.mediaListOptions?.scoreFormat)
        tokenStore.saveUserInfo(viewer.id, viewer.name, userFormat)
        performFullSync()
    }

    open suspend fun performFullSync() {
        performFullSyncFlow().collect { state ->
            if (state is SyncProgressState.Error) {
                throw IllegalStateException(state.message)
            }
        }
    }

    open fun performFullSyncFlow(): Flow<SyncProgressState> = flow {
        emit(SyncProgressState.Fetching("Initializing", 0, 0))

        if (!tokenStore.isLoggedIn()) {
            emit(SyncProgressState.Error("Not logged in to AniList. Please connect your account."))
            return@flow
        }

        var username = tokenStore.getUsername()
        if (username.isNullOrBlank()) {
            try {
                val viewer = api.getViewer()
                username = viewer.name
                val userFormat = ScoreFormat.fromString(viewer.mediaListOptions?.scoreFormat)
                tokenStore.saveUserInfo(viewer.id, viewer.name, userFormat)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to recover viewer username from AniList", e)
            }
        }

        if (username.isNullOrBlank()) {
            emit(SyncProgressState.Error("AniList account name missing. Please re-authenticate."))
            return@flow
        }

        val syncStartTime = System.currentTimeMillis()
        val userFormat = tokenStore.getScoreFormat()
        val fetchedRemoteEntries = mutableListOf<ALMediaListEntry>()

        // 1. Paginated streaming fetch using MALSync Page query for ANIME & MANGA
        for (type in listOf("ANIME", "MANGA")) {
            var page = 1
            var hasNextPage = true
            while (hasNextPage && page <= 50) {
                emit(SyncProgressState.Fetching(type, page, fetchedRemoteEntries.size))
                val pageData = try {
                    api.fetchMediaListPage(userName = username, type = type, page = page)
                } catch (e: Exception) {
                    emit(SyncProgressState.Error("Failed fetching $type page $page: ${e.message}"))
                    return@flow
                }

                val entries = pageData.mediaList ?: emptyList()
                if (entries.isEmpty()) break

                entries.forEach { entry ->
                    if (entry.media != null) {
                        fetchedRemoteEntries.add(entry)
                    }
                }
                hasNextPage = pageData.pageInfo?.hasNextPage == true
                page++
            }
        }

        val distinctRemoteEntries = fetchedRemoteEntries.distinctBy { it.id }
        emit(SyncProgressState.Analyzing(distinctRemoteEntries.size))

        val matchedRemoteEntryIds = mutableSetOf<Long>()
        val localEntities = dao.getAll()
        val entryIdMap = localEntities.filter { it.anilistListEntryId != null }.associateBy { it.anilistListEntryId!! }
        val sourceUrlMap = localEntities.filter { !it.sourceUrl.isNullOrBlank() }.associateBy { it.sourceUrl!! }

        val unmatchedRemoteEntries = mutableListOf<ALMediaListEntry>()
        val pendingUpserts = mutableListOf<MediaItemEntity>()
        val reportItems = mutableListOf<SyncReportItem>()
        var updatedCount = 0
        var importedCount = 0

        var processedCount = 0
        val totalToProcess = distinctRemoteEntries.size

        // 2. Incremental Tier 1 matching & real-time progress emissions
        for (remote in distinctRemoteEntries) {
            processedCount++
            val title = remote.media?.title?.userPreferred
                ?: remote.media?.title?.romaji
                ?: remote.media?.title?.english
                ?: "Media #${remote.mediaId}"

            emit(SyncProgressState.Syncing(processedCount, totalToProcess, title))

            val category = inferCategory(remote)
            val expectedUrl = "https://anilist.co/${if (category == MediaCategory.ANIME) "anime" else "manga"}/${remote.mediaId}"

            val matchedLocal = entryIdMap[remote.id] ?: sourceUrlMap[expectedUrl]

            if (matchedLocal != null) {
                matchedRemoteEntryIds.add(remote.id)
                val updated = processMatchedItem(matchedLocal, remote, category, userFormat, syncStartTime, reportItems)
                if (updated != null) {
                    pendingUpserts.add(updated)
                    updatedCount++
                }
            } else {
                unmatchedRemoteEntries.add(remote)
            }
        }

        // 3. Tier 3 Normalized Title matching & importing unmatched remote entries
        if (unmatchedRemoteEntries.isNotEmpty()) {
            val unboundEntities = dao.getAll().filter { it.anilistListEntryId == null }
            val titleIndex = withContext(Dispatchers.Default) {
                unboundEntities.groupBy { TitleNormalizer.normalize(it.title) }
            }

            for (remote in unmatchedRemoteEntries) {
                val category = inferCategory(remote)
                val rawTitle = remote.media?.title?.userPreferred
                    ?: remote.media?.title?.romaji
                    ?: remote.media?.title?.english
                    ?: ""
                val normalizedTitle = TitleNormalizer.normalize(rawTitle)

                val candidateList = titleIndex[normalizedTitle]?.filter { it.category == category.name } ?: emptyList()
                val bestMatch = candidateList.maxByOrNull { it.dateAdded }

                if (bestMatch != null) {
                    matchedRemoteEntryIds.add(remote.id)
                    val updated = processMatchedItem(bestMatch, remote, category, userFormat, syncStartTime, reportItems)
                    if (updated != null) {
                        pendingUpserts.add(updated)
                        updatedCount++
                    }
                } else {
                    // Import unmatched remote entry into local DB
                    val mediaUrl = remote.media?.siteUrl
                        ?: "https://anilist.co/${if (category == MediaCategory.ANIME) "anime" else "manga"}/${remote.mediaId}"
                    val title = remote.media?.title?.userPreferred
                        ?: remote.media?.title?.romaji
                        ?: remote.media?.title?.english
                        ?: "Unknown"

                    val altTitles = mutableListOf<String>()
                    remote.media?.title?.english?.let { if (it != title) altTitles.add(it) }
                    remote.media?.title?.romaji?.let { if (it != title) altTitles.add(it) }
                    remote.media?.title?.native?.let { altTitles.add(it) }
                    val altTitlesJson = Json.encodeToString(altTitles.distinct())
                    val genresJson = Json.encodeToString(remote.media?.genres ?: emptyList())
                    val pulledStatus = mapAniListStatus(remote.status)
                    val now = System.currentTimeMillis()
                    val remoteTimeMs = (remote.updatedAt ?: (now / 1000L)) * 1000L
                    val coverUrl = remote.media?.coverImage?.large ?: remote.media?.coverImage?.medium

                    val newEntity = MediaItemEntity(
                        id = UUID.randomUUID().toString(),
                        category = category.name,
                        title = title,
                        alternativeTitles = altTitlesJson,
                        sourceUrl = mediaUrl,
                        coverImagePath = "",
                        coverImageUrl = coverUrl,
                        currentProgress = remote.progress ?: 0,
                        totalItems = remote.media?.chapters ?: remote.media?.episodes,
                        userStatus = pulledStatus.name,
                        rating = remote.scoreRaw,
                        notes = "",
                        genres = genresJson,
                        tags = "[]",
                        author = "",
                        description = remote.media?.description ?: "",
                        startDate = null,
                        endDate = null,
                        lastUpdated = remoteTimeMs,
                        dateAdded = now,
                        localUpdatedAt = remoteTimeMs,
                        lastSyncedAt = syncStartTime,
                        anilistListEntryId = remote.id,
                        isPrivate = remote.private == true,
                        mediaFormat = remote.media?.format,
                        rawFormat = remote.media?.format,
                        publishingStatus = remote.media?.status,
                        season = remote.media?.season,
                        totalVolumes = remote.media?.volumes,
                        progressVolumes = remote.progressVolumes ?: 0,
                        durationMinutes = remote.media?.duration,
                        sourceMaterial = remote.media?.source,
                        isAdult = remote.media?.isAdult == true,
                        isDoujin = false,
                        syncPendingAction = null
                    )
                    pendingUpserts.add(newEntity)
                    importedCount++
                    matchedRemoteEntryIds.add(remote.id)

                    reportItems.add(
                        SyncReportItem(
                            title = title,
                            isNewImport = true,
                            newProgress = remote.progress ?: 0,
                            newStatus = pulledStatus.name,
                            coverUrl = coverUrl
                        )
                    )
                }
            }
        }

        // 4. Remote Deletion Reconciliation Phase
        val allBoundItems = dao.getBoundItems()
        for (boundItem in allBoundItems) {
            if (boundItem.anilistListEntryId != null && boundItem.anilistListEntryId !in matchedRemoteEntryIds) {
                if (boundItem.localUpdatedAt < syncStartTime) {
                    val updated = boundItem.copy(syncPendingAction = "REMOTE_DELETED_PENDING_RESOLUTION")
                    pendingUpserts.add(updated)
                }
            }
        }

        if (pendingUpserts.isNotEmpty()) {
            dao.upsertAll(pendingUpserts)
        }

        val report = AnilistSyncReport(
            totalScanned = totalToProcess,
            importedCount = importedCount,
            updatedCount = updatedCount,
            items = reportItems
        )

        emit(SyncProgressState.Completed(report))
    }.flowOn(Dispatchers.IO)

    private suspend fun processMatchedItem(
        localEntity: MediaItemEntity,
        remote: ALMediaListEntry,
        inferredCategory: MediaCategory,
        userFormat: ScoreFormat,
        syncTime: Long,
        reportItems: MutableList<SyncReportItem>
    ): MediaItemEntity? {
        val remoteTimeMs = (remote.updatedAt ?: 0L) * 1000L
        val localTimeMs = if (localEntity.localUpdatedAt > 0) localEntity.localUpdatedAt else localEntity.lastUpdated
        val decision = resolveSyncConflict(localTimeMs, remoteTimeMs)

        val expectedUrl = "https://anilist.co/${if (inferredCategory == MediaCategory.ANIME) "anime" else "manga"}/${remote.mediaId}"

        return when (decision) {
            SyncDecision.PullFromRemote -> {
                val pulledStatus = mapAniListStatus(remote.status)
                val mergedRating = remote.scoreRaw ?: localEntity.rating
                val remoteProgress = remote.progress ?: localEntity.currentProgress
                val coverUrl = remote.media?.coverImage?.large ?: remote.media?.coverImage?.medium ?: localEntity.coverImageUrl

                if (remoteProgress != localEntity.currentProgress || pulledStatus.name != localEntity.userStatus) {
                    reportItems.add(
                        SyncReportItem(
                            title = localEntity.title,
                            isNewImport = false,
                            oldProgress = localEntity.currentProgress,
                            newProgress = remoteProgress,
                            oldStatus = localEntity.userStatus,
                            newStatus = pulledStatus.name,
                            coverUrl = coverUrl
                        )
                    )
                }

                localEntity.copy(
                    anilistListEntryId = remote.id,
                    sourceUrl = localEntity.sourceUrl ?: expectedUrl,
                    coverImageUrl = coverUrl,
                    currentProgress = remoteProgress,
                    progressVolumes = remote.progressVolumes ?: localEntity.progressVolumes,
                    userStatus = pulledStatus.name,
                    rating = mergedRating,
                    lastSyncedAt = syncTime,
                    mediaFormat = remote.media?.format ?: localEntity.mediaFormat,
                    totalItems = remote.media?.chapters ?: remote.media?.episodes ?: localEntity.totalItems,
                    totalVolumes = remote.media?.volumes ?: localEntity.totalVolumes,
                    durationMinutes = remote.media?.duration ?: localEntity.durationMinutes,
                    publishingStatus = remote.media?.status ?: localEntity.publishingStatus,
                    season = remote.media?.season ?: localEntity.season,
                    sourceMaterial = remote.media?.source ?: localEntity.sourceMaterial,
                    isAdult = remote.media?.isAdult ?: localEntity.isAdult,
                    syncPendingAction = null
                )
            }
            SyncDecision.PushToRemote -> {
                try {
                    pushItemEntity(localEntity, userFormat)
                } catch (e: Exception) {
                    Log.w(TAG, "Push to remote failed for item ${localEntity.id}, queuing for background retry", e)
                }
                null
            }
            SyncDecision.KeepLocal -> {
                val mergedProgress = maxOf(localEntity.currentProgress, remote.progress ?: 0)
                val mergedStatus = if (mergedProgress >= (localEntity.totalItems ?: Int.MAX_VALUE)) "COMPLETED" else localEntity.userStatus
                val coverUrl = remote.media?.coverImage?.large ?: remote.media?.coverImage?.medium ?: localEntity.coverImageUrl
                localEntity.copy(
                    anilistListEntryId = remote.id,
                    sourceUrl = localEntity.sourceUrl ?: expectedUrl,
                    coverImageUrl = coverUrl,
                    currentProgress = mergedProgress,
                    userStatus = mergedStatus,
                    lastSyncedAt = syncTime,
                    syncPendingAction = null
                )
            }
        }
    }

    open suspend fun pushItemEntity(localEntity: MediaItemEntity, userFormat: ScoreFormat) = withContext(Dispatchers.IO) {
        val category = MediaCategory.fromString(localEntity.category) ?: return@withContext
        if (category !in listOf(MediaCategory.ANIME, MediaCategory.MANGA, MediaCategory.NOVEL)) return@withContext

        val mediaId = extractAniListMediaId(localEntity.sourceUrl) ?: return@withContext
        val statusString = toAniListStatusString(localEntity.userStatus)
        val scoreRaw = localEntity.rating?.let { ScoreConverter.snapToFormatInterval(it, userFormat) }

        val result = api.saveMediaListEntry(
            id = localEntity.anilistListEntryId,
            mediaId = mediaId,
            status = statusString,
            progress = localEntity.currentProgress,
            progressVolumes = localEntity.progressVolumes,
            scoreRaw = scoreRaw,
            isPrivate = localEntity.isPrivate
        )

        val now = System.currentTimeMillis()
        val updated = localEntity.copy(
            anilistListEntryId = result.id,
            lastSyncedAt = now,
            localUpdatedAt = now,
            syncPendingAction = null
        )
        dao.upsert(updated)
    }

    open fun inferCategory(entry: ALMediaListEntry): MediaCategory {
        val formatStr = entry.media?.format
        return when {
            formatStr in listOf("NOVEL", "LIGHT_NOVEL") -> MediaCategory.NOVEL
            formatStr in listOf("MANGA", "ONE_SHOT") -> MediaCategory.MANGA
            else -> MediaCategory.ANIME
        }
    }

    open fun mapAniListStatus(status: String?): UserStatus {
        return when (status?.uppercase()) {
            "CURRENT" -> UserStatus.READING
            "COMPLETED" -> UserStatus.COMPLETED
            "PAUSED" -> UserStatus.ON_HOLD
            "DROPPED" -> UserStatus.DROPPED
            "PLANNING" -> UserStatus.PLAN_TO
            "REPEATING" -> UserStatus.REPEATING
            else -> UserStatus.PLAN_TO
        }
    }

    open fun toAniListStatusString(userStatus: String): String {
        return when (userStatus.uppercase()) {
            "READING", "WATCHING", "PLAYING" -> "CURRENT"
            "COMPLETED" -> "COMPLETED"
            "ON_HOLD" -> "PAUSED"
            "DROPPED" -> "DROPPED"
            "PLAN_TO" -> "PLANNING"
            "REPEATING" -> "REPEATING"
            else -> "PLANNING"
        }
    }

    open fun extractAniListMediaId(url: String?): Long? {
        if (url == null) return null
        val regex = Regex("anilist\\.co/(?:anime|manga)/(\\d+)")
        return regex.find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }
}
