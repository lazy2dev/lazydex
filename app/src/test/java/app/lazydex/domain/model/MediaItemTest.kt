package app.lazydex.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaItemTest {

    @Test
    fun normalizeGenreList_preservesHyphensAndDeduplicatesCaseInsensitively() {
        val input = listOf("LitRPG", "litrpg", "lit_rpg", "Sci-Fi", "sci-fi", " Action ")
        val result = MediaItem.normalizeGenreList(input)

        // "LitRPG", "litrpg", "lit_rpg" -> dedup key "litrpg", first casing "LitRPG" kept
        // "Sci-Fi", "sci-fi" -> dedup key "scifi", first casing "Sci-Fi" kept
        // " Action " -> "Action"
        assertEquals(listOf("LitRPG", "Sci-Fi", "Action"), result)
    }

    @Test
    fun normalize_trimsAndClampsFields() {
        val item = MediaItem(
            id = "test-id",
            category = MediaCategory.NOVEL,
            title = "   ",
            currentProgress = 15,
            totalItems = 10,
            userStatus = UserStatus.COMPLETED,
            author = "  Author Name  ",
            description = "  Some description  ",
            startDate = -100L,
            endDate = 1600000000000L,
            lastUpdated = 100L,
            dateAdded = 100L
        ).normalize()

        assertEquals("Untitled", item.title)
        assertEquals(10, item.currentProgress) // Clamped to totalItems
        assertEquals("Author Name", item.author)
        assertEquals("Some description", item.description)
        assertNull(item.startDate) // Invalid negative timestamp cleared
        assertEquals(1600000000000L, item.endDate)
    }

    @Test
    fun extraData_storesArbitraryMetadataAndParsesTrackerIds() {
        val baseItem = MediaItem(
            id = "test-123",
            category = MediaCategory.ANIME,
            title = "Frieren",
            currentProgress = 28,
            totalItems = 28,
            userStatus = UserStatus.COMPLETED,
            lastUpdated = 100L,
            dateAdded = 100L
        )

        val withAniList = baseItem.withExtra("anilist_id", "154587")
        assertEquals(154587L, withAniList.anilistId)
        assertEquals("154587", withAniList.getExtra("anilist_id"))

        val withSteam = withAniList.withExtra("steam_id", "1091500")
        assertEquals(154587L, withSteam.anilistId)
        assertEquals("1091500", withSteam.getExtra("steam_id"))
    }
}
