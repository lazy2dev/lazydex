package app.lazydex.data.repository

import android.content.Context
import androidx.room.withTransaction
import app.lazydex.data.local.LazyDexDatabase
import app.lazydex.data.local.converter.Converters
import app.lazydex.data.local.dao.ActivityDao
import app.lazydex.data.local.dao.AppSettingDao
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.dao.MediaLinkDao
import app.lazydex.data.local.dao.MediaPartDao
import app.lazydex.data.local.dao.SyncOutboxDao
import app.lazydex.data.local.entity.ActiveMediaItemView
import app.lazydex.data.local.entity.ActivityEntity
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.data.local.entity.SyncOutboxEntity
import app.lazydex.data.local.entity.UserDataEntity
import app.lazydex.data.tracker.anilist.AnilistTokenStore
import app.lazydex.data.tracker.service.DelayedTrackingUpdateJob
import app.lazydex.data.tracker.sync.AnilistSyncEngine
import app.lazydex.data.tracker.sync.TrackChapter
import app.lazydex.domain.model.ActivityDatePoint
import app.lazydex.domain.model.ActivityLog
import kotlin.math.abs
import app.lazydex.domain.model.LengthUnit
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaPart
import app.lazydex.domain.model.MediaStats
import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.ResolvedTypeConfig
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.TypeConfig
import app.lazydex.domain.model.UserStatus
import app.lazydex.domain.model.resolveMediaType
import app.lazydex.domain.repository.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

