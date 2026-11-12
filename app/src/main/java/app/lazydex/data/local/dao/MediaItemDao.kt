package app.lazydex.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.lazydex.data.local.entity.ActiveMediaItemView
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.domain.model.MediaStats
import kotlinx.coroutines.flow.Flow

data class TypeCount(val type: String, val count: Int)
data class StatusCount(val userStatus: String, val count: Int)

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM active_media_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ActiveMediaItemView>>

    @Query("SELECT * FROM active_media_items WHERE type = :type ORDER BY createdAt DESC")
    fun observeByType(type: String): Flow<List<ActiveMediaItemView>>

    @Query("SELECT type, COUNT(*) as count FROM active_media_items GROUP BY type")
    fun observeTypeCounts(): Flow<List<TypeCount>>

    @Query("""
        SELECT userStatus, COUNT(*) as count 
        FROM active_media_items 
        WHERE (:type IS NULL OR type = :type)
        GROUP BY userStatus
    """)
    fun observeStatusCounts(type: String?): Flow<List<StatusCount>>

    @Query("""
        SELECT * FROM active_media_items 
        WHERE (:type IS NULL OR type = :type)
        AND (:format IS NULL OR format = :format)
        AND (
            :filterType = 'ALL' OR 
            (:filterType = 'IN_PROGRESS' AND userStatus IN ('READING', 'WATCHING', 'PLAYING', 'IN_PROGRESS')) OR
            (:filterType = 'EXACT' AND userStatus = :exactStatus)
        )
        ORDER BY createdAt DESC
    """)
    fun observeFiltered(type: String?, format: String?, filterType: String, exactStatus: String?): Flow<List<ActiveMediaItemView>>

    @Query("""
        SELECT * FROM active_media_items 
        WHERE (:type IS NULL OR type = :type)
        AND (
            :filterType = 'ALL' OR 
            (:filterType = 'IN_PROGRESS' AND userStatus IN ('READING', 'WATCHING', 'PLAYING', 'IN_PROGRESS')) OR
            (:filterType = 'EXACT' AND userStatus = :exactStatus)
        )
        ORDER BY createdAt DESC
    """)
    fun observeFilteredLegacy(type: String?, filterType: String, exactStatus: String?): Flow<List<ActiveMediaItemView>>

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

    @Query("SELECT * FROM media_items WHERE type = :type AND deletedAt IS NULL")
    suspend fun getActiveByType(type: String): List<MediaItemEntity>

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
          (SELECT AVG(scoreRaw) / 20.0 FROM active_media_items WHERE scoreRaw IS NOT NULL) as meanRating,
          (SELECT COUNT(*) FROM active_media_items WHERE userStatus IN ('READING', 'WATCHING', 'PLAYING', 'IN_PROGRESS')) as inProgressCount,
          (SELECT COUNT(*) FROM active_media_items WHERE type = 'NOVEL') as novelCount,
          (SELECT COUNT(*) FROM active_media_items WHERE type = 'MANGA') as mangaCount,
          (SELECT COUNT(*) FROM active_media_items WHERE type = 'ANIME') as animeCount,
          (SELECT COUNT(*) FROM active_media_items WHERE type = 'GAME') as gameCount
    """)
    fun getStats(): Flow<MediaStats>
}
