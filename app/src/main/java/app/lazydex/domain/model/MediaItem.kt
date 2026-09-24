package app.lazydex.domain.model

import app.lazydex.util.UrlNormalizer

data class MediaItem(
    val id: String,              // UUID string
    val category: MediaCategory,
    val title: String,
    val alternativeTitles: List<String> = emptyList(),  // Flexible list stored as JSON
    val sourceUrl: String? = null,      // Nullable — SQLite UNIQUE index treats NULLs as non-duplicates
    val coverImagePath: String = "",  // Local file path (not URL), empty if no cover
    val coverImageUrl: String? = null,  // Nullable original URL of the cover (used as fallback for download/restore)

    val currentProgress: Int,    // Always >= 0, and <= totalItems when total is non-null
    val totalItems: Int?,        // null = unknown/ongoing
    val userStatus: UserStatus,
    val rating: Double? = null,  // 1.0–5.0 stars, null = unrated
    val notes: String = "",      // User notes/annotations
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val author: String = "",
    val description: String = "",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val lastUpdated: Long,       // System.currentTimeMillis()
    val dateAdded: Long,         // System.currentTimeMillis() on creation (stable sort)
    val extraData: String = "{}", // Extensible future-proof JSON payload (tracker IDs, custom metadata)
    val isDeleted: Boolean = false // Soft-delete tombstone for sync
) {
    val anilistId: Long? get() = getExtra("anilist_id")?.toLongOrNull()
    val malId: Long? get() = getExtra("mal_id")?.toLongOrNull()
    val simklId: Long? get() = getExtra("simkl_id")?.toLongOrNull()

    fun getExtra(key: String): String? {
        if (extraData.isBlank() || extraData == "{}") return null
        return try {
            val pattern = Regex(""""${Regex.escape(key)}"\s*:\s*"([^"]*)"""")
            pattern.find(extraData)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    fun withExtra(key: String, value: String): MediaItem {
        val currentMap = parseExtraMap().toMutableMap()
        currentMap[key] = value
        val json = currentMap.entries.joinToString(
            prefix = "{",
            postfix = "}",
            separator = ","
        ) { "\"${it.key}\":\"${it.value.replace("\"", "\\\"")}\"" }
        return copy(extraData = json)
    }

    private fun parseExtraMap(): Map<String, String> {
        if (extraData.isBlank() || extraData == "{}") return emptyMap()
        val regex = Regex(""""([^"]+)"\s*:\s*"([^"]*)"""")
        return regex.findAll(extraData).associate { it.groupValues[1] to it.groupValues[2] }
    }
    /**
     * Canonical normalization — run before EVERY write (add, update, import, merge).
     * - Trims whitespace from title, sourceUrl, coverImagePath, coverImageUrl, notes, author, description
     * - Normalizes genre and tag lists (trim, replace '_', deduplicate case-insensitively)
     * - Ensures title is never blank (defaults to "Untitled")
     * - Filters blank alt titles
     * - Clamps currentProgress to [0, totalItems] when totalItems is non-null
     * - Clamps currentProgress >= 0 when totalItems is null
     * - Caps rating to 1.0–5.0 range
     * - Normalizes sourceUrl via UrlNormalizer
     * - Validates coverImageUrl scheme is HTTP/HTTPS, otherwise null
     */
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
        val safeRating = rating?.coerceIn(1.0, 5.0)
        return copy(
            title = title.trim().ifBlank { "Untitled" },
            alternativeTitles = alternativeTitles.map { it.trim() }.filter { it.isNotBlank() },
            sourceUrl = normalizedUrl,
            coverImagePath = coverImagePath.trim(),
            coverImageUrl = safeCoverUrl,
            totalItems = safeTotal,
            currentProgress = safeProgress,
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

