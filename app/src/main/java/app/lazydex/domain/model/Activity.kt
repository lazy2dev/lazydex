package app.lazydex.domain.model

data class ActivityLog(
    val id: String,
    val mediaId: String,
    val type: String, // 'PROGRESS_UPDATE', 'STATUS_CHANGE', 'RATING', 'CREATED', 'DELETED', 'RESTORED'
    val oldValue: String? = null,
    val newValue: String,
    val delta: Double? = null,
    val mode: String? = null,
    val speed: Double? = null,
    val lengthSnapshot: Int? = null,
    val lengthUnit: LengthUnit? = null,
    val rateSnapshot: Double? = null,
    val counted: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

