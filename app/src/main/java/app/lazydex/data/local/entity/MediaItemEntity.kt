package app.lazydex.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["sourceUrl"], unique = true),
        Index(value = ["isDeleted"])
    ]
)
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val category: String,            // Stored as uppercase string (e.g. "NOVEL"), no ambiguity
    val title: String,
    val alternativeTitles: String,   // JSON array string: ["Alt 1", "Alt 2", ...]
    val sourceUrl: String?,          // Nullable — multiple NULLs allowed under UNIQUE index
    val coverImagePath: String,      // Local file path (not URL), empty if no cover
    val coverImageUrl: String?,      // Nullable original URL of the cover
    val currentProgress: Int,
    val totalItems: Int?,
    val userStatus: String,          // Stored as uppercase string (e.g. "READING")
    val rating: Double?,             // 1.0–5.0, null = unrated
    val notes: String,
    @ColumnInfo(defaultValue = "[]") val genres: String = "[]",
    @ColumnInfo(defaultValue = "[]") val tags: String = "[]",
    @ColumnInfo(defaultValue = "") val author: String = "",
    @ColumnInfo(defaultValue = "") val description: String = "",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val lastUpdated: Long,
    val dateAdded: Long,
    @ColumnInfo(defaultValue = "{}") val extraData: String = "{}",
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false
)

