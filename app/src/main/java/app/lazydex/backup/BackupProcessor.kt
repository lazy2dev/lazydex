package app.lazydex.backup

import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.UserStatus
import app.lazydex.util.UrlNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class BackupEnvelopeDto(
    val schemaVersion: Int = 1,
    val items: List<MediaItemBackupDto>? = null
)

@Serializable
data class MediaItemBackupDto(
    val id: String? = null,
    val category: String? = null,
    val title: String? = null,
    val alternativeTitles: List<String>? = null,
    val sourceUrl: String? = null,
    val coverImageUrl: String? = null,
    val currentProgress: Int? = null,
    val totalItems: Int? = null,
    val userStatus: String? = null,
    val rating: Double? = null,
    val notes: String? = null,
    val lastUpdated: Long? = null,
    val dateAdded: Long? = null
)

data class MergeResult(
    val mergedItems: List<MediaItem>,
    val coverIdsToRestore: Set<String> // IDs of items whose cover images should be updated/restored from the ZIP
)

object BackupProcessor {
    val backupJson = Json {
        ignoreUnknownKeys = true
        decodeEnumsCaseInsensitive = true
    }

    suspend fun serialize(items: List<MediaItem>): String = withContext(Dispatchers.Default) {
        val envelope = BackupEnvelopeDto(items = items.map { it.toDto() })
        backupJson.encodeToString(envelope)
    }

    suspend fun deserialize(json: String): List<MediaItem> = withContext(Dispatchers.Default) {
        if (json.isBlank()) throw IllegalArgumentException("Backup file is empty")
        val envelope = backupJson.decodeFromString<BackupEnvelopeDto>(json)
        if (envelope.schemaVersion > 1) {
            throw IllegalArgumentException("Unsupported backup schema version: ${envelope.schemaVersion}")
        }
        envelope.items?.mapNotNull { dto -> dto.toDomain() } ?: emptyList()
    }

    suspend fun merge(local: List<MediaItem>, imported: List<MediaItem>): MergeResult =
        withContext(Dispatchers.Default) {
            val localById = local.associateBy { it.id }
            val localByUrl = local.filter { it.sourceUrl != null }.associateBy { it.sourceUrl?.let { url -> UrlNormalizer.normalize(url) } }
            
            val result = LinkedHashMap<String, MediaItem>()
            val coverIdsToRestore = mutableSetOf<String>()
            
            // Start with all local items
            local.forEach { result[it.id] = it }
            
            imported.forEachIndexed { index, importedItem ->
                // Check duplicate by URL first, then by ID
                val normalizedImportedUrl = importedItem.sourceUrl?.let { UrlNormalizer.normalize(it) }
                val existingLocal = localById[importedItem.id] ?: normalizedImportedUrl?.let { localByUrl[it] }
                
                if (existingLocal == null) {
                    // Pure addition: imported item is completely new
                    val finalTime = if (importedItem.lastUpdated > 0L) importedItem.lastUpdated
                                    else System.currentTimeMillis() - index
                    val newItem = importedItem.copy(lastUpdated = finalTime).normalize()
                    result[newItem.id] = newItem
                    
                    // We must restore the cover from the imported zip using the imported item's ID
                    coverIdsToRestore.add(importedItem.id) 
                } else {
                    // Conflict: Resolve who is newer
                    if (importedItem.lastUpdated > existingLocal.lastUpdated) {
                        // Imported item wins: adopt local ID, replace local item
                        val winningItem = importedItem.copy(id = existingLocal.id).normalize()
                        result[existingLocal.id] = winningItem
                        
                        // We restore cover from ZIP.
                        // Note: inside ZIP the cover is stored under the imported item's ID (importedItem.id),
                        // but it should be saved locally under the local ID (existingLocal.id) during restoration.
                        // We add the imported item's ID to coverIdsToRestore so that we extract it and map it to the local ID.
                        coverIdsToRestore.add(importedItem.id)
                    } else {
                        // Local wins: keep local item as-is, do not copy imported cover
                    }
                }
            }
            
            MergeResult(
                mergedItems = result.values.toList(),
                coverIdsToRestore = coverIdsToRestore
            )
        }

    private fun MediaItem.toDto() = MediaItemBackupDto(
        id = id,
        category = category.name,
        title = title,
        alternativeTitles = alternativeTitles.ifEmpty { null },
        sourceUrl = sourceUrl,
        coverImageUrl = coverImageUrl,
        currentProgress = currentProgress,
        totalItems = totalItems,
        userStatus = userStatus.name,
        rating = rating,
        notes = notes.ifBlank { null },
        lastUpdated = lastUpdated,
        dateAdded = dateAdded
    )

    private fun MediaItemBackupDto.toDomain(): MediaItem? {
        val safeTitle = title?.takeIf { it.isNotBlank() } ?: return null
        val safeCategory = category?.let { MediaCategory.fromString(it) } ?: return null
        val safeStatus = userStatus?.let { UserStatus.fromString(it) } ?: inProgressStatusFor(safeCategory)
        val safeProgress = maxOf(currentProgress ?: 0, 0)
        val safeTotal = totalItems?.takeIf { it >= 0 }
        val safeLastUpdated = if (lastUpdated != null && lastUpdated > 0L) lastUpdated else 0L
        val safeDateAdded = if (dateAdded != null && dateAdded > 0L) dateAdded else System.currentTimeMillis()
        
        return MediaItem(
            id = id.takeIf { !it.isNullOrBlank() } ?: UUID.randomUUID().toString(),
            category = safeCategory,
            title = safeTitle,
            alternativeTitles = alternativeTitles ?: emptyList(),
            sourceUrl = sourceUrl?.trim(),
            coverImagePath = "",  // Will be populated during cover extraction if zip matches
            coverImageUrl = coverImageUrl?.trim(),
            currentProgress = safeProgress,
            totalItems = safeTotal,
            userStatus = safeStatus,
            rating = rating,
            notes = notes?.trim() ?: "",
            lastUpdated = safeLastUpdated,
            dateAdded = safeDateAdded
        ).normalize()
    }

    private fun inProgressStatusFor(category: MediaCategory): UserStatus {
        return when (category) {
            MediaCategory.NOVEL, MediaCategory.MANGA -> UserStatus.READING
            MediaCategory.ANIME, MediaCategory.MOVIE, MediaCategory.TV -> UserStatus.WATCHING
            MediaCategory.GAME -> UserStatus.PLAYING
        }
    }
}
