package app.lazydex.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["type", "userStatus"]),
        Index(value = ["type", "format"]),
        Index(value = ["updatedAt"]),
        Index(value = ["deletedAt"])
    ]
)
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val type: String = "NOVEL",
    @ColumnInfo(defaultValue = "") val format: String = "",
    val countryOfOrigin: String? = null,
    val title: String,
    val titleEnglish: String? = null,
    val titleRomaji: String? = null,
    val titleNative: String? = null,
    @ColumnInfo(defaultValue = "") val coverImagePath: String = "",
    val coverImageUrl: String? = null,
    val bannerImage: String? = null,
    @ColumnInfo(defaultValue = "") val description: String = "",
    @ColumnInfo(defaultValue = "") val author: String = "",
    val userStatus: String,
    @ColumnInfo(defaultValue = "0") val progress: Double = 0.0,
    val progressTotal: Double? = null,
    @ColumnInfo(defaultValue = "0") val secondaryProgress: Double = 0.0,
    val secondaryProgressTotal: Double? = null,
    val scoreRaw: Int? = null,
    @ColumnInfo(defaultValue = "0") val repeat: Int = 0,
    @ColumnInfo(defaultValue = "") val notes: String = "",
    @ColumnInfo(defaultValue = "[]") val genres: String = "[]",
    @ColumnInfo(defaultValue = "[]") val tags: String = "[]",
    @ColumnInfo(defaultValue = "0") val isPrivate: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isAdult: Boolean = false,
    val startYear: Int? = null,
    val startMonth: Int? = null,
    val startDay: Int? = null,
    val endYear: Int? = null,
    val endMonth: Int? = null,
    val endDay: Int? = null,
    val durationMinutes: Int? = null,
    val avgChapterLength: Int? = null,
    val lengthUnitOverride: String? = null,
    @ColumnInfo(defaultValue = "1.0") val playbackSpeed: Double = 1.0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)
