package app.lazydex.ui.components

import app.lazydex.domain.model.MediaType
import app.lazydex.domain.model.MediaItem
import app.lazydex.domain.model.UserStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCardTest {

    @Test
    fun `resolveFormatName returns correct standard badges`() {
        assertEquals("Light Novel", resolveFormatName(typeId = "typ_novel_ln"))
        assertEquals("Manhwa", resolveFormatName(typeId = "typ_manhwa"))
        assertEquals("Manhua", resolveFormatName(typeId = "typ_manhua"))
        assertEquals("Web Novel", resolveFormatName(typeId = "typ_novel_cn"))
        assertEquals("Novel", resolveFormatName(typeId = "typ_novel"))
        assertEquals("TV", resolveFormatName(typeId = "typ_tv"))
        assertEquals("Anime", resolveFormatName(typeId = "typ_anime"))
        assertEquals("Manga", resolveFormatName(typeId = "typ_manga"))
        assertEquals("Movie", resolveFormatName(typeId = "typ_movie"))
        assertEquals("Game", resolveFormatName(typeId = "typ_game"))
    }

    @Test
    fun `resolveFormatName prioritizes AniList format and custom format`() {
        assertEquals("Custom Badging", resolveFormatName(typeId = "typ_manga", customFormat = "Custom Badging"))
        assertEquals("TV", resolveFormatName(typeId = "typ_anime", extraFormat = "TV"))
        assertEquals("Light Novel", resolveFormatName(typeId = "typ_novel", extraFormat = "NOVEL"))
        assertEquals("One Shot", resolveFormatName(typeId = "typ_manga", extraFormat = "ONE_SHOT"))
        assertEquals("TV Short", resolveFormatName(typeId = "typ_anime", extraFormat = "TV_SHORT"))
    }

    @Test
    fun `resolveHasSecondaryProgress hides volume controls for Anime and standard Manga`() {
        // AC-1: Anime entry with no secondary progress hides secondary counter
        assertFalse(resolveHasSecondaryProgress(typeId = "typ_anime", secondaryProgress = 0.0))
        assertFalse(resolveHasSecondaryProgress(typeId = "typ_anime_movie", secondaryProgress = 0.0))
        assertFalse(resolveHasSecondaryProgress(typeId = "typ_manga", secondaryProgress = 0.0))
        assertFalse(resolveHasSecondaryProgress(typeId = "typ_manhwa", secondaryProgress = 0.0))
        assertFalse(resolveHasSecondaryProgress(typeId = "typ_novel", secondaryProgress = 0.0))
    }

    @Test
    fun `resolveHasSecondaryProgress shows volume controls for formats with volumes`() {
        assertTrue(resolveHasSecondaryProgress(typeId = "typ_novel_ln"))
        assertTrue(resolveHasSecondaryProgress(typeId = "typ_manga_jp"))
        assertTrue(resolveHasSecondaryProgress(typeId = "typ_tv"))
        assertTrue(resolveHasSecondaryProgress(typeId = "typ_anime_series"))
    }

    @Test
    fun `resolveHasSecondaryProgress shows secondary controls when item has existing secondary progress`() {
        assertTrue(resolveHasSecondaryProgress(typeId = "typ_manga", secondaryProgress = 4.0))
        assertTrue(resolveHasSecondaryProgress(typeId = "typ_manga", secondaryProgressTotal = 8.0))
        assertTrue(resolveHasSecondaryProgress(typeId = "typ_anime", hasSecondaryOverride = true))
    }

    @Test
    fun `resolveUnitPrefix returns correct primary and secondary labels`() {
        // Primary
        assertEquals("Ep.", resolveUnitPrefix(MediaType.ANIME, isSecondary = false))
        assertEquals("Ep.", resolveUnitPrefix(MediaType.TV, isSecondary = false))
        assertEquals("Ch.", resolveUnitPrefix(MediaType.MANGA, isSecondary = false))
        assertEquals("Ch.", resolveUnitPrefix(MediaType.NOVEL, isSecondary = false))
        assertEquals("Min.", resolveUnitPrefix(MediaType.MOVIE, isSecondary = false))
        assertEquals("Hr.", resolveUnitPrefix(MediaType.GAME, isSecondary = false))

        // Secondary
        assertEquals("Season", resolveUnitPrefix(MediaType.TV, isSecondary = true))
        assertEquals("Season", resolveUnitPrefix(MediaType.ANIME, isSecondary = true))
        assertEquals("Vol.", resolveUnitPrefix(MediaType.MANGA, isSecondary = true))
        assertEquals("Vol.", resolveUnitPrefix(MediaType.NOVEL, isSecondary = true))
    }

    @Test
    fun `toMediaCardUiState maps entity properties accurately`() {
        val item = MediaItem(
            id = "m1",
            typeId = "typ_manhwa",
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
            mediaType = MediaType.NOVEL,
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
