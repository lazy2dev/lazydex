package app.lazydex.data.local.entity

import androidx.room.DatabaseView

@DatabaseView(
    viewName = "active_media_items",
    value = "SELECT * FROM media_items WHERE deletedAt IS NULL"
)
data class ActiveMediaItemView(
    val id: String,
    val categoryId: String,
    val title: String,
    val alternativeTitles: String,
    val description: String,
    val author: String,
    val coverImagePath: String,
    val coverImageUrl: String?,
    val userStatus: String,
    val progress: Double,
    val progressTotal: Double?,
    val secondaryProgress: Double,
    val secondaryProgressTotal: Double?,
    val actionOverride: String?,
    val avgChapterLength: Int?,
    val lengthUnitOverride: String?,
    val playbackSpeed: Double,
    val rating: Double?,
    val notes: String,
    val genres: String,
    val tags: String,
    val startDate: Long?,
    val endDate: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?
)

