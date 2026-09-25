package app.lazydex.domain.repository

import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.MediaStats
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    /** Reactive observation — UI bindings */
    fun observeAll(): Flow<List<MediaItem>>
    fun observeByCategory(category: MediaCategory): Flow<List<MediaItem>>
    fun observeFiltered(category: MediaCategory?, statusFilter: StatusFilter): Flow<List<MediaItem>>
    fun observeById(id: String): Flow<MediaItem?>  // Single-item observation for UnifiedAddEditViewModel
    fun observeCount(): Flow<Int>                  // Total item count for library badge
    fun observeStats(): Flow<MediaStats>
    fun observeCategoryCounts(): Flow<Map<MediaCategory, Int>>
    fun observeStatusCounts(category: MediaCategory?): Flow<Map<StatusFilter, Int>>
    fun observeDistinctGenres(category: MediaCategory?): Flow<List<String>>
    fun observeDistinctTags(category: MediaCategory?): Flow<List<String>>

    /** One-shot reads */
    suspend fun getById(id: String): MediaItem?
    suspend fun getAll(): List<MediaItem>   // For backup export

    /** Writes — ALL go through MediaItem.normalize() before persisting */
    suspend fun add(item: MediaItem): MediaItem           // generates id + sets lastUpdated
    suspend fun update(item: MediaItem)                    // caller provides full item
    suspend fun delete(id: String)

    /** Bulk operations — atomic via @Transaction */
    suspend fun replaceAll(items: List<MediaItem>)         // clears and inserts atomically
}
