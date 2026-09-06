package dev.wystore.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wystore.R
import dev.wystore.ui.components.WyCard
import dev.wystore.ui.components.ruStoreTaskLabel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import dev.wystore.RuStoreCompatibilityTask
import dev.wystore.data.RuStoreCompatibility
import dev.wystore.data.StoreSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSettingsScreen(
    settings: StoreSettings,
    ruStoreCompatibility: RuStoreCompatibility,
    ruStoreCompatibilityTask: RuStoreCompatibilityTask?,
    githubRepositoriesCount: Int,
    onUpdateSettings: (StoreSettings) -> Unit,
    onCheckRuStore: () -> Unit,
    onManualRuStoreCode: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_hub_sources_title)) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("RuStore", style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.sources_rustore_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(stringResource(R.string.sources_api_code, ruStoreCompatibility.apiVersionCode), style = MaterialTheme.typography.titleSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCheckRuStore,
                    enabled = ruStoreCompatibilityTask == null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.sources_match))
                }
                OutlinedButton(onClick = onManualRuStoreCode, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.sources_manual))
                }
            }

            // The task state was passed into this screen and never rendered: pressing "match"
            // looked like it did nothing until the API code silently changed some seconds later.
            ruStoreCompatibilityTask?.let { task ->
                WyCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                ruStoreTaskLabel(task.status),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (task.status !in setOf("COMPLETE", "FAILED")) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        task.detail?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        task.progress?.takeIf { it.totalBytes > 0 }?.let { progress ->
                            LinearProgressIndicator(
                                progress = { progress.fraction.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.extraSmall)
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Text("GitHub Releases", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.sources_github_enable), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.sources_github_enable_subtitle, githubRepositoriesCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.githubEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(githubEnabled = it)) }
                )
            }
        }
    }
}
