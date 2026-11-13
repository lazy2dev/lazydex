package app.lazydex.domain.repository

import app.lazydex.domain.model.ActivityDatePoint
import app.lazydex.domain.model.ActivityLog
import app.lazydex.domain.model.LengthUnit
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaPart
import app.lazydex.domain.model.MediaStats
import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.ResolvedTypeConfig
import app.lazydex.domain.model.StatusFilter
import app.lazydex.domain.model.TypeConfig
import app.lazydex.domain.model.UserStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface MediaRepository {
    /** Reactive observation — UI bindings */
    fun observeAll(): Flow<List<MediaItem>>
    fun observeHistory(): Flow<List<MediaItem>> = emptyFlow()
    fun observeByType(type: String): Flow<List<MediaItem>> = emptyFlow()
    fun observeByType(mediaType: MediaType): Flow<List<MediaItem>>
    fun observeByTypeId(typeId: String): Flow<List<MediaItem>> = emptyFlow()
    fun observeFiltered(mediaType: MediaType?, statusFilter: StatusFilter): Flow<List<MediaItem>>
    fun observeFiltered(type: String?, format: String?, statusFilter: StatusFilter): Flow<List<MediaItem>> = emptyFlow()
    fun observeFilteredByTypeId(typeId: String?, statusFilter: StatusFilter): Flow<List<MediaItem>> = emptyFlow()
    fun observeById(id: String): Flow<MediaItem?>
    fun observeCount(): Flow<Int>
    fun observeStats(): Flow<MediaStats>
    fun observeTypeCounts(): Flow<Map<MediaType, Int>>
    fun observeStatusCounts(mediaType: MediaType?): Flow<Map<StatusFilter, Int>>
    fun observeDistinctGenres(mediaType: MediaType?): Flow<List<String>>
    fun observeDistinctTags(mediaType: MediaType?): Flow<List<String>>

    /** Type & Format Configuration */
    fun observeTypeConfigs(): Flow<List<TypeConfig>> = emptyFlow()
    fun observeTopLevelTypes(): Flow<List<TypeConfig>> = emptyFlow()
    fun observeFormats(parentId: String): Flow<List<TypeConfig>> = emptyFlow()
    fun observeResolvedConfig(typeId: String): Flow<ResolvedTypeConfig> = emptyFlow()
    suspend fun getResolvedConfig(typeId: String): ResolvedTypeConfig = ResolvedTypeConfig(
        typeId = typeId,
        topLevelId = typeId,
        displayName = "Type",
        icon = "folder",
        unitName = "Chapter",
        secondaryUnitName = null,
        actionVerb = "Read",
        hasParts = false,
        hasCharEstimate = false,
        hasAudioSpeed = false,
        lengthUnit = LengthUnit.WORDS
    )

    /** Parts / Multi-Unit Breakdown */
    fun observeParts(mediaId: String): Flow<List<MediaPart>> = emptyFlow()
    suspend fun setParts(mediaId: String, parts: List<MediaPart>) {}

    /** Activity & Stats */
    fun observeActivity(mediaId: String): Flow<List<ActivityLog>> = emptyFlow()
    fun observeActivityPoints(startMs: Long? = null, endMs: Long? = null): Flow<List<ActivityDatePoint>> = emptyFlow()
    suspend fun getTimeSpentMinutes(mediaId: String): Pair<Double, Double> = Pair(0.0, 0.0)

    /** One-shot reads */
    suspend fun getById(id: String): MediaItem?
    suspend fun getAll(): List<MediaItem>

    /** Writes */
    suspend fun add(item: MediaItem): MediaItem
    suspend fun update(item: MediaItem)
    suspend fun delete(id: String)
    suspend fun incrementProgress(id: String, delta: Double, mode: String? = null, speed: Double = 1.0): MediaItem? = null
    suspend fun updateRepeat(id: String, repeat: Int) {}

    /** Bulk operations */
    suspend fun replaceAll(items: List<MediaItem>)
    suspend fun updateStatus(ids: Collection<String>, status: UserStatus) {}
    suspend fun deleteItems(ids: Collection<String>) {}
}
