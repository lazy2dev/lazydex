package app.lazydex.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.DriverManager
import java.sql.SQLException

class MigrationV6Test {

    @Test
    fun `MIGRATION_5_6 has correct version range`() {
        assertEquals(5, MIGRATION_5_6.startVersion)
        assertEquals(6, MIGRATION_5_6.endVersion)
    }

    @Test
    fun `MIGRATION_5_6 migrates V5 schema to V6 and preserves data losslessly`() {
        val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        val statement = connection.createStatement()

        // 1. Create V5 categories table
        statement.execute(
            """
            CREATE TABLE `categories` (
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

        // Seed V5 categories
        statement.execute(
            """
            INSERT INTO `categories` VALUES
            ('cat_novel', NULL, 'Novel', 'book', 'Chapter', 'Volume', 'Read', 1, 1, 1, 'WORDS', 0, 1000, 1000, NULL),
            ('cat_manga', NULL, 'Manga', 'image', 'Chapter', 'Volume', 'Read', 0, 0, 0, 'WORDS', 1, 1000, 1000, NULL)
            """.trimIndent()
        )

        // 2. Create V5 media_items table
        statement.execute(
            """
            CREATE TABLE `media_items` (
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

        // Seed V5 media_items:
        // item_1: 4.5 rating, epoch startDate (2024-01-01 UTC = 1704067200000L), endDate (2024-06-01 UTC = 1717200000000L)
        // item_2: null rating, null dates
        // item_3: 5.0 rating, deleted
        statement.execute(
            """
            INSERT INTO `media_items` VALUES
            ('item_1', 'cat_novel', 'Overlord', '[]', 'Bone daddy', 'Maruyama', '', NULL, 'IN_PROGRESS', 14.0, 16.0, 1.0, NULL, NULL, 5000, 'WORDS', 1.0, 4.5, 'great', '["Action"]', '["Isekai"]', 1704067200000, 1717200000000, 1000, 2000, NULL),
            ('item_2', 'cat_manga', 'Berserk', '[]', '', 'Miura', '', NULL, 'IN_PROGRESS', 300.0, NULL, 0.0, NULL, NULL, NULL, NULL, 1.0, NULL, '', '[]', '[]', NULL, NULL, 3000, 4000, NULL),
            ('item_3', 'cat_novel', 'Old Novel', '[]', '', 'Author', '', NULL, 'DROPPED', 5.0, 20.0, 0.0, NULL, NULL, NULL, NULL, 1.0, 5.0, '', '[]', '[]', 1735689600000, NULL, 5000, 6000, 7000)
            """.trimIndent()
        )

        // 3. Create V5 media_parts table & seed 3 parts for item_1
        statement.execute(
            """
            CREATE TABLE `media_parts` (
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
        statement.execute(
            """
            INSERT INTO `media_parts` VALUES
            ('part_1', 'item_1', 'Volume 1', 1, 4.0, 1000, 1000),
            ('part_2', 'item_1', 'Volume 2', 2, 4.0, 1000, 1000),
            ('part_3', 'item_1', 'Volume 3', 3, 4.0, 1000, 1000)
            """.trimIndent()
        )

        // 4. Create V5 media_links table & seed link for item_1
        statement.execute(
            """
            CREATE TABLE `media_links` (
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
        statement.execute(
            """
            INSERT INTO `media_links` VALUES
            ('link_1', 'item_1', 'TRACKER', 'anilist', '101759', NULL, 'LINKED', 1000, '{}', 1000, 1000)
            """.trimIndent()
        )

        // 5. Create V5 active_media_items view
        statement.execute("CREATE VIEW `active_media_items` AS SELECT * FROM media_items WHERE deletedAt IS NULL")

        // 6. Execute MIGRATION_5_6 via SupportSQLiteDatabase proxy
        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                val sql = args[0].toString()
                statement.execute(sql)
            }
            null
        } as SupportSQLiteDatabase

        MIGRATION_5_6.migrate(dbProxy)

        // 7. Verify categories table: hasParts renamed to hasSecondaryProgress
        val rsNovelCat = statement.executeQuery("SELECT hasSecondaryProgress FROM categories WHERE id = 'cat_novel'")
        assertTrue(rsNovelCat.next())
        assertEquals(1, rsNovelCat.getInt("hasSecondaryProgress"))

        val rsMangaCat = statement.executeQuery("SELECT hasSecondaryProgress FROM categories WHERE id = 'cat_manga'")
        assertTrue(rsMangaCat.next())
        assertEquals(0, rsMangaCat.getInt("hasSecondaryProgress"))

        try {
            statement.executeQuery("SELECT hasParts FROM categories")
            fail("Expected hasParts column to be removed / renamed")
        } catch (_: SQLException) {
            // Expected
        }

        // 8. Verify media_links table: remoteMediaListId and syncStatus columns added
        val rsLinks = statement.executeQuery("SELECT remoteMediaListId, syncStatus FROM media_links WHERE id = 'link_1'")
        assertTrue(rsLinks.next())
        assertNull(rsLinks.getString("remoteMediaListId"))
        assertEquals("SYNCED", rsLinks.getString("syncStatus"))

        // 9. Verify media_items table:
        // - rating converted losslessly: 4.5 -> 90, null -> null, 5.0 -> 100
        // - dates converted losslessly via strftime: 1704067200000 -> 2024-01-01
        // - secondaryProgressTotal aggregated from media_parts count (3)
        // - new columns: scoreRaw, titleRomaji, titleEnglish, titleNative, isPrivate, isAdult, repeat
        val rsItem1 = statement.executeQuery(
            """
            SELECT scoreRaw, startYear, startMonth, startDay, endYear, endMonth, endDay,
                   secondaryProgressTotal, isPrivate, isAdult, repeat, titleEnglish, titleRomaji, titleNative
            FROM media_items WHERE id = 'item_1'
            """.trimIndent()
        )
        assertTrue(rsItem1.next())
        assertEquals(90, rsItem1.getInt("scoreRaw"))
        assertEquals(2024, rsItem1.getInt("startYear"))
        assertEquals(1, rsItem1.getInt("startMonth"))
        assertEquals(1, rsItem1.getInt("startDay"))
        assertEquals(2024, rsItem1.getInt("endYear"))
        assertEquals(6, rsItem1.getInt("endMonth"))
        assertEquals(1, rsItem1.getInt("endDay"))
        assertEquals(3.0, rsItem1.getDouble("secondaryProgressTotal"), 0.001)
        assertEquals(0, rsItem1.getInt("isPrivate"))
        assertEquals(0, rsItem1.getInt("isAdult"))
        assertEquals(0, rsItem1.getInt("repeat"))
        assertNull(rsItem1.getString("titleEnglish"))
        assertNull(rsItem1.getString("titleRomaji"))
        assertNull(rsItem1.getString("titleNative"))

        val rsItem2 = statement.executeQuery("SELECT scoreRaw, startYear, endYear FROM media_items WHERE id = 'item_2'")
        assertTrue(rsItem2.next())
        assertNull(rsItem2.getObject("scoreRaw"))
        assertNull(rsItem2.getObject("startYear"))
        assertNull(rsItem2.getObject("endYear"))

        val rsItem3 = statement.executeQuery("SELECT scoreRaw, startYear, deletedAt FROM media_items WHERE id = 'item_3'")
        assertTrue(rsItem3.next())
        assertEquals(100, rsItem3.getInt("scoreRaw"))
        assertEquals(2025, rsItem3.getInt("startYear"))
        assertEquals(7000L, rsItem3.getLong("deletedAt"))

        // 10. Verify media_parts table was dropped
        val rsPartsTable = statement.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='media_parts'")
        assertTrue(rsPartsTable.next())
        assertEquals(0, rsPartsTable.getInt(1))

        // 11. Verify active_media_items view recreated and functional
        val rsView = statement.executeQuery("SELECT sql FROM sqlite_master WHERE type='view' AND name='active_media_items'")
        assertTrue(rsView.next())
        assertEquals("CREATE VIEW `active_media_items` AS SELECT * FROM media_items WHERE deletedAt IS NULL", rsView.getString("sql"))

        val rsActive = statement.executeQuery("SELECT count(*) FROM active_media_items")
        assertTrue(rsActive.next())
        assertEquals(2, rsActive.getInt(1)) // item_1 and item_2, item_3 deleted

        val rsActiveItem1 = statement.executeQuery("SELECT scoreRaw, secondaryProgressTotal FROM active_media_items WHERE id = 'item_1'")
        assertTrue(rsActiveItem1.next())
        assertEquals(90, rsActiveItem1.getInt("scoreRaw"))
        assertEquals(3.0, rsActiveItem1.getDouble("secondaryProgressTotal"), 0.001)

        connection.close()
    }
}

