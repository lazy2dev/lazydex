package app.lazydex.extension

import app.lazydex.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {

    @Test
    fun `extractRepoUrl parses properly percent-encoded URLs`() {
        val input = "lazydex://add-repo?url=https%3A%2F%2Fraw.githubusercontent.com%2Fkeiyoushi%2Fextensions%2Frepo%2Findex.min.json"
        val extracted = MainActivity.extractRepoUrl(input)
        assertEquals("https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json", extracted)
    }

    @Test
    fun `extractRepoUrl parses raw unencoded URLs with query params and fragments`() {
        val input = "lazydex://add-repo?url=https://raw.githubusercontent.com/user/repo/branch/index.json?token=xyz123&version=2#section"
        val extracted = MainActivity.extractRepoUrl(input)
        assertEquals("https://raw.githubusercontent.com/user/repo/branch/index.json?token=xyz123&version=2#section", extracted)
    }

    @Test
    fun `extractRepoUrl returns null on invalid schemes or hosts`() {
        assertNull(MainActivity.extractRepoUrl(null))
        assertNull(MainActivity.extractRepoUrl(""))
        assertNull(MainActivity.extractRepoUrl("https://lazydex.app/add-repo?url=https://foo.bar"))
        assertNull(MainActivity.extractRepoUrl("lazydex://other-host?url=https://foo.bar"))
        assertNull(MainActivity.extractRepoUrl("lazydex://add-repo"))
        assertNull(MainActivity.extractRepoUrl("lazydex://add-repo?url="))
    }

    @Test
    fun `extractRepoUrl preserves plus signs in raw unencoded URLs`() {
        val input = "lazydex://add-repo?url=https://example.com/repo/v1+2/index.json"
        val extracted = MainActivity.extractRepoUrl(input)
        assertEquals("https://example.com/repo/v1+2/index.json", extracted)
    }
}
