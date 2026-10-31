package app.lazydex.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.lazydex.data.local.entity.MediaPartEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaPartDao {
    @Query("SELECT * FROM media_parts WHERE mediaId = :mediaId ORDER BY sortOrder ASC")
    fun observeByMediaId(mediaId: String): Flow<List<MediaPartEntity>>

    @Query("SELECT * FROM media_parts WHERE mediaId = :mediaId ORDER BY sortOrder ASC")
    suspend fun getByMediaId(mediaId: String): List<MediaPartEntity>

    @Upsert
    suspend fun upsert(part: MediaPartEntity)

    @Upsert
    suspend fun upsertAll(parts: List<MediaPartEntity>)

    @Query("DELETE FROM media_parts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM media_parts WHERE mediaId = :mediaId")
    suspend fun deleteByMediaId(mediaId: String)
}

