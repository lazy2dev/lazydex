package app.lazydex.domain.model

enum class MediaCategory(val displayName: String) {
    NOVEL("Novel"),
    MANGA("Manga"),
    ANIME("Anime"),
    GAME("Game"),
    MOVIE("Movie"),
    TV("TV");

    companion object {
        fun fromString(value: String): MediaCategory? {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    }
}

fun resolveMediaCategory(categoryId: String?): MediaCategory {
    if (categoryId == null) return MediaCategory.NOVEL
    val raw = categoryId.removePrefix("cat_").removePrefix("typ_").substringBefore("_").lowercase()
    return when (raw) {
        "manhwa", "manhua" -> MediaCategory.MANGA
        else -> MediaCategory.fromString(raw) ?: MediaCategory.NOVEL
    }
}

fun MediaCategory?.statusLabel(statusFilter: StatusFilter): String {
    return when (statusFilter) {
        StatusFilter.ALL -> "All"
        StatusFilter.IN_PROGRESS -> when (this) {
            MediaCategory.NOVEL, MediaCategory.MANGA -> "Reading"
            MediaCategory.ANIME, MediaCategory.MOVIE, MediaCategory.TV -> "Watching"
            MediaCategory.GAME -> "Playing"
            null -> StatusFilter.IN_PROGRESS.displayName
        }
        StatusFilter.PLAN_TO -> when (this) {
            MediaCategory.NOVEL, MediaCategory.MANGA -> "Plan to Read"
            MediaCategory.ANIME, MediaCategory.MOVIE, MediaCategory.TV -> "Plan to Watch"
            MediaCategory.GAME -> "Plan to Play"
            null -> StatusFilter.PLAN_TO.displayName
        }
        StatusFilter.COMPLETED -> StatusFilter.COMPLETED.displayName
        StatusFilter.ON_HOLD -> StatusFilter.ON_HOLD.displayName
        StatusFilter.DROPPED -> StatusFilter.DROPPED.displayName
    }
}

