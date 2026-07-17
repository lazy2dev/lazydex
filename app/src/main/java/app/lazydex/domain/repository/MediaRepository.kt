package app.lazydex.domain.repository

import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.UserStatus
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    /** Reactive observation — UI bindings */
    fun observeAll(): Flow<List<MediaItem>>
    fun observeByCategory(category: MediaCategory): Flow<List<MediaItem>>
    fun observeFiltered(category: MediaCategory?, statusFilter: StatusFilter): Flow<List<MediaItem>>
    fun observeById(id: String): Flow<MediaItem?>  // Single-item observation for UnifiedAddEditViewModel

    /** One-shot reads */
    suspend fun getById(id: String): MediaItem?
    suspend fun getAll(): List<MediaItem>   // For backup export
    suspend fun existsByUrl(url: String): Boolean  // SQLite-bound, near-zero memory

    /** Writes — ALL go through MediaItem.normalize() before persisting */
    suspend fun add(item: MediaItem): MediaItem           // generates id + sets lastUpdated
    suspend fun update(item: MediaItem)                    // caller provides full item
    suspend fun delete(id: String)

    /** Atomic operations (delegated to DAO-level SQL) */
    suspend fun incrementProgress(id: String)
    suspend fun decrementProgress(id: String)
    suspend fun setStatus(id: String, status: UserStatus)

    /** Bulk operations — atomic via @Transaction */
    suspend fun replaceAll(items: List<MediaItem>)         // clears and inserts atomically
}
