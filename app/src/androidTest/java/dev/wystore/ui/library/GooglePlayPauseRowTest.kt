package dev.wystore.ui.library

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.R
import dev.wystore.data.InstallSource
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GooglePlayPauseRowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun versionAndInstallerRemainVisibleWithNarrowWidthAndLargeText() {
        val app = InstalledApp("example.narrow", "ByeByeDPI", "1.8.2", 1820, 1,
            InstallSource.GOOGLE_PLAY, emptySet(), installerPackageName = "com.android.vending")
        compose.setContent {
            // A 360 dp phone leaves 328 dp after screen margins. Keep responsive button padding
            // consistent with that viewport even when this test executes on a wide TV emulator.
            val phoneConfiguration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 360
                screenHeightDp = 800
                smallestScreenWidthDp = 360
                fontScale = 1.3f
            }
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 1.3f),
                LocalConfiguration provides phoneConfiguration
            ) {
                dev.wystore.ui.theme.WyStoreTheme(dev.wystore.settings.AppSettings()) {
                    LibraryAppRow(
                        app = app, managed = null, pendingUpdate = null, queueItem = null,
                        onAdopt = {}, onUpdateManaged = {}, onRequestForce = {}, onRemoveManaged = {},
                        onUninstall = {}, onCheck = {}, onOpenDetails = {}, onOpenStorePage = {},
                        onLaunch = {}, onInstallPending = {}, onDownloadUpdate = {},
                        modifier = Modifier.width(328.dp)
                    )
                }
            }
        }
        compose.onNodeWithText("1.8.2", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Google Play", useUnmergedTree = true).assertIsDisplayed()
        for (label in listOf("1.8.2", "Google Play")) {
            compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<TextLayoutResult>()
                action(layouts)
                assertTrue("The badge must expose its text layout", layouts.isNotEmpty())
                layouts.forEach { layout ->
                    assertFalse("The $label badge must not be ellipsized", layout.isLineEllipsized(0))
                    assertEquals(label.length, layout.getLineEnd(0, visibleEnd = true))
                    label.indices.forEach { index ->
                        val glyph = layout.getBoundingBox(index)
                        assertTrue("The $label glyph must fit horizontally", glyph.left >= 0 && glyph.right <= layout.size.width)
                        assertTrue("The $label glyph must fit vertically", glyph.top >= 0 && glyph.bottom <= layout.size.height)
                    }
                }
            }
        }
    }

    @Test
    fun pauseExplanationRemainsVisibleWhenTheAppRowIsCollapsed() {
        val continueChecks = mutableStateOf(false)
        val app = InstalledApp("example.paused", "Paused app", "1.0", 1, 1,
            InstallSource.WY_STORE, emptySet(), installerPackageName = "app.wystore")
        val managed = ManagedApp(app.packageName, app.label, emptySet(), googlePlayUpdateDetected = true)
        compose.setContent {
            MaterialTheme {
                LibraryAppRow(
                    app = app, managed = managed, pendingUpdate = null, queueItem = null,
                    onAdopt = {}, onUpdateManaged = {}, onRequestForce = {}, onRemoveManaged = {},
                    onUninstall = {}, onCheck = {}, onOpenDetails = {}, onOpenStorePage = {},
                    onLaunch = {}, onInstallPending = {}, onDownloadUpdate = {},
                    expanded = false, continueChecksAfterGooglePlayUpdate = continueChecks.value
                )
            }
        }
        val reason = compose.activity.getString(R.string.google_play_pause_reason)
        compose.onNodeWithText(reason).assertIsDisplayed()

        compose.runOnIdle { continueChecks.value = true }
        compose.onNodeWithText(reason).assertDoesNotExist()

        compose.runOnIdle { continueChecks.value = false }
        compose.onNodeWithText(reason).assertIsDisplayed()
    }
}
