package app.lazydex.backup

import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.UserStatus
import app.lazydex.util.UrlNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.UUID

@Serializable
data class BackupEnvelopeDto(
    val schemaVersion: Int? = 3,
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
    val titleEnglish: String? = null,
    val titleRomaji: String? = null,
    val titleNative: String? = null,
    val sourceUrl: String? = null,
    val coverImagePath: String? = null,
    val coverImageUrl: String? = null,
    val bannerImage: String? = null,
    val currentProgress: Int? = null,
    val totalItems: Int? = null,
    val progress: Double? = null,
    val progressTotal: Double? = null,
    val secondaryProgress: Double? = null,
    val secondaryProgressTotal: Double? = null,
    val userStatus: String? = null,
    val rating: Double? = null,
    val scoreRaw: Int? = null,
    val repeat: Int? = null,
    val notes: String? = null,
    val genres: List<String>? = null,
    val tags: List<String>? = null,
    val author: String? = null,
    val description: String? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val lastUpdated: Long? = null,
    val dateAdded: Long? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val deletedAt: Long? = null,
    val isDeleted: Boolean? = null,
    val isAdult: Boolean? = null,
    val isPrivate: Boolean? = null,
    val durationMinutes: Int? = null,
    val avgChapterLength: Int? = null,
    val lengthUnitOverride: String? = null,
    val playbackSpeed: Double? = null,
    val averageScore: Int? = null,
    val extraData: String? = null,
    val category: String? = null,
    val categoryId: String? = null
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
        encodeDefaults = false
    }

    suspend fun serialize(items: List<MediaItem>): String = withContext(Dispatchers.Default) {
        val envelope = BackupEnvelopeDto(schemaVersion = 3, items = items.map { it.toDto() })
        backupJson.encodeToString(envelope)
    }

    suspend fun deserialize(json: String): DeserializedBackup = withContext(Dispatchers.Default) {
        val sanitized = json.trim().removePrefix("\uFEFF")
        if (sanitized.isBlank()) throw IllegalArgumentException("Backup file is empty")
        val envelope = backupJson.decodeFromString<BackupEnvelopeDto>(sanitized)
        val version = envelope.schemaVersion ?: 1
        val items = envelope.items?.mapNotNull { dto -> dto.toDomain() } ?: emptyList()
        DeserializedBackup(schemaVersion = version, items = items)
    }

    suspend fun merge(
        local: List<MediaItem>,
        imported: List<MediaItem>,
        importedSchemaVersion: Int = 3
    ): MergeResult = withContext(Dispatchers.Default) {
        val result = LinkedHashMap<String, MediaItem>()
        val resultByUrl = LinkedHashMap<String, MediaItem>()
        val resultByTitleType = LinkedHashMap<Pair<String, String>, MediaItem>()
        val coverIdsToRestore = mutableSetOf<String>()

        // Start with all local items
        local.forEach {
            result[it.id] = it
            it.sourceUrl?.let { url -> UrlNormalizer.normalize(url) }?.let { norm -> resultByUrl[norm] = it }
            resultByTitleType[it.title.trim().lowercase() to it.type.uppercase()] = it
        }

        imported.forEachIndexed { index, importedItem ->
            val normalizedImportedUrl = importedItem.sourceUrl?.let { UrlNormalizer.normalize(it) }
            val titleTypeKey = importedItem.title.trim().lowercase() to importedItem.type.uppercase()
            val existingLocal = result[importedItem.id]
                ?: normalizedImportedUrl?.let { resultByUrl[it] }
                ?: resultByTitleType[titleTypeKey]

            if (existingLocal == null) {
                // Pure addition
                val finalTime = if (importedItem.lastUpdated > 0L) importedItem.lastUpdated
                                else System.currentTimeMillis() - index
                val newItem = importedItem.copy(lastUpdated = finalTime).normalize()
                result[newItem.id] = newItem
                newItem.sourceUrl?.let { url -> UrlNormalizer.normalize(url) }?.let { norm -> resultByUrl[norm] = newItem }
                resultByTitleType[titleTypeKey] = newItem
                coverIdsToRestore.add(importedItem.id)
            } else {
                // Conflict: Resolve with forward-only progress protection
                val forwardIntProgress = maxOf(existingLocal.currentProgress, importedItem.currentProgress)
                val forwardDoubleProgress = maxOf(existingLocal.progress, importedItem.progress)
                val forwardSecondary = maxOf(existingLocal.secondaryProgress, importedItem.secondaryProgress)

                if (importedItem.lastUpdated > existingLocal.lastUpdated) {
                    val winningItem = if (importedSchemaVersion < 2) {
                        // Legacy v1 backup merge: preserve local v2 metadata if imported lacks it
                        importedItem.copy(
                            id = existingLocal.id,
                            currentProgress = forwardIntProgress,
                            progress = forwardDoubleProgress,
                            secondaryProgress = forwardSecondary,
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
                            currentProgress = forwardIntProgress,
                            progress = forwardDoubleProgress,
                            secondaryProgress = forwardSecondary,
                            deletedAt = if (!importedItem.isDeleted) null else existingLocal.deletedAt,
                            isDeleted = importedItem.isDeleted
                        ).normalize()
                    }

                    result[existingLocal.id] = winningItem
                    winningItem.sourceUrl?.let { url -> UrlNormalizer.normalize(url) }?.let { norm -> resultByUrl[norm] = winningItem }
                    resultByTitleType[titleTypeKey] = winningItem
                    coverIdsToRestore.add(importedItem.id)
                } else {
                    // Local is newer: still ensure forward progress is preserved
                    var updatedLocal = existingLocal
                    if (forwardDoubleProgress > existingLocal.progress || forwardIntProgress > existingLocal.currentProgress) {
                        updatedLocal = updatedLocal.copy(
                            currentProgress = forwardIntProgress,
                            progress = forwardDoubleProgress
                        )
                    }
                    if (forwardSecondary > existingLocal.secondaryProgress) {
                        updatedLocal = updatedLocal.copy(secondaryProgress = forwardSecondary)
                    }
                    result[existingLocal.id] = updatedLocal
                    updatedLocal.sourceUrl?.let { url -> UrlNormalizer.normalize(url) }?.let { norm -> resultByUrl[norm] = updatedLocal }
                    resultByTitleType[titleTypeKey] = updatedLocal
                    if (existingLocal.coverImagePath.isBlank()) {
                        coverIdsToRestore.add(importedItem.id)
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
        titleEnglish = alternativeTitles.getOrNull(1),
        titleRomaji = alternativeTitles.getOrNull(0),
        titleNative = alternativeTitles.getOrNull(2),
        sourceUrl = sourceUrl,
        coverImagePath = coverImagePath.ifBlank { null },
        coverImageUrl = coverImageUrl,
        bannerImage = getExtra("banner_image"),
        currentProgress = currentProgress,
        totalItems = totalItems,
        progress = progress,
        progressTotal = progressTotal,
        secondaryProgress = secondaryProgress,
        secondaryProgressTotal = secondaryProgressTotal,
        userStatus = userStatus.name,
        rating = rating,
        scoreRaw = rating?.let { kotlin.math.round(it * 20.0).toInt().coerceIn(0, 100) },
        repeat = repeat,
        notes = notes.ifBlank { null },
        genres = genres.ifEmpty { null },
        tags = tags.ifEmpty { null },
        author = author.ifBlank { null },
        description = description.ifBlank { null },
        startDate = startDate,
        endDate = endDate,
        lastUpdated = lastUpdated,
        dateAdded = dateAdded,
        deletedAt = deletedAt,
        isDeleted = if (isDeleted) true else null,
        isAdult = if (isAdult) true else null,
        durationMinutes = durationMinutes,
        avgChapterLength = avgChapterLength,
        lengthUnitOverride = lengthUnitOverride,
        playbackSpeed = playbackSpeed,
        averageScore = averageScore,
        extraData = extraData.takeIf { it.isNotBlank() && it != "{}" },
        category = type,
        categoryId = typeId
    )

    private fun MediaItemBackupDto.toDomain(): MediaItem? {
        val safeTitle = title?.takeIf { it.isNotBlank() } ?: return null

        val resolvedType = when {
            !type.isNullOrBlank() -> type.uppercase()
            !category.isNullOrBlank() -> category.uppercase()
            categoryId?.contains("manga", ignoreCase = true) == true -> "MANGA"
            categoryId?.contains("anime", ignoreCase = true) == true -> "ANIME"
            categoryId?.contains("game", ignoreCase = true) == true -> "GAME"
            else -> "NOVEL"
        }

        val resolvedFormat = when {
            !format.isNullOrBlank() -> format.uppercase()
            categoryId?.contains("manhwa", ignoreCase = true) == true -> "MANHWA"
            categoryId?.contains("manhua", ignoreCase = true) == true -> "MANHUA"
            categoryId?.contains("ln", ignoreCase = true) == true -> "LIGHT_NOVEL"
            categoryId?.contains("cn", ignoreCase = true) == true -> "WEB_NOVEL"
            categoryId?.contains("kr", ignoreCase = true) == true -> "WEB_NOVEL"
            resolvedType == "NOVEL" -> "WEB_NOVEL"
            resolvedType == "MANGA" -> "MANGA"
            resolvedType == "ANIME" -> "TV"
            resolvedType == "GAME" -> "PC"
            else -> ""
        }

        val resolvedCountry = when {
            !countryOfOrigin.isNullOrBlank() -> countryOfOrigin
            resolvedFormat == "MANHWA" || categoryId?.contains("kr", ignoreCase = true) == true -> "KR"
            resolvedFormat == "MANHUA" || categoryId?.contains("cn", ignoreCase = true) == true -> "CN"
            else -> null
        }

        val safeStatus = userStatus?.let { UserStatus.fromString(it) } ?: UserStatus.CURRENT
        val safeIntProgress = maxOf(currentProgress ?: progress?.toInt() ?: 0, 0)
        val safeDoubleProgress = maxOf(progress ?: safeIntProgress.toDouble(), 0.0)
        val safeTotal = totalItems?.takeIf { it >= 0 } ?: progressTotal?.toInt()?.takeIf { it >= 0 }
        val safeDoubleTotal = progressTotal?.takeIf { it >= 0.0 } ?: safeTotal?.toDouble()
        val safeLastUpdated = if (lastUpdated != null && lastUpdated > 0L) lastUpdated else (if (updatedAt != null && updatedAt > 0L) updatedAt else 0L)
        val safeDateAdded = if (dateAdded != null && dateAdded > 0L) dateAdded else (if (createdAt != null && createdAt > 0L) createdAt else System.currentTimeMillis())

        val altTitles = alternativeTitles?.ifEmpty { null } ?: listOfNotNull(titleRomaji, titleEnglish, titleNative)
        val resolvedBanner = bannerImage ?: getBannerFromExtra(extraData)

        return MediaItem(
            id = id.takeIf { !it.isNullOrBlank() } ?: UUID.randomUUID().toString(),
            type = resolvedType,
            format = resolvedFormat,
            countryOfOrigin = resolvedCountry,
            title = safeTitle,
            alternativeTitles = altTitles,
            sourceUrl = sourceUrl?.trim(),
            coverImagePath = coverImagePath?.trim() ?: "",
            coverImageUrl = coverImageUrl?.trim(),
            currentProgress = safeIntProgress,
            totalItems = safeTotal,
            progress = safeDoubleProgress,
            progressTotal = safeDoubleTotal,
            secondaryProgress = secondaryProgress ?: 0.0,
            secondaryProgressTotal = secondaryProgressTotal,
            userStatus = safeStatus,
            rating = rating ?: scoreRaw?.let { it / 20.0 },
            notes = notes?.trim() ?: "",
            genres = genres ?: emptyList(),
            tags = tags ?: emptyList(),
            author = author?.trim() ?: "",
            description = description?.trim() ?: "",
            startDate = startDate,
            endDate = endDate,
            lastUpdated = safeLastUpdated,
            dateAdded = safeDateAdded,
            extraData = if (!resolvedBanner.isNullOrBlank()) """{"banner_image":"${resolvedBanner.replace("\"", "\\\"")}"}""" else (extraData ?: "{}"),
            deletedAt = deletedAt ?: if (isDeleted == true) safeLastUpdated else null,
            isDeleted = isDeleted ?: (deletedAt != null),
            isAdult = isAdult ?: false,
            repeat = repeat ?: 0,
            durationMinutes = durationMinutes,
            avgChapterLength = avgChapterLength,
            lengthUnitOverride = lengthUnitOverride,
            playbackSpeed = playbackSpeed ?: 1.0,
            averageScore = averageScore
        ).normalize()
    }

    private fun getBannerFromExtra(extraData: String?): String? {
        if (extraData.isNullOrBlank() || extraData == "{}") return null
        return try {
            val root = Json.parseToJsonElement(extraData) as? JsonObject
            root?.get("banner_image")?.let { it as? JsonPrimitive }?.contentOrNull
        } catch (_: Exception) {
            null
        }
    }
}
