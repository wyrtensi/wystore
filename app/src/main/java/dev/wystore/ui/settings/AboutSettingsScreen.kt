package dev.wystore.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.wystore.R
import dev.wystore.data.StoreSettings
import dev.wystore.selfupdate.SelfUpdateChecker
import dev.wystore.selfupdate.SelfUpdateStatus
import dev.wystore.ui.components.CompactSettingSwitch
import dev.wystore.ui.components.FactRow
import dev.wystore.ui.components.ScreenPadding
import dev.wystore.ui.components.SectionHeader
import dev.wystore.ui.components.SectionSpacing
import dev.wystore.ui.components.WyCard
import dev.wystore.ui.components.WyDivider

/**
 * Wy Store's own page: which build is installed, where it came from, and whether a newer one is
 * published. Updating happens through the ordinary queue, so it is verified and confirmed exactly
 * like an update to any other app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsScreen(
    settings: StoreSettings,
    status: SelfUpdateStatus,
    versionName: String,
    versionCode: Long,
    onUpdateSettings: (StoreSettings) -> Unit,
    onCheck: () -> Unit,
    onInstall: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_hub_about_title)) },
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
            WyCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Wy Store", style = MaterialTheme.typography.titleMedium)
                    // Says plainly what this is and what it is not: reading RuStore's catalogue
                    // without saying so invites the assumption that RuStore stands behind it.
                    Text(
                        stringResource(R.string.about_independent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    WyDivider()
                    FactRow(stringResource(R.string.about_version), "$versionName ($versionCode)")
                    FactRow(stringResource(R.string.about_source), SelfUpdateChecker.PROJECT_URL)
                    WyDivider()
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, SelfUpdateChecker.PROJECT_URL.toUri())
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.about_source)) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    title = stringResource(R.string.about_check),
                    subtitle = stringResource(R.string.about_signature_note)
                )
                WyCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (status) {
                            SelfUpdateStatus.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    stringResource(R.string.about_checking),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            SelfUpdateStatus.UpToDate -> Text(
                                stringResource(R.string.about_up_to_date),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            is SelfUpdateStatus.Available -> Text(
                                stringResource(R.string.about_available, status.versionName),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            is SelfUpdateStatus.Failed -> Text(
                                stringResource(R.string.about_failed, status.reason),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            SelfUpdateStatus.Idle -> Unit
                        }

                        if (status is SelfUpdateStatus.Available) {
                            Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.about_install))
                            }
                        }
                        FilledTonalButton(
                            onClick = onCheck,
                            enabled = status != SelfUpdateStatus.Checking,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.about_check)) }
                    }
                }
            }

            WyCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CompactSettingSwitch(
                        label = stringResource(R.string.about_auto_title),
                        checked = settings.selfUpdateEnabled
                    ) { onUpdateSettings(settings.copy(selfUpdateEnabled = it)) }
                    Text(
                        stringResource(R.string.about_auto_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
