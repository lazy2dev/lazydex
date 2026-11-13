package app.lazydex.domain.model

enum class StatusFilter(val displayName: String) {
    ALL("All"),
    CURRENT("Current"),
    PLANNING("Planning"),
    COMPLETED("Completed"),
    PAUSED("Paused"),
    DROPPED("Dropped"),
    REPEATING("Repeating");

    fun toUserStatus(): UserStatus? = when (this) {
        ALL -> null
        CURRENT -> UserStatus.CURRENT
        PLANNING -> UserStatus.PLANNING
        COMPLETED -> UserStatus.COMPLETED
        PAUSED -> UserStatus.PAUSED
        DROPPED -> UserStatus.DROPPED
        REPEATING -> UserStatus.REPEATING
    }
}
