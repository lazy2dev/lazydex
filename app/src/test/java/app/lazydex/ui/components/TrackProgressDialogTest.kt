package app.lazydex.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackProgressDialogTest {

    @Test
    fun `formatProgress formats whole integers without trailing decimal point`() {
        assertEquals("0", formatProgress(0.0))
        assertEquals("1", formatProgress(1.0))
        assertEquals("179", formatProgress(179.0))
        assertEquals("10000", formatProgress(10000.0))
    }

    @Test
    fun `formatProgress formats decimal fractions preserving decimal points`() {
        assertEquals("12.5", formatProgress(12.5))
        assertEquals("179.5", formatProgress(179.5))
        assertEquals("0.5", formatProgress(0.5))
        assertEquals("12.25", formatProgress(12.25))
    }

    @Test
    fun `manual text decimal parsing supports dot and comma`() {
        val inputDot = "12.5".replace(',', '.').toDoubleOrNull()
        val inputComma = "12,5".replace(',', '.').toDoubleOrNull()
        val inputInt = "180".replace(',', '.').toDoubleOrNull()

        assertEquals(12.5, inputDot!!, 0.001)
        assertEquals(12.5, inputComma!!, 0.001)
        assertEquals(180.0, inputInt!!, 0.001)
    }
}
