package app.lazydex.ui.dex

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.mp.KoinPlatform.getKoin

@RunWith(AndroidJUnit4::class)
class DexScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        try {
            getKoin()
        } catch (e: Exception) {
            // Ignore context issues
        }
    }

    @Test
    fun testDexScreenElements() {
        composeTestRule.setContent {
            DexScreen(
                onNavigateToAddItem = {},
                onNavigateToEditItem = {}
            )
        }

        composeTestRule.onNodeWithText("Dex").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Filter and sort items").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Add new tracker").assertIsDisplayed()
    }
}
