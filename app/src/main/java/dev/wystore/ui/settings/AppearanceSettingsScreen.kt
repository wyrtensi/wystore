package dev.wystore.ui.settings

import dev.wystore.ui.components.WyDivider
import dev.wystore.settings.AccentPreset
import androidx.compose.material.icons.outlined.Check
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import dev.wystore.ui.components.HubChoiceChip
import dev.wystore.ui.components.HubPageHeader
import dev.wystore.ui.components.switchRow
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wystore.data.StoreSettings
import dev.wystore.R
import dev.wystore.ui.components.LocalBottomBarInset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import dev.wystore.settings.ThemeMode
import dev.wystore.settings.DeviceType
import dev.wystore.device.DeviceTraits
import dev.wystore.device.DeviceProfile
import dev.wystore.device.DeviceProfilePolicy
import dev.wystore.settings.TvCatalog
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    settings: StoreSettings,
    onUpdateSettings: (StoreSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val deviceTraits = remember(context) { DeviceTraits.read(context) }
    Scaffold(
        modifier = modifier,
        topBar = {
            // The concept's page header: a round back button, where the page belongs as the
            // accent line, the title in plain weight.
            HubPageHeader(
                title = stringResource(R.string.settings_hub_appearance_title),
                eyebrow = stringResource(R.string.settings_title),
                onBack = onBack
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + LocalBottomBarInset.current),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // One panel per question, as the concept groups its settings.
            SettingsPanel {
                Text(stringResource(R.string.appearance_theme_title), style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        ThemeMode.SYSTEM to R.string.appearance_theme_system,
                        ThemeMode.LIGHT to R.string.appearance_theme_light,
                        ThemeMode.DARK to R.string.appearance_theme_dark
                    ).forEach { (mode, label) ->
                        HubChoiceChip(
                            selected = settings.themeMode == mode,
                            onClick = { onUpdateSettings(settings.copy(themeMode = mode)) },
                            label = { Text(stringResource(label)) }
                        )
                    }
                }
            }

            val wallpaperColours = settings.dynamicColorEnabled && Build.VERSION.SDK_INT >= 31
            SettingsPanel {
                Text(stringResource(R.string.appearance_accent_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        if (wallpaperColours) R.string.appearance_accent_dynamic else R.string.appearance_accent_text
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AccentSwatches(
                    selected = settings.accent,
                    enabled = !wallpaperColours,
                    onSelect = { onUpdateSettings(settings.copy(accent = it)) }
                )
                WyDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .switchRow(settings.dynamicColorEnabled, enabled = Build.VERSION.SDK_INT >= 31) {
                            onUpdateSettings(settings.copy(dynamicColorEnabled = it))
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.appearance_dynamic_color), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(
                                if (Build.VERSION.SDK_INT >= 31) R.string.appearance_dynamic_color_on
                                else R.string.appearance_dynamic_color_unavailable
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.dynamicColorEnabled,
                        enabled = Build.VERSION.SDK_INT >= 31,
                        onCheckedChange = null
                    )
                }
            }

            SettingsPanel {
                DeviceTypeSection(
                    selected = settings.deviceType,
                    onSelect = { onUpdateSettings(settings.copy(deviceType = it)) }
                )
                // Only where the TV interface is the one showing: a phone has no use for it.
                if (DeviceProfilePolicy.resolve(settings.deviceType, deviceTraits) == DeviceProfile.TV) {
                    TvCatalogSection(
                        selected = settings.tvCatalog,
                        onSelect = { onUpdateSettings(settings.copy(tvCatalog = it)) }
                    )
                }
            }
        }
    }
}

/** A panel of related settings: a card with the hairline, its parts spaced as one group. */
@Composable
private fun SettingsPanel(content: @Composable ColumnScope.() -> Unit) {
    dev.wystore.ui.components.WyCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/**
 * The accent presets as round swatches; the chosen one is ringed and ticked. Dimmed while the
 * wallpaper's colours are on, since then they decide the accent instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentSwatches(selected: AccentPreset, enabled: Boolean, onSelect: (AccentPreset) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AccentPreset.entries.forEach { preset ->
            val colour = androidx.compose.ui.graphics.Color(preset.argb)
            val chosen = preset == selected
            val circle = androidx.compose.foundation.shape.CircleShape
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(circle)
                    .then(if (chosen) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, circle) else Modifier)
                    .padding(if (chosen) 4.dp else 0.dp)
                    .clip(circle)
                    .background(if (enabled) colour else colour.copy(alpha = 0.35f))
                    .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.RadioButton) { onSelect(preset) }
                    .semantics { contentDescription = preset.name.lowercase() },
                contentAlignment = Alignment.Center
            ) {
                if (chosen) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Auto, phone or TV, with what Auto would pick on this device spelled out: whoever is here because
 * the detection got their box wrong needs to see that it did.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceTypeSection(
    selected: DeviceType,
    onSelect: (DeviceType) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val detectedTv = remember(context) { DeviceTraits.read(context).looksLikeTv }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.appearance_device_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.appearance_device_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                DeviceType.AUTO to R.string.appearance_device_auto,
                DeviceType.PHONE to R.string.appearance_device_phone,
                DeviceType.TV to R.string.appearance_device_tv
            ).forEach { (type, label) ->
                HubChoiceChip(
                    selected = selected == type,
                    onClick = { onSelect(type) },
                    label = { Text(stringResource(label)) }
                )
            }
        }
        Text(
            stringResource(
                if (detectedTv) R.string.appearance_device_detected_tv
                else R.string.appearance_device_detected_phone
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Which catalogue a TV browses. The warning is shown while phone apps are part of the list, since
 * that is when an app that cannot be used with a remote gets installed by mistake.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TvCatalogSection(
    selected: TvCatalog,
    onSelect: (TvCatalog) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WyDivider()
        Text(stringResource(R.string.appearance_tv_catalog_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.appearance_tv_catalog_text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                TvCatalog.TV to R.string.appearance_tv_catalog_tv,
                TvCatalog.PHONE to R.string.appearance_tv_catalog_phone,
                TvCatalog.BOTH to R.string.appearance_tv_catalog_both
            ).forEach { (catalog, label) ->
                HubChoiceChip(
                    selected = selected == catalog,
                    onClick = { onSelect(catalog) },
                    label = { Text(stringResource(label)) }
                )
            }
        }
        if (selected != TvCatalog.TV) {
            Text(
                stringResource(R.string.appearance_tv_catalog_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
