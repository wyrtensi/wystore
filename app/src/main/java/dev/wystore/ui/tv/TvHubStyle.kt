package dev.wystore.ui.tv

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import dev.wystore.ui.components.GroupPosition
import dev.wystore.ui.components.HubGroupRow
import dev.wystore.ui.components.HubPanel
import androidx.compose.material3.MaterialTheme as M3Theme

/**
 * The phone's glass panel at TV scale: the falling fill, the accent wash low in one corner, the
 * hairline. The TV's own panels were flat grey slabs, which is most of why it looked bare beside
 * the phone.
 */
@Composable
fun TvGlassPanel(
    modifier: Modifier = Modifier,
    tint: Color = M3Theme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit
) = HubPanel(
    modifier = modifier,
    tint = tint,
    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 24.dp),
    spacing = 12.dp,
    content = content
)

/**
 * One row of a list drawn as one panel, as the phone's lists are, that the remote can land on. The
 * panel's outline and dividers are the phone's [HubGroupRow]; the focus is an inset rounded
 * highlight with the accent ring, so it never spills over the panel's edge as a scaled card would.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvGroupRow(
    position: GroupPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    HubGroupRow(position = position, modifier = modifier) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .tvPointerClick(onClick = onClick),
            shape = ClickableSurfaceDefaults.shape(shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border = ClickableSurfaceDefaults.border(focusedBorder = tvFocusBorder(shape)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = M3Theme.colorScheme.surfaceContainerHighest
            )
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}
