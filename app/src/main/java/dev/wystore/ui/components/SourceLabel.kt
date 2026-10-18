package dev.wystore.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.wystore.R
import dev.wystore.data.InstallSource
import dev.wystore.data.ManagedSource

/**
 * Where an app came from. Wy Store mixes two independent sources with different trust and update
 * paths, so every card and page names its own source rather than leaving the user to infer it.
 */
@Composable
fun SourceLabel(source: ManagedSource, modifier: Modifier = Modifier) {
    SourceLabel(
        text = when (source) {
            ManagedSource.RUSTORE -> stringResource(R.string.source_rustore)
            ManagedSource.GITHUB -> stringResource(R.string.source_github)
        },
        modifier = modifier
    )
}

/**
 * Maps whatever the reducer carried (a [ManagedSource] or [InstallSource] name, or a legacy
 * literal) to the name the source actually goes by.
 *
 * The install sources were missing from this table, so a card for an app the device got from Play
 * printed the enum constant: a badge reading "GOOGLE_PLAY".
 */
@Composable
fun sourceDisplayName(raw: String): String = when (raw.uppercase()) {
    ManagedSource.RUSTORE.name, "RUSTORE" -> stringResource(R.string.source_rustore)
    ManagedSource.GITHUB.name -> stringResource(R.string.source_github)
    InstallSource.GOOGLE_PLAY.name -> "Play"
    InstallSource.WY_STORE.name -> "Wy Store"
    InstallSource.OTHER.name -> stringResource(R.string.source_other_short)
    else -> raw
}

@Composable
fun SourceLabel(text: String, modifier: Modifier = Modifier) {
    // A faint tag, as every secondary fact is now. A source is a word; squeezed, the old badge
    // wrapped into a column of letters, and the tag keeps to one line.
    HubTag(text, modifier, tint = sourceTint(text))
}

/**
 * The standing disclaimer, shown wherever the user is about to install something.
 *
 * Wy Store verifies package name, version and signature, but it does not review the apps
 * themselves; [sourceLine] names the source so responsibility is attributed to it rather than
 * implied to be Wy Store's.
 */
@Composable
fun SourceDisclaimer(sourceLine: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.disclaimer_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                stringResource(R.string.disclaimer_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                sourceLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
