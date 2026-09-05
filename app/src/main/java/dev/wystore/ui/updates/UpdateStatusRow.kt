package dev.wystore.ui.updates

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
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
    onOpen: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = PackageUiStateReducer.reduce(
        installed = installed,
        managed = managed,
        pendingUpdate = pendingUpdate,
        resources = LocalResources.current
    )

    AppRow(
        state = state,
        onRowClick = onOpen,
        onPrimaryActionClick = onAction,
        modifier = modifier
    )
}
