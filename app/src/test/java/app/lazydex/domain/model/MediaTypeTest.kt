package app.lazydex.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTypeTest {

    @Test
    fun resolveMediaType_resolvesSubtypesCorrectly() {
        assertEquals(MediaType.MANGA, resolveMediaType("typ_manhwa"))
        assertEquals(MediaType.MANGA, resolveMediaType("typ_manhua"))
        assertEquals(MediaType.MANGA, resolveMediaType("typ_manga_jp"))
        assertEquals(MediaType.MANGA, resolveMediaType("typ_manga"))
        assertEquals(MediaType.MANGA, resolveMediaType("MANGA"))
        assertEquals(MediaType.NOVEL, resolveMediaType("typ_novel_cn"))
        assertEquals(MediaType.NOVEL, resolveMediaType("typ_novel_kr"))
        assertEquals(MediaType.NOVEL, resolveMediaType("typ_novel_ln"))
        assertEquals(MediaType.NOVEL, resolveMediaType("typ_novel"))
        assertEquals(MediaType.ANIME, resolveMediaType("typ_anime_series"))
        assertEquals(MediaType.ANIME, resolveMediaType("typ_anime_movie"))
        assertEquals(MediaType.ANIME, resolveMediaType("typ_anime"))
        assertEquals(MediaType.GAME, resolveMediaType("typ_game"))
        assertEquals(MediaType.MOVIE, resolveMediaType("typ_movie"))
        assertEquals(MediaType.TV, resolveMediaType("typ_tv"))
        assertEquals(MediaType.NOVEL, resolveMediaType(null))
    }
}
