package dev.wystore.ui.github

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.wystore.R
import dev.wystore.data.*
import dev.wystore.settings.AppSettings
import dev.wystore.ui.theme.WyStoreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GitHubAppScreenCompatibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun installedAppRemainsUsableAndExplicitAssetCanBeChosenWithoutARecommendation() {
        val apk = GitHubAsset(1, "app_android11+_arm64-v8a.apk", 1000, "https://github.com/example/app", null)
        val release = GitHubRelease(1, "v2", "v2", "", null, false, listOf(apk))
        val entry = GitHubCatalogEntry(GitHubRepository("example", "app"), "Example", "Example", "")
        val installed = InstalledApp("example.app", "Example", "1", 1, 0, InstallSource.OTHER, emptySet())
        var opened: String? = null
        var adopted = false
        var selected: GitHubAsset? = null
        compose.setContent {
            WyStoreTheme(AppSettings()) {
                GitHubAppScreen(
                    state = GitHubAppUiState(entry = entry, releases = listOf(release)),
                    installedApp = installed,
                    deviceSdk = 29,
                    onTransferAsset = {},
                    onOpenInstalled = { opened = it },
                    onAdoptInstalled = { adopted = true },
                    onInstallAsset = { selected = it }
                )
            }
        }
        val list = compose.onNode(hasScrollToIndexAction())
        fun scrollTo(text: String) { list.performScrollToNode(hasText(text)) }
        val open = compose.activity.getString(R.string.common_open)
        scrollTo(open)
        compose.onNodeWithText(open).assertIsDisplayed().performClick()
        assertEquals("example.app", opened)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.details_uninstall)).assertIsDisplayed()
        val adopt = compose.activity.getString(R.string.github_adopt_installed)
        scrollTo(adopt)
        compose.onNodeWithText(adopt).assertIsDisplayed().performClick()
        assertEquals(true, adopted)
        compose.onNodeWithText(compose.activity.getString(R.string.github_update_primary)).assertDoesNotExist()
        val choose = compose.activity.getString(R.string.github_other_version)
        scrollTo(choose)
        compose.onNodeWithText(choose).performClick()
        scrollTo(apk.name)
        compose.onNodeWithText(apk.name).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.common_install)).performClick()
        assertEquals(apk, selected)
        val incompatible = compose.activity.getString(R.string.queue_error_incompatible)
        scrollTo(incompatible)
        compose.onNodeWithText(incompatible).assertIsDisplayed()
    }
}
