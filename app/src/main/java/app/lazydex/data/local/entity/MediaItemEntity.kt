package app.lazydex.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["categoryId", "userStatus"]),
        Index(value = ["updatedAt"]),
        Index(value = ["deletedAt"])
    ]
)
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val title: String,
    @ColumnInfo(defaultValue = "[]") val alternativeTitles: String = "[]",
    @ColumnInfo(defaultValue = "") val description: String = "",
    @ColumnInfo(defaultValue = "") val author: String = "",
    @ColumnInfo(defaultValue = "") val coverImagePath: String = "",
    val coverImageUrl: String? = null,
    val userStatus: String,
    @ColumnInfo(defaultValue = "0") val progress: Double = 0.0,
    val progressTotal: Double? = null,
    @ColumnInfo(defaultValue = "0") val secondaryProgress: Double = 0.0,
    val secondaryProgressTotal: Double? = null,
    val actionOverride: String? = null,
    val avgChapterLength: Int? = null,
    val lengthUnitOverride: String? = null,
    @ColumnInfo(defaultValue = "1.0") val playbackSpeed: Double = 1.0,
    val rating: Double? = null,
    @ColumnInfo(defaultValue = "") val notes: String = "",
    @ColumnInfo(defaultValue = "[]") val genres: String = "[]",
    @ColumnInfo(defaultValue = "[]") val tags: String = "[]",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)
