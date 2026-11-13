package app.lazydex.domain.model

enum class UserStatus(val displayName: String) {
    CURRENT("Current"),
    PLANNING("Planning"),
    COMPLETED("Completed"),
    PAUSED("Paused"),
    DROPPED("Dropped"),
    REPEATING("Repeating");

    companion object {
        fun fromString(value: String?): UserStatus? {
            if (value.isNullOrBlank()) return null
            return when (value.uppercase()) {
                "CURRENT", "IN_PROGRESS", "READING", "WATCHING", "PLAYING" -> CURRENT
                "PLANNING", "PLAN_TO" -> PLANNING
                "PAUSED", "ON_HOLD" -> PAUSED
                "COMPLETED" -> COMPLETED
                "DROPPED" -> DROPPED
                "REPEATING" -> REPEATING
                else -> entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            }
        }

        fun fromString(value: String?, type: String?): UserStatus? = fromString(value)
        fun fromString(value: String?, mediaType: MediaType?): UserStatus? = fromString(value)
    }
}
