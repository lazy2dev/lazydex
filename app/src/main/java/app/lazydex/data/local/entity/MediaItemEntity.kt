package app.lazydex.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    indices = [
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
    val progressTotal: Double? = null,
    val secondaryProgressTotal: Double? = null,
    @ColumnInfo(defaultValue = "[]") val genres: String = "[]",
    @ColumnInfo(defaultValue = "[]") val tags: String = "[]",
    @ColumnInfo(defaultValue = "0") val isAdult: Boolean = false,
    val durationMinutes: Int? = null,
    val avgChapterLength: Int? = null,
    val lengthUnitOverride: String? = null,
    @ColumnInfo(defaultValue = "1.0") val playbackSpeed: Double = 1.0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)
