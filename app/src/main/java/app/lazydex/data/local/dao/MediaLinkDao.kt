package app.lazydex.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.lazydex.data.local.entity.MediaLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaLinkDao {
    @Query("SELECT * FROM media_links WHERE mediaId = :mediaId")
    fun observeByMediaId(mediaId: String): Flow<List<MediaLinkEntity>>

    @Query("SELECT * FROM media_links WHERE mediaId = :mediaId")
    suspend fun getByMediaId(mediaId: String): List<MediaLinkEntity>

    @Query("SELECT * FROM media_links WHERE providerId = :providerId AND remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(providerId: String, remoteId: String): MediaLinkEntity?

    @Upsert
    suspend fun upsert(link: MediaLinkEntity)

    @Upsert
    suspend fun upsertAll(links: List<MediaLinkEntity>)

    @Query("DELETE FROM media_links WHERE id = :id")
    suspend fun delete(id: String)
}

