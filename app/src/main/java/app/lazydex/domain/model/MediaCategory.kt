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
