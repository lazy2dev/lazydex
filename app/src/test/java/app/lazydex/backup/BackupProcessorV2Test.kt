package app.lazydex.backup

import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.UserStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BackupProcessorV2Test {

    @Test
    fun serializeAndDeserializeV2_preservesNewFields() = runTest {
        val original = listOf(
            MediaItem(
                id = "item-1",
                category = MediaCategory.NOVEL,
                title = "Solo Leveling",
                currentProgress = 100,
                totalItems = 270,
                userStatus = UserStatus.READING,
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

        assertEquals(2, deserialized.schemaVersion)
        assertEquals(1, deserialized.items.size)
        val item = deserialized.items[0]
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
            category = MediaCategory.NOVEL,
            title = "Solo Leveling",
            sourceUrl = "https://example.com/sl",
            coverImagePath = "/covers/item-1",
            coverImageUrl = null,
            currentProgress = 50,
            totalItems = 100,
            userStatus = UserStatus.READING,
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
            category = MediaCategory.NOVEL,
            title = "Solo Leveling",
            sourceUrl = "https://example.com/sl",
            coverImagePath = "",
            coverImageUrl = null,
            currentProgress = 60,
            totalItems = 100,
            userStatus = UserStatus.READING,
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
        // Local v2 metadata preserved because imported was v1
        assertEquals("Chugong", merged.author)
        assertEquals("Local Description", merged.description)
        assertEquals(listOf("Action"), merged.genres)
        assertEquals(listOf("Monsters"), merged.tags)
        assertEquals(1600000000000L, merged.startDate)
    }

    @Test
    fun merge_forwardOnlyProgress_neverRegressesProgress() = runTest {
        // Device A (Local) read chapter 50 offline
        val localItem = MediaItem(
            id = "item-1",
            category = MediaCategory.MANGA,
            title = "One Piece",
            currentProgress = 50,
            totalItems = 1100,
            userStatus = UserStatus.READING,
            lastUpdated = 1600000000000L,
            dateAdded = 1500000000000L
        )

        // Device B (Imported backup) was synced earlier with chapter 40, but has newer timestamp
        val importedItem = MediaItem(
            id = "item-1",
            category = MediaCategory.MANGA,
            title = "One Piece",
            currentProgress = 40,
            totalItems = 1100,
            userStatus = UserStatus.READING,
            lastUpdated = 1700000000000L,
            dateAdded = 1500000000000L
        )

        val result = BackupProcessor.merge(listOf(localItem), listOf(importedItem), 2)
        assertEquals(1, result.mergedItems.size)
        // Progress must stay at 50, never regress to 40!
        assertEquals(50, result.mergedItems[0].currentProgress)
    }
}
