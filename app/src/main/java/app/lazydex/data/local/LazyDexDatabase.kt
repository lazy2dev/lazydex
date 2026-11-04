package app.lazydex.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import app.lazydex.data.local.converter.Converters
import app.lazydex.data.local.dao.ActivityDao
import app.lazydex.data.local.dao.AppSettingDao
import app.lazydex.data.local.dao.CategoryDao
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.dao.MediaLinkDao
import app.lazydex.data.local.dao.SyncOutboxDao
import app.lazydex.data.local.entity.ActiveMediaItemView
import app.lazydex.data.local.entity.ActivityEntity
import app.lazydex.data.local.entity.AppSettingEntity
import app.lazydex.data.local.entity.CategoryEntity
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.data.local.entity.MediaLinkEntity
import app.lazydex.data.local.entity.SyncOutboxEntity

fun SupportSQLiteDatabase.setupInitialTriggersAndSeeds() {
    val now = System.currentTimeMillis()
    execSQL("DROP INDEX IF EXISTS `index_categories_unique_name`")
    execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `trg_categories_unique_name_insert`
        BEFORE INSERT ON `categories`
        WHEN EXISTS (
            SELECT 1 FROM `categories`
            WHERE `id` <> NEW.`id`
              AND COALESCE(`parentId`, '') = COALESCE(NEW.`parentId`, '')
              AND lower(`name`) = lower(NEW.`name`)
              AND `deletedAt` IS NULL
        )
        BEGIN
            SELECT RAISE(ABORT, 'categories: duplicate sibling name');
        END
        """.trimIndent()
    )
    execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `trg_categories_unique_name_update`
        BEFORE UPDATE OF `name`, `parentId`, `deletedAt` ON `categories`
        WHEN NEW.`deletedAt` IS NULL AND EXISTS (
            SELECT 1 FROM `categories`
            WHERE `id` <> NEW.`id`
              AND COALESCE(`parentId`, '') = COALESCE(NEW.`parentId`, '')
              AND lower(`name`) = lower(NEW.`name`)
              AND `deletedAt` IS NULL
        )
        BEGIN
            SELECT RAISE(ABORT, 'categories: duplicate sibling name');
        END
        """.trimIndent()
    )
    execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `trg_categories_depth_insert`
        BEFORE INSERT ON `categories`
        WHEN NEW.`parentId` IS NOT NULL
         AND (SELECT `parentId` FROM `categories` WHERE `id` = NEW.`parentId`) IS NOT NULL
        BEGIN
            SELECT RAISE(ABORT, 'categories: max depth is 2');
        END
        """.trimIndent()
    )
    execSQL(
        """
        CREATE TRIGGER IF NOT EXISTS `trg_categories_depth_update`
        BEFORE UPDATE OF `parentId` ON `categories`
        WHEN NEW.`parentId` IS NOT NULL
         AND (
              (SELECT `parentId` FROM `categories` WHERE `id` = NEW.`parentId`) IS NOT NULL
              OR EXISTS (SELECT 1 FROM `categories` WHERE `parentId` = NEW.`id`)
         )
        BEGIN
            SELECT RAISE(ABORT, 'categories: max depth is 2');
        END
        """.trimIndent()
    )
    val seedCategoriesSql = """
        INSERT OR IGNORE INTO `categories` (`id`, `parentId`, `name`, `icon`, `unitName`, `secondaryUnitName`, `actionVerb`, `hasSecondaryProgress`, `hasCharEstimate`, `hasAudioSpeed`, `lengthUnit`, `sortOrder`, `createdAt`, `updatedAt`, `deletedAt`) VALUES
        ('cat_uncategorized', NULL, 'Uncategorized', 'folder', 'Item', '', 'Consume', 0, 0, 0, 'WORDS', 999, $now, $now, NULL),
        ('cat_novel', NULL, 'Novel', 'book', 'Chapter', '', 'Read', 0, 1, 1, 'WORDS', 0, $now, $now, NULL),
        ('typ_novel_cn', 'cat_novel', 'Web Novel (CN)', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'CHARS', 0, $now, $now, NULL),
        ('typ_novel_kr', 'cat_novel', 'Web Novel (KR)', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'CHARS', 1, $now, $now, NULL),
        ('typ_novel_ln', 'cat_novel', 'Light Novel', NULL, NULL, 'Volume', NULL, 1, NULL, NULL, 'WORDS', 2, $now, $now, NULL),
        ('cat_manga', NULL, 'Manga', 'image', 'Chapter', '', 'Read', 0, 0, 0, 'WORDS', 1, $now, $now, NULL),
        ('typ_manga_jp', 'cat_manga', 'Manga (JP)', NULL, NULL, 'Volume', NULL, 1, NULL, NULL, NULL, 0, $now, $now, NULL),
        ('typ_manhwa', 'cat_manga', 'Manhwa (KR)', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, $now, $now, NULL),
        ('typ_manhua', 'cat_manga', 'Manhua (CN)', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2, $now, $now, NULL),
        ('cat_anime', NULL, 'Anime', 'tv', 'Episode', '', 'Watch', 0, 0, 0, 'WORDS', 2, $now, $now, NULL),
        ('typ_anime_series', 'cat_anime', 'Anime Series', NULL, NULL, 'Season', NULL, 1, NULL, NULL, NULL, 0, $now, $now, NULL),
        ('typ_anime_movie', 'cat_anime', 'Anime Movie', NULL, 'Minute', NULL, NULL, NULL, NULL, NULL, NULL, 1, $now, $now, NULL),
        ('typ_anime_ova', 'cat_anime', 'OVA / Special', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 2, $now, $now, NULL),
        ('cat_movie', NULL, 'Movie', 'film', 'Minute', '', 'Watch', 0, 0, 0, 'WORDS', 3, $now, $now, NULL),
        ('cat_tv', NULL, 'TV Series', 'tv', 'Episode', 'Season', 'Watch', 1, 0, 0, 'WORDS', 4, $now, $now, NULL),
        ('cat_game', NULL, 'Game', 'gamepad', 'Hour', '', 'Play', 0, 0, 0, 'WORDS', 5, $now, $now, NULL)
    """.trimIndent()
    execSQL(seedCategoriesSql)
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
        CategoryEntity::class,
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
    abstract fun categoryDao(): CategoryDao
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun mediaLinkDao(): MediaLinkDao
    abstract fun activityDao(): ActivityDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun extensionRepoDao(): app.lazydex.data.local.dao.ExtensionRepoDao
    abstract fun installedExtensionDao(): app.lazydex.data.local.dao.InstalledExtensionDao
}
