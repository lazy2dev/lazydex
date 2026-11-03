package app.lazydex.data.repository

import android.content.Context
import androidx.room.withTransaction
import app.lazydex.data.local.LazyDexDatabase
import app.lazydex.data.local.converter.Converters
import app.lazydex.data.local.dao.ActivityDao
import app.lazydex.data.local.dao.AppSettingDao
import app.lazydex.data.local.dao.CategoryDao
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.dao.MediaLinkDao
import app.lazydex.data.local.dao.MediaPartDao
import app.lazydex.data.local.dao.SyncOutboxDao
import app.lazydex.data.local.entity.ActiveMediaItemView
import app.lazydex.data.local.entity.ActivityEntity
import app.lazydex.data.local.entity.CategoryEntity
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.data.local.entity.SyncOutboxEntity
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
    private val categoryDao: CategoryDao,
    private val linkDao: MediaLinkDao,
    private val activityDao: ActivityDao,
    private val outboxDao: SyncOutboxDao,
    private val settingDao: AppSettingDao,
    private val localCoversDir: File,
    private val partDao: MediaPartDao? = null,
    private val syncEngine: AnilistSyncEngine? = null,
    private val trackChapter: TrackChapter? = null,
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

    override fun observeByCategory(category: MediaCategory): Flow<List<MediaItem>> {
        val categoryId = categoryToId(category)
        return dao.observeByCategory(categoryId)
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()
    }

    override fun observeByCategoryId(categoryId: String): Flow<List<MediaItem>> = dao.observeByCategory(categoryId)
        .map { list -> list.map { it.toDomain() } }
        .distinctUntilChanged()

    override fun observeFiltered(category: MediaCategory?, statusFilter: StatusFilter): Flow<List<MediaItem>> {
        val categoryId = category?.let { categoryToId(it) }
        return observeFilteredByCategoryId(categoryId, statusFilter)
    }

    override fun observeFilteredByCategoryId(categoryId: String?, statusFilter: StatusFilter): Flow<List<MediaItem>> {
        val (filterType, exactStatus) = when (statusFilter) {
            StatusFilter.ALL -> "ALL" to null
            StatusFilter.IN_PROGRESS -> "IN_PROGRESS" to null
            StatusFilter.COMPLETED -> "EXACT" to UserStatus.COMPLETED.name
            StatusFilter.ON_HOLD -> "EXACT" to UserStatus.ON_HOLD.name
            StatusFilter.DROPPED -> "EXACT" to UserStatus.DROPPED.name
            StatusFilter.PLAN_TO -> "EXACT" to UserStatus.PLAN_TO.name
        }
        return dao.observeFiltered(categoryId, filterType, exactStatus)
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()
    }

    override fun observeById(id: String): Flow<MediaItem?> = dao.observeById(id)
        .map { it?.toDomain() }
        .distinctUntilChanged()

    override fun observeCount(): Flow<Int> = dao.observeCount().distinctUntilChanged()

    override fun observeStats(): Flow<MediaStats> = dao.getStats().distinctUntilChanged()

    override fun observeCategoryCounts(): Flow<Map<MediaCategory, Int>> = dao.observeCategoryCounts()
        .map { list ->
            val counts = list.associate { count ->
                val cat = resolveMediaCategory(count.category)
                cat to count.count
            }
            MediaCategory.entries.associateWith { counts[it] ?: 0 }
        }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()

    override fun observeStatusCounts(category: MediaCategory?): Flow<Map<StatusFilter, Int>> {
        val catId = category?.let { categoryToId(it) }
        return dao.observeStatusCounts(catId)
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
        val catId = category?.let { categoryToId(it) }
        val source = if (catId != null) dao.observeByCategory(catId) else dao.observeAll()
        return source.map { entities ->
            entities.flatMap { converters.toList(it.genres) }
                .distinctBy { it.lowercase() }
                .sorted()
        }.flowOn(Dispatchers.IO).distinctUntilChanged()
    }

    override fun observeDistinctTags(category: MediaCategory?): Flow<List<String>> {
        val catId = category?.let { categoryToId(it) }
        val source = if (catId != null) dao.observeByCategory(catId) else dao.observeAll()
        return source.map { entities ->
            entities.flatMap { converters.toList(it.tags) }
                .distinctBy { it.lowercase() }
                .sorted()
        }.flowOn(Dispatchers.IO).distinctUntilChanged()
    }

    override fun observeCategories(): Flow<List<Category>> = categoryDao.observeAll()
        .map { list -> list.map { it.toDomain() } }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()

    override fun observeTopLevelCategories(): Flow<List<Category>> = categoryDao.observeTopLevel()
        .map { list -> list.map { it.toDomain() } }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()

    override fun observeTypes(parentId: String): Flow<List<Category>> = categoryDao.observeTypes(parentId)
        .map { list -> list.map { it.toDomain() } }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()

    override fun observeResolvedConfig(categoryId: String): Flow<ResolvedCategoryConfig> = categoryDao.observeAll()
        .map { getResolvedConfig(categoryId) }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()

    override suspend fun getResolvedConfig(categoryId: String): ResolvedCategoryConfig = withContext(Dispatchers.IO) {
        val cat = categoryDao.getById(categoryId) ?: categoryDao.getById("cat_uncategorized")
        ?: CategoryEntity(
            id = "cat_uncategorized",
            parentId = null,
            name = "Uncategorized",
            icon = "folder",
            unitName = "Item",
            secondaryUnitName = "",
            actionVerb = "Consume",
            hasSecondaryProgress = 0,
            hasCharEstimate = 0,
            hasAudioSpeed = 0,
            lengthUnit = "WORDS",
            sortOrder = 999,
            createdAt = 0L,
            updatedAt = 0L
        )

        if (cat.parentId != null) {
            val parent = categoryDao.getById(cat.parentId) ?: cat
            val secUnit = when {
                cat.secondaryUnitName == "" -> null
                cat.secondaryUnitName != null -> cat.secondaryUnitName
                parent.secondaryUnitName == "" -> null
                else -> parent.secondaryUnitName
            }
            ResolvedCategoryConfig(
                categoryId = cat.id,
                topLevelId = parent.id,
                displayName = cat.name,
                icon = cat.icon ?: parent.icon ?: "folder",
                unitName = cat.unitName ?: parent.unitName ?: "Item",
                secondaryUnitName = secUnit,
                actionVerb = cat.actionVerb ?: parent.actionVerb ?: "Consume",
                hasParts = (cat.hasParts ?: parent.hasParts ?: 0) == 1,
                hasCharEstimate = (cat.hasCharEstimate ?: parent.hasCharEstimate ?: 0) == 1,
                hasAudioSpeed = (cat.hasAudioSpeed ?: parent.hasAudioSpeed ?: 0) == 1,
                lengthUnit = LengthUnit.valueOf(cat.lengthUnit ?: parent.lengthUnit ?: "WORDS")
            )
        } else {
            ResolvedCategoryConfig(
                categoryId = cat.id,
                topLevelId = cat.id,
                displayName = cat.name,
                icon = cat.icon ?: "folder",
                unitName = cat.unitName ?: "Item",
                secondaryUnitName = cat.secondaryUnitName?.takeIf { it != "" },
                actionVerb = cat.actionVerb ?: "Consume",
                hasParts = (cat.hasParts ?: 0) == 1,
                hasCharEstimate = (cat.hasCharEstimate ?: 0) == 1,
                hasAudioSpeed = (cat.hasAudioSpeed ?: 0) == 1,
                lengthUnit = LengthUnit.valueOf(cat.lengthUnit ?: "WORDS")
            )
        }
    }

    override suspend fun addCategory(category: Category): Category = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val finalId = category.id.ifBlank { UUID.randomUUID().toString() }
        val entity = category.copy(id = finalId, createdAt = now, updatedAt = now).toEntity()
        categoryDao.upsert(entity)
        entity.toDomain()
    }

    override suspend fun updateCategory(category: Category) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        categoryDao.upsert(category.copy(updatedAt = now).toEntity())
    }

    override suspend fun deleteCategory(categoryId: String) = withContext(Dispatchers.IO) {
        runInTransaction {
            val totalItems = categoryDao.countAllMediaItemsIncludingChildren(categoryId)
            if (totalItems > 0) {
                throw IllegalStateException("Cannot delete category containing media items. Reassign items first.")
            }
            val types = categoryDao.getTypes(categoryId)
            if (types.isNotEmpty()) {
                throw IllegalStateException("Cannot delete category with types. Delete or move types first.")
            }
            if (categoryId == "cat_uncategorized") {
                throw IllegalStateException("Cannot delete default Uncategorized category.")
            }
            categoryDao.delete(categoryId)
        }
    }

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
            val mode = act.mode?.uppercase() ?: "READ"
            val length = act.lengthSnapshot?.toDouble() ?: 0.0

            if (length > 0.0) {
                val rate = act.rateSnapshot ?: if (mode == "LISTEN") 250.0 else 500.0
                val spd = if (mode == "LISTEN") (act.speed ?: 1.0) else 1.0
                val minutes = (delta * length) / (rate * spd)
                if (mode == "LISTEN") {
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
        val now = System.currentTimeMillis()
        val finalId = if (item.id.isNotBlank()) item.id else UUID.randomUUID().toString()

        val finalCoverPath = if (item.coverImagePath.isNotEmpty()) {
            val tempFile = File(item.coverImagePath)
            if (tempFile.exists()) {
                if (!localCoversDir.exists()) localCoversDir.mkdirs()
                val destFile = File(localCoversDir, finalId)
                tempFile.copyTo(destFile, overwrite = true)
                try {
                    tempFile.delete()
                } catch (e: Exception) {
                    // Ignore delete errors
                }
                destFile.absolutePath
            } else {
                item.coverImagePath
            }
        } else {
            ""
        }

        val normalized = item.copy(
            id = finalId,
            coverImagePath = finalCoverPath,
            lastUpdated = now,
            dateAdded = now
        ).normalize()

        runInTransaction {
            dao.upsert(normalized.toEntity())

            activityDao.upsert(
                ActivityEntity(
                    id = UUID.randomUUID().toString(),
                    mediaId = finalId,
                    type = "CREATED",
                    oldValue = null,
                    newValue = normalized.title,
                    counted = 0,
                    timestamp = now
                )
            )

            outboxDao.insert(
                SyncOutboxEntity(
                    id = UUID.randomUUID().toString(),
                    mediaId = finalId,
                    action = "CREATE",
                    payload = """{"title":"${normalized.title}"}""",
                    createdAt = now
                )
            )
        }
        normalized
    }

    override suspend fun update(item: MediaItem) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val current = dao.getById(item.id)
        val normalized = item.copy(lastUpdated = now).normalize()
        var shouldPush = false

        runInTransaction {
            val entity = normalized.toEntity()
            dao.upsert(entity)

            val statusChanged = current != null && current.userStatus != entity.userStatus
            val progressChanged = current != null && (current.progress != entity.progress || current.secondaryProgress != entity.secondaryProgress)
            val scoreChanged = current != null && (current.scoreRaw != entity.scoreRaw)

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
            }

            outboxDao.insert(
                SyncOutboxEntity(
                    id = UUID.randomUUID().toString(),
                    mediaId = item.id,
                    action = "UPDATE",
                    payload = """{"userStatus":"${normalized.userStatus.name}"}""",
                    createdAt = now
                )
            )

            if (statusChanged || progressChanged || scoreChanged || current == null) {
                val dirtyRows = linkDao.updateSyncStatusByMediaId(item.id, "DIRTY_LOCAL", now)
                if (dirtyRows > 0) {
                    shouldPush = true
                }
            }
        }

        if (shouldPush) {
            if (context != null && trackChapter != null) {
                syncScope.launch {
                    trackChapter.await(context, item.id, item.progress)
                }
            } else {
                syncEngine?.let { engine ->
                    syncScope.launch {
                        val success = runCatching { engine.pushDirtyItem(item.id) }.getOrDefault(false)
                        if (!success && context != null) {
                            DelayedTrackingUpdateJob.setupTask(context)
                        }
                    }
                }
            }
        }
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        runInTransaction {
            dao.softDelete(id, now)

            activityDao.upsert(
                ActivityEntity(
                    id = UUID.randomUUID().toString(),
                    mediaId = id,
                    type = "DELETED",
                    oldValue = null,
                    newValue = "soft_deleted",
                    counted = 0,
                    timestamp = now
                )
            )

            outboxDao.insert(
                SyncOutboxEntity(
                    id = UUID.randomUUID().toString(),
                    mediaId = id,
                    action = "DELETE",
                    payload = """{"deletedAt":$now}""",
                    createdAt = now
                )
            )
        }
    }

    override suspend fun incrementProgress(
        id: String,
        delta: Double,
        mode: String?,
        speed: Double
    ): MediaItem? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        var shouldPush = false
        val updatedDomain = runInTransaction {
            val item = dao.getById(id) ?: return@runInTransaction null
            val maxProgress = item.progressTotal
            val target = (item.progress + delta).coerceAtLeast(0.0)
            val clampedProgress = if (maxProgress != null) target.coerceAtMost(maxProgress) else target
            val effectiveDelta = clampedProgress - item.progress

            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = now }
            val currentYear = cal.get(Calendar.YEAR)
            val currentMonth = cal.get(Calendar.MONTH) + 1
            val currentDay = cal.get(Calendar.DAY_OF_MONTH)

            var newStatus = item.userStatus
            var startYear = item.startYear
            var startMonth = item.startMonth
            var startDay = item.startDay
            var endYear = item.endYear
            var endMonth = item.endMonth
            var endDay = item.endDay

            if (item.userStatus == "PLANNING" && clampedProgress > 0.0) {
                newStatus = "IN_PROGRESS"
                if (startYear == null) {
                    startYear = currentYear
                    startMonth = currentMonth
                    startDay = currentDay
                }
            }
            if (maxProgress != null && clampedProgress >= maxProgress && item.userStatus != "COMPLETED") {
                newStatus = "COMPLETED"
                endYear = currentYear
                endMonth = currentMonth
                endDay = currentDay
            } else if (item.userStatus == "COMPLETED" && maxProgress != null && clampedProgress < maxProgress) {
                newStatus = "IN_PROGRESS"
                endYear = null
                endMonth = null
                endDay = null
            }

            val updatedEntity = item.copy(
                progress = clampedProgress,
                userStatus = newStatus,
                startYear = startYear,
                startMonth = startMonth,
                startDay = startDay,
                endYear = endYear,
                endMonth = endMonth,
                endDay = endDay,
                playbackSpeed = if (mode == "LISTEN") speed else item.playbackSpeed,
                updatedAt = now
            )
            dao.upsert(updatedEntity)

            val dirtyRows = linkDao.updateSyncStatusByMediaId(id, "DIRTY_LOCAL", now)
            if (dirtyRows > 0) {
                shouldPush = true
            }

            if (effectiveDelta != 0.0) {
                val coalesceSec = settingDao.get("activity.coalesceSeconds")?.toLongOrNull() ?: 30L
                val minTs = now - (coalesceSec * 1000L)
                val existing = activityDao.findCoalescableProgress(id, mode, minTs)
                val config = getResolvedConfig(item.categoryId)
                val rateKey = "rate.${mode ?: config.actionVerb.uppercase()}.${config.lengthUnit.name}"
                val baseRate = settingDao.get(rateKey)?.toDoubleOrNull() ?: (if (mode == "LISTEN") 250.0 else 500.0)

                if (existing != null) {
                    val combinedDelta = (existing.delta ?: 0.0) + effectiveDelta
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
                            delta = effectiveDelta,
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

    private fun categoryToId(category: MediaCategory): String = when (category) {
        MediaCategory.NOVEL -> "cat_novel"
        MediaCategory.MANGA -> "cat_manga"
        MediaCategory.ANIME -> "cat_anime"
        MediaCategory.GAME -> "cat_game"
        MediaCategory.MOVIE -> "cat_movie"
        MediaCategory.TV -> "cat_tv"
    }

    private fun ActiveMediaItemView.toDomain(): MediaItem {
        val altTitles = listOfNotNull(titleRomaji, titleEnglish, titleNative)
        val genresList = converters.toList(genres)
        val tagsList = converters.toList(tags)
        val stat = UserStatus.fromString(userStatus) ?: UserStatus.PLAN_TO
        val calcRating = scoreRaw?.let { it / 20.0 }
        return MediaItem(
            id = id,
            categoryId = categoryId,
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
            categoryId = categoryId,
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

    private fun MediaItem.toEntity(): MediaItemEntity {
        val genresJson = converters.fromList(genres)
        val tagsJson = converters.fromList(tags)
        val rawScore = rating?.let { kotlin.math.round(it * 20.0).toInt().coerceIn(0, 100) }
        val (sYear, sMonth, sDay) = millisToFuzzyDate(startDate)
        val (eYear, eMonth, eDay) = millisToFuzzyDate(endDate)
        return MediaItemEntity(
            id = id,
            categoryId = categoryId,
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

    private fun CategoryEntity.toDomain(): Category = Category(
        id = id,
        parentId = parentId,
        name = name,
        icon = icon,
        unitName = unitName,
        secondaryUnitName = secondaryUnitName,
        actionVerb = actionVerb,
        hasSecondaryProgress = hasSecondaryProgress?.let { it == 1 },
        hasCharEstimate = hasCharEstimate?.let { it == 1 },
        hasAudioSpeed = hasAudioSpeed?.let { it == 1 },
        lengthUnit = lengthUnit?.let { LengthUnit.valueOf(it) },
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

    private fun Category.toEntity(): CategoryEntity = CategoryEntity(
        id = id,
        parentId = parentId,
        name = name,
        icon = icon,
        unitName = unitName,
        secondaryUnitName = secondaryUnitName,
        actionVerb = actionVerb,
        hasSecondaryProgress = (hasSecondaryProgress ?: hasParts)?.let { if (it) 1 else 0 },
        hasCharEstimate = hasCharEstimate?.let { if (it) 1 else 0 },
        hasAudioSpeed = hasAudioSpeed?.let { if (it) 1 else 0 },
        lengthUnit = lengthUnit?.name,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

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
