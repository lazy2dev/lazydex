package app.lazydex.data.local

import app.lazydex.data.local.entity.CategoryEntity
import app.lazydex.domain.model.LengthUnit
import app.lazydex.domain.model.ResolvedCategoryConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvedCategoryConfigTest {

    private fun resolve(
        cat: CategoryEntity,
        parent: CategoryEntity? = null
    ): ResolvedCategoryConfig {
        return if (cat.parentId != null && parent != null) {
            val secUnit = when {
                cat.secondaryUnitName == "" -> null
                cat.secondaryUnitName != null -> cat.secondaryUnitName
                parent.secondaryUnitName == "" -> null
                else -> parent.secondaryUnitName
            }
            ResolvedCategoryConfig(
                categoryId = cat.id,
                topLevelId = parent.id,
                displayName = cat.name,
                icon = cat.icon ?: parent.icon ?: "folder",
                unitName = cat.unitName ?: parent.unitName ?: "Item",
                secondaryUnitName = secUnit,
                actionVerb = cat.actionVerb ?: parent.actionVerb ?: "Consume",
                hasParts = (cat.hasParts ?: parent.hasParts ?: 0) == 1,
                hasCharEstimate = (cat.hasCharEstimate ?: parent.hasCharEstimate ?: 0) == 1,
                hasAudioSpeed = (cat.hasAudioSpeed ?: parent.hasAudioSpeed ?: 0) == 1,
                lengthUnit = LengthUnit.valueOf(cat.lengthUnit ?: parent.lengthUnit ?: "WORDS")
            )
        } else {
            ResolvedCategoryConfig(
                categoryId = cat.id,
                topLevelId = cat.id,
                displayName = cat.name,
                icon = cat.icon ?: "folder",
                unitName = cat.unitName ?: "Item",
                secondaryUnitName = cat.secondaryUnitName?.takeIf { it != "" },
                actionVerb = cat.actionVerb ?: "Consume",
                hasParts = (cat.hasParts ?: 0) == 1,
                hasCharEstimate = (cat.hasCharEstimate ?: 0) == 1,
                hasAudioSpeed = (cat.hasAudioSpeed ?: 0) == 1,
                lengthUnit = LengthUnit.valueOf(cat.lengthUnit ?: "WORDS")
            )
        }
    }

    @Test
    fun `top level category resolves its own values`() {
        val novel = CategoryEntity(
            id = "cat_novel",
            parentId = null,
            name = "Novel",
            icon = "book",
            unitName = "Chapter",
            secondaryUnitName = "",
            actionVerb = "Read",
            hasParts = 0,
            hasCharEstimate = 1,
            hasAudioSpeed = 1,
            lengthUnit = "WORDS",
            createdAt = 0L,
            updatedAt = 0L
        )

        val config = resolve(novel)

        assertEquals("cat_novel", config.categoryId)
        assertEquals("cat_novel", config.topLevelId)
        assertEquals("Novel", config.displayName)
        assertEquals("Chapter", config.unitName)
        assertNull(config.secondaryUnitName) // '' becomes null
        assertEquals("Read", config.actionVerb)
        assertFalse(config.hasParts)
        assertTrue(config.hasCharEstimate)
        assertTrue(config.hasAudioSpeed)
        assertEquals(LengthUnit.WORDS, config.lengthUnit)
    }

    @Test
    fun `subtype inherits null fields from parent`() {
        val novel = CategoryEntity(
            id = "cat_novel",
            parentId = null,
            name = "Novel",
            icon = "book",
            unitName = "Chapter",
            secondaryUnitName = "",
            actionVerb = "Read",
            hasParts = 0,
            hasCharEstimate = 1,
            hasAudioSpeed = 1,
            lengthUnit = "WORDS",
            createdAt = 0L,
            updatedAt = 0L
        )
        val cn = CategoryEntity(
            id = "typ_novel_cn",
            parentId = "cat_novel",
            name = "Web Novel (CN)",
            icon = null,
            unitName = null,
            secondaryUnitName = null,
            actionVerb = null,
            hasParts = null,
            hasCharEstimate = null,
            hasAudioSpeed = null,
            lengthUnit = "CHARS", // overridden
            createdAt = 0L,
            updatedAt = 0L
        )

        val config = resolve(cn, novel)

        assertEquals("typ_novel_cn", config.categoryId)
        assertEquals("cat_novel", config.topLevelId)
        assertEquals("Web Novel (CN)", config.displayName)
        assertEquals("Chapter", config.unitName)
        assertEquals("Read", config.actionVerb)
        assertFalse(config.hasParts)
        assertTrue(config.hasCharEstimate)
        assertTrue(config.hasAudioSpeed)
        assertEquals(LengthUnit.CHARS, config.lengthUnit) // overridden to CHARS
    }

    @Test
    fun `subtype can override secondaryUnitName to Volume`() {
        val novel = CategoryEntity(
            id = "cat_novel",
            parentId = null,
            name = "Novel",
            icon = "book",
            unitName = "Chapter",
            secondaryUnitName = "",
            actionVerb = "Read",
            hasParts = 0,
            hasCharEstimate = 1,
            hasAudioSpeed = 1,
            lengthUnit = "WORDS",
            createdAt = 0L,
            updatedAt = 0L
        )
        val ln = CategoryEntity(
            id = "typ_novel_ln",
            parentId = "cat_novel",
            name = "Light Novel",
            icon = null,
            unitName = null,
            secondaryUnitName = "Volume", // overridden
            actionVerb = null,
            hasParts = 1,                 // overridden
            hasCharEstimate = null,
            hasAudioSpeed = null,
            lengthUnit = null,
            createdAt = 0L,
            updatedAt = 0L
        )

        val config = resolve(ln, novel)

        assertEquals("Volume", config.secondaryUnitName)
        assertTrue(config.hasParts)
    }

    @Test
    fun `sentinel empty string overrides parent secondaryUnitName to null`() {
        val parent = CategoryEntity(
            id = "cat_parent",
            parentId = null,
            name = "Parent",
            icon = "tv",
            unitName = "Episode",
            secondaryUnitName = "Season", // parent has Season
            actionVerb = "Watch",
            hasParts = 1,
            hasCharEstimate = 0,
            hasAudioSpeed = 0,
            lengthUnit = "WORDS",
            createdAt = 0L,
            updatedAt = 0L
        )
        val child = CategoryEntity(
            id = "typ_child",
            parentId = "cat_parent",
            name = "Movie Special",
            icon = null,
            unitName = "Minute",
            secondaryUnitName = "", // explicitly none
            actionVerb = null,
            hasParts = 0,
            hasCharEstimate = null,
            hasAudioSpeed = null,
            lengthUnit = null,
            createdAt = 0L,
            updatedAt = 0L
        )

        val config = resolve(child, parent)

        assertNull(config.secondaryUnitName)
        assertEquals("Minute", config.unitName)
        assertFalse(config.hasParts)
    }
}

