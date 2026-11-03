package app.lazydex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainActivityCompanionTest {

    @Test
    fun `extractAniListToken extracts token from hash fragment`() {
        val uri = "lazydex://anilist-auth#access_token=test_token_12345&token_type=Bearer&expires_in=31536000"
        assertEquals("test_token_12345", MainActivity.extractAniListToken(uri))
    }

    @Test
    fun `extractAniListToken extracts token from query parameter`() {
        val uri = "lazydex://anilist-auth?access_token=query_token_abc&token_type=Bearer"
        assertEquals("query_token_abc", MainActivity.extractAniListToken(uri))
    }

    @Test
    fun `extractAniListToken returns null for wrong scheme or host`() {
        assertNull(MainActivity.extractAniListToken("https://anilist.co/login"))
        assertNull(MainActivity.extractAniListToken("lazydex://add-repo?url=https://example.com"))
        assertNull(MainActivity.extractAniListToken(null))
        assertNull(MainActivity.extractAniListToken(""))
    }
}

