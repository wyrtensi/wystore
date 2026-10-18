package dev.wystore.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wystore.ui.theme.LocalHubStyle

/*
 * The parts of the Wy Hub concept that are layout rather than colour.
 *
 * Its screens are not a top bar over a list of cards. They open with a small coloured line over a
 * large plain title, lead with one glass panel that says the state of things in a sentence, and
 * gather lists into panels whose rows are divided by hairlines instead of each row being a card of
 * its own. These are those pieces; screens assemble them.
 */

/** Radius of a panel, and of the group a list of rows sits in. */
private val PanelRadius = 22.dp

/**
 * A screen's opening: a small accent line naming where you are, then the title in a large plain
 * weight - not the bold bar title of a stock app.
 */
@Composable
fun HubHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    /** The screen's side margin; none when the header sits in a list that already has one. */
    horizontalPadding: androidx.compose.ui.unit.Dp = ScreenPadding,
    /** False when the screen already stands clear of the status bar, as a scrolling one must. */
    insetStatusBar: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (insetStatusBar) Modifier.statusBarsPadding() else Modifier)
            .padding(start = horizontalPadding, end = horizontalPadding, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            eyebrow?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 31.sp,
                    lineHeight = 37.sp,
                    letterSpacing = (-0.5).sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

/** The small grey glass pill at a header's right: "Manage", "Check". */
@Composable
fun HubHeaderPill(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        border = hairline()
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

/**
 * The glass panel: a soft vertical gradient with a faint wash of [tint] from one corner, and the
 * hairline. The concept leads every screen with one, saying the state of things in a sentence.
 */
@Composable
fun HubPanel(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val hub = LocalHubStyle.current
    val shape = RoundedCornerShape(PanelRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(colors.surfaceContainerHigh, colors.surfaceContainerLow)))
            // The concept's panels catch a little of their meaning's colour low in one corner - a
            // wash, not a fill: stronger, it read as a brown card.
            .drawWithContent {
                drawRect(
                    Brush.radialGradient(
                        listOf(tint.copy(alpha = if (!hub) 0.08f else if (colors.surface.luminance() < 0.5f) 0.15f else 0.09f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(0f, size.height),
                        radius = size.width * 0.75f
                    )
                )
                drawContent()
            }
            .then(if (hub) Modifier.border(1.dp, colors.outlineVariant, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

/** A panel's title row: the name of the group, and a grey note or an action at its right. */
@Composable
fun HubGroupTitle(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    onMetaClick: (() -> Unit)? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        meta?.let {
            Text(
                it,
                modifier = if (onMetaClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onMetaClick)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                } else Modifier,
                style = MaterialTheme.typography.labelLarge,
                color = if (onMetaClick != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/** Where a row stands in its group, which decides which corners it rounds and where its lines go. */
enum class GroupPosition { SINGLE, FIRST, MIDDLE, LAST }

fun groupPosition(index: Int, count: Int): GroupPosition = when {
    count <= 1 -> GroupPosition.SINGLE
    index == 0 -> GroupPosition.FIRST
    index == count - 1 -> GroupPosition.LAST
    else -> GroupPosition.MIDDLE
}

/**
 * One row of a group that is a list of its own - each row a separate list item, so a group of a
 * hundred and fifty accepted apps still scrolls lazily. Together the rows draw one panel: the first
 * rounds the top, the last the bottom, and a hairline divides them.
 */
@Composable
fun HubGroupRow(
    position: GroupPosition,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val hub = LocalHubStyle.current
    val top = if (position == GroupPosition.FIRST || position == GroupPosition.SINGLE) PanelRadius else 0.dp
    val bottom = if (position == GroupPosition.LAST || position == GroupPosition.SINGLE) PanelRadius else 0.dp
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    val line = colors.outlineVariant
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                if (!hub) return@drawWithContent
                // The panel's outline, drawn a piece per row: each row strokes the whole rounded
                // outline of a box that reaches past its own open edges, clipped to itself.
                val stroke = 1.dp.toPx()
                val reach = PanelRadius.toPx() * 2
                val extendTop = if (top == 0.dp) reach else 0f
                val extendBottom = if (bottom == 0.dp) reach else 0f
                val full = Size(size.width - stroke, size.height + extendTop + extendBottom - stroke)
                val outline = RoundedCornerShape(PanelRadius).createOutline(full, layoutDirection, this)
                val path = Path().apply {
                    when (outline) {
                        is Outline.Rounded -> addRoundRect(outline.roundRect)
                        is Outline.Rectangle -> addRect(outline.rect)
                        is Outline.Generic -> addPath(outline.path)
                    }
                    translate(androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2 - extendTop))
                }
                clipRect { drawPath(path, line, style = Stroke(stroke)) }
                // The divider under every row but the last, inset like the concept's.
                if (bottom == 0.dp) {
                    val inset = 16.dp.toPx()
                    drawLine(line, androidx.compose.ui.geometry.Offset(inset, size.height - stroke / 2),
                        androidx.compose.ui.geometry.Offset(size.width - inset, size.height - stroke / 2), stroke)
                }
            }
            .clip(shape)
            .background(colors.surfaceContainerLow)
            .then(
                when {
                    onClick != null && onLongClick != null ->
                        Modifier.combinedClickableCompat(onClick, onLongClick)
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                }
            ),
        content = content
    )
}

private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: () -> Unit) =
    combinedClickable(onClick = onClick, onLongClick = onLongClick)

/** The concept's wide grey pill, "Open Bip 6 ›"; [primary] fills it with the accent. */
@Composable
fun HubWideButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    chevron: Boolean = !primary
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(50),
        color = if (primary) colors.primary else colors.secondaryContainer,
        contentColor = if (primary) colors.onPrimary else colors.onSecondaryContainer
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            if (chevron) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.primary
                )
            }
        }
    }
}

/** The thin single-line panel with a coloured icon: a note the screen wants read once. */
@Composable
fun HubNote(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val shape = RoundedCornerShape(PanelRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(if (LocalHubStyle.current) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * A faint tag behind a secondary fact - a version, who installed the app, its section - so the
 * eye can find it in a row of grey text without it competing with the name. [tint] colours it for
 * a fact that is a state, as the concept's status pills are.
 */
@Composable
fun HubTag(text: String, modifier: Modifier = Modifier, tint: Color? = null) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(
                tint?.copy(alpha = if (dark) 0.16f else 0.12f)
                    ?: colors.onSurface.copy(alpha = if (dark) 0.07f else 0.05f)
            )
            .padding(horizontal = 7.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = tint ?: colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun hairline(): BorderStroke? =
    if (LocalHubStyle.current) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
