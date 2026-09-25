package app.lazydex.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.domain.model.MediaStats
import kotlinx.coroutines.flow.Flow

data class CategoryCount(val category: String, val count: Int)
data class StatusCount(val userStatus: String, val count: Int)

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items WHERE isDeleted = 0 ORDER BY dateAdded DESC")
    fun observeAll(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isDeleted = 0 AND category = :category ORDER BY dateAdded DESC")
    fun observeByCategory(category: String): Flow<List<MediaItemEntity>>

    @Query("SELECT category, COUNT(*) as count FROM media_items WHERE isDeleted = 0 GROUP BY category")
    fun observeCategoryCounts(): Flow<List<CategoryCount>>

    @Query("SELECT userStatus, COUNT(*) as count FROM media_items WHERE isDeleted = 0 AND (:category IS NULL OR category = :category) GROUP BY userStatus")
    fun observeStatusCounts(category: String?): Flow<List<StatusCount>>

    /**
     * Filtered observation that supports category and status filters.
     * Uses a single query to map status filters in SQLite.
     */
    @Query("""
        SELECT * FROM media_items 
        WHERE isDeleted = 0 
        AND (:category IS NULL OR category = :category)
        AND (
            :filterType = 'ALL' OR 
            (:filterType = 'IN_PROGRESS' AND userStatus IN ('READING', 'WATCHING', 'PLAYING')) OR
            (:filterType = 'EXACT' AND userStatus = :exactStatus)
        )
        ORDER BY dateAdded DESC
    """)
    fun observeFiltered(category: String?, filterType: String, exactStatus: String?): Flow<List<MediaItemEntity>>

    @Query("SELECT COUNT(*) FROM media_items WHERE isDeleted = 0")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM media_items WHERE id = :id AND isDeleted = 0")
    fun observeById(id: String): Flow<MediaItemEntity?>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE isDeleted = 0")
    suspend fun getAll(): List<MediaItemEntity>

    @Upsert
    suspend fun upsert(item: MediaItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<MediaItemEntity>)

    @Query("UPDATE media_items SET isDeleted = 1, lastUpdated = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("DELETE FROM media_items")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(items: List<MediaItemEntity>) {
        deleteAll()
        upsertAll(items)
    }

    @Query("""
        SELECT 
          (SELECT COUNT(*) FROM media_items WHERE isDeleted = 0) as totalCount,
          (SELECT COUNT(*) FROM media_items WHERE isDeleted = 0 AND userStatus = 'COMPLETED') as completedCount,
          (SELECT COALESCE(SUM(currentProgress), 0) FROM media_items WHERE isDeleted = 0) as totalProgress,
          (SELECT AVG(rating) FROM media_items WHERE isDeleted = 0 AND rating IS NOT NULL) as meanRating,
          (SELECT COUNT(*) FROM media_items WHERE isDeleted = 0 AND userStatus IN ('READING', 'WATCHING', 'PLAYING')) as inProgressCount,
          (SELECT COUNT(*) FROM media_items WHERE isDeleted = 0 AND category = 'NOVEL') as novelCount,
          (SELECT COUNT(*) FROM media_items WHERE isDeleted = 0 AND category = 'MANGA') as mangaCount,
          (SELECT COUNT(*) FROM media_items WHERE isDeleted = 0 AND category = 'ANIME') as animeCount,
          (SELECT COUNT(*) FROM media_items WHERE isDeleted = 0 AND category = 'GAME') as gameCount
    """)
    fun getStats(): Flow<MediaStats>
}
