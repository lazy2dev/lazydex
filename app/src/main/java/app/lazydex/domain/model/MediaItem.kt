package app.lazydex.domain.model

import app.lazydex.util.UrlNormalizer

data class MediaItem(
    val id: String,              // UUID string
    val category: MediaCategory,
    val title: String,
    val alternativeTitles: List<String> = emptyList(),  // Flexible list stored as JSON
    val sourceUrl: String?,      // Nullable — SQLite UNIQUE index treats NULLs as non-duplicates
    val coverImagePath: String,  // Local file path (not URL), empty if no cover
    val coverImageUrl: String?,  // Nullable original URL of the cover (used as fallback for download/restore)
    val currentProgress: Int,    // Always >= 0, and <= totalItems when total is non-null
    val totalItems: Int?,        // null = unknown/ongoing
    val userStatus: UserStatus,
    val rating: Double? = null,  // 1.0–5.0 stars, null = unrated
    val notes: String = "",      // User notes/annotations
    val lastUpdated: Long,       // System.currentTimeMillis()
    val dateAdded: Long          // System.currentTimeMillis() on creation (stable sort)
) {
    /**
     * Canonical normalization — run before EVERY write (add, update, import, merge).
     * - Trims whitespace from title, sourceUrl, coverImagePath, coverImageUrl, notes
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
            notes = notes.trim()
        )
    }
}
