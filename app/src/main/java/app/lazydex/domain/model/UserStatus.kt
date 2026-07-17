package app.lazydex.domain.model

enum class UserStatus(val displayName: String) {
    READING("Reading"),
    WATCHING("Watching"),
    PLAYING("Playing"),
    COMPLETED("Completed"),
    ON_HOLD("On Hold"),
    DROPPED("Dropped"),
    PLAN_TO("Plan to");

    companion object {
        fun fromString(value: String): UserStatus? {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    }
}
