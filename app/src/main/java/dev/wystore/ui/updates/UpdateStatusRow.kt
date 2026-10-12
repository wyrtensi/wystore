package dev.wystore.ui.updates

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import dev.wystore.InstallQueueItem
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedApp
import dev.wystore.data.PendingUpdate
import dev.wystore.ui.components.AppRow
import dev.wystore.ui.components.PackageUiStateReducer

@Composable
fun UpdateStatusRow(
    managed: ManagedApp,
    installed: InstalledApp?,
    pendingUpdate: PendingUpdate?,
    /** Only an install in progress: this row's one action is install-or-open, see the caller. */
    installingItem: InstallQueueItem? = null,
    onOpen: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = PackageUiStateReducer.reduce(
        installed = installed,
        managed = managed,
        pendingUpdate = pendingUpdate,
        queueItem = installingItem,
        resources = LocalResources.current
    )

    AppRow(
        state = state,
        onRowClick = onOpen,
        onPrimaryActionClick = onAction,
        modifier = modifier
    )
}
