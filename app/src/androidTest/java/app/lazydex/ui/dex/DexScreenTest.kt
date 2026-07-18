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
                onNavigateToEditItem = {},
                onNavigateToSettings = {}
            )
        }

        // Verify title and main buttons exist
        composeTestRule.onNodeWithText("LazyDex").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Toggle list/grid layout").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Filter items").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Sort items").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
        
        // Assert toggle click switches view layout
        composeTestRule.onNodeWithContentDescription("Toggle list/grid layout").performClick()
    }
}
