package dev.wystore.ui

import androidx.compose.runtime.Composable
import dev.wystore.StoreViewModel
import dev.wystore.ui.navigation.WyStoreRoot

@Composable
fun WyStoreApp(
    viewModel: StoreViewModel,
    onInstallPending: (String) -> Unit
) {
    WyStoreRoot(
        viewModel = viewModel,
        onInstallPending = onInstallPending
    )
}
