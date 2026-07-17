package app.lazydex.domain.model

enum class StatusFilter(val displayName: String) {
    ALL("All"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    ON_HOLD("On Hold"),
    DROPPED("Dropped"),
    PLAN_TO("Plan to")
}
