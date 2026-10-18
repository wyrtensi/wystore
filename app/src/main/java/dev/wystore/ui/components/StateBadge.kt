package dev.wystore.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun StateBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    // A faint tag; a badge given colours of its own keeps them as its tint, so a state still reads
    // as that state. A badge is a word: the tag keeps it to one line.
    val default = containerColor == MaterialTheme.colorScheme.secondaryContainer
    HubTag(text, modifier, tint = if (default) null else contentColor)
}
