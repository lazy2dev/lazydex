package app.lazydex.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_data",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["progressIncreasedAt"]),
        Index(value = ["userStatus"])
    ]
)
data class UserDataEntity(
    @PrimaryKey val mediaId: String,
    @ColumnInfo(defaultValue = "PLAN_TO") val userStatus: String = "PLAN_TO",
    @ColumnInfo(defaultValue = "0") val progress: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val secondaryProgress: Double = 0.0,
    val scoreRaw: Int? = null,
    @ColumnInfo(defaultValue = "0") val repeat: Int = 0,
    @ColumnInfo(defaultValue = "") val notes: String = "",
    @ColumnInfo(defaultValue = "0") val isPrivate: Boolean = false,
    val startYear: Int? = null,
    val startMonth: Int? = null,
    val startDay: Int? = null,
    val endYear: Int? = null,
    val endMonth: Int? = null,
    val endDay: Int? = null,
    val updatedAt: Long? = null,
    val progressIncreasedAt: Long? = null
)
