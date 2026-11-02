package app.lazydex.data.local.dao

import app.lazydex.data.local.entity.MediaPartEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Deprecated("Retired in Database V6. Aggregated into secondaryProgressTotal.")
interface MediaPartDao {
    fun observeByMediaId(mediaId: String): Flow<List<MediaPartEntity>> = emptyFlow()
    suspend fun getByMediaId(mediaId: String): List<MediaPartEntity> = emptyList()
    suspend fun upsert(part: MediaPartEntity) {}
    suspend fun upsertAll(parts: List<MediaPartEntity>) {}
    suspend fun delete(id: String) {}
    suspend fun deleteByMediaId(mediaId: String) {}
}
