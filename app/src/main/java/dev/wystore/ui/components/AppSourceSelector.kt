package dev.wystore.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wystore.R
import dev.wystore.data.AppSourceChoice
import dev.wystore.data.ManagedSource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppSourceSelector(choices: List<AppSourceChoice>, preview: ManagedSource,
                      managed: ManagedSource?, onChoose: (AppSourceChoice) -> Unit) {
    if (choices.size < 2) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.source_choose))
        managed?.let { Text(stringResource(R.string.source_managed, sourceDisplayName(it.name))) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            choices.forEach { choice ->
                HubChoiceChip(selected = preview == choice.source, onClick = { onChoose(choice) },
                    label = { Text(sourceDisplayName(choice.source.name)) })
            }
        }
    }
}
