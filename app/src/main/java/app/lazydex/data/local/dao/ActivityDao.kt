package app.lazydex.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.lazydex.data.local.entity.ActivityEntity
import app.lazydex.domain.model.ActivityDatePoint
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity WHERE mediaId = :mediaId ORDER BY timestamp DESC")
    fun observeByMediaId(mediaId: String): Flow<List<ActivityEntity>>

    @Query("""
        SELECT a.mediaId AS mediaId, m.type AS mediaType, m.format AS mediaFormat,
               a.type AS type, a.delta AS delta, a.timestamp AS timestamp
        FROM activity a
        INNER JOIN media_items m ON a.mediaId = m.id
        WHERE m.deletedAt IS NULL
          AND (:startMs IS NULL OR a.timestamp >= :startMs)
          AND (:endMs IS NULL OR a.timestamp <= :endMs)
        ORDER BY a.timestamp ASC
    """)
    fun observeActivityPoints(startMs: Long?, endMs: Long?): Flow<List<ActivityDatePoint>>

    @Query("SELECT * FROM activity WHERE mediaId = :mediaId ORDER BY timestamp DESC")
    suspend fun getByMediaId(mediaId: String): List<ActivityEntity>

    @Query("""
        SELECT * FROM activity 
        WHERE mediaId = :mediaId 
          AND type = 'PROGRESS_UPDATE' 
          AND (:mode IS NULL OR mode = :mode)
          AND timestamp >= :minTimestamp
        ORDER BY timestamp DESC 
        LIMIT 1
    """)
    suspend fun findCoalescableProgress(mediaId: String, mode: String?, minTimestamp: Long): ActivityEntity?

    @Query("SELECT * FROM activity WHERE mediaId = :mediaId AND type = 'PROGRESS_UPDATE' AND counted = 1")
    suspend fun getCountedProgressUpdates(mediaId: String): List<ActivityEntity>

    @Upsert
    suspend fun upsert(activity: ActivityEntity)

    @Query("DELETE FROM activity WHERE id = :id")
    suspend fun delete(id: String)
}

