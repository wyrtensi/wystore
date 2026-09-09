package dev.wystore.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.wystore.R

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

/**
 * What the install queue is doing, in the strip the snackbars use.
 *
 * Android confirms one install at a time, so pressing "Install" on several apps means waiting -
 * and the wait used to look like nothing happening at all, since only the first dialog appeared
 * and the rest of the taps left no trace on screen.
 */
@Composable
fun InstallQueueBanner(
    current: String?,
    waiting: Int,
    modifier: Modifier = Modifier
) {
    if (current == null && waiting == 0) return
    Surface(
        modifier = modifier
            .padding(bottom = SnackbarBottomInset, start = 8.dp, end = 8.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WySpinner(size = 18.dp, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (waiting > 0) {
                    stringResource(R.string.install_queue_waiting, waiting)
                } else {
                    stringResource(R.string.install_queue_current)
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val SnackbarBottomInset = 8.dp
