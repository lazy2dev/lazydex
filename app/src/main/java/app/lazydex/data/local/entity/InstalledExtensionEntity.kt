package app.lazydex.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "installed_extensions",
    indices = [
        Index(value = ["repoId"])
    ]
)
data class InstalledExtensionEntity(
    @PrimaryKey val pkgName: String,
    val repoId: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val lang: String,
    val ecosystem: String,
    val runtimeType: String,
    val apkOrArtifactPath: String,
    val iconUrl: String? = null,
    val entryClassOrFile: String? = null,
    val categoryOverride: String? = null,
    val installedAt: Long = 0L,
    val hasUpdate: Boolean = false,
    val latestVersionName: String? = null,
    val latestVersionCode: Long? = null,
    val downloadUrl: String? = null
)
