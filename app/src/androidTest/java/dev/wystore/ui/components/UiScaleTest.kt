package dev.wystore.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.wystore.settings.AppSettings
import dev.wystore.settings.UiScale
import dev.wystore.settings.toStoreSettings
import dev.wystore.settings.toAppSettings
import dev.wystore.ui.settings.AppearanceSettingsScreen
import dev.wystore.ui.theme.WyStoreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UiScaleTest {
    @Test fun densityChoiceUpdatesIndependentlyOfScale() {
        val settings = mutableStateOf(AppSettings(uiScale = UiScale.LARGE).toStoreSettings())
        compose.setContent {
            WyStoreTheme(settings.value.toAppSettings()) {
                AppearanceSettingsScreen(settings.value, { settings.value = it }, {})
            }
        }
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(dev.wystore.R.string.appearance_density_compact))
            .performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(dev.wystore.settings.LayoutDensity.COMPACT, settings.value.layoutDensity)
            assertEquals(UiScale.LARGE, settings.value.uiScale)
        }
    }

    @Test fun compactChoiceReducesActualAppRowHeight() {
        val density = mutableStateOf(dev.wystore.settings.LayoutDensity.STANDARD)
        val state = PackageUiState("example.app", "Example", null, null,
            status = StatusMessage(StatusCode.INSTALLED), primaryAction = PrimaryAction.None)
        compose.setContent {
            WyStoreTheme(AppSettings(layoutDensity = density.value)) {
                AppRow(state, {}, {}, modifier = Modifier.testTag("row"), groupPosition = GroupPosition.SINGLE)
            }
        }
        val standard = compose.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { density.value = dev.wystore.settings.LayoutDensity.COMPACT }
        val compact = compose.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot.height
        org.junit.Assert.assertTrue("Compact row should be shorter", compact < standard)
    }

    @Test fun compactSmallScaleKeepsPhysicalMinimumRowHeight() {
        val state = PackageUiState("example.app", "Example", null, null,
            status = StatusMessage(StatusCode.INSTALLED), primaryAction = PrimaryAction.None)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                WyStoreTheme(AppSettings(uiScale = UiScale.SMALL,
                    layoutDensity = dev.wystore.settings.LayoutDensity.COMPACT)) {
                    AppRow(state, {}, {}, modifier = Modifier.testTag("row"), groupPosition = GroupPosition.SINGLE)
                }
            }
        }
        val heightPx = compose.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot.height
        org.junit.Assert.assertTrue("Row must be at least 48 system dp", heightPx >= 96f)
    }

    @Test fun searchCardBecomesDenserWithoutChangingTextHeight() {
        val density = mutableStateOf(dev.wystore.settings.LayoutDensity.STANDARD)
        val app = dev.wystore.data.StoreApp(
            appId = null, packageName = "example.app", name = "Example app", publisher = "",
            categories = emptyList(), shortDescription = "", fullDescription = "", iconUrl = null,
            screenshots = emptyList(), rating = null, ratingCount = null, downloadsText = null,
            versionName = "1", versionCode = 1, updatedAt = null, sizeBytes = 0,
            minAndroidVersion = null, signatureHint = null, sourceVersionId = null,
            availableSources = emptySet()
        )
        compose.setContent {
            WyStoreTheme(AppSettings(layoutDensity = density.value)) {
                Box(Modifier.testTag("search-row")) {
                    dev.wystore.ui.search.SearchCard(app, GroupPosition.SINGLE, null, null, null,
                        {}, {}, {}, {}, {})
                }
            }
        }
        val standard = compose.onNodeWithTag("search-row").fetchSemanticsNode().boundsInRoot.height
        val textHeight = compose.onNodeWithText("Example app", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { density.value = dev.wystore.settings.LayoutDensity.COMPACT }
        val compact = compose.onNodeWithTag("search-row").fetchSemanticsNode().boundsInRoot.height
        val compactTextHeight = compose.onNodeWithText("Example app", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
        org.junit.Assert.assertTrue("Compact search card should be shorter", compact < standard)
        assertEquals(textHeight, compactTextHeight, 0.01f)
    }

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun ordinaryPhoneKeepsEveryNavigationLabelInEqualColumns() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                WyStoreTheme(AppSettings()) {
                    Box(Modifier.requiredWidth(452.dp)) {
                        dev.wystore.ui.navigation.FloatingNavigationBar(
                            dev.wystore.ui.navigation.WyStoreDestination.Home, {})
                    }
                }
            }
        }
        val bounds = listOf(dev.wystore.R.string.bottom_nav_home, dev.wystore.R.string.bottom_nav_search,
            dev.wystore.R.string.bottom_nav_updates, dev.wystore.R.string.bottom_nav_library, dev.wystore.R.string.bottom_nav_settings)
            .map { compose.onNodeWithText(compose.activity.getString(it), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
        bounds.forEach { org.junit.Assert.assertEquals(bounds.first().top, it.top, 1f) }
        bounds.forEach { org.junit.Assert.assertEquals(bounds.first().width, it.width, 1f) }
    }

    @Test fun largeSystemTextDoesNotClipAnyNavigationLabel() {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 1.5f)) {
                WyStoreTheme(AppSettings()) {
                    Box(Modifier.requiredWidth(320.dp)) {
                        dev.wystore.ui.navigation.FloatingNavigationBar(
                            dev.wystore.ui.navigation.WyStoreDestination.Settings, {})
                    }
                }
            }
        }
        for (resource in listOf(dev.wystore.R.string.bottom_nav_home, dev.wystore.R.string.bottom_nav_search,
            dev.wystore.R.string.bottom_nav_updates, dev.wystore.R.string.bottom_nav_library, dev.wystore.R.string.bottom_nav_settings)) {
            val label = compose.activity.getString(resource)
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            org.junit.Assert.assertFalse("navigation must retain the complete label: $label; size=${layouts.single().size}; constraints=${layouts.single().layoutInput.constraints}; widthOverflow=${layouts.single().didOverflowWidth}; heightOverflow=${layouts.single().didOverflowHeight}; intrinsic=${layouts.single().multiParagraph.maxIntrinsicWidth}", layouts.single().hasVisualOverflow)
        }
    }

    @Test fun smallModeKeepsTheRoundActionTargetAtLeastFortyEightSystemDp() {
        var physicalMinimum = 0f
        compose.setContent {
            val system = LocalDensity.current
            physicalMinimum = with(system) { 48.dp.toPx() }
            WyStoreTheme(AppSettings(uiScale = UiScale.SMALL)) {
                HubRoundIconButton(androidx.compose.material.icons.Icons.Outlined.Search, "round action", {})
            }
        }
        val bounds = compose.onNode(hasClickAction() and hasContentDescription("round action"))
            .fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("action is too narrow: $bounds", bounds.width >= physicalMinimum - 1f)
        org.junit.Assert.assertTrue("action is too short: $bounds", bounds.height >= physicalMinimum - 1f)
    }

    @Test fun nestedTvThemesApplyTheInterfaceScaleOnlyOnce() {
        val scale = mutableStateOf(UiScale.SMALL)
        var actualDensity = 0f
        var actualFontScale = 0f
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1.5f)) {
                val settings = AppSettings(uiScale = scale.value)
                WyStoreTheme(settings) {
                    WyStoreTheme(settings, tv = true) {
                        val actual = LocalDensity.current
                        SideEffect { actualDensity = actual.density; actualFontScale = actual.fontScale }
                    }
                }
            }
        }
        for ((choice, expected) in listOf(UiScale.SMALL to 1.8f, UiScale.MEDIUM to 2f, UiScale.LARGE to 2.2f)) {
            compose.runOnIdle { scale.value = choice }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(expected, actualDensity, 0.001f)
                assertEquals(1.5f, actualFontScale, 0f)
            }
        }
    }

    @Test fun allThreeSizeChoicesRemainReachableOnANarrowScreenWithLargeText() {
        val settings = mutableStateOf(AppSettings().toStoreSettings())
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 1.5f)) {
                WyStoreTheme(AppSettings()) {
                    Box(Modifier.requiredWidth(320.dp)) {
                        AppearanceSettingsScreen(settings.value, { settings.value = it }, {})
                    }
                }
            }
        }
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        for ((choice, label) in listOf(UiScale.SMALL to dev.wystore.R.string.appearance_scale_small,
            UiScale.LARGE to dev.wystore.R.string.appearance_scale_large,
            UiScale.MEDIUM to dev.wystore.R.string.appearance_scale_medium)) {
            compose.onNodeWithText(context.getString(label)).performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(choice, settings.value.uiScale) }
        }
    }
}
