package app.lazydex.backup

import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.UserStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupProcessorV2Test {

    @Test
    fun serializeAndDeserialize_preservesTypeAndFormat() = runTest {
        val original = listOf(
            MediaItem(
                id = "item-1",
                type = "NOVEL",
                format = "WEB_NOVEL",
                countryOfOrigin = "KR",
                title = "Solo Leveling",
                currentProgress = 100,
                totalItems = 270,
                userStatus = UserStatus.CURRENT,
                author = "Chugong",
                description = "Shadow Monarch web novel",
                genres = listOf("Action", "Fantasy"),
                tags = listOf("OP MC", "System"),
                startDate = 1600000000000L,
                endDate = null,
                lastUpdated = 1700000000000L,
                dateAdded = 1600000000000L
            )
        )

        val serialized = BackupProcessor.serialize(original)
        val deserialized = BackupProcessor.deserialize(serialized)

        assertEquals(3, deserialized.schemaVersion)
        assertEquals(1, deserialized.items.size)
        val item = deserialized.items[0]
        assertEquals("NOVEL", item.type)
        assertEquals("WEB_NOVEL", item.format)
        assertEquals("KR", item.countryOfOrigin)
        assertEquals("Chugong", item.author)
        assertEquals("Shadow Monarch web novel", item.description)
        assertEquals(listOf("Action", "Fantasy"), item.genres)
        assertEquals(listOf("OP MC", "System"), item.tags)
        assertEquals(1600000000000L, item.startDate)
    }

    @Test
    fun mergeV1LegacyBackup_preservesLocalV2Metadata() = runTest {
        val localV2Item = MediaItem(
            id = "item-1",
            type = "NOVEL",
            format = "WEB_NOVEL",
            title = "Solo Leveling",
            sourceUrl = "https://example.com/sl",
            coverImagePath = "/covers/item-1",
            coverImageUrl = null,
            currentProgress = 50,
            totalItems = 100,
            userStatus = UserStatus.CURRENT,
            author = "Chugong",
            description = "Local Description",
            genres = listOf("Action"),
            tags = listOf("Monsters"),
            startDate = 1600000000000L,
            endDate = null,
            lastUpdated = 1650000000000L,
            dateAdded = 1600000000000L
        )

        val importedV1Item = MediaItem(
            id = "item-1",
            type = "NOVEL",
            format = "WEB_NOVEL",
            title = "Solo Leveling",
            sourceUrl = "https://example.com/sl",
            coverImagePath = "",
            coverImageUrl = null,
            currentProgress = 60,
            totalItems = 100,
            userStatus = UserStatus.CURRENT,
            author = "",
            description = "",
            genres = emptyList(),
            tags = emptyList(),
            startDate = null,
            endDate = null,
            lastUpdated = 1700000000000L, // Newer lastUpdated
            dateAdded = 1600000000000L
        )

        val mergeResult = BackupProcessor.merge(
            local = listOf(localV2Item),
            imported = listOf(importedV1Item),
            importedSchemaVersion = 1 // Legacy v1 import
        )

        assertEquals(1, mergeResult.mergedItems.size)
        val merged = mergeResult.mergedItems[0]

        // Newer progress from imported
        assertEquals(60, merged.currentProgress)
        // Local metadata preserved because imported was v1
        assertEquals("Chugong", merged.author)
        assertEquals("Local Description", merged.description)
        assertEquals(listOf("Action"), merged.genres)
        assertEquals(listOf("Monsters"), merged.tags)
        assertEquals(1600000000000L, merged.startDate)
    }

    @Test
    fun merge_forwardOnlyProgress_neverRegressesProgress() = runTest {
        val localItem = MediaItem(
            id = "item-1",
            type = "MANGA",
            format = "MANGA",
            title = "One Piece",
            currentProgress = 50,
            totalItems = 1100,
            userStatus = UserStatus.CURRENT,
            lastUpdated = 1600000000000L,
            dateAdded = 1500000000000L
        )

        val importedItem = MediaItem(
            id = "item-1",
            type = "MANGA",
            format = "MANGA",
            title = "One Piece",
            currentProgress = 40,
            totalItems = 1100,
            userStatus = UserStatus.CURRENT,
            lastUpdated = 1700000000000L,
            dateAdded = 1500000000000L
        )

        val result = BackupProcessor.merge(listOf(localItem), listOf(importedItem), 3)
        assertEquals(1, result.mergedItems.size)
        // Progress must stay at 50, never regress to 40!
        assertEquals(50, result.mergedItems[0].currentProgress)
    }

    @Test
    fun deserializeModernizedBackup_parsesAll308ItemsLosslessly() = runTest {
        val backupFile = java.io.File("C:/Users/lazyman/Downloads/backup_1793768941212.lazydex")
        if (!backupFile.exists()) return@runTest

        var jsonText = ""
        java.util.zip.ZipFile(backupFile).use { zip ->
            val entry = zip.getEntry("backup.json")
            jsonText = zip.getInputStream(entry).bufferedReader().readText()
        }

        val deserialized = BackupProcessor.deserialize(jsonText)
        assertEquals(308, deserialized.items.size)

        val first = deserialized.items[0]
        assertEquals("NOVEL", first.type)
        assertEquals(MediaType.NOVEL, first.mediaType)
        assertEquals(230, first.currentProgress)
        assertEquals(UserStatus.DROPPED, first.userStatus)
        assertEquals(1.0, first.rating ?: 0.0, 0.01)
    }

    @Test
    fun deserialize_futureSchemaVersion_succeedsWithoutError() = runTest {
        val futureJson = """
            {
                "schemaVersion": 99,
                "futureGlobalSetting": "enabled",
                "items": [
                    {
                        "id": "future-item-1",
                        "type": "NOVEL",
                        "format": "WEB_NOVEL",
                        "title": "Future Cultivator",
                        "currentProgress": 15,
                        "userStatus": "CURRENT",
                        "futureUnknownField": 12345
                    }
                ]
            }
        """.trimIndent()

        val deserialized = BackupProcessor.deserialize(futureJson)
        assertEquals(99, deserialized.schemaVersion)
        assertEquals(1, deserialized.items.size)
        assertEquals("Future Cultivator", deserialized.items[0].title)
    }

    @Test
    fun deserialize_missingSchemaVersion_defaultsGracefully() = runTest {
        val jsonNoVersion = """
            {
                "items": [
                    {
                        "id": "legacy-item-1",
                        "category": "NOVEL",
                        "categoryId": "typ_novel_cn",
                        "title": "Legacy Novel",
                        "currentProgress": 10,
                        "userStatus": "READING"
                    }
                ]
            }
        """.trimIndent()

        val deserialized = BackupProcessor.deserialize(jsonNoVersion)
        assertEquals(3, deserialized.schemaVersion)
        assertEquals(1, deserialized.items.size)
        val item = deserialized.items[0]
        assertEquals("Legacy Novel", item.title)
        assertEquals("NOVEL", item.type)
        assertEquals("WEB_NOVEL", item.format)
        assertEquals("CN", item.countryOfOrigin)
        assertEquals(UserStatus.CURRENT, item.userStatus)
    }

    @Test
    fun serializeAndDeserialize_preservesAllModernFieldsLosslessly() = runTest {
        val modernItem = MediaItem(
            id = "modern-1",
            type = "NOVEL",
            format = "LIGHT_NOVEL",
            countryOfOrigin = "JP",
            title = "Overlord",
            alternativeTitles = listOf("Overlord Romaji", "Overlord English"),
            coverImagePath = "/covers/modern-1",
            coverImageUrl = "https://example.com/cover.jpg",
            currentProgress = 14,
            totalItems = 18,
            progress = 14.5,
            progressTotal = 18.0,
            secondaryProgress = 2.0,
            secondaryProgressTotal = 4.0,
            userStatus = UserStatus.CURRENT,
            rating = 4.5,
            notes = "Sasuga Ainz-sama",
            genres = listOf("Fantasy", "Isekai"),
            tags = listOf("Anti-Hero", "OP MC"),
            author = "Kugane Maruyama",
            description = "A salaryman in a skeletal body",
            startDate = 1500000000000L,
            endDate = null,
            lastUpdated = 1700000000000L,
            dateAdded = 1500000000000L,
            extraData = """{"banner_image":"https://example.com/banner.jpg"}""",
            isDeleted = false,
            isAdult = true,
            repeat = 3,
            durationMinutes = 120,
            avgChapterLength = 4000,
            lengthUnitOverride = "WORDS",
            playbackSpeed = 1.25,
            averageScore = 88
        )

        val serialized = BackupProcessor.serialize(listOf(modernItem))
        val deserialized = BackupProcessor.deserialize(serialized)

        assertEquals(1, deserialized.items.size)
        val item = deserialized.items[0]

        assertEquals("modern-1", item.id)
        assertEquals("Overlord", item.title)
        assertEquals(14.5, item.progress, 0.001)
        assertEquals(18.0, item.progressTotal ?: 0.0, 0.001)
        assertEquals(2.0, item.secondaryProgress, 0.001)
        assertEquals(4.0, item.secondaryProgressTotal ?: 0.0, 0.001)
        assertEquals(3, item.repeat)
        assertEquals(true, item.isAdult)
        assertEquals(88, item.averageScore)
        assertEquals(120, item.durationMinutes)
        assertEquals(4000, item.avgChapterLength)
        assertEquals("WORDS", item.lengthUnitOverride)
        assertEquals(1.25, item.playbackSpeed, 0.001)
        assertEquals("https://example.com/banner.jpg", item.getExtra("banner_image"))
    }

    @Test
    fun merge_matchesByTitleAndTypeWhenIdDiffers() = runTest {
        val local = MediaItem(
            id = "local-uuid-1",
            type = "NOVEL",
            format = "WEB_NOVEL",
            title = "A Will Eternal",
            currentProgress = 200,
            userStatus = UserStatus.CURRENT,
            lastUpdated = 1600000000000L,
            dateAdded = 1500000000000L
        )

        val importedWithDifferentId = MediaItem(
            id = "new-uuid-2",
            type = "NOVEL",
            format = "WEB_NOVEL",
            title = "  a will eternal  ", // Different casing & whitespace
            currentProgress = 250,
            userStatus = UserStatus.CURRENT,
            lastUpdated = 1700000000000L,
            dateAdded = 1500000000000L
        )

        val result = BackupProcessor.merge(listOf(local), listOf(importedWithDifferentId), 3)

        // Must deduplicate by Title + Type!
        assertEquals(1, result.mergedItems.size)
        assertEquals("local-uuid-1", result.mergedItems[0].id)
        assertEquals(250, result.mergedItems[0].currentProgress)
    }

    @Test
    fun merge_deduplicatesIntraBatchItemsWithSameUrlOrTitle() = runTest {
        val imported1 = MediaItem(
            id = "batch-1",
            type = "NOVEL",
            format = "WEB_NOVEL",
            title = "Lord of the Mysteries",
            sourceUrl = "https://novelbin.me/novel-book/lord-of-the-mysteries",
            currentProgress = 500,
            userStatus = UserStatus.CURRENT,
            lastUpdated = 1600000000000L,
            dateAdded = 1500000000000L
        )
        val imported2 = MediaItem(
            id = "batch-2",
            type = "NOVEL",
            format = "WEB_NOVEL",
            title = "Lord of the Mysteries",
            sourceUrl = "https://novelbin.me/novel-book/lord-of-the-mysteries",
            currentProgress = 600,
            userStatus = UserStatus.CURRENT,
            lastUpdated = 1700000000000L,
            dateAdded = 1500000000000L
        )

        val result = BackupProcessor.merge(emptyList(), listOf(imported1, imported2), 3)
        assertEquals(1, result.mergedItems.size)
        assertEquals(600, result.mergedItems[0].currentProgress)
    }

    @Test
    fun deserialize_populatesAltTitlesFromRomajiAndEnglishWhenAlternativeTitlesEmpty() = runTest {
        val json = """
        {
            "schemaVersion": 3,
            "items": [
                {
                    "title": "Frieren",
                    "type": "ANIME",
                    "alternativeTitles": [],
                    "titleRomaji": "Sousou no Frieren",
                    "titleEnglish": "Frieren: Beyond Journey's End",
                    "titleNative": "葬送のフリーレン"
                }
            ]
        }
        """.trimIndent()

        val deserialized = BackupProcessor.deserialize(json)
        assertEquals(1, deserialized.items.size)
        val item = deserialized.items[0]
        assertEquals(listOf("Sousou no Frieren", "Frieren: Beyond Journey's End", "葬送のフリーレン"), item.alternativeTitles)
    }

    @Test
    fun deserializeCleanNovelBackup_parsesAll315NovelsLosslessly() = runTest {
        val cleanBackupFile = java.io.File("C:/Users/lazyman/Downloads/lazydex_novels_clean.lazydex")
        if (!cleanBackupFile.exists()) return@runTest

        var jsonText = ""
        java.util.zip.ZipFile(cleanBackupFile).use { zip ->
            val entry = zip.getEntry("backup.json")
            jsonText = zip.getInputStream(entry).bufferedReader().readText()
        }

        val deserialized = BackupProcessor.deserialize(jsonText)
        assertEquals(315, deserialized.items.size)
        assertTrue(deserialized.items.all { it.type == "NOVEL" })
        assertTrue(deserialized.items.all { it.title.isNotBlank() })
    }
}
