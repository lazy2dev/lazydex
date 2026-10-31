package app.lazydex.domain.model

data class MediaPart(
    val id: String,
    val mediaId: String,
    val name: String,
    val sortOrder: Int,
    val totalUnits: Double?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

