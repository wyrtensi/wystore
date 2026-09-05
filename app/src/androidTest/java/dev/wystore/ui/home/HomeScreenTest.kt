package dev.wystore.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Test
    fun homeScreenRendersSearchAndSections() {
        composeTestRule.setContent {
            HomeScreen(
                onSearchClick = {},
                onAppClick = {},
                onUpdatesClick = {}
            )
        }

        composeTestRule.onNodeWithText("Wy Store").assertIsDisplayed()
    }
}
