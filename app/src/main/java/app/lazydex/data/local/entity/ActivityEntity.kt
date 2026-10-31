package app.lazydex.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "activity",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["mediaId", "timestamp"]),
        Index(value = ["timestamp"])
    ]
)
data class ActivityEntity(
    @PrimaryKey val id: String,
    val mediaId: String,
    val type: String,
    val oldValue: String? = null,
    val newValue: String = "",
    val delta: Double? = null,
    val mode: String? = null,
    val speed: Double? = null,
    val lengthSnapshot: Int? = null,
    val lengthUnit: String? = null,
    val rateSnapshot: Double? = null,
    @ColumnInfo(defaultValue = "1") val counted: Int = 1,
    val timestamp: Long
)

