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
}
