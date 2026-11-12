package app.lazydex.backup

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
    val schemaVersion: Int = 3,
    val items: List<MediaItemBackupDto>? = null
)

@Serializable
data class MediaItemBackupDto(
    val id: String? = null,
    val type: String? = null,
    val format: String? = null,
    val countryOfOrigin: String? = null,
    val title: String? = null,
    val alternativeTitles: List<String>? = null,
    val sourceUrl: String? = null,
    val coverImageUrl: String? = null,
    val currentProgress: Int? = null,
    val totalItems: Int? = null,
    val userStatus: String? = null,
    val rating: Double? = null,
    val notes: String? = null,
    val genres: List<String>? = null,
    val tags: List<String>? = null,
    val author: String? = null,
    val description: String? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val lastUpdated: Long? = null,
    val dateAdded: Long? = null,
    val extraData: String? = null,
    val isDeleted: Boolean? = null,
    val progressIncreasedAt: Long? = null
)

data class DeserializedBackup(
    val schemaVersion: Int,
    val items: List<MediaItem>
)

data class MergeResult(
    val mergedItems: List<MediaItem>,
    val coverIdsToRestore: Set<String>
)

object BackupProcessor {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    val backupJson = Json {
        ignoreUnknownKeys = true
        decodeEnumsCaseInsensitive = true
    }

    suspend fun serialize(items: List<MediaItem>): String = withContext(Dispatchers.Default) {
        val envelope = BackupEnvelopeDto(schemaVersion = 3, items = items.map { it.toDto() })
        backupJson.encodeToString(envelope)
    }

    suspend fun deserialize(json: String): DeserializedBackup = withContext(Dispatchers.Default) {
        val sanitized = json.trim().removePrefix("\uFEFF")
        if (sanitized.isBlank()) throw IllegalArgumentException("Backup file is empty")
        val envelope = backupJson.decodeFromString<BackupEnvelopeDto>(sanitized)
        if (envelope.schemaVersion > 3) {
            throw IllegalArgumentException("Unsupported backup schema version: ${envelope.schemaVersion}")
        }
        val items = envelope.items?.mapNotNull { dto -> dto.toDomain() } ?: emptyList()
        DeserializedBackup(schemaVersion = envelope.schemaVersion, items = items)
    }

