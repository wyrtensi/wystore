package dev.wystore.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
                .height(66.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
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
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
