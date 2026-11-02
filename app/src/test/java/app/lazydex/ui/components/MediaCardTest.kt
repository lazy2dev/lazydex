package app.lazydex.ui.components

import app.lazydex.domain.model.MediaCategory
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.UserStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCardTest {

    @Test
    fun `resolveFormatName returns correct standard badges`() {
        assertEquals("Light Novel", resolveFormatName(categoryId = "typ_novel_ln"))
        assertEquals("Manhwa", resolveFormatName(categoryId = "typ_manhwa"))
        assertEquals("Manhua", resolveFormatName(categoryId = "typ_manhua"))
        assertEquals("Web Novel", resolveFormatName(categoryId = "typ_novel_cn"))
        assertEquals("Novel", resolveFormatName(categoryId = "cat_novel"))
        assertEquals("TV", resolveFormatName(categoryId = "cat_tv"))
        assertEquals("Anime", resolveFormatName(categoryId = "cat_anime"))
        assertEquals("Manga", resolveFormatName(categoryId = "cat_manga"))
        assertEquals("Movie", resolveFormatName(categoryId = "cat_movie"))
        assertEquals("Game", resolveFormatName(categoryId = "cat_game"))
    }

    @Test
    fun `resolveFormatName prioritizes AniList format and custom format`() {
        assertEquals("Custom Badging", resolveFormatName(categoryId = "cat_manga", customFormat = "Custom Badging"))
        assertEquals("TV", resolveFormatName(categoryId = "cat_anime", extraFormat = "TV"))
        assertEquals("Light Novel", resolveFormatName(categoryId = "cat_novel", extraFormat = "NOVEL"))
        assertEquals("One Shot", resolveFormatName(categoryId = "cat_manga", extraFormat = "ONE_SHOT"))
        assertEquals("TV Short", resolveFormatName(categoryId = "cat_anime", extraFormat = "TV_SHORT"))
    }

    @Test
    fun `resolveHasSecondaryProgress hides volume controls for Anime and standard Manga`() {
        // AC-1: Anime entry with no secondary progress hides secondary counter
        assertFalse(resolveHasSecondaryProgress(categoryId = "cat_anime", secondaryProgress = 0.0))
        assertFalse(resolveHasSecondaryProgress(categoryId = "typ_anime_movie", secondaryProgress = 0.0))
        assertFalse(resolveHasSecondaryProgress(categoryId = "cat_manga", secondaryProgress = 0.0))
        assertFalse(resolveHasSecondaryProgress(categoryId = "typ_manhwa", secondaryProgress = 0.0))
        assertFalse(resolveHasSecondaryProgress(categoryId = "cat_novel", secondaryProgress = 0.0))
    }

    @Test
    fun `resolveHasSecondaryProgress shows volume controls for formats with volumes`() {
        assertTrue(resolveHasSecondaryProgress(categoryId = "typ_novel_ln"))
        assertTrue(resolveHasSecondaryProgress(categoryId = "typ_manga_jp"))
        assertTrue(resolveHasSecondaryProgress(categoryId = "cat_tv"))
        assertTrue(resolveHasSecondaryProgress(categoryId = "typ_anime_series"))
    }

    @Test
    fun `resolveHasSecondaryProgress shows secondary controls when item has existing secondary progress`() {
        assertTrue(resolveHasSecondaryProgress(categoryId = "cat_manga", secondaryProgress = 4.0))
        assertTrue(resolveHasSecondaryProgress(categoryId = "cat_manga", secondaryProgressTotal = 8.0))
        assertTrue(resolveHasSecondaryProgress(categoryId = "cat_anime", hasSecondaryOverride = true))
    }

    @Test
    fun `resolveUnitPrefix returns correct primary and secondary labels`() {
        // Primary
        assertEquals("Ep.", resolveUnitPrefix(MediaCategory.ANIME, isSecondary = false))
        assertEquals("Ep.", resolveUnitPrefix(MediaCategory.TV, isSecondary = false))
        assertEquals("Ch.", resolveUnitPrefix(MediaCategory.MANGA, isSecondary = false))
        assertEquals("Ch.", resolveUnitPrefix(MediaCategory.NOVEL, isSecondary = false))
        assertEquals("Min.", resolveUnitPrefix(MediaCategory.MOVIE, isSecondary = false))
        assertEquals("Hr.", resolveUnitPrefix(MediaCategory.GAME, isSecondary = false))

        // Secondary
        assertEquals("Season", resolveUnitPrefix(MediaCategory.TV, isSecondary = true))
        assertEquals("Season", resolveUnitPrefix(MediaCategory.ANIME, isSecondary = true))
        assertEquals("Vol.", resolveUnitPrefix(MediaCategory.MANGA, isSecondary = true))
        assertEquals("Vol.", resolveUnitPrefix(MediaCategory.NOVEL, isSecondary = true))
    }

    @Test
    fun `toMediaCardUiState maps entity properties accurately`() {
        val item = MediaItem(
            id = "m1",
            categoryId = "typ_manhwa",
            title = "Solo Leveling",
            progress = 179.0,
            progressTotal = 200.0,
            secondaryProgress = 0.0,
            secondaryProgressTotal = null,
            userStatus = UserStatus.READING,
            rating = 8.5,
            lastUpdated = 1000L,
            dateAdded = 500L,
        )

        val uiState = item.toMediaCardUiState()
        assertEquals("m1", uiState.id)
        assertEquals("Solo Leveling", uiState.title)
        assertEquals("Manhwa", uiState.formatName)
        assertEquals(179.0, uiState.progress, 0.001)
        assertEquals(200.0, uiState.progressTotal!!, 0.001)
        assertFalse(uiState.hasSecondaryProgress)
        assertEquals(170, uiState.scoreRaw)
        assertFalse(uiState.isDirtyLocal)
    }

    @Test
    fun `MediaCardUiState roundtrip preserves integrity`() {
        val uiState = MediaCardUiState(
            id = "ln1",
            title = "Overlord",
            formatName = "Light Novel",
            coverPath = "/covers/overlord.jpg",
            coverUrl = "https://example.com/overlord.jpg",
            progress = 16.0,
            progressTotal = 18.0,
            secondaryProgress = 16.0,
            secondaryProgressTotal = 18.0,
            hasSecondaryProgress = true,
            isDirtyLocal = true,
            scoreRaw = 180,
            userStatus = UserStatus.READING,
            lastUpdated = 2000L,
            category = MediaCategory.NOVEL,
        )

        val item = uiState.toMediaItem()
        assertEquals("ln1", item.id)
        assertEquals("Overlord", item.title)
        assertEquals(16.0, item.progress, 0.001)
        assertEquals(18.0, item.progressTotal!!, 0.001)
        assertEquals(16.0, item.secondaryProgress, 0.001)
        assertEquals(18.0, item.secondaryProgressTotal!!, 0.001)
        assertEquals(9.0, item.rating!!, 0.001)
        assertEquals(UserStatus.READING, item.userStatus)
    }
}