    suspend fun merge(
        local: List<MediaItem>,
        imported: List<MediaItem>,
        importedSchemaVersion: Int = 3
    ): MergeResult = withContext(Dispatchers.Default) {
        val localById = local.associateBy { it.id }
        val localByUrl = local.filter { it.sourceUrl != null }.associateBy { it.sourceUrl?.let { url -> UrlNormalizer.normalize(url) } }

        val result = LinkedHashMap<String, MediaItem>()
        val coverIdsToRestore = mutableSetOf<String>()

        // Start with all local items
        local.forEach { result[it.id] = it }

        imported.forEachIndexed { index, importedItem ->
            val normalizedImportedUrl = importedItem.sourceUrl?.let { UrlNormalizer.normalize(it) }
            val existingLocal = localById[importedItem.id] ?: normalizedImportedUrl?.let { localByUrl[it] }

            if (existingLocal == null) {
                // Pure addition
                val finalTime = if (importedItem.lastUpdated > 0L) importedItem.lastUpdated
                                else System.currentTimeMillis() - index
                val newItem = importedItem.copy(lastUpdated = finalTime).normalize()
                result[newItem.id] = newItem
                coverIdsToRestore.add(importedItem.id)
            } else {
                // Conflict: Resolve who is newer with forward-only progress protection
                val forwardProgress = maxOf(existingLocal.currentProgress, importedItem.currentProgress)

                if (importedItem.lastUpdated > existingLocal.lastUpdated) {
                    val winningItem = if (importedSchemaVersion < 2) {
                        // Legacy v1 backup merge: preserve local v2 metadata if imported lacks it
                        importedItem.copy(
                            id = existingLocal.id,
                            currentProgress = forwardProgress,
                            genres = importedItem.genres.ifEmpty { existingLocal.genres },
                            tags = importedItem.tags.ifEmpty { existingLocal.tags },
                            author = importedItem.author.ifBlank { existingLocal.author },
                            description = importedItem.description.ifBlank { existingLocal.description },
                            startDate = importedItem.startDate ?: existingLocal.startDate,
                            endDate = importedItem.endDate ?: existingLocal.endDate,
                            extraData = if (importedItem.extraData.isNotBlank() && importedItem.extraData != "{}") importedItem.extraData else existingLocal.extraData
                        ).normalize()
                    } else {
                        // v2/v3 merge: take imported as-is (with forward-only progress)
                        importedItem.copy(
                            id = existingLocal.id,
                            currentProgress = forwardProgress
                        ).normalize()
                    }

                    result[existingLocal.id] = winningItem
                    coverIdsToRestore.add(importedItem.id)
                } else {
                    // Local is newer: still ensure forward progress is preserved
                    if (forwardProgress > existingLocal.currentProgress) {
                        result[existingLocal.id] = existingLocal.copy(currentProgress = forwardProgress)
                    }
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
        type = type,
        format = format,
        countryOfOrigin = countryOfOrigin,
        title = title,
        alternativeTitles = alternativeTitles.ifEmpty { null },
        sourceUrl = sourceUrl,
        coverImageUrl = coverImageUrl,
        currentProgress = currentProgress,
        totalItems = totalItems,
        userStatus = userStatus.name,
        rating = rating,
        notes = notes.ifBlank { null },
        genres = genres.ifEmpty { null },
        tags = tags.ifEmpty { null },
        author = author.ifBlank { null },
        description = description.ifBlank { null },
        startDate = startDate,
        endDate = endDate,
        lastUpdated = lastUpdated,
        dateAdded = dateAdded,
        extraData = extraData.takeIf { it.isNotBlank() && it != "{}" },
        isDeleted = if (isDeleted) true else null,
        progressIncreasedAt = progressIncreasedAt
    )

    private fun MediaItemBackupDto.toDomain(): MediaItem? {
        val safeTitle = title?.takeIf { it.isNotBlank() } ?: return null

        val resolvedType = type?.takeIf { it.isNotBlank() }?.uppercase() ?: "NOVEL"

        val resolvedFormat = when {
            !format.isNullOrBlank() -> format.uppercase()
            resolvedType == "NOVEL" -> "WEB_NOVEL"
            resolvedType == "MANGA" -> "MANGA"
            resolvedType == "ANIME" -> "TV"
            resolvedType == "GAME" -> "PC"
            else -> ""
        }

        val resolvedCountry = countryOfOrigin

        val safeStatus = userStatus?.let { UserStatus.fromString(it) } ?: inProgressStatusFor(resolvedType)
        val safeProgress = maxOf(currentProgress ?: 0, 0)
        val safeTotal = totalItems?.takeIf { it >= 0 }
        val safeLastUpdated = if (lastUpdated != null && lastUpdated > 0L) lastUpdated else 0L
        val safeDateAdded = if (dateAdded != null && dateAdded > 0L) dateAdded else System.currentTimeMillis()

        return MediaItem(
            id = id.takeIf { !it.isNullOrBlank() } ?: UUID.randomUUID().toString(),
            type = resolvedType,
            format = resolvedFormat,
            countryOfOrigin = resolvedCountry,
            title = safeTitle,
            alternativeTitles = alternativeTitles ?: emptyList(),
            sourceUrl = sourceUrl?.trim(),
            coverImagePath = "",  // Populated during cover extraction
            coverImageUrl = coverImageUrl?.trim(),
            currentProgress = safeProgress,
            totalItems = safeTotal,
            userStatus = safeStatus,
            rating = rating,
            notes = notes?.trim() ?: "",
            genres = genres ?: emptyList(),
            tags = tags ?: emptyList(),
            author = author?.trim() ?: "",
            description = description?.trim() ?: "",
            startDate = startDate,
            endDate = endDate,
            lastUpdated = safeLastUpdated,
            dateAdded = safeDateAdded,
            extraData = extraData ?: "{}",
            isDeleted = isDeleted ?: false,
            progressIncreasedAt = progressIncreasedAt ?: if (safeProgress > 0) safeLastUpdated.takeIf { it > 0 } ?: safeDateAdded else null
        ).normalize()
    }

    private fun inProgressStatusFor(type: String): UserStatus {
        return when (type.uppercase()) {
            "NOVEL", "MANGA" -> UserStatus.READING
            "ANIME" -> UserStatus.WATCHING
            "GAME" -> UserStatus.PLAYING
            else -> UserStatus.READING
        }
    }
}
