package dev.wystore.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.wystore.StoreViewModel
import dev.wystore.device.DeviceProfile
import dev.wystore.device.DeviceProfilePolicy
import dev.wystore.device.DeviceTraits
import dev.wystore.ui.navigation.WyStoreRoot
import dev.wystore.ui.tv.TvApp

/**
 * Picks the interface: the phone one unless this device is, or is set to be, a TV.
 *
 * Read from settings on every change, so switching the device type in Settings swaps the interface
 * at once rather than on the next launch.
 */
@Composable
fun WyStoreApp(
    viewModel: StoreViewModel,
    onInstallPending: (String) -> Unit
) {
    val context = LocalContext.current
    val traits = remember(context) { DeviceTraits.read(context) }
    val state by viewModel.state.collectAsState()
    when (DeviceProfilePolicy.resolve(state.settings.deviceType, traits)) {
        DeviceProfile.PHONE -> WyStoreRoot(
            viewModel = viewModel,
            onInstallPending = onInstallPending
        )
        DeviceProfile.TV -> TvApp(
            viewModel = viewModel,
            onInstallPending = onInstallPending
        )
    }
}
