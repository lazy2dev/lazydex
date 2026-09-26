package app.lazydex.domain.model

enum class SortField(val displayName: String) {
    ALPHABETICAL("Alphabetical"),
    TOTAL_COUNT("Total count"),
    LAST_READ("Last read"),
    UNREAD_COUNT("Unread count"),
    CURRENT_PROGRESS("Current progress"),
    DATE_ADDED("Date added"),
    RATING("Score"),
    RANDOM("Random");

    companion object {
        fun fromNameOrDefault(name: String?): SortField {
            return entries.firstOrNull { it.name == name } ?: DATE_ADDED
        }
    }
}

enum class SortDirection(val displayName: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending");

    companion object {
        fun fromNameOrDefault(name: String?): SortDirection {
            return entries.firstOrNull { it.name == name } ?: DESCENDING
        }
    }
}

