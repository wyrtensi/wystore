package dev.wystore.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.material3.MaterialTheme
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
        // A capsule, as in the concept: its ends round all the way, like the tab pills inside it.
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 4.dp,
        border = if (dev.wystore.ui.theme.LocalHubStyle.current) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else null
    ) {
        val labels = listOf(
            stringResource(R.string.nav_home),
            stringResource(R.string.nav_search),
            stringResource(R.string.nav_updates),
            stringResource(R.string.nav_library),
            stringResource(R.string.nav_settings)
        )
        val measurer = rememberTextMeasurer()
        val baseStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
        val density = LocalDensity.current
        BoxWithConstraints {
        // One size for every name, the largest at which the longest still fits its fifth. Each
        // shrinking on its own left "Обновления" a size smaller than its neighbours on a compact
        // phone, and the row looked uneven.
        val itemWidth = with(density) { ((maxWidth - 12.dp) / 5 - 8.dp).roundToPx() }
        val labelSize = remember(labels, itemWidth, baseStyle) {
            var size = 12f
            while (size > 8f && labels.any { label ->
                    measurer.measure(label, baseStyle.copy(fontSize = size.sp), maxLines = 1, softWrap = false)
                        .size.width > itemWidth
                }) size -= 0.5f
            size.sp
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // A minimum rather than a fixed height: at a large system font size the label needs
                // the extra few dp, and pinning the bar at 66 cropped it.
                .heightIn(min = 64.dp)
                // Enough to clear the bar's rounded ends: the first and last labels are as wide
                // as their share of the bar, and at the corners that share is cut by the curve.
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Destination(
                icon = Icons.Outlined.Home,
                label = stringResource(R.string.nav_home),
                destination = WyStoreDestination.Home,
                currentDestination = currentDestination,
                onNavigate = onNavigate,
                labelSize = labelSize
            )
            Destination(
                icon = Icons.Outlined.Search,
                label = stringResource(R.string.nav_search),
                destination = WyStoreDestination.Search,
                currentDestination = currentDestination,
                onNavigate = onNavigate,
                labelSize = labelSize
            )
            Destination(
                icon = Icons.Outlined.Refresh,
                label = stringResource(R.string.nav_updates),
                destination = WyStoreDestination.Updates,
                currentDestination = currentDestination,
                onNavigate = onNavigate,
                labelSize = labelSize
            )
            Destination(
                icon = Icons.AutoMirrored.Outlined.List,
                label = stringResource(R.string.nav_library),
                destination = WyStoreDestination.Library,
                currentDestination = currentDestination,
                onNavigate = onNavigate,
                labelSize = labelSize
            )
            Destination(
                icon = Icons.Outlined.Settings,
                label = stringResource(R.string.nav_settings),
                destination = WyStoreDestination.Settings,
                currentDestination = currentDestination,
                onNavigate = onNavigate,
                labelSize = labelSize
            )
        }
        }
    }
}

/**
 * One tab: icon over its name, and for the open one a pill that holds both - the way the Wy Hub
 * concept marks it.
 *
 * Material's bar item rings only the icon and leaves the name hanging under it, in the same size
 * whether the tab is open or not, which read as a stock template laid over the floating bar.
 */
@Composable
private fun RowScope.Destination(
    icon: ImageVector,
    label: String,
    destination: WyStoreDestination,
    currentDestination: WyStoreDestination,
    onNavigate: (WyStoreDestination) -> Unit,
    labelSize: androidx.compose.ui.unit.TextUnit
) {
    val selected = currentDestination == destination
    val hub = dev.wystore.ui.theme.LocalHubStyle.current
    val colors = MaterialTheme.colorScheme
    val pill by animateColorAsState(
        if (!selected) Color.Transparent else if (hub) colors.primaryContainer else colors.secondaryContainer,
        label = "tab pill"
    )
    val iconTint by animateColorAsState(
        when {
            !selected -> colors.onSurfaceVariant
            hub -> colors.primary
            else -> colors.onSecondaryContainer
        },
        label = "tab icon"
    )
    val textColor = if (selected) colors.onSurface else colors.onSurfaceVariant
    Box(
        // An equal fifth each. Laid out by content width, the first tabs took what they wanted and
        // the last ones were left with whatever remained, which on a compact screen broke
        // "Обновления" across two lines and pushed "Настройки" against the edge.
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 1.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(pill)
            .selectable(selected = selected, role = Role.Tab, onClick = { onNavigate(destination) })
            // Inside the pill, so a long name shrinks to clear its rounded corners rather than
            // being cut by them.
            .padding(horizontal = 3.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // The name below already says where the tab goes, so the icon is not announced twice.
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = labelSize,
                    textAlign = TextAlign.Center,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = textColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
