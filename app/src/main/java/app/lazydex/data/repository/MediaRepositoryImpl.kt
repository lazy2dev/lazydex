package app.lazydex.data.repository

import android.database.sqlite.SQLiteConstraintException
import app.lazydex.data.local.converter.Converters
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.UserStatus
import app.lazydex.domain.repository.DuplicateUrlException
import app.lazydex.domain.repository.ImportFailedException
import app.lazydex.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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

        val finalItem = item.copy(
            id = finalId,
            coverImagePath = finalCoverPath,
            dateAdded = now,
            lastUpdated = now
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

        val finalItem = item.copy(
            coverImagePath = finalCoverPath,
            lastUpdated = now
        ).normalize()

        try {
            dao.upsert(finalItem.toEntity())
        } catch (e: SQLiteConstraintException) {
            throw DuplicateUrlException("A media item with this URL already exists", e)
        }
    }

    override suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        dao.deleteById(id)
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
        dao.atomicIncrement(id, System.currentTimeMillis())
    }

    override suspend fun decrementProgress(id: String): Unit = withContext(Dispatchers.IO) {
        dao.atomicDecrement(id, System.currentTimeMillis())
    }

    override suspend fun setStatus(id: String, status: UserStatus): Unit = withContext(Dispatchers.IO) {
        dao.updateStatus(id, status.name, System.currentTimeMillis())
    }

    override suspend fun replaceAll(items: List<MediaItem>): Unit = withContext(Dispatchers.IO) {
        try {
            val entities = items.map { it.normalize().toEntity() }
            dao.replaceAll(entities)
        } catch (e: SQLiteConstraintException) {
            throw ImportFailedException("Import failed: duplicate source URL detected", e)
        } catch (e: Exception) {
            throw ImportFailedException("Import failed due to database error", e)
        }
    }

    private fun MediaItemEntity.toDomain(): MediaItem? {
        return try {
            val cat = MediaCategory.fromString(category) ?: return null
            val stat = UserStatus.fromString(userStatus) ?: return null
            val altTitles = converters.toList(alternativeTitles)
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
                lastUpdated = lastUpdated,
                dateAdded = dateAdded
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun MediaItem.toEntity(): MediaItemEntity {
        val altTitlesJson = converters.fromList(alternativeTitles)
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
            lastUpdated = lastUpdated,
            dateAdded = dateAdded
        )
    }
}
