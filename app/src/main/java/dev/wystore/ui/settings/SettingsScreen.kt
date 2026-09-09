package dev.wystore.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import dev.wystore.BuildConfig
import dev.wystore.InstallQueueItem
import dev.wystore.R
import dev.wystore.selfupdate.SelfUpdateStatus
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import dev.wystore.RuStoreCompatibilityTask
import dev.wystore.data.PendingUpdate
import dev.wystore.data.RuStoreCompatibility
import dev.wystore.data.StoreSettings
import dev.wystore.updates.model.QueueMode
import dev.wystore.ui.components.LocalBottomBarInset
import dev.wystore.ui.components.CompactSettingSwitch
import dev.wystore.ui.components.ScreenPadding
import dev.wystore.ui.components.SectionHeader
import dev.wystore.ui.components.SectionSpacing
import dev.wystore.ui.components.SourceDisclaimer
import dev.wystore.ui.components.WyCard
import dev.wystore.ui.components.WyDivider

private enum class SettingsSubScreen {
    PERMISSIONS,
    APPEARANCE,
    LANGUAGE,
    NOTIFICATIONS,
    SOURCES,
    ABOUT
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    settings: StoreSettings,
    ruStoreCompatibility: RuStoreCompatibility,
    ruStoreCompatibilityTask: RuStoreCompatibilityTask?,
    rootAvailable: Boolean?,
    managedCount: Int,
    githubCount: Int,
    onSave: (StoreSettings) -> Unit,
    onCheckRoot: () -> Unit,
    onCheckRuStore: () -> Unit,
    onSetRuStoreVersionCode: (Long) -> Unit,
    onExportUri: (android.net.Uri) -> Unit,
    onImportUri: (android.net.Uri, Boolean) -> Unit,
    onExportJson: () -> String,
    onRestoreJson: (String, Boolean) -> Unit,
    onOpenGitHub: () -> Unit = {},
    selfUpdate: SelfUpdateStatus = SelfUpdateStatus.Idle,
    selfUpdateQueueItem: InstallQueueItem? = null,
    selfUpdatePending: PendingUpdate? = null,
    onCheckSelfUpdate: () -> Unit = {},
    onInstallSelfUpdate: () -> Unit = {},
    onInstallDownloadedSelfUpdate: () -> Unit = {},
    onCancelSelfUpdateDownload: (String) -> Unit = {}
) {
    var subScreen by remember { mutableStateOf<SettingsSubScreen?>(null) }
    var editedSettings by remember(settings) { mutableStateOf(settings) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showManualRuStoreDialog by remember { mutableStateOf(false) }
    var manualRuStoreInput by remember(ruStoreCompatibility.apiVersionCode) { mutableStateOf(ruStoreCompatibility.apiVersionCode.toString()) }
    val haptic = LocalHapticFeedback.current

    when (subScreen) {
        SettingsSubScreen.PERMISSIONS -> {
            PermissionCenterScreen(
                onBack = { subScreen = null },
                rootAvailable = rootAvailable,
                onCheckRoot = onCheckRoot
            )
            return
        }
        SettingsSubScreen.APPEARANCE -> {
            AppearanceSettingsScreen(
                settings = editedSettings,
                onUpdateSettings = {
                    editedSettings = it
                    onSave(it)
                },
                onBack = { subScreen = null }
            )
            return
        }
        SettingsSubScreen.LANGUAGE -> {
            LanguageSettingsScreen(
                settings = editedSettings,
                onUpdateSettings = {
                    editedSettings = it
                    onSave(it)
                },
                onBack = { subScreen = null }
            )
            return
        }
        SettingsSubScreen.NOTIFICATIONS -> {
            NotificationSettingsScreen(
                settings = editedSettings,
                onUpdateSettings = {
                    editedSettings = it
                    onSave(it)
                },
                onBack = { subScreen = null }
            )
            return
        }
        SettingsSubScreen.SOURCES -> {
            SourceSettingsScreen(
                settings = editedSettings,
                ruStoreCompatibility = ruStoreCompatibility,
                ruStoreCompatibilityTask = ruStoreCompatibilityTask,
                githubRepositoriesCount = githubCount,
                onUpdateSettings = {
                    editedSettings = it
                    onSave(it)
                },
                onCheckRuStore = onCheckRuStore,
                onManualRuStoreCode = { showManualRuStoreDialog = true },
                onBack = { subScreen = null }
            )
            return
        }
        SettingsSubScreen.ABOUT -> {
            AboutSettingsScreen(
                settings = editedSettings,
                status = selfUpdate,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                onUpdateSettings = {
                    editedSettings = it
                    onSave(it)
                },
                onCheck = onCheckSelfUpdate,
                onInstall = onInstallSelfUpdate,
                onBack = { subScreen = null },
                queueItem = selfUpdateQueueItem,
                pendingUpdate = selfUpdatePending,
                onInstallDownloaded = onInstallDownloadedSelfUpdate,
                onCancelDownload = onCancelSelfUpdateDownload
            )
            return
        }
        null -> Unit
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    top = 4.dp,
                    bottom = 28.dp + LocalBottomBarInset.current
                ),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing)
        ) {
            // One grouped list rather than six separate cards: the destinations read as a menu,
            // which is what they are.
            WyCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsHubRow(
                        title = stringResource(R.string.settings_hub_permissions_title),
                        subtitle = stringResource(R.string.settings_hub_permissions_subtitle),
                        onClick = { subScreen = SettingsSubScreen.PERMISSIONS }
                    )
                    SettingsHubDivider()
                    SettingsHubRow(
                        title = stringResource(R.string.settings_hub_appearance_title),
                        subtitle = stringResource(R.string.settings_hub_appearance_subtitle),
                        onClick = { subScreen = SettingsSubScreen.APPEARANCE }
                    )
                    SettingsHubDivider()
                    SettingsHubRow(
                        title = stringResource(R.string.settings_hub_language_title),
                        subtitle = stringResource(R.string.settings_hub_language_subtitle),
                        onClick = { subScreen = SettingsSubScreen.LANGUAGE }
                    )
                    SettingsHubDivider()
                    SettingsHubRow(
                        title = stringResource(R.string.settings_hub_notifications_title),
                        subtitle = stringResource(R.string.settings_hub_notifications_subtitle),
                        onClick = { subScreen = SettingsSubScreen.NOTIFICATIONS }
                    )
                    SettingsHubDivider()
                    SettingsHubRow(
                        title = stringResource(R.string.settings_hub_sources_title),
                        subtitle = stringResource(R.string.settings_hub_sources_subtitle, ruStoreCompatibility.apiVersionCode),
                        onClick = { subScreen = SettingsSubScreen.SOURCES }
                    )
                    // The GitHub Releases screen had no reachable entry point anywhere in the app.
                    if (editedSettings.githubEnabled) {
                        SettingsHubDivider()
                        SettingsHubRow(
                            title = "GitHub Releases",
                            subtitle = if (githubCount > 0) {
                                stringResource(R.string.settings_hub_github_subtitle_count, githubCount)
                            } else {
                                stringResource(R.string.settings_hub_github_subtitle_empty)
                            },
                            onClick = onOpenGitHub
                        )
                    }
                    SettingsHubDivider()
                    SettingsHubRow(
                        title = stringResource(R.string.settings_hub_about_title),
                        subtitle = stringResource(R.string.settings_hub_about_subtitle),
                        onClick = { subScreen = SettingsSubScreen.ABOUT }
                    )
                }
            }

            SettingsGroup(
                title = stringResource(R.string.settings_background_title),
                subtitle = stringResource(R.string.settings_background_hint)
            ) {
                SettingsChoiceRow(label = stringResource(R.string.settings_network)) {
                    FilterChip(
                        selected = editedSettings.wifiOnly,
                        onClick = {
                            editedSettings = editedSettings.copy(wifiOnly = true, allowMobileData = false)
                            onSave(editedSettings)
                        },
                        label = { Text("Wi‑Fi") }
                    )
                    FilterChip(
                        selected = editedSettings.allowMobileData,
                        onClick = {
                            editedSettings = editedSettings.copy(wifiOnly = false, allowMobileData = true)
                            onSave(editedSettings)
                        },
                        label = { Text(stringResource(R.string.settings_network_any)) }
                    )
                }

                SettingsChoiceRow(label = stringResource(R.string.settings_interval)) {
                    listOf(6L, 12L, 24L).forEach { hours ->
                        FilterChip(
                            selected = editedSettings.updateIntervalHours == hours,
                            onClick = {
                                editedSettings = editedSettings.copy(updateIntervalHours = hours)
                                onSave(editedSettings)
                            },
                            label = { Text(stringResource(R.string.settings_interval_hours, hours)) }
                        )
                    }
                }

                WyDivider()

                CompactSettingSwitch(stringResource(R.string.settings_charging_only), editedSettings.requiresCharging) {
                    editedSettings = editedSettings.copy(requiresCharging = it)
                    onSave(editedSettings)
                }

                // WorkManager has no battery-saver constraint, so the worker checks this itself
                // and skips the round rather than doing network work while the device is saving.
                CompactSettingSwitch(
                    stringResource(R.string.settings_respect_battery_saver),
                    editedSettings.respectBatterySaver
                ) {
                    editedSettings = editedSettings.copy(respectBatterySaver = it)
                    onSave(editedSettings)
                }

                CompactSettingSwitch(
                    stringResource(R.string.settings_silent_root_install),
                    editedSettings.backgroundRootUpdates,
                    enabled = rootAvailable == true
                ) {
                    editedSettings = editedSettings.copy(backgroundRootUpdates = it, rootSilentInstallEnabled = it)
                    onSave(editedSettings)
                }

                // Was persisted and readable by the scheduler but had no control anywhere.
                CompactSettingSwitch(
                    stringResource(R.string.settings_root_background_downloads),
                    editedSettings.rootBackgroundDownloadsEnabled,
                    enabled = rootAvailable == true
                ) {
                    editedSettings = editedSettings.copy(rootBackgroundDownloadsEnabled = it)
                    onSave(editedSettings)
                }
            }

            // The permission for this is granted at install time and asks the user nothing; the
            // switch exists because "updates itself quietly" should still be a choice.
            SettingsGroup(
                title = stringResource(R.string.settings_silent_updates_title),
                subtitle = stringResource(R.string.settings_silent_updates_hint)
            ) {
                CompactSettingSwitch(
                    stringResource(R.string.settings_silent_updates_switch),
                    editedSettings.silentUpdatesEnabled
                ) {
                    editedSettings = editedSettings.copy(silentUpdatesEnabled = it)
                    onSave(editedSettings)
                }
            }

            // Fetching was root-only, so on an ordinary phone a check announced an update and
            // then stood still until the user went looking for it on another screen.
            SettingsGroup(
                title = stringResource(R.string.settings_auto_download_title),
                subtitle = stringResource(R.string.settings_auto_download_hint)
            ) {
                CompactSettingSwitch(
                    stringResource(R.string.settings_auto_download_updates),
                    editedSettings.autoDownloadUpdates
                ) {
                    editedSettings = editedSettings.copy(autoDownloadUpdates = it)
                    onSave(editedSettings)
                }
            }

            // Without root the install still needs Android's dialog, so this means "ask me the
            // moment it is downloaded" rather than "install behind my back".
            SettingsGroup(
                title = stringResource(R.string.settings_auto_install_title),
                subtitle = stringResource(R.string.settings_auto_install_hint)
            ) {
                CompactSettingSwitch(
                    stringResource(R.string.settings_auto_install_updates),
                    editedSettings.autoInstallUpdates
                ) {
                    editedSettings = editedSettings.copy(autoInstallUpdates = it)
                    onSave(editedSettings)
                }
                CompactSettingSwitch(
                    stringResource(R.string.settings_auto_install_new_apps),
                    editedSettings.autoInstallNewApps
                ) {
                    editedSettings = editedSettings.copy(autoInstallNewApps = it)
                    onSave(editedSettings)
                }
                // Off by default: the library switch is meant to make an app disappear from all of
                // this, not to keep offering it in a different place.
                CompactSettingSwitch(
                    stringResource(R.string.settings_show_excluded_updates),
                    editedSettings.showExcludedUpdates
                ) {
                    editedSettings = editedSettings.copy(showExcludedUpdates = it)
                    onSave(editedSettings)
                }
            }

            // The queue coordinator has always read this; there was simply no way to set it.
            SettingsGroup(
                title = stringResource(R.string.settings_queue_mode),
                subtitle = stringResource(R.string.settings_queue_mode_hint)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = editedSettings.queueMode == QueueMode.SMART_PROMPTS,
                        onClick = {
                            editedSettings = editedSettings.copy(queueMode = QueueMode.SMART_PROMPTS)
                            onSave(editedSettings)
                        },
                        label = { Text(stringResource(R.string.settings_queue_mode_smart)) }
                    )
                    FilterChip(
                        selected = editedSettings.queueMode == QueueMode.MANUAL_ONE_BY_ONE,
                        onClick = {
                            editedSettings = editedSettings.copy(queueMode = QueueMode.MANUAL_ONE_BY_ONE)
                            onSave(editedSettings)
                        },
                        label = { Text(stringResource(R.string.settings_queue_mode_manual)) }
                    )
                }
            }

            // Storage and retention. These drive QueueRepository.cleanupWithSettings; they used to
            // be rendered as read-only text, so the user could not change either bound.
            SettingsGroup(
                title = stringResource(R.string.settings_storage_title),
                subtitle = stringResource(
                    R.string.settings_storage_summary,
                    editedSettings.artifactRetentionDays,
                    editedSettings.artifactStorageLimitMb
                )
            ) {
                // The label sits above its chips and the chips wrap: a fixed-width label plus four
                // chips on one row pushes the last chip off-screen, where it renders one letter per line.
                Text(
                    stringResource(R.string.settings_retention_days),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(3, 7, 14, 30).forEach { days ->
                        FilterChip(
                            selected = editedSettings.artifactRetentionDays == days,
                            onClick = {
                                editedSettings = editedSettings.copy(artifactRetentionDays = days)
                                onSave(editedSettings)
                            },
                            label = { Text(stringResource(R.string.settings_retention_days_value, days)) }
                        )
                    }
                }
                Text(
                    stringResource(R.string.settings_storage_limit),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(512, 1_024, 2_048, 4_096).forEach { megabytes ->
                        FilterChip(
                            selected = editedSettings.artifactStorageLimitMb == megabytes,
                            onClick = {
                                editedSettings = editedSettings.copy(artifactStorageLimitMb = megabytes)
                                onSave(editedSettings)
                            },
                            label = { Text(stringResource(R.string.settings_storage_limit_value, megabytes)) }
                        )
                    }
                }
            }

            // Backup section
            BackupSettingsSection(
                managedCount = managedCount,
                githubCount = githubCount,
                onExportUri = onExportUri,
                onImportUri = onImportUri,
                onExportJson = onExportJson,
                onRestoreJson = onRestoreJson
            )

            // The standing disclaimer, reachable from Settings as well as from every install page.
            SourceDisclaimer(sourceLine = stringResource(R.string.disclaimer_sources_both))

            TextButton(
                onClick = { showPrivacyDialog = true },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.settings_privacy_link))
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text(stringResource(R.string.settings_privacy_title)) },
            text = { Text(stringResource(R.string.settings_privacy_text)) },
            confirmButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text(stringResource(R.string.common_got_it)) } }
        )
    }

    if (showManualRuStoreDialog) {
        AlertDialog(
            onDismissRequest = { showManualRuStoreDialog = false },
            title = { Text(stringResource(R.string.settings_rustore_code_title)) },
            text = {
                OutlinedTextField(
                    value = manualRuStoreInput,
                    onValueChange = { manualRuStoreInput = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.settings_rustore_code_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    manualRuStoreInput.toLongOrNull()?.let(onSetRuStoreVersionCode)
                    showManualRuStoreDialog = false
                }) { Text(stringResource(R.string.common_apply)) }
            },
            dismissButton = { TextButton(onClick = { showManualRuStoreDialog = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

/** One destination inside the settings menu card. */
@Composable
private fun SettingsHubRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            // The row title already names the destination.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The hairline between two menu rows, inset past the text so the group reads as one block. */
@Composable
private fun SettingsHubDivider() {
    WyDivider(modifier = Modifier.padding(start = 16.dp))
}

/** A titled block of related settings, boxed so the screen is not one long undivided column. */
@Composable
private fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title = title, subtitle = subtitle)
        WyCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

/** A label with its choice chips, wrapping instead of pushing the last chip off the screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsChoiceRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }
    }
}
