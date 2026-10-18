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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
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
    // The pill shares the eyebrow's line, so the title has the whole width under it. Beside the
    // title, a long pill ("Check and install") left a compact phone "Обновл..." for a title.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (insetStatusBar) Modifier.statusBarsPadding() else Modifier)
            .padding(start = horizontalPadding, end = horizontalPadding, top = 8.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (eyebrow != null || trailing != null) Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                eyebrow.orEmpty(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
            }
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
    /** Washed with the accent, for a row that is picked. */
    highlighted: Boolean = false,
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
            .then(if (highlighted) Modifier.background(colors.primary.copy(alpha = 0.12f)) else Modifier)
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

/**
 * Whether the screen is narrow enough that row controls give their padding to the text: a compact
 * phone at a large font size left a name two words wide between an icon and three buttons.
 */
@Composable
fun isNarrowScreen(): Boolean =
    androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 420

/** Padding of a row's button: narrower on a narrow screen. */
@Composable
fun rowButtonPadding(): androidx.compose.foundation.layout.PaddingValues =
    if (isNarrowScreen()) androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    else androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)

/**
 * Lets a horizontal rail run to the screen's edges from inside a list with side margins: the cards
 * then leave the screen at its edge instead of being sliced off at the margin, a strip short of
 * it, which read as cut-offs. Give the rail the same [margin] as content padding, so its first card
 * still lines up with everything above it.
 */
fun Modifier.bleedToScreenEdges(margin: androidx.compose.ui.unit.Dp = ScreenPadding): Modifier =
    layout { measurable, constraints ->
        val extra = margin.roundToPx() * 2
        val width = constraints.maxWidth + extra
        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
    }

/**
 * Fades the top of a list into the page under a header that stays put, so rows scrolled up under
 * it dissolve rather than end on a hard line across a card.
 */
fun Modifier.fadingTopEdge(length: androidx.compose.ui.unit.Dp = 20.dp): Modifier =
    graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = length.toPx()
            drawRect(
                brush = Brush.verticalGradient(0f to Color.Transparent, fade to Color.Black, startY = 0f, endY = fade),
                size = Size(size.width, fade),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
            )
        }

/**
 * A dialog the Wy Hub way: the panel radius, the hairline, and its buttons as pills - see
 * [DialogButton]. Material's own drew a squarer card with a row of accent text links, the one
 * place left where the old look showed through.
 */
@Composable
fun HubAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: androidx.compose.ui.window.DialogProperties = androidx.compose.ui.window.DialogProperties()
) {
    val shape = RoundedCornerShape(28.dp)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest,
        // Wide pills, one under the other, the step first: side by side two of them did not fit a
        // dialog's width, and Material stepped them into a staircase.
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                confirmButton()
                dismissButton?.invoke()
            }
        },
        modifier = modifier.then(
            if (LocalHubStyle.current) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape) else Modifier
        ),
        dismissButton = null,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        properties = properties
    )
}

/**
 * A dialog's button: the accent pill for the step the dialog is asking about, a grey one for
 * the way out and for anything that only closes it.
 */
@Composable
fun DialogButton(
    onClick: () -> Unit,
    primary: Boolean = false,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val wide = Modifier.fillMaxWidth().heightIn(min = 46.dp)
    if (primary) {
        androidx.compose.material3.Button(onClick = onClick, modifier = wide, enabled = enabled, content = content)
    } else {
        androidx.compose.material3.FilledTonalButton(onClick = onClick, modifier = wide, enabled = enabled, content = content)
    }
}

/**
 * A secondary action as a small grey capsule - "Retry", "Later", "Show all" - where a line of
 * accent text used to stand. Takes the arguments a text button did, so it replaces one in place;
 * [colors] and [contentPadding] are accepted and left to the capsule's own.
 */
@Composable
fun HubTextAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: androidx.compose.material3.ButtonColors? = null,
    @Suppress("UNUSED_PARAMETER") contentPadding: androidx.compose.foundation.layout.PaddingValues? = null,
    content: @Composable RowScope.() -> Unit
) {
    androidx.compose.material3.FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp),
        enabled = enabled,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        content = content
    )
}

/**
 * A page's opening, as the concept's detail pages have it: a round back button, the small accent
 * line naming where the page belongs, the title, and round actions at the right.
 */
@Composable
fun HubPageHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = ScreenPadding, end = ScreenPadding, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HubRoundIconButton(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = androidx.compose.ui.res.stringResource(dev.wystore.R.string.common_back),
            onClick = onBack
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            eyebrow?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (actions != null) Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** A round grey glass button holding one icon: back, share. */
@Composable
fun HubRoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        border = hairline(),
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * One choice of a set - Wi-Fi or any network, light or dark - as the concept's segmented pills:
 * the chosen one filled with the accent, the rest grey glass. Takes FilterChip's arguments, so it
 * stands in for one in place; Material's outlined chips read as form fields.
 */
@Composable
fun HubChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 38.dp),
        shape = RoundedCornerShape(percent = 50),
        color = if (selected) colors.primary else colors.secondaryContainer,
        contentColor = if (selected) colors.onPrimary else colors.onSecondaryContainer,
        border = if (selected) null else hairline()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            leadingIcon?.invoke()
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.labelLarge
            ) { label() }
            trailingIcon?.invoke()
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

/**
 * The colour a source or an installer is told in, so "RuStore", "GitHub", "Wy Store" and "Google
 * Play" can be told apart at a glance instead of being four grey words; null for anything else.
 */
@Composable
fun sourceTint(name: String): Color? {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (name.trim().lowercase()) {
        "rustore" -> if (dark) Color(0xFF6CA8F5) else Color(0xFF1F5FB8)
        "github" -> if (dark) Color(0xFFC59BF2) else Color(0xFF7440B8)
        "wy store" -> MaterialTheme.colorScheme.primary
        "google play", "play" -> if (dark) Color(0xFF6FCF97) else Color(0xFF1E7F48)
        else -> null
    }
}

@Composable
private fun hairline(): BorderStroke? =
    if (LocalHubStyle.current) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
