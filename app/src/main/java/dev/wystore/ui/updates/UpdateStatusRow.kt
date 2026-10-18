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
import dev.wystore.ui.components.installerShortLabel

@Composable
fun UpdateStatusRow(
    managed: ManagedApp,
    installed: InstalledApp?,
    pendingUpdate: PendingUpdate?,
    /** Only an install in progress: this row's one action is install-or-open, see the caller. */
    installingItem: InstallQueueItem? = null,
    onOpen: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    onWhatsNew: (() -> Unit)? = null,
    groupPosition: dev.wystore.ui.components.GroupPosition? = null
) {
    val state = PackageUiStateReducer.reduce(
        installed = installed,
        managed = managed,
        pendingUpdate = pendingUpdate,
        queueItem = installingItem,
        resources = LocalResources.current
    )

    // Who installed it, as on My apps: "RuStore" is the RuStore app's install, "Wy Store" is ours.
    // The badge used to name where Wy Store looks for updates, so both read "RuStore".
    val installer = installed?.let { installerShortLabel(it) }
    AppRow(
        state = if (installer != null) state.copy(sourceProvenance = installer) else state,
        onRowClick = onOpen,
        onPrimaryActionClick = onAction,
        modifier = modifier,
        onWhatsNew = onWhatsNew,
        groupPosition = groupPosition
    )
}
