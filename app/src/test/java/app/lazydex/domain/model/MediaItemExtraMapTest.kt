package app.lazydex.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaItemExtraMapTest {

    @Test
    fun getExtra_returnsValueFromMemoizedExtraMap() {
        val json = """{"anilist_id":"12345","format":"MANHWA","score":"9.5"}"""
        val item = MediaItem(
            id = "test-1",
            title = "Solo Leveling",
            userStatus = UserStatus.CURRENT,
            lastUpdated = 0L,
            dateAdded = 0L,
            extraData = json
        )

        assertEquals("12345", item.getExtra("anilist_id"))
        assertEquals(12345L, item.anilistId)
        assertEquals("MANHWA", item.getExtra("format"))
        assertEquals("9.5", item.getExtra("score"))
        assertNull(item.getExtra("nonexistent"))
    }

    @Test
    fun withExtra_updatesJsonAndRefreshesMemoizedMapOnNewInstance() {
        val item = MediaItem(
            id = "test-1",
            title = "Solo Leveling",
            userStatus = UserStatus.CURRENT,
            lastUpdated = 0L,
            dateAdded = 0L
        )

        assertNull(item.getExtra("anilist_id"))

        val updated = item.withExtra("anilist_id", "9999")
        assertEquals("9999", updated.getExtra("anilist_id"))
        assertEquals(9999L, updated.anilistId)
        assertNull(item.getExtra("anilist_id"))
    }
}
