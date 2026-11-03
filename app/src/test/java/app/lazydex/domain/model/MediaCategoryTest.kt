package app.lazydex.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCategoryTest {

    @Test
    fun statusLabel_returnsCorrectCategoryAdaptiveName() {
        val novel = MediaCategory.NOVEL
        assertEquals("Reading", novel.statusLabel(StatusFilter.IN_PROGRESS))
        assertEquals("Plan to Read", novel.statusLabel(StatusFilter.PLAN_TO))

        val anime = MediaCategory.ANIME
        assertEquals("Watching", anime.statusLabel(StatusFilter.IN_PROGRESS))
        assertEquals("Plan to Watch", anime.statusLabel(StatusFilter.PLAN_TO))

        val game = MediaCategory.GAME
        assertEquals("Playing", game.statusLabel(StatusFilter.IN_PROGRESS))
        assertEquals("Plan to Play", game.statusLabel(StatusFilter.PLAN_TO))

        val movie = MediaCategory.MOVIE
        assertEquals("Watching", movie.statusLabel(StatusFilter.IN_PROGRESS))

        val nullCategory: MediaCategory? = null
        assertEquals("In Progress", nullCategory.statusLabel(StatusFilter.IN_PROGRESS))
        assertEquals("Plan to", nullCategory.statusLabel(StatusFilter.PLAN_TO))
    }

    @Test
    fun resolveMediaCategory_resolvesMangaSubtypesCorrectly() {
        assertEquals(MediaCategory.MANGA, resolveMediaCategory("typ_manhwa"))
        assertEquals(MediaCategory.MANGA, resolveMediaCategory("typ_manhua"))
        assertEquals(MediaCategory.MANGA, resolveMediaCategory("typ_manga_jp"))
        assertEquals(MediaCategory.MANGA, resolveMediaCategory("cat_manga"))
        assertEquals(MediaCategory.NOVEL, resolveMediaCategory("typ_novel_cn"))
        assertEquals(MediaCategory.NOVEL, resolveMediaCategory("typ_novel_kr"))
        assertEquals(MediaCategory.NOVEL, resolveMediaCategory("typ_novel_ln"))
        assertEquals(MediaCategory.NOVEL, resolveMediaCategory("cat_novel"))
        assertEquals(MediaCategory.ANIME, resolveMediaCategory("typ_anime_series"))
        assertEquals(MediaCategory.ANIME, resolveMediaCategory("typ_anime_movie"))
        assertEquals(MediaCategory.ANIME, resolveMediaCategory("cat_anime"))
        assertEquals(MediaCategory.GAME, resolveMediaCategory("cat_game"))
        assertEquals(MediaCategory.MOVIE, resolveMediaCategory("cat_movie"))
        assertEquals(MediaCategory.TV, resolveMediaCategory("cat_tv"))
        assertEquals(MediaCategory.NOVEL, resolveMediaCategory(null))
    }
}
