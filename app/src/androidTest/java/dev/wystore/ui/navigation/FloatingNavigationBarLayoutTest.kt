package dev.wystore.ui.navigation

import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wystore.R
import dev.wystore.settings.AppSettings
import dev.wystore.settings.UiScale
import dev.wystore.ui.theme.WyStoreTheme
import java.util.Locale
import java.io.File
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FloatingNavigationBarLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val languages = listOf("en", "ru", "be", "uk", "lv", "kk", "sr", "uz", "zh")
    private val titles = listOf(R.string.nav_home, R.string.nav_search, R.string.nav_updates,
        R.string.nav_library, R.string.nav_settings)
    private val labels = listOf(R.string.bottom_nav_home, R.string.bottom_nav_search,
        R.string.bottom_nav_updates, R.string.bottom_nav_library, R.string.bottom_nav_settings)
    private val destinations = listOf(WyStoreDestination.Home, WyStoreDestination.Search,
        WyStoreDestination.Updates, WyStoreDestination.Library, WyStoreDestination.Settings)

    @Test fun compactLargeTextKeepsWordsOnOneLine() {
        compose.setContent {
            val config = Configuration(compose.activity.resources.configuration).apply {
                setLocale(Locale.forLanguageTag("ru")); fontScale = 1.5f
            }
            val context = remember { compose.activity.createConfigurationContext(config) }
            CompositionLocalProvider(LocalConfiguration provides config, LocalContext provides context,
                LocalDensity provides Density(context)) {
                Box(Modifier.requiredWidth(320.dp)) {
                    WyStoreTheme(AppSettings(uiScale = UiScale.LARGE, dynamicColorEnabled = false)) {
                        FloatingNavigationBar(WyStoreDestination.Home, {})
                    }
                }
            }
        }
        val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag("ru")) }
        val context = compose.activity.createConfigurationContext(config)
        labels.forEach { id ->
            val label = context.getString(id)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertEquals("word must stay on one line: $label", 1, layout.lineCount)
            assertFalse("word must be complete: $label", layout.hasVisualOverflow)
        }
    }

    @Test fun wideNormalBarDoesNotReserveAnEmptySecondLine() {
        compose.setContent {
            Box(Modifier.requiredWidth(452.dp)) {
                WyStoreTheme(AppSettings(uiScale = UiScale.MEDIUM, dynamicColorEnabled = false)) {
                    FloatingNavigationBar(WyStoreDestination.Home, {})
                }
            }
        }
        val label = compose.activity.getString(R.string.bottom_nav_home)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals(1, layout.lineCount)
        val bottom = layout.getLineBottom(0)
        assertTrue("one-line text should not reserve another line: height=${layout.size.height}, bottom=$bottom",
            layout.size.height - bottom < bottom / 2)
    }

    @Test fun captureNavigationSamples() {
        val large = mutableStateOf(false)
        val language = mutableStateOf("ru")
        compose.setContent {
            val config = Configuration(compose.activity.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language.value))
                fontScale = if (large.value) 1.5f else 1f
            }
            val context = remember(large.value, language.value) { compose.activity.createConfigurationContext(config) }
            CompositionLocalProvider(LocalConfiguration provides config, LocalContext provides context,
                LocalDensity provides Density(context)) {
                Box(Modifier.testTag("navigation-preview").requiredWidth(if (large.value) 320.dp else 360.dp)) {
                    WyStoreTheme(AppSettings(uiScale = if (large.value) UiScale.LARGE else UiScale.MEDIUM)) {
                        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                            FloatingNavigationBar(WyStoreDestination.Home, {})
                        }
                    }
                }
            }
        }
        for ((name, largeText, lang) in listOf(Triple("normal", false, "ru"),
            Triple("large-text", true, "ru"), Triple("long-labels", true, "lv"))) {
            compose.runOnIdle { large.value = largeText; language.value = lang }
            val config = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) }
            val caption = compose.activity.createConfigurationContext(config).getString(R.string.bottom_nav_home)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(caption, useUnmergedTree = true).performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val size = layout.layoutInput.style.fontSize
            val px = with(layout.layoutInput.density) { size.toPx() }
            Log.i("NavFontStudy", "sample=$name locale=$lang font=$size px=$px")
            val file = File(compose.activity.getExternalFilesDir(null), "fixed-navigation-$name.png")
            file.outputStream().use { stream ->
                compose.onNodeWithTag("navigation-preview").captureToImage().asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }
    }

    @Test fun navigationKeepsLabelsAndEqualStableTargetsAtLargeText() {
        val language = mutableStateOf("en")
        val scale = mutableStateOf(UiScale.LARGE)
        val fontScale = mutableFloatStateOf(1.5f)
        val width = mutableIntStateOf(320)
        val selected = mutableStateOf(destinations.first())
        compose.setContent {
            val config = Configuration(compose.activity.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language.value))
                this.fontScale = fontScale.floatValue
            }
            val context = remember(language.value, fontScale.floatValue) {
                compose.activity.createConfigurationContext(config)
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalContext provides context,
                LocalDensity provides Density(context)) {
                Box(Modifier.requiredWidth(width.intValue.dp)) {
                    WyStoreTheme(AppSettings(uiScale = scale.value, dynamicColorEnabled = false)) {
                        FloatingNavigationBar(selected.value, { selected.value = it })
                    }
                }
            }
        }
        val scenarios = languages.map { Scenario(it, 320, 1.5f, UiScale.LARGE) } +
            listOf("en", "ru").flatMap { lang ->
                listOf(320, 360, 452).flatMap { w ->
                    listOf(1f, 1.15f, 1.5f).flatMap { f ->
                        UiScale.entries.map { s -> Scenario(lang, w, f, s) }
                    }
                }
            } + listOf(359, 361).map { Scenario("en", it, 1f, UiScale.MEDIUM) }
        val overflows = mutableListOf<String>()
        val referenceBounds = mutableMapOf<Triple<Int, Float, UiScale>, List<androidx.compose.ui.geometry.Rect>>()
        val referenceFonts = mutableMapOf<Triple<Int, Float, UiScale>, androidx.compose.ui.unit.TextUnit>()
        for (scenario in scenarios.distinct()) {
            compose.runOnIdle {
                language.value = scenario.language; width.intValue = scenario.width
                fontScale.floatValue = scenario.fontScale; scale.value = scenario.scale
                selected.value = destinations.first()
            }
            compose.waitForIdle()
            val config = Configuration(compose.activity.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(scenario.language)); this.fontScale = scenario.fontScale
            }
            val context = compose.activity.createConfigurationContext(config)
            val full = titles.map(context::getString)
            val short = labels.map(context::getString)
            fun bounds() = full.map { title ->
                compose.onNode(hasContentDescription(title) and hasClickAction(), useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
            }
            val initial = bounds()
            val reference = referenceBounds.getOrPut(Triple(scenario.width, scenario.fontScale, scenario.scale)) { initial }
            initial.zip(reference).forEach { (actual, expected) ->
                assertEquals("stable language height: $scenario", expected.height, actual.height, 0.5f)
                assertEquals("stable language top: $scenario", expected.top, actual.top, 0.5f)
                assertEquals("stable language left: $scenario", expected.left, actual.left, 0.5f)
                assertEquals("stable language width: $scenario", expected.width, actual.width, 0.5f)
            }
            initial.forEach { rect ->
                assertEquals("equal widths: $scenario", initial.first().width, rect.width, 1f)
                assertEquals("one row: $scenario", initial.first().top, rect.top, 1f)
                assertTrue("touch width: $scenario $rect", rect.width >= 48f * context.resources.displayMetrics.density - 1f)
                assertTrue("touch height: $scenario $rect", rect.height >= 48f * context.resources.displayMetrics.density - 1f)
            }
            short.forEachIndexed { index, label ->
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(
                    androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                assertTrue("space between labels: $scenario $label", layout.size.width < initial[index].width - 1f)
                val horizontalPadding = (3f * context.resources.displayMetrics.density * scenario.scale.factor).roundToInt()
                assertEquals("actual caption width: $scenario $label",
                    initial[index].width - 2 * horizontalPadding, layout.layoutInput.constraints.maxWidth.toFloat(), 1f)
                if (layout.hasVisualOverflow) {
                    overflows += "label overflow: $scenario $label size=${layout.size} width=${layout.didOverflowWidth} height=${layout.didOverflowHeight} lines=${layout.lineCount} lastEnd=${layout.getLineEnd(layout.lineCount - 1)} textLength=${label.length} lastBottom=${layout.getLineBottom(layout.lineCount - 1)}"
                }
                val fontSize = layout.layoutInput.style.fontSize
                assertTrue("font size: $scenario $label $fontSize", fontSize.value > 0f && fontSize.value <= 12f)
                assertEquals("same font for all labels and languages: $scenario $label",
                    referenceFonts.getOrPut(Triple(scenario.width, scenario.fontScale, scenario.scale)) { fontSize }, fontSize)
                assertEquals("single line: $scenario $label", 1, layout.lineCount)
                assertEquals("label weight: $scenario $label", FontWeight.Medium, layout.layoutInput.style.fontWeight)
            }
            destinations.drop(1).forEach { destination ->
                compose.onNode(hasContentDescription(full[destinations.indexOf(destination)]) and hasClickAction(),
                    useUnmergedTree = true).performClick()
                compose.runOnIdle { assertEquals(destination, selected.value) }
                bounds().zip(initial).forEach { (after, before) ->
                    assertEquals("stable left: $scenario", before.left, after.left, 0.5f)
                    assertEquals("stable top: $scenario", before.top, after.top, 0.5f)
                    assertEquals("stable width: $scenario", before.width, after.width, 0.5f)
                    assertEquals("stable height: $scenario", before.height, after.height, 0.5f)
                }
            }
        }
        assertTrue(overflows.joinToString("\n"), overflows.isEmpty())
    }

    private data class Scenario(val language: String, val width: Int, val fontScale: Float, val scale: UiScale)
}
