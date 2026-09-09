package dev.wystore.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wystore.R
import dev.wystore.background.NotificationCoordinator
import dev.wystore.data.StoreSettings
import dev.wystore.ui.components.ScreenPadding
import dev.wystore.ui.components.SectionHeader
import dev.wystore.ui.components.SectionSpacing
import dev.wystore.ui.components.WyCard
import dev.wystore.ui.components.WyDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    settings: StoreSettings,
    onUpdateSettings: (StoreSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_hub_notifications_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = ScreenPadding, end = ScreenPadding, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing)
        ) {
            Text(
                stringResource(R.string.notifications_policy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = stringResource(R.string.notifications_categories_title))
                WyCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        CategoryRow(
                            title = stringResource(R.string.notifications_ready_title),
                            subtitle = stringResource(R.string.notifications_ready_subtitle),
                            checked = settings.readyNotificationsEnabled,
                            onChange = { onUpdateSettings(settings.copy(readyNotificationsEnabled = it)) }
                        )
                        WyDivider(modifier = Modifier.padding(horizontal = 14.dp))
                        CategoryRow(
                            title = stringResource(R.string.notifications_errors_title),
                            subtitle = stringResource(R.string.notifications_errors_subtitle),
                            checked = settings.errorNotificationsEnabled,
                            onChange = { onUpdateSettings(settings.copy(errorNotificationsEnabled = it)) }
                        )
                        WyDivider(modifier = Modifier.padding(horizontal = 14.dp))
                        CategoryRow(
                            title = stringResource(R.string.notifications_summary_title),
                            subtitle = stringResource(R.string.notifications_summary_subtitle),
                            checked = settings.checkSummaryNotificationsEnabled,
                            onChange = { onUpdateSettings(settings.copy(checkSummaryNotificationsEnabled = it)) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    title = stringResource(R.string.notifications_quiet_title),
                    subtitle = stringResource(R.string.notifications_quiet_subtitle)
                )
                WyCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.notifications_quiet_title),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(
                                checked = settings.quietHoursEnabled,
                                onCheckedChange = { onUpdateSettings(settings.copy(quietHoursEnabled = it)) }
                            )
                        }
                        if (settings.quietHoursEnabled) {
                            HourPicker(
                                label = stringResource(R.string.notifications_quiet_from),
                                selected = settings.quietHoursStart,
                                onSelect = { onUpdateSettings(settings.copy(quietHoursStart = it)) }
                            )
                            HourPicker(
                                label = stringResource(R.string.notifications_quiet_to),
                                selected = settings.quietHoursEnd,
                                onSelect = { onUpdateSettings(settings.copy(quietHoursEnd = it)) }
                            )
                        }
                    }
                }
            }

            // Deep links into the system settings for each channel: importance, sound and vibration
            // belong to Android, and this is the only place a user can actually change them.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(title = stringResource(R.string.notifications_channel_links_title))
                WyCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        val channels = listOf(
                            NotificationCoordinator.CHANNEL_READY to R.string.notifications_channel_ready,
                            NotificationCoordinator.CHANNEL_ERRORS to R.string.notifications_channel_errors,
                            NotificationCoordinator.CHANNEL_TRANSFERS to R.string.notifications_channel_transfers,
                            NotificationCoordinator.CHANNEL_CHECKS to R.string.notifications_channel_checks
                        )
                        channels.forEachIndexed { index, (channelId, labelRes) ->
                            if (index > 0) WyDivider(modifier = Modifier.padding(horizontal = 14.dp))
                            TextButton(
                                onClick = { openChannelSettings(context, channelId) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(labelRes),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Hours as a scrolling rail: a 24-entry dropdown is worse to use than a row you can flick. */
@Composable
private fun HourPicker(label: String, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 1).coerceAtLeast(0))
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items((0..23).toList(), key = { it }) { hour ->
                FilterChip(
                    selected = hour == selected,
                    onClick = { onSelect(hour) },
                    label = { Text(stringResource(R.string.notifications_hour_value, hour)) }
                )
            }
        }
    }
}

private fun openChannelSettings(context: android.content.Context, channelId: String) {
    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
    runCatching { context.startActivity(intent) }
}
