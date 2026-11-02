package app.lazydex.data.local.entity

@Deprecated("Retired in Database V6. Aggregated into secondaryProgressTotal.")
data class MediaPartEntity(
    val id: String,
    val mediaId: String,
    val name: String,
    val sortOrder: Int,
    val totalUnits: Double?,
    val createdAt: Long,
    val updatedAt: Long
)
