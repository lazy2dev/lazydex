package app.lazydex.data.tracker.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMatcherTest {

    @Test
    fun `exact match regardless of case and punctuation`() {
        val candidate = listOf("Frieren: Beyond Journey's End")
        val target = listOf("Sousou no Frieren", "frieren beyond journeys end")
        assertTrue(TitleMatcher.isMatch(candidate, target))
    }

    @Test
    fun `matching with romaji and native`() {
        val candidate = listOf("Sousou no Frieren", "葬送のフリーレン")
        val target = listOf("Sousou no Frieren")
        assertTrue(TitleMatcher.isMatch(candidate, target))
    }

    @Test
    fun `non matching titles return false`() {
        val candidate = listOf("One Piece")
        val target = listOf("Naruto")
        assertFalse(TitleMatcher.isMatch(candidate, target))
    }

    @Test
    fun `short titles do not false positive on prefix`() {
        val candidate = listOf("Nana")
        val target = listOf("Nanatsu no Taizai")
        // "nana" has length 4 (< 6), so should NOT match "nanatsunotaizai"
        assertFalse(TitleMatcher.isMatch(candidate, target))
    }
}

