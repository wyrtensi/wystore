package dev.wystore.ui.settings

import dev.wystore.ui.components.fadingTopEdge
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.foundation.clickable
import dev.wystore.ui.components.HubPageHeader
import dev.wystore.ui.components.switchRow
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
import dev.wystore.ui.components.WySpinner
import dev.wystore.R
import dev.wystore.ui.components.LocalBottomBarInset
import dev.wystore.ui.components.WyCard
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import dev.wystore.data.StoreSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSettingsScreen(
    settings: StoreSettings,
    githubRepositoriesCount: Int,
    onUpdateSettings: (StoreSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            // The concept's page header: a round back button, where the page belongs as the
            // accent line, the title in plain weight.
            HubPageHeader(
                title = stringResource(R.string.settings_hub_sources_title),
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // A panel per source, each named by its own coloured tag.
            dev.wystore.ui.components.WyCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            dev.wystore.ui.components.HubTag("RuStore", tint = dev.wystore.ui.components.sourceTint("RuStore"))
            Text(
                stringResource(R.string.sources_rustore_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            }
            }

            dev.wystore.ui.components.WyCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            dev.wystore.ui.components.HubTag("GitHub Releases", tint = dev.wystore.ui.components.sourceTint("GitHub"))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .switchRow(settings.githubEnabled) { onUpdateSettings(settings.copy(githubEnabled = it)) },
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
                Switch(checked = settings.githubEnabled, onCheckedChange = null)
            }
            }
            }
        }
    }
}
