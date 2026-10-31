package app.lazydex.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_links",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["mediaId", "kind", "providerId"], unique = true),
        Index(value = ["mediaId"]),
        Index(value = ["providerId", "remoteId"])
    ]
)
data class MediaLinkEntity(
    @PrimaryKey val id: String,
    val mediaId: String,
    val kind: String,
    val providerId: String,
    val remoteId: String?,
    val url: String?,
    @ColumnInfo(defaultValue = "LINKED") val state: String = "LINKED",
    val lastSyncedAt: Long? = null,
    @ColumnInfo(defaultValue = "{}") val syncedSnapshot: String = "{}",
    val createdAt: Long,
    val updatedAt: Long
)

