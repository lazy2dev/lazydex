package app.lazydex.data.repository

import android.database.sqlite.SQLiteConstraintException
import app.lazydex.data.local.converter.Converters
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaStats
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.UserStatus
import app.lazydex.domain.repository.DuplicateUrlException
import app.lazydex.domain.repository.ImportFailedException
import app.lazydex.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MediaRepositoryImpl(
    private val dao: MediaItemDao,
    private val localCoversDir: File
) : MediaRepository {

    private val converters = Converters()

    override fun observeAll(): Flow<List<MediaItem>> = dao.observeAll()
        .map { list -> list.mapNotNull { it.toDomain() } }
        .distinctUntilChanged()

    override fun observeByCategory(category: MediaCategory): Flow<List<MediaItem>> = dao.observeByCategory(category.name)
        .map { list -> list.mapNotNull { it.toDomain() } }
        .distinctUntilChanged()

    override fun observeFiltered(category: MediaCategory?, statusFilter: StatusFilter): Flow<List<MediaItem>> {
        val (filterType, exactStatus) = when (statusFilter) {
            StatusFilter.ALL -> "ALL" to null
            StatusFilter.IN_PROGRESS -> "IN_PROGRESS" to null
            StatusFilter.COMPLETED -> "EXACT" to UserStatus.COMPLETED.name
            StatusFilter.ON_HOLD -> "EXACT" to UserStatus.ON_HOLD.name
            StatusFilter.DROPPED -> "EXACT" to UserStatus.DROPPED.name
            StatusFilter.PLAN_TO -> "EXACT" to UserStatus.PLAN_TO.name
        }
        return dao.observeFiltered(category?.name, filterType, exactStatus)
            .map { list -> list.mapNotNull { it.toDomain() } }
            .distinctUntilChanged()
    }

    override fun observeById(id: String): Flow<MediaItem?> = dao.observeById(id)
        .map { it?.toDomain() }
        .distinctUntilChanged()

    override fun observeCount(): Flow<Int> = dao.observeCount().distinctUntilChanged()

    override fun observeStats(): Flow<MediaStats> = dao.getStats().distinctUntilChanged()

    override fun observeCategoryCounts(): Flow<Map<MediaCategory, Int>> = dao.observeCategoryCounts()
        .map { list ->
            val counts = list.mapNotNull { MediaCategory.fromString(it.category)?.let { cat -> cat to it.count } }.toMap()
            MediaCategory.entries.associateWith { counts[it] ?: 0 }
        }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()

    override fun observeStatusCounts(category: MediaCategory?): Flow<Map<StatusFilter, Int>> = dao.observeStatusCounts(category?.name)
        .map { list ->
            val raw = list.associate { it.userStatus to it.count }
            val inProgress = (raw["READING"] ?: 0) + (raw["WATCHING"] ?: 0) + (raw["PLAYING"] ?: 0)
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

    override fun observeDistinctGenres(category: MediaCategory?): Flow<List<String>> {
        val source = if (category != null) dao.observeByCategory(category.name) else dao.observeAll()
        return source.map { entities ->
            entities.flatMap { converters.toList(it.genres) }
                .distinctBy { it.lowercase() }
                .sorted()
        }.flowOn(Dispatchers.IO).distinctUntilChanged()
    }

    override fun observeDistinctTags(category: MediaCategory?): Flow<List<String>> {
        val source = if (category != null) dao.observeByCategory(category.name) else dao.observeAll()
        return source.map { entities ->
            entities.flatMap { converters.toList(it.tags) }
                .distinctBy { it.lowercase() }
                .sorted()
        }.flowOn(Dispatchers.IO).distinctUntilChanged()
    }

    override suspend fun getById(id: String): MediaItem? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    override suspend fun getAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        dao.getAll().mapNotNull { it.toDomain() }
    }

    override suspend fun existsByUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        dao.existsByUrl(url)
    }

    override suspend fun add(item: MediaItem): MediaItem = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val finalId = UUID.randomUUID().toString()

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

        val finalItem = applyAutoDates(
            item.copy(
                id = finalId,
                coverImagePath = finalCoverPath,
                dateAdded = now,
                lastUpdated = now
            )
        ).normalize()

        try {
            dao.upsert(finalItem.toEntity())
            finalItem
        } catch (e: SQLiteConstraintException) {
            throw DuplicateUrlException("A media item with this URL already exists", e)
        }
    }

    override suspend fun update(item: MediaItem): Unit = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        val finalCoverPath = if (item.coverImagePath.isNotEmpty()) {
            val tempFile = File(item.coverImagePath)
            val isInCoversDir = tempFile.absolutePath.startsWith(localCoversDir.absolutePath)
            if (tempFile.exists() && !isInCoversDir) {
                if (!localCoversDir.exists()) localCoversDir.mkdirs()
                val destFile = File(localCoversDir, item.id)
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

        val finalItem = applyAutoDates(
            item.copy(
                coverImagePath = finalCoverPath,
                lastUpdated = now
            )
        ).normalize()

        try {
            dao.upsert(finalItem.toEntity())
        } catch (e: SQLiteConstraintException) {
            throw DuplicateUrlException("A media item with this URL already exists", e)
        }
    }

    override suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        dao.softDelete(id, System.currentTimeMillis())
        try {
            val coverFile = File(localCoversDir, id)
            if (coverFile.exists()) {
                coverFile.delete()
            }
        } catch (e: Exception) {
            // Ignore cover deletion errors
        }
    }

    override suspend fun incrementProgress(id: String): Unit = withContext(Dispatchers.IO) {
        val existing = dao.getById(id)?.toDomain() ?: return@withContext
        val total = existing.totalItems
        val newProgress = if (total != null && total >= 0) minOf(existing.currentProgress + 1, total) else existing.currentProgress + 1
        val isNowCompleted = total != null && newProgress >= total
        val updatedStatus = if (isNowCompleted) UserStatus.COMPLETED else existing.userStatus
        val updatedItem = applyAutoDates(existing.copy(
            currentProgress = newProgress,
            userStatus = updatedStatus,
            lastUpdated = System.currentTimeMillis()
        ))
        dao.upsert(updatedItem.normalize().toEntity())
    }

    override suspend fun decrementProgress(id: String): Unit = withContext(Dispatchers.IO) {
        val existing = dao.getById(id)?.toDomain() ?: return@withContext
        val newProgress = maxOf(existing.currentProgress - 1, 0)
        val total = existing.totalItems
        val isWasCompleted = existing.userStatus == UserStatus.COMPLETED
        val isNowBelowTotal = total != null && newProgress < total
        val updatedStatus = if (isWasCompleted && isNowBelowTotal) categoryDefaultInProgress(existing.category) else existing.userStatus
        val updatedItem = applyAutoDates(existing.copy(
            currentProgress = newProgress,
            userStatus = updatedStatus,
            lastUpdated = System.currentTimeMillis()
        ))
        dao.upsert(updatedItem.normalize().toEntity())
    }

    override suspend fun setStatus(id: String, status: UserStatus): Unit = withContext(Dispatchers.IO) {
        val existing = dao.getById(id)?.toDomain() ?: return@withContext
        val updated = applyAutoDates(existing.copy(
            userStatus = status,
            lastUpdated = System.currentTimeMillis()
        ))
        dao.upsert(updated.normalize().toEntity())
    }

    override suspend fun replaceAll(items: List<MediaItem>): Unit = withContext(Dispatchers.IO) {
        try {
            val entities = items.map { applyAutoDates(it).normalize().toEntity() }
            dao.replaceAll(entities)
        } catch (e: SQLiteConstraintException) {
            throw ImportFailedException("Import failed: duplicate source URL detected", e)
        } catch (e: Exception) {
            throw ImportFailedException("Import failed due to database error", e)
        }
    }

    private fun applyAutoDates(item: MediaItem): MediaItem {
        val isInProgress = item.userStatus in listOf(UserStatus.READING, UserStatus.WATCHING, UserStatus.PLAYING)
        val isCompleted = item.userStatus == UserStatus.COMPLETED
        val isPlanTo = item.userStatus == UserStatus.PLAN_TO

        return item.copy(
            startDate = when {
                isPlanTo -> null
                (isInProgress || isCompleted) && item.startDate == null -> System.currentTimeMillis()
                else -> item.startDate
            },
            endDate = when {
                isCompleted && item.endDate == null -> System.currentTimeMillis()
                !isCompleted -> null
                else -> item.endDate
            }
        )
    }

    private fun categoryDefaultInProgress(category: MediaCategory): UserStatus {
        return when (category) {
            MediaCategory.NOVEL, MediaCategory.MANGA -> UserStatus.READING
            MediaCategory.ANIME, MediaCategory.MOVIE, MediaCategory.TV -> UserStatus.WATCHING
            MediaCategory.GAME -> UserStatus.PLAYING
        }
    }

    private fun MediaItemEntity.toDomain(): MediaItem? {
        return try {
            val cat = MediaCategory.fromString(category) ?: return null
            val stat = UserStatus.fromString(userStatus) ?: return null
            val altTitles = converters.toList(alternativeTitles)
            val genresList = converters.toList(genres)
            val tagsList = converters.toList(tags)
            MediaItem(
                id = id,
                category = cat,
                title = title,
                alternativeTitles = altTitles,
                sourceUrl = sourceUrl,
                coverImagePath = coverImagePath,
                coverImageUrl = coverImageUrl,
                currentProgress = currentProgress,
                totalItems = totalItems,
                userStatus = stat,
                rating = rating,
                notes = notes,
                genres = genresList,
                tags = tagsList,
                author = author,
                description = description,
                startDate = startDate,
                endDate = endDate,
                lastUpdated = lastUpdated,
                dateAdded = dateAdded,
                extraData = extraData,
                isDeleted = isDeleted
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun MediaItem.toEntity(): MediaItemEntity {
        val altTitlesJson = converters.fromList(alternativeTitles)
        val genresJson = converters.fromList(genres)
        val tagsJson = converters.fromList(tags)
        return MediaItemEntity(
            id = id,
            category = category.name,
            title = title,
            alternativeTitles = altTitlesJson,
            sourceUrl = sourceUrl,
            coverImagePath = coverImagePath,
            coverImageUrl = coverImageUrl,
            currentProgress = currentProgress,
            totalItems = totalItems,
            userStatus = userStatus.name,
            rating = rating,
            notes = notes,
            genres = genresJson,
            tags = tagsJson,
            author = author,
            description = description,
            startDate = startDate,
            endDate = endDate,
            lastUpdated = lastUpdated,
            dateAdded = dateAdded,
            extraData = extraData,
            isDeleted = isDeleted
        )
    }
}

