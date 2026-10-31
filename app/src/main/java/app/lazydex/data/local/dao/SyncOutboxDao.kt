package app.lazydex.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.lazydex.data.local.entity.SyncOutboxEntity

@Dao
interface SyncOutboxDao {
    @Insert
    suspend fun insert(entry: SyncOutboxEntity)

    @Query("SELECT * FROM sync_outbox ORDER BY createdAt ASC")
    suspend fun getAll(): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE mediaId = :mediaId")
    suspend fun countForMedia(mediaId: String): Int
}

