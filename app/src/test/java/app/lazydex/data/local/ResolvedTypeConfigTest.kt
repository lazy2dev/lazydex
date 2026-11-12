package app.lazydex.data.local

import app.lazydex.domain.model.LengthUnit
import app.lazydex.domain.model.ResolvedTypeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvedTypeConfigTest {

    @Test
    fun `resolved config properties hold expected values`() {
        val config = ResolvedTypeConfig(
            typeId = "NOVEL",
            topLevelId = "NOVEL",
            displayName = "Novel",
            icon = "book",
            unitName = "Chapter",
            secondaryUnitName = "Volume",
            actionVerb = "Read",
            hasParts = false,
            hasCharEstimate = true,
            hasAudioSpeed = true,
            lengthUnit = LengthUnit.WORDS
        )

        assertEquals("NOVEL", config.typeId)
        assertEquals("NOVEL", config.topLevelId)
        assertEquals("Novel", config.displayName)
        assertEquals("book", config.icon)
        assertEquals("Chapter", config.unitName)
        assertEquals("Volume", config.secondaryUnitName)
        assertEquals("Read", config.actionVerb)
        assertFalse(config.hasParts)
        assertTrue(config.hasCharEstimate)
        assertTrue(config.hasAudioSpeed)
        assertEquals(LengthUnit.WORDS, config.lengthUnit)
    }

    @Test
    fun `resolved config without secondary unit`() {
        val config = ResolvedTypeConfig(
            typeId = "GAME",
            topLevelId = "GAME",
            displayName = "Game",
            icon = "gamepad",
            unitName = "Hour",
            secondaryUnitName = null,
            actionVerb = "Play",
            hasParts = false,
            hasCharEstimate = false,
            hasAudioSpeed = false,
            lengthUnit = LengthUnit.WORDS
        )

        assertNull(config.secondaryUnitName)
        assertFalse(config.hasParts)
    }
}
