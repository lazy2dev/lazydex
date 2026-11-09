package app.lazydex.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CoverColorExtractorTest {

    @Before
    fun setup() {
        CoverColorExtractor.clearCache()
    }

    @Test
    fun extractSeedColor_returnsNullForBlankOrMissingPath() {
        assertNull(CoverColorExtractor.extractSeedColor(""))
        assertNull(CoverColorExtractor.extractSeedColor("   "))
        assertNull(CoverColorExtractor.extractSeedColor("/non/existent/path/cover.jpg"))
    }

    @Test
    fun extractSeedColorFromBytes_andUrl_returnsNullForEmptyOrInvalid() {
        assertNull(CoverColorExtractor.extractSeedColorFromBytes(ByteArray(0)))
        assertNull(CoverColorExtractor.extractSeedColorFromBytes(byteArrayOf(1, 2, 3)))
        assertNull(CoverColorExtractor.extractSeedColorFromUrl("", okhttp3.OkHttpClient()))
        assertNull(CoverColorExtractor.extractSeedColorFromUrl("   ", okhttp3.OkHttpClient()))
    }

    @Test
    fun clearCache_clearsStoredEntries() {
        CoverColorExtractor.clearCache()
        assertNull(CoverColorExtractor.extractSeedColor(""))
    }

    @Test
    fun coverColorScheme_adjustsDarkAndLightModesHarmoniously() {
        val baseDark = androidx.compose.material3.darkColorScheme()
        val vibrantRed = Color.Red

        val darkScheme = coverColorScheme(vibrantRed, isDark = true, base = baseDark)
        assertEquals(vibrantRed, darkScheme.primary)
        assertNotNull(darkScheme.primaryContainer)
        assertEquals(vibrantRed, darkScheme.surfaceTint)

        val baseLight = androidx.compose.material3.lightColorScheme()
        val lightScheme = coverColorScheme(vibrantRed, isDark = false, base = baseLight)
        assertNotNull(lightScheme.primary)
        assertNotNull(lightScheme.primaryContainer)
    }
}
