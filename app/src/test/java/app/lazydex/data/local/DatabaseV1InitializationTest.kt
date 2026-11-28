package app.lazydex.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.DriverManager

class DatabaseV1InitializationTest {

    @Test
    fun `setupInitialTriggersAndSeeds seeds default app settings`() {
        val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        val statement = connection.createStatement()

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

        // Run setupInitialTriggersAndSeeds
        dbProxy.setupInitialTriggersAndSeeds()

        // Verify app settings seeded
        val rsSetting = statement.executeQuery("SELECT value FROM app_settings WHERE `key` = 'rate.READ.WORDS'")
        assertTrue(rsSetting.next())
        assertEquals("240", rsSetting.getString("value"))

        statement.close()
        connection.close()
    }

    @Test
    fun `foreign key cascade deletes media_links and activity on DELETE but preserves them on UPDATE`() {
        val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        val statement = connection.createStatement()
        statement.execute("PRAGMA foreign_keys = ON")

        statement.execute("CREATE TABLE media_items (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL)")
        statement.execute(
            """
            CREATE TABLE media_links (
                id TEXT NOT NULL PRIMARY KEY,
                mediaId TEXT NOT NULL,
                FOREIGN KEY(mediaId) REFERENCES media_items(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        statement.execute(
            """
            CREATE TABLE activity (
                id TEXT NOT NULL PRIMARY KEY,
                mediaId TEXT NOT NULL,
                FOREIGN KEY(mediaId) REFERENCES media_items(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        statement.execute("INSERT INTO media_items (id, title) VALUES ('m1', 'Original')")
        statement.execute("INSERT INTO media_links (id, mediaId) VALUES ('l1', 'm1')")
        statement.execute("INSERT INTO activity (id, mediaId) VALUES ('a1', 'm1')")

        // Non-destructive upsert conflict path (UPDATE) preserves child rows
        statement.execute("UPDATE media_items SET title = 'Merged' WHERE id = 'm1'")
        val linkAfterUpdate = statement.executeQuery("SELECT COUNT(*) FROM media_links WHERE mediaId = 'm1'")
        assertTrue(linkAfterUpdate.next())
        assertEquals(1, linkAfterUpdate.getInt(1))

        val activityAfterUpdate = statement.executeQuery("SELECT COUNT(*) FROM activity WHERE mediaId = 'm1'")
        assertTrue(activityAfterUpdate.next())
        assertEquals(1, activityAfterUpdate.getInt(1))

        // Destructive DELETE FROM media_items cascades and wipes child rows
        statement.execute("DELETE FROM media_items")
        val linkAfterDelete = statement.executeQuery("SELECT COUNT(*) FROM media_links")
        assertTrue(linkAfterDelete.next())
        assertEquals(0, linkAfterDelete.getInt(1))

        val activityAfterDelete = statement.executeQuery("SELECT COUNT(*) FROM activity")
        assertTrue(activityAfterDelete.next())
        assertEquals(0, activityAfterDelete.getInt(1))

        statement.close()
        connection.close()
    }
}
