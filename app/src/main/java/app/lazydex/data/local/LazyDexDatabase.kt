package app.lazydex.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.lazydex.data.local.converter.Converters
import app.lazydex.data.local.dao.ActivityDao
import app.lazydex.data.local.dao.AppSettingDao
import app.lazydex.data.local.dao.CategoryDao
import app.lazydex.data.local.dao.MediaItemDao
import app.lazydex.data.local.dao.MediaLinkDao
import app.lazydex.data.local.dao.MediaPartDao
import app.lazydex.data.local.dao.SyncOutboxDao
import app.lazydex.data.local.entity.ActiveMediaItemView
import app.lazydex.data.local.entity.ActivityEntity
import app.lazydex.data.local.entity.AppSettingEntity
import app.lazydex.data.local.entity.CategoryEntity
import app.lazydex.data.local.entity.MediaItemEntity
import app.lazydex.data.local.entity.MediaLinkEntity
import app.lazydex.data.local.entity.MediaPartEntity
import app.lazydex.data.local.entity.SyncOutboxEntity

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

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA defer_foreign_keys = ON;")

        // 1. Create categories table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `categories` (
                `id` TEXT NOT NULL,
                `parentId` TEXT,
                `name` TEXT NOT NULL,
                `icon` TEXT,
                `unitName` TEXT,
                `secondaryUnitName` TEXT,
                `actionVerb` TEXT,
                `hasParts` INTEGER,
                `hasCharEstimate` INTEGER,
                `hasAudioSpeed` INTEGER,
                `lengthUnit` TEXT,
                `sortOrder` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deletedAt` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`parentId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_parentId` ON `categories` (`parentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_sortOrder` ON `categories` (`sortOrder`)")
        db.execSQL("DROP INDEX IF EXISTS `index_categories_unique_name`")

        db.execSQL(
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
        db.execSQL(
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
        db.execSQL(
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
        db.execSQL(
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

        // 2. Seed initial categories
        val now = System.currentTimeMillis()
        val seedCategoriesSql = """
            INSERT OR IGNORE INTO `categories` (`id`, `parentId`, `name`, `icon`, `unitName`, `secondaryUnitName`, `actionVerb`, `hasParts`, `hasCharEstimate`, `hasAudioSpeed`, `lengthUnit`, `sortOrder`, `createdAt`, `updatedAt`, `deletedAt`) VALUES
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
        db.execSQL(seedCategoriesSql)

        // 3. Stash legacy source URLs before table drop
        db.execSQL("CREATE TEMP TABLE IF NOT EXISTS `legacy_source` AS SELECT `id`, `sourceUrl`, `dateAdded`, `lastUpdated` FROM `media_items` WHERE `sourceUrl` IS NOT NULL AND trim(`sourceUrl`) <> ''")

        // 4. Create new media_items table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `media_items_new` (
                `id` TEXT NOT NULL,
                `categoryId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `alternativeTitles` TEXT NOT NULL DEFAULT '[]',
                `description` TEXT NOT NULL DEFAULT '',
                `author` TEXT NOT NULL DEFAULT '',
                `coverImagePath` TEXT NOT NULL DEFAULT '',
                `coverImageUrl` TEXT,
                `userStatus` TEXT NOT NULL,
                `progress` REAL NOT NULL DEFAULT 0,
                `progressTotal` REAL,
                `secondaryProgress` REAL NOT NULL DEFAULT 0,
                `secondaryProgressTotal` REAL,
                `actionOverride` TEXT,
                `avgChapterLength` INTEGER,
                `lengthUnitOverride` TEXT,
                `playbackSpeed` REAL NOT NULL DEFAULT 1.0,
                `rating` REAL,
                `notes` TEXT NOT NULL DEFAULT '',
                `genres` TEXT NOT NULL DEFAULT '[]',
                `tags` TEXT NOT NULL DEFAULT '[]',
                `startDate` INTEGER,
                `endDate` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `deletedAt` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )

        // 5. Copy data with defensive mapping
        db.execSQL(
            """
            INSERT INTO `media_items_new` (
                `id`, `categoryId`, `title`, `alternativeTitles`, `description`, `author`,
                `coverImagePath`, `coverImageUrl`, `userStatus`,
                `progress`, `progressTotal`, `secondaryProgress`, `secondaryProgressTotal`,
                `actionOverride`, `avgChapterLength`, `lengthUnitOverride`, `playbackSpeed`,
                `rating`, `notes`, `genres`, `tags`, `startDate`, `endDate`,
                `createdAt`, `updatedAt`, `deletedAt`
            )
            SELECT
                `id`,
                COALESCE(
                  CASE upper(trim(`category`))
                    WHEN 'NOVEL'        THEN 'cat_novel'
                    WHEN 'LIGHT_NOVEL'  THEN 'typ_novel_ln'
                    WHEN 'WEB_NOVEL'    THEN 'cat_novel'
                    WHEN 'MANGA'        THEN 'cat_manga'
                    WHEN 'MANHWA'       THEN 'typ_manhwa'
                    WHEN 'MANHUA'       THEN 'typ_manhua'
                    WHEN 'ANIME'        THEN 'cat_anime'
                    WHEN 'MOVIE'        THEN 'cat_movie'
                    WHEN 'TV'           THEN 'cat_tv'
                    WHEN 'TV_SERIES'    THEN 'cat_tv'
                    WHEN 'GAME'         THEN 'cat_game'
                  END,
                  'cat_uncategorized'
                ),
                `title`, COALESCE(`alternativeTitles`,'[]'), COALESCE(`description`,''), COALESCE(`author`,''),
                COALESCE(`coverImagePath`,''), `coverImageUrl`,
                CASE WHEN `userStatus` IN ('PLANNING','IN_PROGRESS','COMPLETED','ON_HOLD','DROPPED')
                     THEN `userStatus` ELSE 'PLANNING' END,
                COALESCE(`currentProgress`, 0), `totalItems`, 0, NULL,
                NULL, NULL, NULL, 1.0,
                `rating`, COALESCE(`notes`,''), COALESCE(`genres`,'[]'), COALESCE(`tags`,'[]'),
                `startDate`, `endDate`,
                `dateAdded`, `lastUpdated`,
                CASE WHEN `isDeleted` = 1 THEN COALESCE(`lastUpdated`, `dateAdded`) ELSE NULL END
            FROM `media_items`
            """.trimIndent()
        )

        // 6. Swap tables
        db.execSQL("DROP TABLE `media_items`")
        db.execSQL("ALTER TABLE `media_items_new` RENAME TO `media_items`")

        // 7. Rebuild indices on media_items
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_categoryId_userStatus` ON `media_items` (`categoryId`, `userStatus`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_updatedAt` ON `media_items` (`updatedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_deletedAt` ON `media_items` (`deletedAt`)")

        // 8. Create active_media_items view
        db.execSQL("CREATE VIEW IF NOT EXISTS `active_media_items` AS SELECT * FROM media_items WHERE deletedAt IS NULL")

        // 9. Create media_parts table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `media_parts` (
                `id` TEXT NOT NULL,
                `mediaId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                `totalUnits` REAL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`mediaId`) REFERENCES `media_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_media_parts_mediaId_sortOrder` ON `media_parts` (`mediaId`, `sortOrder`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_parts_mediaId` ON `media_parts` (`mediaId`)")

        // 10. Create media_links table and migrate legacy sources
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `media_links` (
                `id` TEXT NOT NULL,
                `mediaId` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `providerId` TEXT NOT NULL,
                `remoteId` TEXT,
                `url` TEXT,
                `state` TEXT NOT NULL DEFAULT 'LINKED',
                `lastSyncedAt` INTEGER,
                `syncedSnapshot` TEXT NOT NULL DEFAULT '{}',
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`mediaId`) REFERENCES `media_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_media_links_mediaId_kind_providerId` ON `media_links` (`mediaId`, `kind`, `providerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_links_mediaId` ON `media_links` (`mediaId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_links_providerId_remoteId` ON `media_links` (`providerId`, `remoteId`)")

        db.execSQL(
            """
            INSERT INTO `media_links` (`id`, `mediaId`, `kind`, `providerId`, `remoteId`, `url`, `state`, `syncedSnapshot`, `createdAt`, `updatedAt`)
            SELECT lower(hex(randomblob(16))), `id`, 'SOURCE', 'manual', NULL, `sourceUrl`, 'LINKED', '{}', `dateAdded`, `lastUpdated`
            FROM `legacy_source`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE IF EXISTS `legacy_source`")

        // 11. Create activity table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `activity` (
                `id` TEXT NOT NULL,
                `mediaId` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `oldValue` TEXT,
                `newValue` TEXT NOT NULL,
                `delta` REAL,
                `mode` TEXT,
                `speed` REAL,
                `lengthSnapshot` INTEGER,
                `lengthUnit` TEXT,
                `rateSnapshot` REAL,
                `counted` INTEGER NOT NULL DEFAULT 1,
                `timestamp` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`mediaId`) REFERENCES `media_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_mediaId_timestamp` ON `activity` (`mediaId`, `timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_activity_timestamp` ON `activity` (`timestamp`)")

        // 12. Create sync_outbox table (NO FK by design)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sync_outbox` (
                `id` TEXT NOT NULL,
                `mediaId` TEXT NOT NULL,
                `action` TEXT NOT NULL,
                `payload` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_createdAt` ON `sync_outbox` (`createdAt`)")

        // 13. Create app_settings table and seed defaults
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `app_settings` (
                `key` TEXT NOT NULL,
                `value` TEXT NOT NULL,
                PRIMARY KEY(`key`)
            )
            """.trimIndent()
        )
        db.execSQL(
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

        // 14. Activity entries for migrated items (counted = 0)
        db.execSQL(
            """
            INSERT INTO `activity` (`id`, `mediaId`, `type`, `newValue`, `counted`, `timestamp`)
            SELECT lower(hex(randomblob(16))), `id`, 'CREATED', 'migrated_v5', 0, `createdAt`
            FROM `media_items`
            """.trimIndent()
        )
    }
}

fun SupportSQLiteDatabase.setupV5TriggersAndSeeds() {
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
        INSERT OR IGNORE INTO `categories` (`id`, `parentId`, `name`, `icon`, `unitName`, `secondaryUnitName`, `actionVerb`, `hasParts`, `hasCharEstimate`, `hasAudioSpeed`, `lengthUnit`, `sortOrder`, `createdAt`, `updatedAt`, `deletedAt`) VALUES
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
        MediaPartEntity::class,
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
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LazyDexDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun mediaPartDao(): MediaPartDao
    abstract fun mediaLinkDao(): MediaLinkDao
    abstract fun activityDao(): ActivityDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun extensionRepoDao(): app.lazydex.data.local.dao.ExtensionRepoDao
    abstract fun installedExtensionDao(): app.lazydex.data.local.dao.InstalledExtensionDao
}
