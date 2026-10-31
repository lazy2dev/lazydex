package app.lazydex.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["createdAt"])
    ]
)
data class SyncOutboxEntity(
    @PrimaryKey val id: String,
    val mediaId: String,
    val action: String,
    val payload: String,
    val createdAt: Long
)

