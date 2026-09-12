package dev.wystore.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wystore.R

/**
 * The bottom bar, floating clear of the screen edges so the content behind it reads as a
 * continuous surface rather than being cut off by a full-width band.
 */
@Composable
fun FloatingNavigationBar(
    currentDestination: WyStoreDestination,
    onNavigate: (WyStoreDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // A minimum rather than a fixed height: at a large system font size the label needs
                // the extra few dp, and pinning the bar at 66 cropped it.
                .heightIn(min = 66.dp)
                // Enough to clear the pill's rounded ends: the first and last labels are as wide
                // as their share of the bar, and at the corners that share is cut by the curve.
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Destination(
                icon = Icons.Outlined.Home,
                label = stringResource(R.string.nav_home),
                destination = WyStoreDestination.Home,
                currentDestination = currentDestination,
                onNavigate = onNavigate
            )
            Destination(
                icon = Icons.Outlined.Search,
                label = stringResource(R.string.nav_search),
                destination = WyStoreDestination.Search,
                currentDestination = currentDestination,
                onNavigate = onNavigate
            )
            Destination(
                icon = Icons.Outlined.Refresh,
                label = stringResource(R.string.nav_updates),
                destination = WyStoreDestination.Updates,
                currentDestination = currentDestination,
                onNavigate = onNavigate
            )
            Destination(
                icon = Icons.AutoMirrored.Outlined.List,
                label = stringResource(R.string.nav_library),
                destination = WyStoreDestination.Library,
                currentDestination = currentDestination,
                onNavigate = onNavigate
            )
            Destination(
                icon = Icons.Outlined.Settings,
                label = stringResource(R.string.nav_settings),
                destination = WyStoreDestination.Settings,
                currentDestination = currentDestination,
                onNavigate = onNavigate
            )
        }
    }
}

@Composable
private fun RowScope.Destination(
    icon: ImageVector,
    label: String,
    destination: WyStoreDestination,
    currentDestination: WyStoreDestination,
    onNavigate: (WyStoreDestination) -> Unit
) {
    NavigationBarItem(
        // An equal fifth each. Laid out by content width, the first tabs took what they wanted and
        // the last ones were left with whatever remained, which on a compact screen broke
        // "Обновления" across two lines and pushed "Настройки" against the edge.
        modifier = Modifier.weight(1f),
        selected = currentDestination == destination,
        onClick = { onNavigate(destination) },
        icon = {
            Icon(
                imageVector = icon,
                // The label below already names the destination, so the icon is decorative for a
                // screen reader rather than announced twice.
                contentDescription = null
            )
        },
        label = {
            // The label shrinks to fit its fifth of the bar instead of wrapping or being clipped,
            // which is what a long word plus a large system font size used to do to it.
            //
            // softWrap has to stay on for that: turned off, the text is measured against an
            // unbounded width, every size "fits", and the largest one is chosen and then cropped by
            // the bar - which is exactly what the Ukrainian "Налаштування" did. maxLines keeps it
            // on one line; the shrinking is what makes that line fit.
            BasicText(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = LocalContentColor.current,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 12.sp)
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
