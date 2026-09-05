package dev.wystore.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

@OptIn(ExperimentalMaterial3Api::class)
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = settings.language == AppLanguage.SYSTEM,
                    onClick = {
                        AppLocaleController.apply(AppLanguage.SYSTEM)
                        onUpdateSettings(settings.copy(language = AppLanguage.SYSTEM))
                    },
                    label = { Text(stringResource(R.string.language_system)) }
                )
                FilterChip(
                    selected = settings.language == AppLanguage.RU,
                    onClick = {
                        AppLocaleController.apply(AppLanguage.RU)
                        onUpdateSettings(settings.copy(language = AppLanguage.RU))
                    },
                    label = { Text(stringResource(R.string.language_russian)) }
                )
                FilterChip(
                    selected = settings.language == AppLanguage.EN,
                    onClick = {
                        AppLocaleController.apply(AppLanguage.EN)
                        onUpdateSettings(settings.copy(language = AppLanguage.EN))
                    },
                    label = { Text("English") }
                )
            }
        }
    }
}
