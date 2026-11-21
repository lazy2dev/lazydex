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

    @Query("SELECT * FROM media_links WHERE providerId = :providerId AND syncStatus = 'DIRTY_LOCAL'")
    suspend fun getDirtyLinks(providerId: String): List<MediaLinkEntity>

    @Query("SELECT * FROM media_links WHERE providerId = :providerId AND state = :state")
    suspend fun getByProviderAndState(providerId: String, state: String = "LINKED"): List<MediaLinkEntity>

    @Query("SELECT * FROM media_links WHERE providerId = :providerId")
    suspend fun getByProviderId(providerId: String): List<MediaLinkEntity>

    @Query("SELECT * FROM media_links WHERE mediaId = :mediaId AND providerId = :providerId LIMIT 1")
    suspend fun getByMediaIdAndProvider(mediaId: String, providerId: String): MediaLinkEntity?

    @Query("""
        SELECT l.* FROM media_links l
        INNER JOIN media_items m ON m.id = l.mediaId
        WHERE l.providerId = :providerId AND l.state = 'LINKED'
          AND l.metadataFetchedAt IS NULL
          AND m.deletedAt IS NULL
        ORDER BY m.createdAt DESC
        LIMIT :limit
    """)
    suspend fun getLinksNeedingMetadata(providerId: String, limit: Int): List<MediaLinkEntity>

    @Query("""
        UPDATE media_links
        SET metadataFetchedAt = :timestamp, updatedAt = :timestamp
        WHERE providerId = :providerId AND remoteId IN (:remoteIds)
    """)
    suspend fun markMetadataFetched(providerId: String, remoteIds: List<String>, timestamp: Long): Int

    @Query("UPDATE media_links SET syncStatus = :syncStatus, updatedAt = :updatedAt WHERE mediaId = :mediaId AND state = 'LINKED'")
    suspend fun updateSyncStatusByMediaId(mediaId: String, syncStatus: String, updatedAt: Long): Int

    @Upsert
    suspend fun upsert(link: MediaLinkEntity)

    @Upsert
    suspend fun upsertAll(links: List<MediaLinkEntity>)

    @Query("DELETE FROM media_links WHERE id = :id")
    suspend fun delete(id: String)
}
