package app.lazydex.domain.model

import app.lazydex.util.UrlNormalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class MediaItem(
    val id: String,
    val categoryId: String = "cat_novel",
    val title: String,
    val alternativeTitles: List<String> = emptyList(),
    val sourceUrl: String? = null,
    val coverImagePath: String = "",
    val coverImageUrl: String? = null,
    val currentProgress: Int = 0,
    val totalItems: Int? = null,
    val progress: Double = currentProgress.toDouble(),
    val progressTotal: Double? = totalItems?.toDouble(),
    val secondaryProgress: Double = 0.0,
    val secondaryProgressTotal: Double? = null,
    val actionOverride: String? = null,
    val avgChapterLength: Int? = null,
    val lengthUnitOverride: String? = null,
    val playbackSpeed: Double = 1.0,
    val userStatus: UserStatus,
    val rating: Double? = null,
    val notes: String = "",
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val author: String = "",
    val description: String = "",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val lastUpdated: Long,
    val dateAdded: Long,
    val extraData: String = "{}",
    val deletedAt: Long? = null,
    val isDeleted: Boolean = deletedAt != null,
    val category: MediaCategory = MediaCategory.fromString(
        categoryId.removePrefix("cat_").removePrefix("typ_").substringBefore("_")
    ) ?: MediaCategory.NOVEL
) {
    constructor(
        id: String,
        category: MediaCategory,
        title: String,
        alternativeTitles: List<String> = emptyList(),
        sourceUrl: String? = null,
        coverImagePath: String = "",
        coverImageUrl: String? = null,
        currentProgress: Int,
        totalItems: Int?,
        userStatus: UserStatus,
        rating: Double? = null,
        notes: String = "",
        genres: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        author: String = "",
        description: String = "",
        startDate: Long? = null,
        endDate: Long? = null,
        lastUpdated: Long,
        dateAdded: Long,
        extraData: String = "{}",
        isDeleted: Boolean = false
    ) : this(
        id = id,
        categoryId = when (category) {
            MediaCategory.NOVEL -> "cat_novel"
            MediaCategory.MANGA -> "cat_manga"
            MediaCategory.ANIME -> "cat_anime"
            MediaCategory.GAME -> "cat_game"
            MediaCategory.MOVIE -> "cat_movie"
            MediaCategory.TV -> "cat_tv"
        },
        title = title,
        alternativeTitles = alternativeTitles,
        sourceUrl = sourceUrl,
        coverImagePath = coverImagePath,
        coverImageUrl = coverImageUrl,
        currentProgress = currentProgress,
        totalItems = totalItems,
        progress = currentProgress.toDouble(),
        progressTotal = totalItems?.toDouble(),
        userStatus = userStatus,
        rating = rating,
        notes = notes,
        genres = genres,
        tags = tags,
        author = author,
        description = description,
        startDate = startDate,
        endDate = endDate,
        lastUpdated = lastUpdated,
        dateAdded = dateAdded,
        extraData = extraData,
        deletedAt = if (isDeleted) lastUpdated else null,
        isDeleted = isDeleted,
        category = category
    )

    val anilistId: Long? get() = getExtra("anilist_id")?.toLongOrNull()
    val malId: Long? get() = getExtra("mal_id")?.toLongOrNull()
    val simklId: Long? get() = getExtra("simkl_id")?.toLongOrNull()

    fun getExtra(key: String): String? {
        if (extraData.isBlank() || extraData == "{}") return null
        return try {
            val root = Json.parseToJsonElement(extraData).jsonObject
            root[key]?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            null
        }
    }

    fun withExtra(key: String, value: String): MediaItem {
        val json = try {
            val current = if (extraData.isNotBlank() && extraData != "{}") {
                Json.parseToJsonElement(extraData).jsonObject.toMutableMap()
            } else {
                mutableMapOf()
            }
            current[key] = JsonPrimitive(value)
            JsonObject(current).toString()
        } catch (e: Exception) {
            buildJsonObject { put(key, value) }.toString()
        }
        return copy(extraData = json)
    }

    fun normalize(): MediaItem {
        val normalizedUrl = sourceUrl?.takeIf { it.isNotBlank() }?.let { UrlNormalizer.normalize(it) }
        val safeCoverUrl = coverImageUrl?.trim()?.takeIf {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        }
        val safeTotal = totalItems?.takeIf { it >= 0 }
        val safeProgress = when {
            currentProgress < 0 -> 0
            safeTotal != null && currentProgress > safeTotal -> safeTotal
            else -> currentProgress
        }
        val safeDoubleTotal = progressTotal?.takeIf { it >= 0.0 } ?: safeTotal?.toDouble()
        val safeDoubleProgress = when {
            progress < 0.0 -> 0.0
            safeDoubleTotal != null && progress > safeDoubleTotal -> safeDoubleTotal
            else -> if (progress == 0.0 && safeProgress > 0) safeProgress.toDouble() else progress
        }
        val safeRating = rating?.coerceIn(0.0, 10.0)
        return copy(
            title = title.trim().ifBlank { "Untitled" },
            alternativeTitles = alternativeTitles.map { it.trim() }.filter { it.isNotBlank() },
            sourceUrl = normalizedUrl,
            coverImagePath = coverImagePath.trim(),
            coverImageUrl = safeCoverUrl,
            currentProgress = safeProgress,
            totalItems = safeTotal,
            progress = safeDoubleProgress,
            progressTotal = safeDoubleTotal,
            playbackSpeed = playbackSpeed.coerceIn(0.1, 4.0),
            rating = safeRating,
            notes = notes.trim(),
            genres = normalizeGenreList(genres),
            tags = normalizeGenreList(tags),
            author = author.trim(),
            description = description.trim(),
            startDate = startDate?.takeIf { it > 0 },
            endDate = endDate?.takeIf { it > 0 }
        )
    }

    companion object {
        fun normalizeGenreList(list: List<String>): List<String> {
            val seen = mutableSetOf<String>()
            return list.map {
                it.trim().replace('_', ' ').replace(Regex("\\s+"), " ")
            }.filter { it.isNotBlank() }
             .filter { seen.add(it.lowercase().replace(" ", "")) }
        }
    }
}
