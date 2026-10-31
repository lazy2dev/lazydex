package app.lazydex.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["sortOrder"])
    ]
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val icon: String?,
    val unitName: String?,
    val secondaryUnitName: String?,
    val actionVerb: String?,
    val hasParts: Int?,
    val hasCharEstimate: Int?,
    val hasAudioSpeed: Int?,
    val lengthUnit: String?,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

