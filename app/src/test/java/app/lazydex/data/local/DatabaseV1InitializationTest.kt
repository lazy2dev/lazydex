package app.lazydex.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.DriverManager
import java.sql.SQLException

class DatabaseV1InitializationTest {

    @Test
    fun `setupInitialTriggersAndSeeds seeds categories and enforces triggers`() {
        val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        val statement = connection.createStatement()

        // 1. Create categories and app_settings tables matching V1 schema
        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS `categories` (
                `id` TEXT NOT NULL,
                `parentId` TEXT,
                `name` TEXT NOT NULL,
                `icon` TEXT,
                `unitName` TEXT,
                `secondaryUnitName` TEXT,
                `actionVerb` TEXT,
                `hasSecondaryProgress` INTEGER,
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

        statement.execute(
            """
            CREATE TABLE IF NOT EXISTS `app_settings` (
                `key` TEXT NOT NULL,
                `value` TEXT NOT NULL,
                PRIMARY KEY(`key`)
            )
            """.trimIndent()
        )

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

        // 2. Run setupInitialTriggersAndSeeds
        dbProxy.setupInitialTriggersAndSeeds()

        // 3. Verify categories seeded (16 items)
        val rsCategories = statement.executeQuery("SELECT COUNT(*) FROM categories")
        rsCategories.next()
        val catCount = rsCategories.getInt(1)
        assertEquals(16, catCount)

        val rsNovel = statement.executeQuery("SELECT name, hasCharEstimate, hasAudioSpeed FROM categories WHERE id = 'cat_novel'")
        assertTrue(rsNovel.next())
        assertEquals("Novel", rsNovel.getString("name"))
        assertEquals(1, rsNovel.getInt("hasCharEstimate"))
        assertEquals(1, rsNovel.getInt("hasAudioSpeed"))

        // 4. Verify app settings seeded
        val rsSetting = statement.executeQuery("SELECT value FROM app_settings WHERE `key` = 'rate.READ.WORDS'")
        assertTrue(rsSetting.next())
        assertEquals("240", rsSetting.getString("value"))

        // 5. Test trigger: duplicate sibling name must fail
        try {
            statement.execute(
                """
                INSERT INTO categories (`id`, `parentId`, `name`, `sortOrder`, `createdAt`, `updatedAt`)
                VALUES ('cat_novel_dup', NULL, 'Novel', 99, 1000, 1000)
                """.trimIndent()
            )
            fail("Expected SQLException for duplicate sibling category name")
        } catch (e: SQLException) {
            assertTrue(e.message?.contains("duplicate sibling name") == true)
        }

        // 6. Test trigger: max depth > 2 must fail
        try {
            statement.execute(
                """
                INSERT INTO categories (`id`, `parentId`, `name`, `sortOrder`, `createdAt`, `updatedAt`)
                VALUES ('sub_sub_novel', 'typ_novel_cn', 'Deep Novel', 99, 1000, 1000)
                """.trimIndent()
            )
            fail("Expected SQLException for category depth exceeding 2")
        } catch (e: SQLException) {
            assertTrue(e.message?.contains("max depth is 2") == true)
        }

        statement.close()
        connection.close()
    }
}

