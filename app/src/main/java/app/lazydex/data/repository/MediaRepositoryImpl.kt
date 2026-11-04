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
import app.lazydex.data.tracker.anilist.AnilistTokenStore
import app.lazydex.data.tracker.service.DelayedTrackingUpdateJob
import app.lazydex.data.tracker.sync.AnilistSyncEngine
import app.lazydex.data.tracker.sync.TrackChapter
import app.lazydex.domain.model.ActivityLog
import app.lazydex.domain.model.Category
import app.lazydex.domain.model.LengthUnit
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.resolveMediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaPart
import app.lazydex.domain.model.MediaStats
import app.lazydex.domain.model.ResolvedCategoryConfig
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.UserStatus
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

    override fun observeByType(type: String): Flow<List<MediaItem>> = dao.observeByType(type)
        .map { list -> list.map { it.toDomain() } }
        .distinctUntilChanged()

    override fun observeByCategory(category: MediaCategory): Flow<List<MediaItem>> {
        val type = categoryToType(category)
        return dao.observeByType(type)
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()
    }

    override fun observeByCategoryId(categoryId: String): Flow<List<MediaItem>> {
        val (type, format) = resolveTypeAndFormat(categoryId)
        return observeFiltered(type, format, StatusFilter.ALL)
    }

    override fun observeFiltered(category: MediaCategory?, statusFilter: StatusFilter): Flow<List<MediaItem>> {
        val type = category?.let { categoryToType(it) }
        return observeFiltered(type, null, statusFilter)
    }

    override fun observeFiltered(type: String?, format: String?, statusFilter: StatusFilter): Flow<List<MediaItem>> {
        val (filterType, exactStatus) = when (statusFilter) {
            StatusFilter.ALL -> "ALL" to null
            StatusFilter.IN_PROGRESS -> "IN_PROGRESS" to null
            StatusFilter.COMPLETED -> "EXACT" to UserStatus.COMPLETED.name
            StatusFilter.ON_HOLD -> "EXACT" to UserStatus.ON_HOLD.name
            StatusFilter.DROPPED -> "EXACT" to UserStatus.DROPPED.name
            StatusFilter.PLAN_TO -> "EXACT" to UserStatus.PLAN_TO.name
        }
        return dao.observeFiltered(type, format, filterType, exactStatus)
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()
    }

    override fun observeFilteredByCategoryId(categoryId: String?, statusFilter: StatusFilter): Flow<List<MediaItem>> {
        val (type, format) = resolveTypeAndFormat(categoryId)
        return observeFiltered(type, format, statusFilter)
    }

    override fun observeById(id: String): Flow<MediaItem?> = dao.observeById(id)
        .map { it?.toDomain() }
        .distinctUntilChanged()

    override fun observeCount(): Flow<Int> = dao.observeCount().distinctUntilChanged()

    override fun observeStats(): Flow<MediaStats> = dao.getStats().distinctUntilChanged()

    override fun observeCategoryCounts(): Flow<Map<MediaCategory, Int>> = dao.observeTypeCounts()
        .map { list ->
            val counts = list.associate { count ->
                val cat = when (count.type.uppercase()) {
                    "MANGA" -> MediaCategory.MANGA
                    "ANIME" -> MediaCategory.ANIME
                    "GAME" -> MediaCategory.GAME
                    else -> MediaCategory.NOVEL
                }
                cat to count.count
            }
            MediaCategory.entries.associateWith { counts[it] ?: 0 }
        }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()

    override fun observeStatusCounts(category: MediaCategory?): Flow<Map<StatusFilter, Int>> {
        val type = category?.let { categoryToType(it) }
        return dao.observeStatusCounts(type)
            .map { list ->
                val raw = list.associate { it.userStatus to it.count }
                val inProgress = (raw["READING"] ?: 0) + (raw["WATCHING"] ?: 0) + (raw["PLAYING"] ?: 0) + (raw["IN_PROGRESS"] ?: 0)
                mapOf(
                    StatusFilter.ALL to raw.values.sum(),
                    StatusFilter.IN_PROGRESS to inProgress,
                    StatusFilter.COMPLETED to (raw["COMPLETED"] ?: 0),
                    StatusFilter.ON_HOLD to (raw["ON_HOLD"] ?: 0),
                    StatusFilter.DROPPED to (raw["DROPPED"] ?: 0),
                    StatusFilter.PLAN_TO to (raw["PLAN_TO"] ?: 0)
                )
            }
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
    }

    override fun observeDistinctGenres(category: MediaCategory?): Flow<List<String>> {
        val type = category?.let { categoryToType(it) }
        val source = if (type != null) dao.observeByType(type) else dao.observeAll()
        return source.map { entities ->
            entities.flatMap { converters.toList(it.genres) }
                .distinctBy { it.lowercase() }
                .sorted()
        }.flowOn(Dispatchers.IO).distinctUntilChanged()
    }

    override fun observeDistinctTags(category: MediaCategory?): Flow<List<String>> {
        val type = category?.let { categoryToType(it) }
        val source = if (type != null) dao.observeByType(type) else dao.observeAll()
        return source.map { entities ->
            entities.flatMap { converters.toList(it.tags) }
                .distinctBy { it.lowercase() }
                .sorted()
        }.flowOn(Dispatchers.IO).distinctUntilChanged()
    }

    private val staticTopLevelCategories = listOf(
        Category(id = "NOVEL", name = "Novel", icon = "book", unitName = "Chapter", actionVerb = "Read", sortOrder = 0),
        Category(id = "MANGA", name = "Manga", icon = "image", unitName = "Chapter", actionVerb = "Read", sortOrder = 1),
        Category(id = "ANIME", name = "Anime", icon = "tv", unitName = "Episode", actionVerb = "Watch", sortOrder = 2),
        Category(id = "GAME", name = "Game", icon = "gamepad", unitName = "Hour", actionVerb = "Play", sortOrder = 3)
    )

    private val staticSubtypes = mapOf(
        "NOVEL" to listOf(
            Category(id = "LIGHT_NOVEL", parentId = "NOVEL", name = "Light Novel", unitName = "Volume", sortOrder = 0),
            Category(id = "WEB_NOVEL", parentId = "NOVEL", name = "Web Novel", unitName = "Chapter", sortOrder = 1)
        ),
        "MANGA" to listOf(
            Category(id = "MANGA", parentId = "MANGA", name = "Manga", unitName = "Volume", sortOrder = 0),
            Category(id = "MANHWA", parentId = "MANGA", name = "Manhwa", unitName = "Chapter", sortOrder = 1),
            Category(id = "MANHUA", parentId = "MANGA", name = "Manhua", unitName = "Chapter", sortOrder = 2),
            Category(id = "ONE_SHOT", parentId = "MANGA", name = "One Shot", unitName = "Chapter", sortOrder = 3)
        ),
        "ANIME" to listOf(
            Category(id = "TV", parentId = "ANIME", name = "TV Series", unitName = "Episode", secondaryUnitName = "Season", sortOrder = 0),
            Category(id = "MOVIE", parentId = "ANIME", name = "Movie", unitName = "Minute", sortOrder = 1),
            Category(id = "OVA", parentId = "ANIME", name = "OVA / Special", unitName = "Episode", sortOrder = 2)
        ),
        "GAME" to listOf(
            Category(id = "PC", parentId = "GAME", name = "PC", unitName = "Hour", sortOrder = 0),
            Category(id = "ANDROID", parentId = "GAME", name = "Android", unitName = "Hour", sortOrder = 1),
            Category(id = "CONSOLE", parentId = "GAME", name = "Console", unitName = "Hour", sortOrder = 2),
            Category(id = "WEB", parentId = "GAME", name = "Web", unitName = "Hour", sortOrder = 3),
            Category(id = "CROSS_PLATFORM", parentId = "GAME", name = "Cross Platform", unitName = "Hour", sortOrder = 4)
        )
    )

    override fun observeCategories(): Flow<List<Category>> = flowOf(staticTopLevelCategories + staticSubtypes.values.flatten())

    override fun observeTopLevelCategories(): Flow<List<Category>> = flowOf(staticTopLevelCategories)

    override fun observeTypes(parentId: String): Flow<List<Category>> = flowOf(staticSubtypes[parentId.uppercase()] ?: emptyList())

    override fun observeResolvedConfig(categoryId: String): Flow<ResolvedCategoryConfig> = flow {
        emit(getResolvedConfig(categoryId))
    }

    override suspend fun getResolvedConfig(categoryId: String): ResolvedCategoryConfig {
        val (type, format) = resolveTypeAndFormat(categoryId)
        val actualType = type ?: "NOVEL"
        return when (actualType) {
            "MANGA" -> ResolvedCategoryConfig(
                categoryId = categoryId,
                topLevelId = "MANGA",
                displayName = format ?: "Manga",
                icon = "image",
                unitName = "Chapter",
                secondaryUnitName = if (format == "MANGA") "Volume" else null,
                actionVerb = "Read",
                hasParts = false,
                hasCharEstimate = false,
                hasAudioSpeed = false,
                lengthUnit = LengthUnit.WORDS
            )
            "ANIME" -> ResolvedCategoryConfig(
                categoryId = categoryId,
                topLevelId = "ANIME",
                displayName = format ?: "Anime",
                icon = "tv",
                unitName = if (format == "MOVIE") "Minute" else "Episode",
                secondaryUnitName = if (format == "TV") "Season" else null,
                actionVerb = "Watch",
                hasParts = false,
                hasCharEstimate = false,
                hasAudioSpeed = false,
                lengthUnit = LengthUnit.WORDS
            )
            "GAME" -> ResolvedCategoryConfig(
                categoryId = categoryId,
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
            else -> ResolvedCategoryConfig(
                categoryId = categoryId,
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

    override suspend fun addCategory(category: Category): Category = category
    override suspend fun updateCategory(category: Category) {}
    override suspend fun deleteCategory(categoryId: String) {}

    override fun observeParts(mediaId: String): Flow<List<MediaPart>> = emptyFlow()

    override suspend fun setParts(mediaId: String, parts: List<MediaPart>) {}

    override fun observeActivity(mediaId: String): Flow<List<ActivityLog>> = activityDao.observeByMediaId(mediaId)
        .map { list -> list.map { it.toDomain() } }
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
        dao.getActiveById(id)?.toDomain()
    }

    override suspend fun getAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        dao.getAll().map { it.toDomain() }
    }

    override suspend fun add(item: MediaItem): MediaItem = withContext(Dispatchers.IO) {
        val normalized = item.normalize()
        val entity = normalized.toEntity()
        dao.upsert(entity)

        val autoUpdateOnMarkRead = settingDao.get("tracker.autoUpdateOnMarkRead") ?: "ALWAYS"
        val skipPush = normalized.userStatus == UserStatus.COMPLETED && autoUpdateOnMarkRead == "NEVER"

        if (!skipPush) {
            syncEngine?.let { engine ->
                syncScope.launch {
                    runCatching { engine.pushDirtyItem(item.id) }
                }
            }
        }

        normalized
    }

    override suspend fun update(item: MediaItem) = withContext(Dispatchers.IO) {
        val normalized = item.normalize()
        val entity = normalized.toEntity()

        val autoUpdateOnMarkRead = settingDao.get("tracker.autoUpdateOnMarkRead") ?: "ALWAYS"
        val current = dao.getById(item.id)
        val statusChanged = current != null && current.userStatus != entity.userStatus

        runInTransaction {
            val now = System.currentTimeMillis()
            dao.upsert(entity)

            val dirtyRows = linkDao.updateSyncStatusByMediaId(item.id, "DIRTY_LOCAL", now)
            if (dirtyRows > 0) {
                // marked dirty
            }

            if (statusChanged) {
                activityDao.upsert(
                    ActivityEntity(
                        id = UUID.randomUUID().toString(),
                        mediaId = item.id,
                        type = "STATUS_CHANGE",
                        oldValue = current!!.userStatus,
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

        val skipStatusPush = statusChanged && normalized.userStatus == UserStatus.COMPLETED && autoUpdateOnMarkRead == "NEVER"
        if (!skipStatusPush) {
            syncEngine?.let { engine ->
                syncScope.launch {
                    runCatching { engine.pushDirtyItem(item.id) }
                }
            }
        }
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.softDelete(id, now)
    }

    override suspend fun incrementProgress(
        id: String,
        delta: Double,
        mode: String?,
        speed: Double
    ): MediaItem? = withContext(Dispatchers.IO) {
        var shouldPush = false
        val updatedDomain = runInTransaction {
            val item = dao.getActiveById(id) ?: return@runInTransaction null
            val now = System.currentTimeMillis()

            val maxProgress = item.progressTotal
            val newProgress = item.progress + delta
            val clampedProgress = if (maxProgress != null) {
                newProgress.coerceIn(0.0, maxProgress)
            } else {
                newProgress.coerceAtLeast(0.0)
            }

            var newStatus = item.userStatus
            if (item.userStatus == "PLANNING" && clampedProgress > 0.0) {
                newStatus = "IN_PROGRESS"
            }
            if (maxProgress != null && clampedProgress >= maxProgress && item.userStatus != "COMPLETED") {
                newStatus = "COMPLETED"
            } else if (item.userStatus == "COMPLETED" && maxProgress != null && clampedProgress < maxProgress) {
                newStatus = "IN_PROGRESS"
            }

            val updatedEntity = item.toEntity().copy(
                progress = clampedProgress,
                userStatus = newStatus,
                updatedAt = now
            )
            dao.upsert(updatedEntity)

            val dirtyRows = linkDao.updateSyncStatusByMediaId(id, "DIRTY_LOCAL", now)
            if (dirtyRows > 0) {
                shouldPush = true
            }

            val autoUpdateOnMarkRead = settingDao.get("tracker.autoUpdateOnMarkRead") ?: "ALWAYS"
            if (newStatus == "COMPLETED" && autoUpdateOnMarkRead == "NEVER") {
                shouldPush = false
            }

            if (delta != 0.0) {
                val coalesceSec = settingDao.get("activity.coalesceSeconds")?.toLongOrNull() ?: 30L
                val minTs = now - (coalesceSec * 1000L)
                val existing = activityDao.findCoalescableProgress(id, mode, minTs)
                val config = getResolvedConfig(item.type)
                val rateKey = "rate.${mode ?: config.actionVerb.uppercase()}.${config.lengthUnit.name}"
                val baseRate = settingDao.get(rateKey)?.toDoubleOrNull() ?: (if (mode == "LISTEN") 250.0 else 500.0)

                if (existing != null) {
                    val combinedDelta = (existing.delta ?: 0.0) + delta
                    if (combinedDelta == 0.0) {
                        activityDao.delete(existing.id)
                    } else {
                        activityDao.upsert(
                            existing.copy(
                                delta = combinedDelta,
                                newValue = clampedProgress.toString(),
                                timestamp = now
                            )
                        )
                    }
                } else {
                    activityDao.upsert(
                        ActivityEntity(
                            id = UUID.randomUUID().toString(),
                            mediaId = id,
                            type = "PROGRESS_UPDATE",
                            oldValue = item.progress.toString(),
                            newValue = clampedProgress.toString(),
                            delta = delta,
                            mode = mode ?: config.actionVerb.uppercase(),
                            speed = if (mode == "LISTEN") speed else 1.0,
                            lengthSnapshot = item.avgChapterLength,
                            lengthUnit = config.lengthUnit.name,
                            rateSnapshot = baseRate,
                            counted = 1,
                            timestamp = now
                        )
                    )
                }
            }

            if (newStatus != item.userStatus) {
                activityDao.upsert(
                    ActivityEntity(
                        id = UUID.randomUUID().toString(),
                        mediaId = id,
                        type = "STATUS_CHANGE",
                        oldValue = item.userStatus,
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
                    payload = """{"progress":$clampedProgress,"userStatus":"$newStatus"}""",
                    createdAt = now
                )
            )

            updatedEntity.toDomain()
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

    override suspend fun replaceAll(items: List<MediaItem>) = withContext(Dispatchers.IO) {
        runInTransaction {
            dao.deleteAll()
            dao.upsertAll(items.map { it.normalize().toEntity() })
        }
    }

    private fun categoryToType(category: MediaCategory): String = when (category) {
        MediaCategory.NOVEL -> "NOVEL"
        MediaCategory.MANGA -> "MANGA"
        MediaCategory.ANIME, MediaCategory.MOVIE, MediaCategory.TV -> "ANIME"
        MediaCategory.GAME -> "GAME"
    }

    private fun resolveTypeAndFormat(categoryId: String?): Pair<String?, String?> {
        if (categoryId == null) return null to null
        return when (categoryId.uppercase()) {
            "NOVEL", "CAT_NOVEL" -> "NOVEL" to null
            "MANGA", "CAT_MANGA" -> "MANGA" to null
            "ANIME", "CAT_ANIME" -> "ANIME" to null
            "GAME", "CAT_GAME" -> "GAME" to null
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
            else -> categoryId to null
        }
    }

    private fun ActiveMediaItemView.toDomain(): MediaItem {
        val altTitles = listOfNotNull(titleRomaji, titleEnglish, titleNative)
        val genresList = converters.toList(genres)
        val tagsList = converters.toList(tags)
        val stat = UserStatus.fromString(userStatus) ?: UserStatus.PLAN_TO
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
            lastUpdated = updatedAt,
            dateAdded = createdAt,
            deletedAt = deletedAt,
            isAdult = isAdult
        )
    }

    private fun MediaItemEntity.toDomain(): MediaItem {
        val altTitles = listOfNotNull(titleRomaji, titleEnglish, titleNative)
        val genresList = converters.toList(genres)
        val tagsList = converters.toList(tags)
        val stat = UserStatus.fromString(userStatus) ?: UserStatus.PLAN_TO
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
            lastUpdated = updatedAt,
            dateAdded = createdAt,
            deletedAt = deletedAt,
            isAdult = isAdult
        )
    }

    private fun ActiveMediaItemView.toEntity(): MediaItemEntity = MediaItemEntity(
        id = id,
        type = type,
        format = format,
        countryOfOrigin = countryOfOrigin,
        title = title,
        titleEnglish = titleEnglish,
        titleRomaji = titleRomaji,
        titleNative = titleNative,
        coverImagePath = coverImagePath,
        coverImageUrl = coverImageUrl,
        bannerImage = bannerImage,
        description = description,
        author = author,
        userStatus = userStatus,
        progress = progress,
        progressTotal = progressTotal,
        secondaryProgress = secondaryProgress,
        secondaryProgressTotal = secondaryProgressTotal,
        scoreRaw = scoreRaw,
        repeat = repeat,
        notes = notes,
        genres = genres,
        tags = tags,
        isPrivate = isPrivate,
        isAdult = isAdult,
        startYear = startYear,
        startMonth = startMonth,
        startDay = startDay,
        endYear = endYear,
        endMonth = endMonth,
        endDay = endDay,
        durationMinutes = durationMinutes,
        avgChapterLength = avgChapterLength,
        lengthUnitOverride = lengthUnitOverride,
        playbackSpeed = playbackSpeed,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

    private fun MediaItem.toEntity(): MediaItemEntity {
        val genresJson = converters.fromList(genres)
        val tagsJson = converters.fromList(tags)
        val rawScore = rating?.let { kotlin.math.round(it * 20.0).toInt().coerceIn(0, 100) }
        val (sYear, sMonth, sDay) = millisToFuzzyDate(startDate)
        val (eYear, eMonth, eDay) = millisToFuzzyDate(endDate)
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
            bannerImage = null,
            description = description,
            author = author,
            userStatus = userStatus.name,
            progress = progress,
            progressTotal = progressTotal,
            secondaryProgress = secondaryProgress,
            secondaryProgressTotal = secondaryProgressTotal,
            scoreRaw = rawScore,
            repeat = 0,
            notes = notes,
            genres = genresJson,
            tags = tagsJson,
            isPrivate = false,
            isAdult = isAdult,
            startYear = sYear,
            startMonth = sMonth,
            startDay = sDay,
            endYear = eYear,
            endMonth = eMonth,
            endDay = eDay,
            durationMinutes = null,
            avgChapterLength = avgChapterLength,
            lengthUnitOverride = lengthUnitOverride,
            playbackSpeed = playbackSpeed,
            createdAt = dateAdded,
            updatedAt = lastUpdated,
            deletedAt = deletedAt
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
