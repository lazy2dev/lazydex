package app.lazydex.domain.model

enum class MediaType(val displayName: String) {
    NOVEL("Novel"),
    MANGA("Manga"),
    ANIME("Anime"),
    GAME("Game"),
    MOVIE("Movie"),
    TV("TV");

    companion object {
        fun fromString(value: String): MediaType? {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    }
}

fun MediaType.toTopLevelId(): String = when (this) {
    MediaType.NOVEL -> "typ_novel"
    MediaType.MANGA -> "typ_manga"
    MediaType.ANIME -> "typ_anime"
    MediaType.GAME -> "typ_game"
    MediaType.MOVIE -> "typ_movie"
    MediaType.TV -> "typ_tv"
}

fun resolveMediaType(typeId: String?): MediaType {
    if (typeId == null) return MediaType.NOVEL
    val raw = typeId.removePrefix("typ_").substringBefore("_").lowercase()
    return when (raw) {
        "manhwa", "manhua" -> MediaType.MANGA
        else -> MediaType.fromString(raw) ?: MediaType.NOVEL
    }
}
