package app.lazydex.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.DriverManager

class MigrationV5Test {

    @Test
    fun `MIGRATION_4_5 has correct version range`() {
        assertEquals(4, MIGRATION_4_5.startVersion)
        assertEquals(5, MIGRATION_4_5.endVersion)
    }

    @Test
    fun `MIGRATION_4_5 migrates V4 schema to V5 and preserves data`() {
        val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        val statement = connection.createStatement()

        // 1. Create V4 media_items table
        statement.execute(
            """
            CREATE TABLE `media_items` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `category` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `alternativeTitles` TEXT NOT NULL DEFAULT '[]',
                `sourceUrl` TEXT,
                `coverImagePath` TEXT NOT NULL DEFAULT '',
                `coverImageUrl` TEXT,
                `currentProgress` INTEGER NOT NULL,
                `totalItems` INTEGER,
                `userStatus` TEXT NOT NULL,
                `rating` REAL,
                `notes` TEXT NOT NULL DEFAULT '',
                `genres` TEXT NOT NULL DEFAULT '[]',
                `tags` TEXT NOT NULL DEFAULT '[]',
                `author` TEXT NOT NULL DEFAULT '',
                `description` TEXT NOT NULL DEFAULT '',
                `startDate` INTEGER,
                `endDate` INTEGER,
                `lastUpdated` INTEGER NOT NULL,
                `dateAdded` INTEGER NOT NULL,
                `extraData` TEXT NOT NULL DEFAULT '{}',
                `isDeleted` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        // Seed V4 sample rows
        statement.execute(
            """
            INSERT INTO `media_items` VALUES 
            ('item_1', 'NOVEL', 'Lord of Mysteries', '["LOTM"]', 'https://royalroad.com/1', '', NULL, 420, 1432, 'IN_PROGRESS', 9.5, 'great', '["Fantasy"]', '["Steampunk"]', 'Cuttlefish', 'Desc', 1000, NULL, 5000, 2000, '{}', 0),
            ('item_2', 'MANGA', 'One Piece', '[]', NULL, '', NULL, 1050, NULL, 'IN_PROGRESS', 9.0, '', '[]', '[]', 'Oda', '', NULL, NULL, 6000, 3000, '{}', 0),
            ('item_3', 'UNKNOWN_MEDIUM', 'Random Book', '[]', NULL, '', NULL, 10, 20, 'PLANNING', NULL, '', '[]', '[]', '', '', NULL, NULL, 7000, 4000, '{}', 1)
            """.trimIndent()
        )

        // 2. Run MIGRATION_4_5 via SupportSQLiteDatabase proxy
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

        MIGRATION_4_5.migrate(dbProxy)

        // 3. Verify categories seeded
        val rsCategories = statement.executeQuery("SELECT COUNT(*) FROM categories")
        rsCategories.next()
        val catCount = rsCategories.getInt(1)
        assertEquals(16, catCount)

        // Check novel and its subtypes
        val rsNovel = statement.executeQuery("SELECT name, hasCharEstimate, hasAudioSpeed FROM categories WHERE id = 'cat_novel'")
        assertTrue(rsNovel.next())
        assertEquals("Novel", rsNovel.getString("name"))
        assertEquals(1, rsNovel.getInt("hasCharEstimate"))
        assertEquals(1, rsNovel.getInt("hasAudioSpeed"))

        // 4. Verify media_items data migrated properly
        val rsItem1 = statement.executeQuery("SELECT categoryId, progress, progressTotal, deletedAt FROM media_items WHERE id = 'item_1'")
        assertTrue(rsItem1.next())
        assertEquals("cat_novel", rsItem1.getString("categoryId"))
        assertEquals(420.0, rsItem1.getDouble("progress"), 0.001)
        assertEquals(1432.0, rsItem1.getDouble("progressTotal"), 0.001)
        assertTrue(rsItem1.getObject("deletedAt") == null)

        // Verify unknown category fallback to cat_uncategorized and tombstone
        val rsItem3 = statement.executeQuery("SELECT categoryId, deletedAt FROM media_items WHERE id = 'item_3'")
        assertTrue(rsItem3.next())
        assertEquals("cat_uncategorized", rsItem3.getString("categoryId"))
        assertEquals(7000L, rsItem3.getLong("deletedAt")) // lastUpdated was 7000

        // 5. Verify active_media_items view hides deleted items
        val rsActive = statement.executeQuery("SELECT COUNT(*) FROM active_media_items")
        rsActive.next()
        assertEquals(2, rsActive.getInt(1)) // item_1 and item_2, item_3 is deleted

        // 6. Verify legacy sourceUrl was migrated to media_links
        val rsLinks = statement.executeQuery("SELECT mediaId, url, kind, providerId FROM media_links WHERE mediaId = 'item_1'")
        assertTrue(rsLinks.next())
        assertEquals("https://royalroad.com/1", rsLinks.getString("url"))
        assertEquals("SOURCE", rsLinks.getString("kind"))
        assertEquals("manual", rsLinks.getString("providerId"))

        // 7. Verify activity created
        val rsAct = statement.executeQuery("SELECT COUNT(*) FROM activity WHERE type = 'CREATED' AND counted = 0")
        rsAct.next()
        assertEquals(3, rsAct.getInt(1))

        // 8. Verify app_settings defaults
        val rsSettings = statement.executeQuery("SELECT value FROM app_settings WHERE key = 'rate.READ.WORDS'")
        assertTrue(rsSettings.next())
        assertEquals("240", rsSettings.getString("value"))

        // 9. Verify depth limit trigger: inserting a child under a subtype should fail
        try {
            statement.execute(
                """
                INSERT INTO categories (id, parentId, name, sortOrder, createdAt, updatedAt)
                VALUES ('typ_too_deep', 'typ_novel_cn', 'Too Deep', 0, 0, 0)
                """.trimIndent()
            )
            fail("Expected trigger trg_categories_depth_insert to abort depth > 2")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("categories: max depth is 2") == true)
        }

        // 10. Verify sibling unique name constraint trigger
        try {
            statement.execute(
                """
                INSERT INTO categories (id, parentId, name, sortOrder, createdAt, updatedAt)
                VALUES ('typ_dup', 'cat_novel', 'web novel (cn)', 0, 0, 0)
                """.trimIndent()
            )
            fail("Expected unique sibling name trigger to reject duplicate case-insensitive name")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("categories: duplicate sibling name") == true)
        }

        // 11. Verify index list on categories has ONLY index_categories_parentId and index_categories_sortOrder (No unexpected indexes for Room)
        val rsIndices = statement.executeQuery("PRAGMA index_list(categories)")
        val foundIndices = mutableListOf<String>()
        while (rsIndices.next()) {
            val name = rsIndices.getString("name")
            if (!name.startsWith("sqlite_")) {
                foundIndices.add(name)
            }
        }
        assertEquals(
            listOf("index_categories_sortOrder", "index_categories_parentId").sorted(),
            foundIndices.sorted()
        )

        // 12. Verify sortOrder defaultValue is null (matches Room expected defaultValue='undefined')
        val rsColumns = statement.executeQuery("PRAGMA table_info(categories)")
        var sortOrderDflt: String? = null
        while (rsColumns.next()) {
            if (rsColumns.getString("name") == "sortOrder") {
                sortOrderDflt = rsColumns.getString("dflt_value")
            }
        }
        assertEquals(null, sortOrderDflt)

        connection.close()
    }
}

