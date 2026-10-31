package app.lazydex.domain.repository

import app.lazydex.domain.model.ActivityLog
import app.lazydex.domain.model.Category
import app.lazydex.domain.model.LengthUnit
import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.MediaPart
import app.lazydex.domain.model.MediaStats
import app.lazydex.domain.model.ResolvedCategoryConfig
import app.lazydex.domain.model.StatusFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface MediaRepository {
    /** Reactive observation — UI bindings */
    fun observeAll(): Flow<List<MediaItem>>
    fun observeByCategory(category: MediaCategory): Flow<List<MediaItem>>
    fun observeByCategoryId(categoryId: String): Flow<List<MediaItem>> = emptyFlow()
    fun observeFiltered(category: MediaCategory?, statusFilter: StatusFilter): Flow<List<MediaItem>>
    fun observeFilteredByCategoryId(categoryId: String?, statusFilter: StatusFilter): Flow<List<MediaItem>> = emptyFlow()
    fun observeById(id: String): Flow<MediaItem?>
    fun observeCount(): Flow<Int>
    fun observeStats(): Flow<MediaStats>
    fun observeCategoryCounts(): Flow<Map<MediaCategory, Int>>
    fun observeStatusCounts(category: MediaCategory?): Flow<Map<StatusFilter, Int>>
    fun observeDistinctGenres(category: MediaCategory?): Flow<List<String>>
    fun observeDistinctTags(category: MediaCategory?): Flow<List<String>>

    /** Category Management */
    fun observeCategories(): Flow<List<Category>> = emptyFlow()
    fun observeTopLevelCategories(): Flow<List<Category>> = emptyFlow()
    fun observeTypes(parentId: String): Flow<List<Category>> = emptyFlow()
    fun observeResolvedConfig(categoryId: String): Flow<ResolvedCategoryConfig> = emptyFlow()
    suspend fun getResolvedConfig(categoryId: String): ResolvedCategoryConfig = ResolvedCategoryConfig(
        categoryId = categoryId,
        topLevelId = categoryId,
        displayName = "Category",
        icon = "folder",
        unitName = "Chapter",
        secondaryUnitName = null,
        actionVerb = "Read",
        hasParts = false,
        hasCharEstimate = false,
        hasAudioSpeed = false,
        lengthUnit = LengthUnit.WORDS
    )
    suspend fun addCategory(category: Category): Category = category
    suspend fun updateCategory(category: Category) {}
    suspend fun deleteCategory(categoryId: String) {}

    /** Parts / Multi-Unit Breakdown */
    fun observeParts(mediaId: String): Flow<List<MediaPart>> = emptyFlow()
    suspend fun setParts(mediaId: String, parts: List<MediaPart>) {}

    /** Activity & Stats */
    fun observeActivity(mediaId: String): Flow<List<ActivityLog>> = emptyFlow()
    suspend fun getTimeSpentMinutes(mediaId: String): Pair<Double, Double> = Pair(0.0, 0.0)

    /** One-shot reads */
    suspend fun getById(id: String): MediaItem?
    suspend fun getAll(): List<MediaItem>

    /** Writes */
    suspend fun add(item: MediaItem): MediaItem
    suspend fun update(item: MediaItem)
    suspend fun delete(id: String)
    suspend fun incrementProgress(id: String, delta: Double, mode: String? = null, speed: Double = 1.0): MediaItem? = null

    /** Bulk operations */
    suspend fun replaceAll(items: List<MediaItem>)
}
