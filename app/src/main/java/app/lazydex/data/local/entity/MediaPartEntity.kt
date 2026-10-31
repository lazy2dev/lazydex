package app.lazydex.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_parts",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["mediaId", "sortOrder"], unique = true),
        Index(value = ["mediaId"])
    ]
)
data class MediaPartEntity(
    @PrimaryKey val id: String,
    val mediaId: String,
    val name: String,
    val sortOrder: Int,
    val totalUnits: Double?,
    val createdAt: Long,
    val updatedAt: Long
)

