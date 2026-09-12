package dev.wystore.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wystore.data.StoreSettings
import dev.wystore.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import dev.wystore.localization.AppLocaleController
import dev.wystore.settings.AppLanguage

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LanguageSettingsScreen(
    settings: StoreSettings,
    onUpdateSettings: (StoreSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_hub_language_title)) },
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
            Text(stringResource(R.string.language_pick), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.language_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Seven entries do not fit one line on a narrow screen at a large font scale, and a
            // language nobody can reach is worse than one nobody chose.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // What is in force, not what was last tapped here: a language chosen in Android's
                // own per-app settings never passes through this screen.
                val chosen = AppLocaleController.inForce(settings.language)
                AppLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = chosen == language,
                        onClick = {
                            AppLocaleController.apply(language)
                            onUpdateSettings(settings.copy(language = language))
                        },
                        label = {
                            Text(
                                if (language == AppLanguage.SYSTEM) {
                                    stringResource(R.string.language_system)
                                } else {
                                    // A language names itself: translating the names would hide the
                                    // one the reader came here for.
                                    language.endonym
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}
