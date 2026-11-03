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
    val items = listOf<Triple<WyStoreDestination, ImageVector, String>>(
        Triple(WyStoreDestination.Home, Icons.Outlined.Home, stringResource(R.string.nav_home)),
        Triple(WyStoreDestination.Search, Icons.Outlined.Search, stringResource(R.string.nav_search)),
        Triple(WyStoreDestination.Updates, Icons.Outlined.Refresh, stringResource(R.string.nav_updates)),
        Triple(WyStoreDestination.Library, Icons.AutoMirrored.Outlined.List, stringResource(R.string.nav_library)),
        Triple(WyStoreDestination.Settings, Icons.Outlined.Settings, stringResource(R.string.nav_settings))
    )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelSize = 12.sp
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = labelSize, fontWeight = FontWeight.SemiBold)
    BoxWithConstraints(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        val available = with(density) { (maxWidth - 12.dp).toPx() }
        val required = remember(items.map { it.third }, labelStyle, density) {
            items.map { measurer.measure(it.third, labelStyle, maxLines = 1, softWrap = false).size.width +
                with(density) { 8.dp.roundToPx() } }
        }
        // Give each name the width it needs. Equal columns wasted space beside short labels
        // and forced ordinary screens into a second row just for a longer destination name.
        val rowSizes = dev.wystore.ui.components.NavigationLayoutPolicy.rows(required, available.toInt().coerceAtLeast(0))
        var nextItem = 0
        val rows = rowSizes.map { count -> items.subList(nextItem, nextItem + count).also { nextItem += count } }
        Surface(
            shape = if (rows.size == 1) RoundedCornerShape(percent = 50) else RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 4.dp,
            border = if (dev.wystore.ui.theme.LocalHubStyle.current) {
                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            } else null
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                var cursor = 0
                rows.forEach { row ->
                    val widths = required.subList(cursor, cursor + row.size)
                    val extra = ((available - widths.sum()) / row.size).coerceAtLeast(0f)
                    cursor += row.size
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                        row.forEachIndexed { index, (destination, icon, label) ->
                            Destination(icon, label, destination, currentDestination, onNavigate, labelSize,
                                (widths[index] + extra).coerceAtLeast(1f))
                        }
                    }
                }
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
    labelSize: androidx.compose.ui.unit.TextUnit,
    weight: Float
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
        // The measured label width and an equal share of spare space preserve every destination.
        modifier = Modifier
            .weight(weight)
            .padding(horizontal = 1.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(pill)
            .selectable(selected = selected, role = Role.Tab, onClick = { onNavigate(destination) })
            // Keep text clear of the selection pill without changing its font size.
            .padding(horizontal = 3.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // The name below already says where the tab goes, so the icon is not announced twice.
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
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
