package dev.wystore.ui.settings

import dev.wystore.ui.components.fadingTopEdge
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import dev.wystore.ui.components.color
import dev.wystore.ui.components.HubPageHeader
import dev.wystore.permissions.openUnknownSourcesSettings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.wystore.permissions.NotificationPermissionAction
import dev.wystore.permissions.NotificationPermissionPolicy
import dev.wystore.permissions.NotificationPermissionStore
import dev.wystore.permissions.PermissionRepository
import dev.wystore.permissions.VendorBackgroundSettings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import dev.wystore.ui.components.StateBadge
import dev.wystore.R
import dev.wystore.ui.components.LocalBottomBarInset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import dev.wystore.ui.components.WyCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionCenterScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    rootAvailable: Boolean? = null,
    onCheckRoot: () -> Unit = {}
) {
    val context = LocalContext.current
    val permissionRepository = remember(context) { PermissionRepository(context) }

    // Every card here sends the user into Android Settings to change the very state it displays.
    // Reading it once left the screen showing stale answers after they came back, so it is re-read
    // on each resume through the same repository seam the queue coordinator uses.
    var snapshot by remember { mutableStateOf(permissionRepository.snapshot()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, permissionRepository) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                snapshot = permissionRepository.snapshot()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Android's own dialog where it can still appear, Settings where it cannot. It is one-shot:
    // after a refusal the system never shows it again, and the button has to stop pretending.
    val permissionStore = remember(context) { NotificationPermissionStore(context) }
    var notificationsAsked by remember { mutableStateOf(permissionStore.asked()) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        snapshot = permissionRepository.snapshot()
    }

    val vendorBackgroundIntent = remember(context) {
        runCatching { VendorBackgroundSettings.intentFor(context) }.getOrNull()
    }

    val notificationsEnabled = snapshot.notificationsGranted
    val installUnknownSourcesEnabled = snapshot.canInstallUnknownApps
    val ignoringBatteryOptimizations = snapshot.batteryOptimizationsIgnored

    Scaffold(
        modifier = modifier,
        topBar = {
            // The concept's page header: a round back button, where the page belongs as the
            // accent line, the title in plain weight.
            HubPageHeader(
                title = stringResource(R.string.permissions_title),
                eyebrow = stringResource(R.string.settings_title),
                onBack = onBack
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()).fadingTopEdge()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp + LocalBottomBarInset.current),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // The concept's Permissions screen opens with how many are ready, as a number and a
            // ring; the intro becomes its caption.
            val ready = listOf(notificationsEnabled, installUnknownSourcesEnabled, ignoringBatteryOptimizations).count { it }
            dev.wystore.ui.components.HubPanel(tint = if (ready == 3) READY_GREEN else MaterialTheme.colorScheme.primary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.permissions_hero_title, ready, 3),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                        )
                        Text(
                            stringResource(R.string.permissions_intro),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.width(14.dp))
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
                        androidx.compose.material3.CircularProgressIndicator(
                            progress = { ready / 3f },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 5.dp,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                            gapSize = 4.dp,
                            color = if (ready == 3) READY_GREEN else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        Text("$ready/3", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // 1. Notifications
            WyCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.permissions_notifications_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        StateBadge(
                            text = stringResource(if (notificationsEnabled) R.string.permissions_state_allowed else R.string.permissions_state_off),
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = if (notificationsEnabled) READY_GREEN else MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        stringResource(R.string.permissions_notifications_text),
                        style = MaterialTheme.typography.bodySmall
                    )
                    FilledTonalButton(
                        onClick = {
                            val action = NotificationPermissionPolicy.actionForRequest(
                                granted = notificationsEnabled,
                                alreadyAsked = notificationsAsked
                            )
                            if (action == NotificationPermissionAction.ASK_SYSTEM &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ) {
                                permissionStore.markAsked()
                                notificationsAsked = true
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.permissions_notifications_button))
                    }
                }
            }

            // 2. Installing unknown apps
            WyCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.permissions_unknown_sources_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        StateBadge(
                            text = stringResource(if (installUnknownSourcesEnabled) R.string.permissions_state_allowed else R.string.permissions_state_denied),
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = if (installUnknownSourcesEnabled) READY_GREEN else MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        stringResource(R.string.permissions_unknown_sources_text),
                        style = MaterialTheme.typography.bodySmall
                    )
                    FilledTonalButton(
                        onClick = {
                            context.openUnknownSourcesSettings()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.permissions_unknown_sources_button))
                    }
                }
            }

            // 3. Background work (battery)
            WyCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.permissions_background_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        StateBadge(
                            text = stringResource(if (ignoringBatteryOptimizations) R.string.permissions_state_unrestricted else R.string.permissions_state_optimized),
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = if (ignoringBatteryOptimizations) READY_GREEN else PAUSED_YELLOW
                        )
                    }
                    Text(
                        stringResource(R.string.permissions_background_text),
                        style = MaterialTheme.typography.bodySmall
                    )
                    FilledTonalButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.permissions_background_button))
                    }
                }
            }

            // 3a. The vendor's own background rules, where the vendor has any. No badge: the
            // list cannot be read, and a card that guessed "on" or "off" here would be guessing.
            vendorBackgroundIntent?.let { intent ->
                WyCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.permissions_vendor_title),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            stringResource(R.string.permissions_vendor_text),
                            style = MaterialTheme.typography.bodySmall
                        )
                        FilledTonalButton(
                            onClick = { runCatching { context.startActivity(intent) } },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(stringResource(R.string.permissions_vendor_button))
                        }
                    }
                }
            }

            // 4. Root access
            WyCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.permissions_root_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        StateBadge(
                            text = stringResource(
                                when (rootAvailable) {
                                    true -> R.string.permissions_root_available
                                    false -> R.string.permissions_root_denied
                                    null -> R.string.permissions_root_unchecked
                                }
                            )
                        )
                    }
                    Text(
                        stringResource(R.string.permissions_root_text),
                        style = MaterialTheme.typography.bodySmall
                    )
                    FilledTonalButton(
                        onClick = onCheckRoot,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.permissions_root_button))
                    }
                }
            }
        }
    }
}

/** The tones the status tags are told in: see StatusTone. */
private val READY_GREEN: androidx.compose.ui.graphics.Color
    @Composable get() = dev.wystore.ui.components.StatusTone.READY.color()!!
private val PAUSED_YELLOW: androidx.compose.ui.graphics.Color
    @Composable get() = dev.wystore.ui.components.StatusTone.PAUSED.color()!!
