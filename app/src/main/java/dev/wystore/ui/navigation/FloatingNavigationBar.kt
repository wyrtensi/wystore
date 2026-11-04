package dev.wystore.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import dev.wystore.R
import dev.wystore.ui.theme.Manrope
import dev.wystore.ui.theme.ThemePolicy
import java.util.Locale

/** Five fixed destinations in a floating bar. */
@Composable
fun FloatingNavigationBar(
    currentDestination: WyStoreDestination,
    onNavigate: (WyStoreDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavigationItem(WyStoreDestination.Home, Icons.Outlined.Home, stringResource(R.string.bottom_nav_home), stringResource(R.string.nav_home)),
        NavigationItem(WyStoreDestination.Search, Icons.Outlined.Search, stringResource(R.string.bottom_nav_search), stringResource(R.string.nav_search)),
        NavigationItem(WyStoreDestination.Updates, Icons.Outlined.Refresh, stringResource(R.string.bottom_nav_updates), stringResource(R.string.nav_updates)),
        NavigationItem(WyStoreDestination.Library, Icons.AutoMirrored.Outlined.List, stringResource(R.string.bottom_nav_library), stringResource(R.string.nav_library)),
        NavigationItem(WyStoreDestination.Settings, Icons.Outlined.Settings, stringResource(R.string.bottom_nav_settings), stringResource(R.string.nav_settings))
    )
    val density = LocalDensity.current
    val context = LocalContext.current.applicationContext
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val baseStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp,
        letterSpacing = 0.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
        platformStyle = PlatformTextStyle(includeFontPadding = false), lineBreak = LineBreak.Heading)
    BoxWithConstraints(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp)) {
        // Row distributes leftover pixels among cells, so measure at the narrowest actual cell width.
        val captionWidth = with(density) {
            ((constraints.maxWidth - 2 * 4.dp.roundToPx()) / 5 - 2 * 3.dp.roundToPx()).coerceAtLeast(0)
        }
        val metricStyle = baseStyle.copy(fontFamily = FontFamily.Default)
        // Fit one size to all shipped captions, so changing languages or selection never moves a tab.
        // TextMeasurer uses the actual fonts and Android's nonlinear font scaling.
        val fitted = remember(density, measurer, context, captionWidth, metricStyle) {
            val ids = listOf(R.string.bottom_nav_home, R.string.bottom_nav_search, R.string.bottom_nav_updates,
                R.string.bottom_nav_library, R.string.bottom_nav_settings)
            val captions = listOf("en", "ru", "be", "uk", "lv", "kk", "sr", "uz", "zh").flatMap { language ->
                val configuration = android.content.res.Configuration(context.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(language))
                }
                val localized = context.createConfigurationContext(configuration)
                val family = if (ThemePolicy.brandFontCovers(language)) Manrope else FontFamily.Default
                ids.map { id -> localized.getString(id) to family }
            }
            fun measure(text: String, family: FontFamily, size: Float) =
                measurer.measure(text, style = metricStyle.copy(fontFamily = family,
                    fontSize = size.sp, lineHeight = (size * 4f / 3f).sp),
                    constraints = Constraints(maxWidth = captionWidth), maxLines = 1, softWrap = false)
            fun fits(size: Float) = captions.all { (text, family) -> !measure(text, family, size).hasVisualOverflow }
            var lower = 0f
            var upper = 12f
            if (fits(upper)) {
                lower = upper
            } else {
                repeat(14) {
                    val candidate = (lower + upper) / 2f
                    if (fits(candidate)) lower = candidate else upper = candidate
                }
            }
            val height = captions.maxOf { (text, family) -> measure(text, family, lower).size.height }
            lower.sp to with(density) { height.toDp() }
        }
        val labelStyle = baseStyle.copy(fontSize = fitted.first,
            lineHeight = (fitted.first.value * 4f / 3f).sp)
        val labelHeight = fitted.second
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 4.dp,
            border = if (dev.wystore.ui.theme.LocalHubStyle.current) {
                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            } else null
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).selectableGroup()) {
                items.forEach { item ->
                    val selected = item.destination == currentDestination
                    Column(
                        Modifier.weight(1f)
                            .selectable(selected, role = Role.Tab, onClick = { onNavigate(item.destination) })
                            .semantics { contentDescription = item.title }
                            .padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(width = 44.dp, height = 30.dp)
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(item.icon, contentDescription = null, modifier = Modifier.size(22.dp),
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(item.label, modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp).height(labelHeight),
                            style = labelStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

private data class NavigationItem(val destination: WyStoreDestination, val icon: ImageVector,
    val label: String, val title: String)
