package dev.wystore.ui.settings

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
import androidx.compose.material3.FilterChip
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
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_hub_appearance_title)) },
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
                .padding(top = contentPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + LocalBottomBarInset.current),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.appearance_theme_title), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = settings.themeMode == ThemeMode.SYSTEM,
                    onClick = { onUpdateSettings(settings.copy(themeMode = ThemeMode.SYSTEM)) },
                    label = { Text(stringResource(R.string.appearance_theme_system)) }
                )
                FilterChip(
                    selected = settings.themeMode == ThemeMode.LIGHT,
                    onClick = { onUpdateSettings(settings.copy(themeMode = ThemeMode.LIGHT)) },
                    label = { Text(stringResource(R.string.appearance_theme_light)) }
                )
                FilterChip(
                    selected = settings.themeMode == ThemeMode.DARK,
                    onClick = { onUpdateSettings(settings.copy(themeMode = ThemeMode.DARK)) },
                    label = { Text(stringResource(R.string.appearance_theme_dark)) }
                )
            }

            HorizontalDivider()

            DeviceTypeSection(
                selected = settings.deviceType,
                onSelect = { onUpdateSettings(settings.copy(deviceType = it)) }
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    onCheckedChange = { onUpdateSettings(settings.copy(dynamicColorEnabled = it)) }
                )
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
                FilterChip(
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
