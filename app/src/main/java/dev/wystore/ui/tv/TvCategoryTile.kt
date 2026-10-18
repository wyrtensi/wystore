package dev.wystore.ui.tv

import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.wystore.ui.home.CategoryPreviewRow
import dev.wystore.ui.home.categoryTone
import androidx.compose.material3.MaterialTheme as M3Theme

/**
 * A category of the TV catalogue, drawn as the phone draws its category tiles: its own tone out of
 * the theme, settling towards the page at the bottom, with the first apps inside it overlapped
 * above the name.
 *
 * The TV catalogue publishes no sections of its own, so these are the categories its apps carry.
 * Pressing one moves down to that category's row.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvCategoryTile(
    title: String,
    accent: Int,
    previewIcons: List<String>,
    onClick: () -> Unit,
    /** The source's icon for the category, drawn as the phone's tiles draw it. */
    iconUrl: String? = null,
    /** A glyph of the app's own for a category the source has no icon for, such as a game genre. */
    fallbackIcon: Int? = null,
    modifier: Modifier = Modifier
) {
    val (container, onContainer) = categoryTone(accent)
    val scrim = M3Theme.colorScheme.scrim
    val shape = M3Theme.shapes.large
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(TvCategoryTileWidth)
            .tvPointerClick(onClick = onClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = tvCardBorder(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = onContainer,
            focusedContainerColor = container,
            focusedContentColor = onContainer
        )
    ) {
        Box(Modifier.background(Brush.verticalGradient(listOf(container, lerp(container, scrim, 0.14f))))) {
            // The phone's watermark: the section's glyph in the tile's ink, half out of the corner.
            // The TV tiles were the only ones without it.
            val glyph = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .offset(x = 16.dp, y = 14.dp)
                .size(72.dp)
                .rotate(-8f)
                .alpha(0.18f)
            if (iconUrl != null) {
                coil.compose.AsyncImage(
                    model = iconUrl,
                    contentDescription = null,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(onContainer),
                    modifier = glyph
                )
            } else if (fallbackIcon != null) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(fallbackIcon),
                    contentDescription = null,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(onContainer),
                    modifier = glyph
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.height(30.dp)) {
                    CategoryPreviewRow(icons = previewIcons.take(4), ring = container)
                }
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = onContainer,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

val TvCategoryTileWidth = 156.dp
