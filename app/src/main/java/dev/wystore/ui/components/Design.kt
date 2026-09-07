package dev.wystore.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * The pieces every screen is assembled from.
 *
 * Screens used to write their own titles, radii and empty-state paragraphs inline, so the same
 * element looked slightly different on each of them. These composables are the single definition:
 * they read shapes and colours from the theme and take only their content from the caller, which
 * keeps text resource-driven for translation and for the UI tests that assert on it.
 */

/** Standard gutter between a screen edge and its content. */
val ScreenPadding: Dp = 16.dp

/** Vertical rhythm between two sections of a screen. */
val SectionSpacing: Dp = 20.dp

/**
 * A section title with an optional caption and an optional trailing action.
 *
 * The action is a labelled button rather than a chevron-only affordance so its purpose is
 * announced to a screen reader by the label itself.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (actionLabel != null && onActionClick != null) {
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    // The label next to it already carries the meaning.
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * An app icon that occupies its slot even before the image arrives.
 *
 * Coil draws nothing while a request is in flight or after it fails, which left ragged holes in
 * lists where an icon URL was missing; the tinted tile keeps the row geometry stable.
 */
@Composable
fun AppIcon(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    fallbackPackageName: String? = null
) {
    val context = LocalContext.current
    // An app adopted from GitHub or from the device has no catalogue icon, so rows for it used to
    // show an empty tile; the launcher icon of the installed package is the right thing to draw.
    val installedIcon = remember(fallbackPackageName, model) {
        if (model != null || fallbackPackageName.isNullOrBlank()) {
            null
        } else {
            runCatching { context.packageManager.getApplicationIcon(fallbackPackageName) }.getOrNull()
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        (model ?: installedIcon)?.let {
            AsyncImage(
                model = it,
                contentDescription = contentDescription,
                modifier = Modifier.size(size)
            )
        }
    }
}

/**
 * The app card: one radius, one container colour. Used instead of `Card` so a tapped card and a
 * static one cannot drift apart visually.
 */
@Composable
fun WyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        content = content
    )
}

/**
 * What a screen shows instead of an empty list: an icon, a one-line explanation of why nothing is
 * here, and, when there is one, the action that fixes it.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center
        )
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onActionClick != null) {
            Spacer(Modifier.height(2.dp))
            Button(onClick = onActionClick) { Text(actionLabel) }
        }
    }
}

/** A key/value line for detail pages, so facts line up in a column instead of running as prose. */
@Composable
fun FactRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            modifier = Modifier.width(fontScaledWidth(132.dp)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            overflow = TextOverflow.Ellipsis,
            maxLines = 3
        )
    }
}

/** A hairline that matches the card colours rather than the default divider grey. */
@Composable
fun WyDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}
