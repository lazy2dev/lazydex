package app.lazydex.domain.model

enum class SortField(val displayName: String) {
    DATE_ADDED("Date Added"),
    LAST_ACTIVE("Last Active"),
    TITLE("Title"),
    PROGRESS("Progress %")
}

enum class SortDirection(val displayName: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending")
}
