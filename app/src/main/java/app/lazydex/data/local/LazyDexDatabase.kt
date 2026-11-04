package app.lazydex.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import app.lazydex.data.local.converter.Converters
import app.lazydex.data.local.dao.ActivityDao
import app.lazydex.data.local.dao.AppSettingDao
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.dao.MediaLinkDao
import app.lazydex.data.local.dao.SyncOutboxDao
import app.lazydex.data.local.entity.ActiveMediaItemView
import app.lazydex.data.local.entity.ActivityEntity
import app.lazydex.data.local.entity.AppSettingEntity
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.data.local.entity.MediaLinkEntity
import app.lazydex.data.local.entity.SyncOutboxEntity

fun SupportSQLiteDatabase.setupInitialTriggersAndSeeds() {
    execSQL(
        """
        INSERT OR IGNORE INTO `app_settings` (`key`, `value`) VALUES
        ('rate.READ.WORDS', '240'),
        ('rate.READ.CHARS', '500'),
        ('rate.LISTEN.WORDS', '150'),
        ('rate.LISTEN.CHARS', '250'),
        ('activity.coalesceSeconds', '30'),
        ('tombstone.retainDays', '30')
        """.trimIndent()
    )
}

@Database(
    entities = [
        MediaItemEntity::class,
        MediaLinkEntity::class,
        ActivityEntity::class,
        SyncOutboxEntity::class,
        AppSettingEntity::class,
        app.lazydex.data.local.entity.ExtensionRepoEntity::class,
        app.lazydex.data.local.entity.InstalledExtensionEntity::class
    ],
    views = [
        ActiveMediaItemView::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LazyDexDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun mediaLinkDao(): MediaLinkDao
    abstract fun activityDao(): ActivityDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun extensionRepoDao(): app.lazydex.data.local.dao.ExtensionRepoDao
    abstract fun installedExtensionDao(): app.lazydex.data.local.dao.InstalledExtensionDao
}
