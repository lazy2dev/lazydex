package app.lazydex.data.local.entity

import androidx.room.DatabaseView

@DatabaseView(
    viewName = "active_media_items",
    value = "SELECT * FROM media_items WHERE deletedAt IS NULL"
)
data class ActiveMediaItemView(
    val id: String,
    val type: String,
    val format: String,
    val countryOfOrigin: String?,
    val title: String,
    val titleEnglish: String?,
    val titleRomaji: String?,
    val titleNative: String?,
    val coverImagePath: String,
    val coverImageUrl: String?,
    val bannerImage: String?,
    val description: String,
    val author: String,
    val userStatus: String,
    val progress: Double,
    val progressTotal: Double?,
    val secondaryProgress: Double,
    val secondaryProgressTotal: Double?,
    val scoreRaw: Int?,
    val repeat: Int,
    val notes: String,
    val genres: String,
    val tags: String,
    val isPrivate: Boolean,
    val isAdult: Boolean,
    val startYear: Int?,
    val startMonth: Int?,
    val startDay: Int?,
    val endYear: Int?,
    val endMonth: Int?,
    val endDay: Int?,
    val durationMinutes: Int?,
    val avgChapterLength: Int?,
    val lengthUnitOverride: String?,
    val playbackSpeed: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?
)
