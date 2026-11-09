package app.lazydex.data.local

import app.lazydex.data.local.converter.Converters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `empty list encodes to empty string`() {
        val result = converters.fromList(emptyList())
        assertEquals("", result)
    }

    @Test
    fun `list encodes to comma-separated string`() {
        val input = listOf("Action", "Adventure", "Fantasy")
        val result = converters.fromList(input)
        assertEquals("Action, Adventure, Fantasy", result)
    }

    @Test
    fun `empty or blank string decodes to empty list`() {
        assertTrue(converters.toList("").isEmpty())
        assertTrue(converters.toList("   ").isEmpty())
        assertTrue(converters.toList("[]").isEmpty())
    }

    @Test
    fun `comma-separated string decodes to list`() {
        val input = "Action, Adventure, Fantasy"
        val result = converters.toList(input)
        assertEquals(listOf("Action", "Adventure", "Fantasy"), result)
    }

    @Test
    fun `comma-separated string with single item decodes properly`() {
        val input = "Sci-Fi"
        val result = converters.toList(input)
        assertEquals(listOf("Sci-Fi"), result)
    }

    @Test
    fun `legacy JSON array string decodes properly`() {
        val jsonInput = "[\"Action\",\"Adventure\",\"Comedy\"]"
        val result = converters.toList(jsonInput)
        assertEquals(listOf("Action", "Adventure", "Comedy"), result)
    }

    @Test
    fun `legacy malformed JSON array string falls back gracefully`() {
        val malformedJson = "['Action', 'Romance']"
        val result = converters.toList(malformedJson)
        assertEquals(listOf("Action", "Romance"), result)
    }

    @Test
    fun `roundtrip encode and decode`() {
        val original = listOf("Drama", "Mystery", "Psychological")
        val encoded = converters.fromList(original)
        val decoded = converters.toList(encoded)
        assertEquals(original, decoded)
    }
}
