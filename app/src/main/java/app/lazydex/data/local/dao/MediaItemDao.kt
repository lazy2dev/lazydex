package app.lazydex.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.lazydex.data.local.entity.ActiveMediaItemView
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.domain.model.MediaStats
import kotlinx.coroutines.flow.Flow

data class CategoryCount(val category: String, val count: Int)
data class StatusCount(val userStatus: String, val count: Int)

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM active_media_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ActiveMediaItemView>>

    @Query("SELECT * FROM active_media_items WHERE categoryId = :categoryId ORDER BY createdAt DESC")
    fun observeByCategory(categoryId: String): Flow<List<ActiveMediaItemView>>

    @Query("SELECT categoryId as category, COUNT(*) as count FROM active_media_items GROUP BY categoryId")
    fun observeCategoryCounts(): Flow<List<CategoryCount>>

    @Query("""
        SELECT userStatus, COUNT(*) as count 
        FROM active_media_items 
        WHERE (:categoryId IS NULL OR categoryId = :categoryId OR categoryId IN (SELECT id FROM categories WHERE parentId = :categoryId))
        GROUP BY userStatus
    """)
    fun observeStatusCounts(categoryId: String?): Flow<List<StatusCount>>

    @Query("""
        SELECT * FROM active_media_items 
        WHERE (:categoryId IS NULL OR categoryId = :categoryId OR categoryId IN (SELECT id FROM categories WHERE parentId = :categoryId))
        AND (
            :filterType = 'ALL' OR 
            (:filterType = 'IN_PROGRESS' AND userStatus IN ('READING', 'WATCHING', 'PLAYING', 'IN_PROGRESS')) OR
            (:filterType = 'EXACT' AND userStatus = :exactStatus)
        )
        ORDER BY createdAt DESC
    """)
    fun observeFiltered(categoryId: String?, filterType: String, exactStatus: String?): Flow<List<ActiveMediaItemView>>

    @Query("SELECT COUNT(*) FROM active_media_items")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM active_media_items WHERE id = :id")
    fun observeById(id: String): Flow<ActiveMediaItemView?>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: String): MediaItemEntity?

    @Query("SELECT * FROM active_media_items WHERE id = :id")
    suspend fun getActiveById(id: String): ActiveMediaItemView?

    @Query("SELECT * FROM active_media_items")
    suspend fun getAll(): List<ActiveMediaItemView>

    @Upsert
    suspend fun upsert(item: MediaItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<MediaItemEntity>)

    @Query("""
        UPDATE media_items
        SET progress = MAX(0.0, MIN(progress + :delta, COALESCE(progressTotal, progress + :delta))),
            updatedAt = :now
        WHERE id = :id AND deletedAt IS NULL
    """)
    suspend fun atomicIncrementProgress(id: String, delta: Double, now: Long): Int

    @Query("UPDATE media_items SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE media_items SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM media_items")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(items: List<MediaItemEntity>) {
        deleteAll()
        upsertAll(items)
    }

    @Query("""
        SELECT 
          (SELECT COUNT(*) FROM active_media_items) as totalCount,
          (SELECT COUNT(*) FROM active_media_items WHERE userStatus = 'COMPLETED') as completedCount,
          (SELECT CAST(COALESCE(SUM(progress), 0) AS INTEGER) FROM active_media_items) as totalProgress,
          (SELECT AVG(rating) FROM active_media_items WHERE rating IS NOT NULL) as meanRating,
          (SELECT COUNT(*) FROM active_media_items WHERE userStatus IN ('READING', 'WATCHING', 'PLAYING', 'IN_PROGRESS')) as inProgressCount,
          (SELECT COUNT(*) FROM active_media_items WHERE categoryId = 'cat_novel' OR categoryId IN (SELECT id FROM categories WHERE parentId = 'cat_novel')) as novelCount,
          (SELECT COUNT(*) FROM active_media_items WHERE categoryId = 'cat_manga' OR categoryId IN (SELECT id FROM categories WHERE parentId = 'cat_manga')) as mangaCount,
          (SELECT COUNT(*) FROM active_media_items WHERE categoryId = 'cat_anime' OR categoryId IN (SELECT id FROM categories WHERE parentId = 'cat_anime')) as animeCount,
          (SELECT COUNT(*) FROM active_media_items WHERE categoryId = 'cat_game' OR categoryId IN (SELECT id FROM categories WHERE parentId = 'cat_game')) as gameCount
    """)
    fun getStats(): Flow<MediaStats>
}
