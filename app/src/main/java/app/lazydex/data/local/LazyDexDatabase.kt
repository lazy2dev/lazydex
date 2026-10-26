package app.lazydex.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.lazydex.data.local.converter.Converters
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.entity.MediaItemEntity

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media_items ADD COLUMN genres TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE media_items ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE media_items ADD COLUMN author TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE media_items ADD COLUMN description TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE media_items ADD COLUMN startDate INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE media_items ADD COLUMN endDate INTEGER DEFAULT NULL")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media_items ADD COLUMN extraData TEXT NOT NULL DEFAULT '{}'")
        db.execSQL("ALTER TABLE media_items ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_media_items_isDeleted ON media_items(isDeleted)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `extension_repos` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `baseUrl` TEXT NOT NULL,
                `manifestUrl` TEXT NOT NULL,
                `ecosystem` TEXT NOT NULL,
                `extensionCount` INTEGER NOT NULL,
                `lastSynced` INTEGER NOT NULL,
                `syncStatus` TEXT NOT NULL,
                `syncError` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_extension_repos_manifestUrl` ON `extension_repos` (`manifestUrl`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `installed_extensions` (
                `pkgName` TEXT NOT NULL,
                `repoId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `versionName` TEXT NOT NULL,
                `versionCode` INTEGER NOT NULL,
                `lang` TEXT NOT NULL,
                `ecosystem` TEXT NOT NULL,
                `runtimeType` TEXT NOT NULL,
                `apkOrArtifactPath` TEXT NOT NULL,
                `iconUrl` TEXT,
                `entryClassOrFile` TEXT,
                `categoryOverride` TEXT,
                `installedAt` INTEGER NOT NULL,
                `hasUpdate` INTEGER NOT NULL,
                `latestVersionName` TEXT,
                `latestVersionCode` INTEGER,
                `downloadUrl` TEXT,
                PRIMARY KEY(`pkgName`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_installed_extensions_repoId` ON `installed_extensions` (`repoId`)")
    }
}

@Database(
    entities = [
        MediaItemEntity::class,
        app.lazydex.data.local.entity.ExtensionRepoEntity::class,
        app.lazydex.data.local.entity.InstalledExtensionEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LazyDexDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun extensionRepoDao(): app.lazydex.data.local.dao.ExtensionRepoDao
    abstract fun installedExtensionDao(): app.lazydex.data.local.dao.InstalledExtensionDao
}
