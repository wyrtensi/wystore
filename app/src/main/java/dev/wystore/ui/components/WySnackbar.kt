package dev.wystore.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The line the app talks back on.
 *
 * Material's default snackbar paints itself in `inverseSurface`, which means it arrives as a
 * near-black slab in a light app and a near-white one in a dark app - inverted from everything
 * around it either way, and read as something gone wrong rather than as an answer. It also lands
 * exactly where the floating navigation bar already is.
 *
 * Both are fixed by staying inside the app's own surface roles, which flip with the theme on
 * their own, and by lifting the bar clear of the navigation.
 */
@Composable
fun WySnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(
        hostState = hostState,
        // Scaffold already lifts the host clear of the bottom bar; this is just the gap above it,
        // so the message reads as belonging to the bar rather than floating in the middle.
        modifier = modifier.padding(bottom = SnackbarBottomInset, start = 8.dp, end = 8.dp)
    ) { data ->
        Snackbar(
            snackbarData = data,
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionColor = MaterialTheme.colorScheme.primary,
            dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val SnackbarBottomInset = 8.dp
