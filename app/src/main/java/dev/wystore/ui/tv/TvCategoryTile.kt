package dev.wystore.ui.tv

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
        border = ClickableSurfaceDefaults.border(focusedBorder = tvFocusBorder(shape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = onContainer,
            focusedContainerColor = container,
            focusedContentColor = onContainer
        )
    ) {
        Box(Modifier.background(Brush.verticalGradient(listOf(container, lerp(container, scrim, 0.14f))))) {
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