class MediaRepositoryImpl(
    private val context: Context? = null,
    private val db: LazyDexDatabase? = null,
    private val dao: MediaItemDao,
    private val linkDao: MediaLinkDao,
    private val activityDao: ActivityDao,
    private val outboxDao: SyncOutboxDao,
    private val settingDao: AppSettingDao,
    private val localCoversDir: File,
    private val partDao: MediaPartDao? = null,
    private val syncEngine: AnilistSyncEngine? = null,
    private val trackChapter: TrackChapter? = null,
    private val tokenStore: AnilistTokenStore? = null,
    private val syncScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val transactionRunner: (suspend (suspend () -> Any?) -> Any?)? = null
) : MediaRepository {

    private val converters = Converters()

    private suspend fun <T> runInTransaction(block: suspend () -> T): T {
        val runner = transactionRunner
        if (runner != null) {
            @Suppress("UNCHECKED_CAST")
            return runner { block() } as T
        }
        val database = db
        if (database != null) {
            return database.withTransaction { block() }
        }
        return block()
    }

    override fun observeAll(): Flow<List<MediaItem>> = dao.observeAll()
        .map { list -> list.map { it.toDomain() } }
        .distinctUntilChanged()

    override fun observeHistory(): Flow<List<MediaItem>> = dao.observeHistory()
        .map { list -> list.map { it.toDomain() } }
        .distinctUntilChanged()

    override fun observeByType(type: String): Flow<List<MediaItem>> = dao.observeByType(type)
        .map { list -> list.map { it.toDomain() } }
        .distinctUntilChanged()

    override fun observeByType(mediaType: MediaType): Flow<List<MediaItem>> {
        val type = mediaTypeToDbType(mediaType)
        return dao.observeByType(type)
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()
    }

    override fun observeByTypeId(typeId: String): Flow<List<MediaItem>> {
        val (type, format) = resolveTypeAndFormat(typeId)
        return observeFiltered(type, format, StatusFilter.ALL)
    }

    override fun observeFiltered(mediaType: MediaType?, statusFilter: StatusFilter): Flow<List<MediaItem>> {
        val type = mediaType?.let { mediaTypeToDbType(it) }
        return observeFiltered(type, null, statusFilter)
    }

    override fun observeFiltered(type: String?, format: String?, statusFilter: StatusFilter): Flow<List<MediaItem>> {
        val filterType = if (statusFilter == StatusFilter.ALL) "ALL" else statusFilter.name
        return dao.observeFiltered(type, format, filterType)
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()
    }

    override fun observeFilteredByTypeId(typeId: String?, statusFilter: StatusFilter): Flow<List<MediaItem>> {
        val (type, format) = resolveTypeAndFormat(typeId)
        return observeFiltered(type, format, statusFilter)
    }

    override fun observeById(id: String): Flow<MediaItem?> = dao.observeById(id)
        .map { it?.toDomain() }
        .distinctUntilChanged()

    override fun observeCount(): Flow<Int> = dao.observeCount().distinctUntilChanged()

    override fun observeStats(): Flow<MediaStats> = dao.getStats().distinctUntilChanged()

    override fun observeTypeCounts(): Flow<Map<MediaType, Int>> = dao.observeTypeCounts()
        .map { list ->
            val counts = list.associate { count ->
                val mediaType = when (count.type.uppercase()) {
                    "MANGA" -> MediaType.MANGA
                    "ANIME" -> MediaType.ANIME
                    "GAME" -> MediaType.GAME
                    else -> MediaType.NOVEL
                }
                mediaType to count.count
            }
            MediaType.entries.associateWith { counts[it] ?: 0 }
        }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()

    override fun observeStatusCounts(mediaType: MediaType?): Flow<Map<StatusFilter, Int>> {
        val type = mediaType?.let { mediaTypeToDbType(it) }
        return dao.observeStatusCounts(type)
            .map { list ->
                val raw = list.associate { it.userStatus to it.count }
                val current = (raw["CURRENT"] ?: 0) + (raw["READING"] ?: 0) + (raw["WATCHING"] ?: 0) + (raw["PLAYING"] ?: 0) + (raw["IN_PROGRESS"] ?: 0)
                val planning = (raw["PLANNING"] ?: 0) + (raw["PLAN_TO"] ?: 0)
                val paused = (raw["PAUSED"] ?: 0) + (raw["ON_HOLD"] ?: 0)
                val completed = raw["COMPLETED"] ?: 0
                val dropped = raw["DROPPED"] ?: 0
                val repeating = raw["REPEATING"] ?: 0
                val allCount = current + planning + paused + completed + dropped + repeating
                mapOf(
                    StatusFilter.ALL to allCount,
                    StatusFilter.CURRENT to current,
                    StatusFilter.PLANNING to planning,
                    StatusFilter.COMPLETED to completed,
                    StatusFilter.PAUSED to paused,
                    StatusFilter.DROPPED to dropped,
                    StatusFilter.REPEATING to repeating
                )
            }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
    }

    override fun observeDistinctGenres(mediaType: MediaType?): Flow<List<String>> {
        val type = mediaType?.let { mediaTypeToDbType(it) }
        val source = if (type != null) dao.observeByType(type) else dao.observeAll()
        return source.map { entities ->
            entities.flatMap { converters.toList(it.genres) }
                .distinctBy { it.lowercase() }
                .sorted()
        }.flowOn(Dispatchers.IO).distinctUntilChanged()
    }

    override fun observeDistinctTags(mediaType: MediaType?): Flow<List<String>> {
        val type = mediaType?.let { mediaTypeToDbType(it) }
        val source = if (type != null) dao.observeByType(type) else dao.observeAll()
        return source.map { entities ->
            entities.flatMap { converters.toList(it.tags) }
                .distinctBy { it.lowercase() }
                .sorted()
        }.flowOn(Dispatchers.IO).distinctUntilChanged()
    }

    private val staticTopLevelTypes = listOf(
        TypeConfig(id = "NOVEL", name = "Novel", icon = "book", unitName = "Chapter", actionVerb = "Read", sortOrder = 0),
        TypeConfig(id = "MANGA", name = "Manga", icon = "image", unitName = "Chapter", actionVerb = "Read", sortOrder = 1),
        TypeConfig(id = "ANIME", name = "Anime", icon = "tv", unitName = "Episode", actionVerb = "Watch", sortOrder = 2),
        TypeConfig(id = "GAME", name = "Game", icon = "gamepad", unitName = "Hour", actionVerb = "Play", sortOrder = 3)
    )

    private val staticFormats = mapOf(
        "NOVEL" to listOf(
            TypeConfig(id = "LIGHT_NOVEL", parentId = "NOVEL", name = "Light Novel", unitName = "Volume", sortOrder = 0),
            TypeConfig(id = "WEB_NOVEL", parentId = "NOVEL", name = "Web Novel", unitName = "Chapter", sortOrder = 1)
        ),
        "MANGA" to listOf(
            TypeConfig(id = "MANGA", parentId = "MANGA", name = "Manga", unitName = "Volume", sortOrder = 0),
            TypeConfig(id = "MANHWA", parentId = "MANGA", name = "Manhwa", unitName = "Chapter", sortOrder = 1),
            TypeConfig(id = "MANHUA", parentId = "MANGA", name = "Manhua", unitName = "Chapter", sortOrder = 2),
            TypeConfig(id = "ONE_SHOT", parentId = "MANGA", name = "One Shot", unitName = "Chapter", sortOrder = 3)
        ),
        "ANIME" to listOf(
            TypeConfig(id = "TV", parentId = "ANIME", name = "TV Series", unitName = "Episode", sortOrder = 0),
            TypeConfig(id = "MOVIE", parentId = "ANIME", name = "Movie", unitName = "Minute", sortOrder = 1),
            TypeConfig(id = "OVA", parentId = "ANIME", name = "OVA / Special", unitName = "Episode", sortOrder = 2)
        ),
        "GAME" to listOf(
            TypeConfig(id = "PC", parentId = "GAME", name = "PC", unitName = "Hour", sortOrder = 0),
            TypeConfig(id = "ANDROID", parentId = "GAME", name = "Android", unitName = "Hour", sortOrder = 1),
            TypeConfig(id = "CONSOLE", parentId = "GAME", name = "Console", unitName = "Hour", sortOrder = 2),
            TypeConfig(id = "WEB", parentId = "GAME", name = "Web", unitName = "Hour", sortOrder = 3),
            TypeConfig(id = "CROSS_PLATFORM", parentId = "GAME", name = "Cross Platform", unitName = "Hour", sortOrder = 4)
        )
    )

    override fun observeTypeConfigs(): Flow<List<TypeConfig>> = flowOf(staticTopLevelTypes + staticFormats.values.flatten())

    override fun observeTopLevelTypes(): Flow<List<TypeConfig>> = flowOf(staticTopLevelTypes)

    override fun observeFormats(parentId: String): Flow<List<TypeConfig>> {
        val normalizedParent = resolveTypeAndFormat(parentId).first ?: parentId.uppercase()
        return flowOf(staticFormats[normalizedParent] ?: emptyList())
    }

    override fun observeResolvedConfig(typeId: String): Flow<ResolvedTypeConfig> = flow {
        emit(getResolvedConfig(typeId))
    }

    override suspend fun getResolvedConfig(typeId: String): ResolvedTypeConfig {
        val (type, format) = resolveTypeAndFormat(typeId)
        val actualType = type ?: "NOVEL"
        return when (actualType) {
            "MANGA" -> ResolvedTypeConfig(
                typeId = typeId,
                topLevelId = "MANGA",
                displayName = format ?: "Manga",
                icon = "image",
                unitName = "Chapter",
                secondaryUnitName = if (format == null || format == "MANGA") "Volume" else null,
                actionVerb = "Read",
                hasParts = true,
                hasCharEstimate = false,
                hasAudioSpeed = false,
                lengthUnit = LengthUnit.WORDS
            )
            "ANIME" -> ResolvedTypeConfig(
                typeId = typeId,
                topLevelId = "ANIME",
                displayName = format ?: "Anime",
                icon = "tv",
                unitName = if (format == "MOVIE") "Minute" else "Episode",
                secondaryUnitName = null,
                actionVerb = "Watch",
                hasParts = false,
                hasCharEstimate = false,
                hasAudioSpeed = false,
                lengthUnit = LengthUnit.WORDS
            )
            "GAME" -> ResolvedTypeConfig(
                typeId = typeId,
                topLevelId = "GAME",
                displayName = format ?: "Game",
                icon = "gamepad",
                unitName = "Hour",
                secondaryUnitName = null,
                actionVerb = "Play",
                hasParts = false,
                hasCharEstimate = false,
                hasAudioSpeed = false,
                lengthUnit = LengthUnit.WORDS
            )
            else -> ResolvedTypeConfig(
                typeId = typeId,
                topLevelId = "NOVEL",
                displayName = format ?: "Novel",
                icon = "book",
                unitName = "Chapter",
                secondaryUnitName = if (format == "LIGHT_NOVEL") "Volume" else null,
                actionVerb = "Read",
                hasParts = false,
                hasCharEstimate = true,
                hasAudioSpeed = true,
                lengthUnit = LengthUnit.WORDS
            )
        }
    }

    override fun observeParts(mediaId: String): Flow<List<MediaPart>> = emptyFlow()

    override suspend fun setParts(mediaId: String, parts: List<MediaPart>) {}

    override fun observeActivity(mediaId: String): Flow<List<ActivityLog>> = activityDao.observeByMediaId(mediaId)
        .map { list -> list.map { it.toDomain() } }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()

    override fun observeActivityPoints(startMs: Long?, endMs: Long?): Flow<List<ActivityDatePoint>> =
        activityDao.observeActivityPoints(startMs, endMs)
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()

    override suspend fun getTimeSpentMinutes(mediaId: String): Pair<Double, Double> = withContext(Dispatchers.IO) {
        val updates = activityDao.getCountedProgressUpdates(mediaId)
        var readMinutes = 0.0
        var listenMinutes = 0.0

        for (act in updates) {
            val delta = act.delta ?: continue
            if (delta <= 0.0) continue
            val actMode = act.mode?.uppercase() ?: "READ"
            val length = act.lengthSnapshot?.toDouble() ?: 0.0

            if (length > 0.0) {
                val rate = act.rateSnapshot ?: if (actMode == "LISTEN") 250.0 else 500.0
                val spd = if (actMode == "LISTEN") (act.speed ?: 1.0) else 1.0
                val minutes = (delta * length) / (rate * spd)
                if (actMode == "LISTEN") {
                    listenMinutes += minutes
                } else {
                    readMinutes += minutes
                }
            }
        }
        Pair(readMinutes, listenMinutes)
    }

    override suspend fun getById(id: String): MediaItem? = withContext(Dispatchers.IO) {
        dao.getActiveById(id)?.toDomain() ?: dao.getById(id)?.let { media ->
            media.toDomain(dao.getUserData(id))
        }
    }

    override suspend fun getAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        dao.getAll().map { it.toDomain() }
    }

    override suspend fun add(item: MediaItem): MediaItem = withContext(Dispatchers.IO) {
        val normalized = item.normalize()
        val now = System.currentTimeMillis()
        val hasInitialProgress = normalized.progress > 0.0 ||
            (normalized.hasSecondaryProgress && normalized.secondaryProgress > 0.0)
        val initialProgressIncreasedAt = normalized.progressIncreasedAt
            ?: if (hasInitialProgress) normalized.dateAdded else null

        val mediaEntity = normalized.toEntity()
        val userEntity = normalized.toUserDataEntity(
            updatedAtOverride = now,
            progressIncreasedAtOverride = initialProgressIncreasedAt
        )

        runInTransaction {
            dao.upsert(mediaEntity)
            dao.upsertUserData(userEntity)
        }

        val autoUpdateOnMarkRead = settingDao.get("tracker.autoUpdateOnMarkRead") ?: "ALWAYS"
        val skipPush = normalized.userStatus == UserStatus.COMPLETED && autoUpdateOnMarkRead == "NEVER"

        if (!skipPush) {
            syncEngine?.let { engine ->
                syncScope.launch {
                    runCatching { engine.pushDirtyItem(item.id) }
                }
            }
        }

        normalized.copy(progressIncreasedAt = initialProgressIncreasedAt)
    }

    override suspend fun update(item: MediaItem) = withContext(Dispatchers.IO) {
        val normalized = item.normalize()
        val existingActive = dao.getActiveById(item.id)
        val currentDomain = existingActive?.toDomain()
        val currentUserData = dao.getUserData(item.id)

        val autoUpdateOnMarkRead = settingDao.get("tracker.autoUpdateOnMarkRead") ?: "ALWAYS"

        if (currentDomain == null) {
            val now = System.currentTimeMillis()
            val initialProgressIncreasedAt = normalized.progressIncreasedAt
                ?: if (normalized.progress > 0.0 || (normalized.hasSecondaryProgress && normalized.secondaryProgress > 0.0)) now else null
            runInTransaction {
                dao.upsert(normalized.toEntity().copy(updatedAt = now))
                dao.upsertUserData(
                    normalized.toUserDataEntity(
                        updatedAtOverride = now,
                        progressIncreasedAtOverride = initialProgressIncreasedAt
                    )
                )
            }
            return@withContext
        }

        val userdataChanged = normalized.hasUserdataChangedFrom(currentDomain)
        val itemDataChanged = normalized.hasItemDataChangedFrom(currentDomain)
        val progressIncreased = normalized.hasProgressIncreasedFrom(currentDomain)

        if (!userdataChanged && !itemDataChanged) {
            return@withContext
        }

        val now = System.currentTimeMillis()
        val existingRawStatus = currentUserData?.userStatus ?: currentDomain.userStatus.name
        val statusChanged = normalized.userStatus != currentDomain.userStatus
        val progressDelta = normalized.progress - currentDomain.progress

        runInTransaction {
            if (itemDataChanged) {
                dao.upsert(normalized.toEntity().copy(updatedAt = now))
            }

            if (userdataChanged) {
                val newProgressIncreasedAt = if (progressIncreased) now else currentDomain.progressIncreasedAt
                val updatedUserData = normalized.toUserDataEntity(
                    updatedAtOverride = now,
                    progressIncreasedAtOverride = newProgressIncreasedAt,
                    existing = currentUserData
                )
                dao.upsertUserData(updatedUserData)

                linkDao.updateSyncStatusByMediaId(item.id, "DIRTY_LOCAL", now)

                if (abs(progressDelta) > 1e-6) {
                    recordProgressActivity(
                        mediaId = item.id,
                        itemType = currentDomain.type,
                        avgChapterLength = currentDomain.avgChapterLength,
                        oldProgress = currentDomain.progress,
                        newProgress = normalized.progress,
                        delta = progressDelta,
                        mode = null,
                        speed = 1.0,
                        now = now
                    )
                }

                if (statusChanged) {
                    activityDao.upsert(
                        ActivityEntity(
                            id = UUID.randomUUID().toString(),
                            mediaId = item.id,
                            type = "STATUS_CHANGE",
                            oldValue = existingRawStatus,
                            newValue = normalized.userStatus.name,
                            counted = 0,
                            timestamp = now
                        )
                    )

                    outboxDao.insert(
                        SyncOutboxEntity(
                            id = UUID.randomUUID().toString(),
                            mediaId = item.id,
                            action = "UPDATE",
                            payload = """{"userStatus":"${normalized.userStatus.name}"}""",
                            createdAt = now
                        )
                    )
                }
            }
        }

        if (userdataChanged) {
            val skipStatusPush = statusChanged && normalized.userStatus == UserStatus.COMPLETED && autoUpdateOnMarkRead == "NEVER"
            if (!skipStatusPush) {
                syncEngine?.let { engine ->
                    syncScope.launch {
                        runCatching { engine.pushDirtyItem(item.id) }
                    }
                }
            }
        }
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.softDelete(id, now)
    }

    override suspend fun updateStatus(ids: Collection<String>, status: UserStatus) {
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext
            val now = System.currentTimeMillis()
            val delayedStore = context?.let { app.lazydex.data.tracker.service.DelayedTrackingStore(it) }

            runInTransaction {
                ids.forEach { id ->
                    val existingActive = dao.getActiveById(id) ?: return@forEach
                    val currentDomain = existingActive.toDomain()
                    val targetStatus = status

                    if (currentDomain.userStatus == targetStatus) return@forEach

                    val currentUserData = dao.getUserData(id)
                    val updated = currentDomain.copy(userStatus = targetStatus)
                    val updatedUserData = updated.toUserDataEntity(
                        updatedAtOverride = now,
                        progressIncreasedAtOverride = currentDomain.progressIncreasedAt,
                        existing = currentUserData
                    )
                    dao.upsertUserData(updatedUserData)
                    linkDao.updateSyncStatusByMediaId(id, "DIRTY_LOCAL", now)

                    activityDao.upsert(
                        ActivityEntity(
                            id = UUID.randomUUID().toString(),
                            mediaId = id,
                            type = "STATUS_CHANGE",
                            oldValue = currentDomain.userStatus.name,
                            newValue = targetStatus.name,
                            counted = 0,
                            timestamp = now
                        )
                    )

                    outboxDao.insert(
                        SyncOutboxEntity(
                            id = UUID.randomUUID().toString(),
                            mediaId = id,
                            action = "UPDATE",
                            payload = """{"userStatus":"${targetStatus.name}"}""",
                            createdAt = now
                        )
                    )

                    delayedStore?.addStatus(id, targetStatus.name)
                }
            }

            context?.let { ctx ->
                DelayedTrackingUpdateJob.setupTask(ctx)
            }

            syncEngine?.let { engine ->
                syncScope.launch {
                    ids.forEach { id ->
                        runCatching { engine.pushDirtyItem(id) }
                    }
                }
            }
        }
    }

    override suspend fun updateRepeat(id: String, repeat: Int) {
        withContext(Dispatchers.IO) {
            val safeRepeat = repeat.coerceAtLeast(0)
            val now = System.currentTimeMillis()
            var shouldPush = false
            runInTransaction {
                val existingUser = dao.getUserData(id) ?: return@runInTransaction
                if (existingUser.repeat == safeRepeat) return@runInTransaction

                val updatedUser = existingUser.copy(repeat = safeRepeat, updatedAt = now)
                dao.upsertUserData(updatedUser)
                val dirtyRows = linkDao.updateSyncStatusByMediaId(id, "DIRTY_LOCAL", now)
                if (dirtyRows > 0) {
                    shouldPush = true
                }
                outboxDao.insert(
                    SyncOutboxEntity(
                        id = UUID.randomUUID().toString(),
                        mediaId = id,
                        action = "UPDATE",
                        payload = """{"repeat":$safeRepeat}""",
                        createdAt = now
                    )
                )
            }
            if (shouldPush) {
                syncEngine?.let { engine ->
                    syncScope.launch {
                        runCatching { engine.pushDirtyItem(id) }
                    }
                }
            }
        }
    }

    override suspend fun deleteItems(ids: Collection<String>) {
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext
            val now = System.currentTimeMillis()
            runInTransaction {
                ids.forEach { id ->
                    dao.softDelete(id, now)
                }
            }
        }
    }

    override suspend fun incrementProgress(
        id: String,
        delta: Double,
        mode: String?,
        speed: Double
    ): MediaItem? = withContext(Dispatchers.IO) {
        var shouldPush = false
        val updatedDomain = runInTransaction {
            val activeView = dao.getActiveById(id) ?: return@runInTransaction null
            val mediaEntity = dao.getById(id) ?: return@runInTransaction null
            val existingUser = dao.getUserData(id) ?: UserDataEntity(
                mediaId = id,
                userStatus = activeView.userStatus,
                progress = activeView.progress,
                secondaryProgress = activeView.secondaryProgress,
                scoreRaw = activeView.scoreRaw,
                repeat = activeView.repeat,
                notes = activeView.notes,
                isPrivate = activeView.isPrivate,
                startYear = activeView.startYear,
                startMonth = activeView.startMonth,
                startDay = activeView.startDay,
                endYear = activeView.endYear,
                endMonth = activeView.endMonth,
                endDay = activeView.endDay,
                updatedAt = activeView.userUpdatedAt,
                progressIncreasedAt = activeView.progressIncreasedAt
            )
            val now = System.currentTimeMillis()

            val maxProgress = activeView.progressTotal
            val newProgress = existingUser.progress + delta
            val clampedProgress = if (maxProgress != null) {
                newProgress.coerceIn(0.0, maxProgress)
            } else {
                newProgress.coerceAtLeast(0.0)
            }

            var newStatus = existingUser.userStatus
            var newRepeat = existingUser.repeat
            if ((existingUser.userStatus == "PLANNING" || existingUser.userStatus == "PLAN_TO") && clampedProgress > 0.0) {
                newStatus = "CURRENT"
            }
            if (maxProgress != null && clampedProgress >= maxProgress && existingUser.userStatus != "COMPLETED") {
                if (existingUser.userStatus == "REPEATING") {
                    newRepeat = existingUser.repeat + 1
                }
                newStatus = "COMPLETED"
            } else if (existingUser.userStatus == "COMPLETED" && maxProgress != null && clampedProgress < maxProgress) {
                newStatus = "CURRENT"
            }

            val progressChanged = abs(clampedProgress - existingUser.progress) > 1e-6 || newStatus != existingUser.userStatus || newRepeat != existingUser.repeat
            if (!progressChanged) {
                return@runInTransaction activeView.toDomain()
            }

            val progressIncreased = clampedProgress > existingUser.progress + 1e-6
            val newProgressIncreasedAt = if (progressIncreased) now else existingUser.progressIncreasedAt

            val updatedUser = existingUser.copy(
                progress = clampedProgress,
                userStatus = newStatus,
                repeat = newRepeat,
                updatedAt = now,
                progressIncreasedAt = newProgressIncreasedAt
            )
            dao.upsertUserData(updatedUser)

            val dirtyRows = linkDao.updateSyncStatusByMediaId(id, "DIRTY_LOCAL", now)
            if (dirtyRows > 0) {
                shouldPush = true
            }

            val autoUpdateOnMarkRead = settingDao.get("tracker.autoUpdateOnMarkRead") ?: "ALWAYS"
            if (newStatus == "COMPLETED" && autoUpdateOnMarkRead == "NEVER") {
                shouldPush = false
            }

            if (abs(delta) > 1e-6) {
                recordProgressActivity(
                    mediaId = id,
                    itemType = activeView.type,
                    avgChapterLength = activeView.avgChapterLength,
                    oldProgress = existingUser.progress,
                    newProgress = clampedProgress,
                    delta = delta,
                    mode = mode,
                    speed = speed,
                    now = now
                )
            }

            if (newStatus != existingUser.userStatus) {
                activityDao.upsert(
                    ActivityEntity(
                        id = UUID.randomUUID().toString(),
                        mediaId = id,
                        type = "STATUS_CHANGE",
                        oldValue = existingUser.userStatus,
                        newValue = newStatus,
                        counted = 0,
                        timestamp = now
                    )
                )
            }

            outboxDao.insert(
                SyncOutboxEntity(
                    id = UUID.randomUUID().toString(),
                    mediaId = id,
                    action = "UPDATE",
                    payload = """{"progress":$clampedProgress,"userStatus":"$newStatus","repeat":$newRepeat}""",
                    createdAt = now
                )
            )

            mediaEntity.toDomain(updatedUser)
        }

        if (shouldPush) {
            syncEngine?.let { engine ->
                syncScope.launch {
                    runCatching { engine.pushDirtyItem(id) }
                }
            }
        }

        updatedDomain
    }

    private suspend fun recordProgressActivity(
        mediaId: String,
        itemType: String,
        avgChapterLength: Int?,
        oldProgress: Double,
        newProgress: Double,
        delta: Double,
        mode: String?,
        speed: Double,
        now: Long
    ) {
        val coalesceSec = settingDao.get("activity.coalesceSeconds")?.toLongOrNull() ?: 30L
        val minTs = now - (coalesceSec * 1000L)
        val existing = activityDao.findCoalescableProgress(mediaId, mode, minTs)
        val config = getResolvedConfig(itemType)
        val rateKey = "rate.${mode ?: config.actionVerb.uppercase()}.${config.lengthUnit.name}"
        val baseRate = settingDao.get(rateKey)?.toDoubleOrNull() ?: (if (mode == "LISTEN") 250.0 else 500.0)

        if (existing != null) {
            val combinedDelta = (existing.delta ?: 0.0) + delta
            if (abs(combinedDelta) <= 1e-6) {
                activityDao.delete(existing.id)
            } else {
                activityDao.upsert(
                    existing.copy(
                        delta = combinedDelta,
                        newValue = newProgress.toString(),
                        timestamp = now
                    )
                )
            }
        } else {
            activityDao.upsert(
                ActivityEntity(
                    id = UUID.randomUUID().toString(),
                    mediaId = mediaId,
                    type = "PROGRESS_UPDATE",
                    oldValue = oldProgress.toString(),
                    newValue = newProgress.toString(),
                    delta = delta,
                    mode = mode ?: config.actionVerb.uppercase(),
                    speed = if (mode == "LISTEN") speed else 1.0,
                    lengthSnapshot = avgChapterLength,
                    lengthUnit = config.lengthUnit.name,
                    rateSnapshot = baseRate,
                    counted = 1,
                    timestamp = now
                )
            )
        }
    }

    override suspend fun replaceAll(items: List<MediaItem>) = withContext(Dispatchers.IO) {
        runInTransaction {
            val normalizedItems = items.map { it.normalize() }
            dao.replaceAllWithUserData(
                normalizedItems.map { it.toEntity() },
                normalizedItems.map {
                    val hasInitial = it.progress > 0.0 || (it.hasSecondaryProgress && it.secondaryProgress > 0.0)
                    it.toUserDataEntity(
                        progressIncreasedAtOverride = it.progressIncreasedAt ?: if (hasInitial) it.lastUpdated.takeIf { ts -> ts > 0L } ?: it.dateAdded else null
                    )
                }
            )
        }
    }

    private fun mediaTypeToDbType(mediaType: MediaType): String = when (mediaType) {
        MediaType.NOVEL -> "NOVEL"
        MediaType.MANGA -> "MANGA"
        MediaType.ANIME, MediaType.MOVIE, MediaType.TV -> "ANIME"
        MediaType.GAME -> "GAME"
    }

    private fun resolveTypeAndFormat(typeId: String?): Pair<String?, String?> {
        if (typeId == null) return null to null
        return when (typeId.uppercase()) {
            "NOVEL", "TYP_NOVEL" -> "NOVEL" to null
            "MANGA", "TYP_MANGA" -> "MANGA" to null
            "ANIME", "TYP_ANIME" -> "ANIME" to null
            "GAME", "TYP_GAME" -> "GAME" to null
            "LIGHT_NOVEL", "TYP_NOVEL_LN" -> "NOVEL" to "LIGHT_NOVEL"
            "WEB_NOVEL", "TYP_NOVEL_CN", "TYP_NOVEL_KR" -> "NOVEL" to "WEB_NOVEL"
            "MANHWA", "TYP_MANHWA" -> "MANGA" to "MANHWA"
            "MANHUA", "TYP_MANHUA" -> "MANGA" to "MANHUA"
            "ONE_SHOT" -> "MANGA" to "ONE_SHOT"
            "TV", "TYP_ANIME_SERIES" -> "ANIME" to "TV"
            "MOVIE", "TYP_ANIME_MOVIE" -> "ANIME" to "MOVIE"
            "OVA", "TYP_ANIME_OVA" -> "ANIME" to "OVA"
            "PC" -> "GAME" to "PC"
            "ANDROID" -> "GAME" to "ANDROID"
            "CONSOLE" -> "GAME" to "CONSOLE"
            "WEB" -> "GAME" to "WEB"
            "CROSS_PLATFORM" -> "GAME" to "CROSS_PLATFORM"
            else -> typeId to null
        }
    }

    private fun ActiveMediaItemView.toDomain(): MediaItem {
        val altTitles = listOfNotNull(titleRomaji, titleEnglish, titleNative)
        val genresList = converters.toList(genres)
        val tagsList = converters.toList(tags)
        val stat = UserStatus.fromString(userStatus) ?: UserStatus.PLANNING
        val calcRating = scoreRaw?.let { it / 20.0 }
        return MediaItem(
            id = id,
            type = type,
            format = format,
            countryOfOrigin = countryOfOrigin,
            title = title,
            alternativeTitles = altTitles,
            sourceUrl = null,
            coverImagePath = coverImagePath,
            coverImageUrl = coverImageUrl,
            currentProgress = progress.toInt(),
            totalItems = progressTotal?.toInt(),
            progress = progress,
            progressTotal = progressTotal,
            secondaryProgress = secondaryProgress,
            secondaryProgressTotal = secondaryProgressTotal,
            actionOverride = null,
            avgChapterLength = avgChapterLength,
            lengthUnitOverride = lengthUnitOverride,
            playbackSpeed = playbackSpeed,
            userStatus = stat,
            rating = calcRating,
            notes = notes,
            genres = genresList,
            tags = tagsList,
            author = author,
            description = description,
            startDate = fuzzyDateToMillis(startYear, startMonth, startDay),
            endDate = fuzzyDateToMillis(endYear, endMonth, endDay),
            lastUpdated = userUpdatedAt ?: catalogUpdatedAt,
            dateAdded = createdAt,
            extraData = if (!bannerImage.isNullOrBlank()) """{"banner_image":"${bannerImage.replace("\"", "\\\"")}"}""" else "{}",
            deletedAt = deletedAt,
            isAdult = isAdult,
            repeat = repeat,
            durationMinutes = durationMinutes,
            progressIncreasedAt = progressIncreasedAt
        )
    }

    private fun MediaItemEntity.toDomain(userData: UserDataEntity? = null): MediaItem {
        val altTitles = listOfNotNull(titleRomaji, titleEnglish, titleNative)
        val genresList = converters.toList(genres)
        val tagsList = converters.toList(tags)
        val stat = UserStatus.fromString(userData?.userStatus) ?: UserStatus.PLANNING
        val calcRating = userData?.scoreRaw?.let { it / 20.0 }
        val userProg = userData?.progress ?: 0.0
        return MediaItem(
            id = id,
            type = type,
            format = format,
            countryOfOrigin = countryOfOrigin,
            title = title,
            alternativeTitles = altTitles,
            sourceUrl = null,
            coverImagePath = coverImagePath,
            coverImageUrl = coverImageUrl,
            currentProgress = userProg.toInt(),
            totalItems = progressTotal?.toInt(),
            progress = userProg,
            progressTotal = progressTotal,
            secondaryProgress = userData?.secondaryProgress ?: 0.0,
            secondaryProgressTotal = secondaryProgressTotal,
            actionOverride = null,
            avgChapterLength = avgChapterLength,
            lengthUnitOverride = lengthUnitOverride,
            playbackSpeed = playbackSpeed,
            userStatus = stat,
            rating = calcRating,
            notes = userData?.notes ?: "",
            genres = genresList,
            tags = tagsList,
            author = author,
            description = description,
            startDate = fuzzyDateToMillis(userData?.startYear, userData?.startMonth, userData?.startDay),
            endDate = fuzzyDateToMillis(userData?.endYear, userData?.endMonth, userData?.endDay),
            lastUpdated = userData?.updatedAt ?: updatedAt,
            dateAdded = createdAt,
            extraData = if (!bannerImage.isNullOrBlank()) """{"banner_image":"${bannerImage.replace("\"", "\\\"")}"}""" else "{}",
            deletedAt = deletedAt,
            isAdult = isAdult,
            repeat = userData?.repeat ?: 0,
            durationMinutes = durationMinutes,
            progressIncreasedAt = userData?.progressIncreasedAt
        )
    }

    private fun MediaItem.toEntity(): MediaItemEntity {
        val genresJson = converters.fromList(genres)
        val tagsJson = converters.fromList(tags)
        return MediaItemEntity(
            id = id,
            type = type,
            format = format,
            countryOfOrigin = countryOfOrigin,
            title = title,
            titleEnglish = alternativeTitles.getOrNull(1),
            titleRomaji = alternativeTitles.getOrNull(0),
            titleNative = alternativeTitles.getOrNull(2),
            coverImagePath = coverImagePath,
            coverImageUrl = coverImageUrl,
            bannerImage = getExtra("banner_image"),
            description = description,
            author = author,
            progressTotal = progressTotal,
            secondaryProgressTotal = secondaryProgressTotal,
            genres = genresJson,
            tags = tagsJson,
            isAdult = isAdult,
            durationMinutes = durationMinutes,
            avgChapterLength = avgChapterLength,
            lengthUnitOverride = lengthUnitOverride,
            playbackSpeed = playbackSpeed,
            createdAt = dateAdded,
            updatedAt = lastUpdated,
            deletedAt = deletedAt
        )
    }

    private fun MediaItem.toUserDataEntity(
        updatedAtOverride: Long? = null,
        progressIncreasedAtOverride: Long? = null,
        existing: UserDataEntity? = null
    ): UserDataEntity {
        val rawScore = rating?.let { kotlin.math.round(it * 20.0).toInt().coerceIn(0, 100) }
        val (sYear, sMonth, sDay) = millisToFuzzyDate(startDate)
        val (eYear, eMonth, eDay) = millisToFuzzyDate(endDate)
        return UserDataEntity(
            mediaId = id,
            userStatus = userStatus.name,
            progress = progress,
            secondaryProgress = if (hasSecondaryProgress) secondaryProgress else 0.0,
            scoreRaw = rawScore,
            repeat = repeat,
            notes = notes,
            isPrivate = existing?.isPrivate ?: false,
            startYear = sYear,
            startMonth = sMonth,
            startDay = sDay,
            endYear = eYear,
            endMonth = eMonth,
            endDay = eDay,
            updatedAt = updatedAtOverride ?: lastUpdated,
            progressIncreasedAt = progressIncreasedAtOverride ?: progressIncreasedAt
        )
    }

    private fun ActivityEntity.toDomain(): ActivityLog = ActivityLog(
        id = id,
        mediaId = mediaId,
        type = type,
        oldValue = oldValue,
        newValue = newValue,
        delta = delta,
        mode = mode,
        speed = speed,
        lengthSnapshot = lengthSnapshot,
        lengthUnit = lengthUnit?.let { LengthUnit.valueOf(it) },
        rateSnapshot = rateSnapshot,
        counted = counted == 1,
        timestamp = timestamp
    )

    private fun fuzzyDateToMillis(year: Int?, month: Int?, day: Int?): Long? {
        if (year == null || year <= 0) return null
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, (month?.coerceIn(1, 12) ?: 1) - 1)
        cal.set(Calendar.DAY_OF_MONTH, day?.coerceIn(1, 31) ?: 1)
        return cal.timeInMillis
    }

    private fun millisToFuzzyDate(millis: Long?): Triple<Int?, Int?, Int?> {
        if (millis == null || millis <= 0L) return Triple(null, null, null)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = millis
        return Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }
}
