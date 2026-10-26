package app.lazydex.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class MigrationTest {

    @Test
    fun `MIGRATION_3_4 has correct version range and creates tables and indices`() {
        assertEquals(3, MIGRATION_3_4.startVersion)
        assertEquals(4, MIGRATION_3_4.endVersion)

        val executedSql = mutableListOf<String>()
        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0].toString())
            }
            null
        } as SupportSQLiteDatabase

        MIGRATION_3_4.migrate(dbProxy)

        assertTrue("Should create extension_repos table", executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `extension_repos`") })
        assertTrue("Should create manifestUrl index", executedSql.any { it.contains("index_extension_repos_manifestUrl") })
        assertTrue("Should create installed_extensions table", executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS `installed_extensions`") })
        assertTrue("Should create repoId index", executedSql.any { it.contains("index_installed_extensions_repoId") })
    }

    @Test
    fun `MIGRATION_3_4 SQL syntax is valid SQLite`() {
        // Test SQL syntax using SQLite JDBC in-memory connection
        val connection = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:")
        val statement = connection.createStatement()

        val executedSql = mutableListOf<String>()
        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                val sql = args[0].toString()
                statement.execute(sql)
                executedSql.add(sql)
            }
            null
        } as SupportSQLiteDatabase

        MIGRATION_3_4.migrate(dbProxy)

        // Verify tables and columns were created in SQLite
        val rsRepos = statement.executeQuery("PRAGMA table_info(extension_repos)")
        val repoCols = mutableListOf<String>()
        while (rsRepos.next()) {
            repoCols.add(rsRepos.getString("name"))
        }
        assertTrue(repoCols.contains("id"))
        assertTrue(repoCols.contains("manifestUrl"))
        assertTrue(repoCols.contains("ecosystem"))

        val rsExt = statement.executeQuery("PRAGMA table_info(installed_extensions)")
        val extCols = mutableListOf<String>()
        while (rsExt.next()) {
            extCols.add(rsExt.getString("name"))
        }
        assertTrue(extCols.contains("pkgName"))
        assertTrue(extCols.contains("repoId"))
        assertTrue(extCols.contains("runtimeType"))
        assertTrue(extCols.contains("apkOrArtifactPath"))

        connection.close()
    }
}
