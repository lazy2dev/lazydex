package app.lazydex.ui.navigation

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
class MainShellTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        // Instrumented tests run in the application context where LazyDexApp
        // has already started Koin. We verify it's active.
        try {
            getKoin()
        } catch (e: Exception) {
            // If not active, we handle it or let it fail gracefully.
        }
    }

    @Test
    fun testTabNavigation() {
        composeTestRule.setContent {
            MainShellScreen(
                onNavigateToSettings = {},
                onNavigateToAddItem = {},
                onNavigateToEditItem = {}
            )
        }

        // Initially we are on DEX tab, assert LazyDex title is shown
        composeTestRule.onNodeWithText("LazyDex").assertIsDisplayed()

        // Click on Statistics tab
        composeTestRule.onNodeWithContentDescription("Statistics").performClick()
        composeTestRule.onNodeWithText("Statistics").assertIsDisplayed()

        // Click on More tab
        composeTestRule.onNodeWithContentDescription("More").performClick()
        composeTestRule.onNodeWithText("More Screen Placeholder").assertIsDisplayed()

        // Click back to Dex tab
        composeTestRule.onNodeWithContentDescription("Dex").performClick()
        composeTestRule.onNodeWithText("LazyDex").assertIsDisplayed()
    }
}
