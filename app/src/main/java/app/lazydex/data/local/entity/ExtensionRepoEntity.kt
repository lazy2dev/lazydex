package app.lazydex.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "extension_repos",
    indices = [
        Index(value = ["manifestUrl"], unique = true)
    ]
)
data class ExtensionRepoEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val manifestUrl: String,
    val ecosystem: String,
    val extensionCount: Int = 0,
    val lastSynced: Long = 0L,
    val syncStatus: String = "SUCCESS", // "SUCCESS", "ERROR", "SYNCING"
    val syncError: String? = null
)
