package dev.wystore.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.wystore.R
import dev.wystore.data.AppSourceChoice
import dev.wystore.data.ManagedSource
import dev.wystore.ui.components.sourceDisplayName

@OptIn(ExperimentalLayoutApi::class, androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun TvSourceSelector(choices: List<AppSourceChoice>, preview: ManagedSource, managed: ManagedSource?,
                     onChoose: (AppSourceChoice) -> Unit) {
    if (choices.size < 2) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.source_choose), style = MaterialTheme.typography.titleMedium)
        managed?.let { Text(stringResource(R.string.source_managed, sourceDisplayName(it.name))) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { choice ->
                val name = sourceDisplayName(choice.source.name)
                if (choice.source == preview) TvPrimaryButton(name, { onChoose(choice) })
                else TvSecondaryButton(name, { onChoose(choice) })
            }
        }
    }
}
