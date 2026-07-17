package app.lazydex.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.lazydex.data.local.entity.MediaItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items ORDER BY dateAdded DESC")
    fun observeAll(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE category = :category ORDER BY dateAdded DESC")
    fun observeByCategory(category: String): Flow<List<MediaItemEntity>>

    /**
     * Filtered observation that supports category and status filters.
     * Uses a single query to map status filters in SQLite.
     */
    @Query("""
        SELECT * FROM media_items 
        WHERE (:category IS NULL OR category = :category)
        AND (
            :filterType = 'ALL' OR 
            (:filterType = 'IN_PROGRESS' AND userStatus IN ('READING', 'WATCHING', 'PLAYING')) OR
            (:filterType = 'EXACT' AND userStatus = :exactStatus)
        )
        ORDER BY dateAdded DESC
    """)
    fun observeFiltered(category: String?, filterType: String, exactStatus: String?): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items ORDER BY dateAdded DESC")
    fun observeAllByDateAdded(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items ORDER BY lastUpdated DESC")
    fun observeAllByLastUpdated(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items ORDER BY title ASC")
    fun observeAllByTitle(): Flow<List<MediaItemEntity>>

    @Query("""
        SELECT * FROM media_items 
        ORDER BY CASE 
            WHEN totalItems IS NULL OR totalItems <= 0 THEN 0.0 
            ELSE CAST(currentProgress AS REAL) / CAST(totalItems AS REAL) 
        END ASC
    """)
    fun observeAllByProgress(): Flow<List<MediaItemEntity>>

    @Query("SELECT COUNT(*) FROM media_items")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM media_items WHERE id = :id")
    fun observeById(id: String): Flow<MediaItemEntity?>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: String): MediaItemEntity?

    @Query("SELECT * FROM media_items")
    suspend fun getAll(): List<MediaItemEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM media_items WHERE sourceUrl = :url LIMIT 1)")
    suspend fun existsByUrl(url: String): Boolean

    @Upsert
    suspend fun upsert(item: MediaItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<MediaItemEntity>)

    /**
     * Atomic progress increment — capped by totalItems.
     */
    @Query("""
        UPDATE media_items
        SET currentProgress = MIN(currentProgress + 1, COALESCE(totalItems, currentProgress + 1)),
            lastUpdated = :now
        WHERE id = :id
    """)
    suspend fun atomicIncrement(id: String, now: Long)

    /**
     * Atomic progress decrement — floored by 0.
     */
    @Query("""
        UPDATE media_items
        SET currentProgress = MAX(currentProgress - 1, 0),
            lastUpdated = :now
        WHERE id = :id
    """)
    suspend fun atomicDecrement(id: String, now: Long)

    @Query("""
        UPDATE media_items
        SET userStatus = :status,
            lastUpdated = :now
        WHERE id = :id
    """)
    suspend fun updateStatus(id: String, status: String, now: Long)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM media_items")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(items: List<MediaItemEntity>) {
        deleteAll()
        upsertAll(items)
    }
}
