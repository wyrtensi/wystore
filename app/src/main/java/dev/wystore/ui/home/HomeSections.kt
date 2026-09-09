package dev.wystore.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import coil.compose.AsyncImage
import dev.wystore.R
import dev.wystore.ui.components.fontScaledWidth
import dev.wystore.data.PendingUpdate
import dev.wystore.data.StoreCategory
import dev.wystore.ui.components.AppIcon
import dev.wystore.ui.components.SectionHeader
import dev.wystore.ui.components.WyCard

@Composable
fun PersistentSearchBar(
    query: String,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSearchClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = query.ifBlank { stringResource(R.string.home_search_placeholder) },
                style = MaterialTheme.typography.bodyMedium,
                color = if (query.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The one thing Home has to answer at a glance: is anything waiting to be updated.
 *
 * Both states share a layout — a leading status icon, a headline and a caption — so the card does
 * not visibly change shape when updates appear; only its colour and its actions do.
 */
@Composable
fun UpdateHeroSection(
    pendingUpdates: List<PendingUpdate>,
    onUpdateAll: () -> Unit,
    onOpenUpdates: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasUpdates = pendingUpdates.isNotEmpty()
    val container = if (hasUpdates) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val onContainer = if (hasUpdates) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    WyCard(modifier = modifier.fillMaxWidth(), containerColor = container) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(onContainer.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (hasUpdates) Icons.Outlined.Refresh else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = onContainer
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (hasUpdates) R.string.home_updates_available else R.string.home_all_up_to_date
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = onContainer
                    )
                    Text(
                        text = if (hasUpdates) {
                            stringResource(R.string.home_ready_to_install_count, pendingUpdates.size)
                        } else {
                            stringResource(R.string.home_background_checks)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = 0.75f)
                    )
                }
            }

            if (hasUpdates) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onUpdateAll,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = onContainer,
                            contentColor = container
                        )
                    ) {
                        Text(stringResource(R.string.home_update_all), maxLines = 1)
                    }
                    TextButton(
                        onClick = onOpenUpdates,
                        colors = ButtonDefaults.textButtonColors(contentColor = onContainer)
                    ) {
                        Text(stringResource(R.string.home_updates_list), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun CategoriesSection(
    categories: List<StoreCategory>,
    loading: Boolean = false,
    onCategoryClick: (StoreCategory) -> Unit,
    onAllCategoriesClick: () -> Unit,
    modifier: Modifier = Modifier,
    previews: Map<String, List<String>> = emptyMap(),
    onNeedPreview: (String) -> Unit = {},
    githubEnabled: Boolean = true,
    onGitHubClick: () -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            title = stringResource(R.string.home_categories_title),
            actionLabel = stringResource(R.string.home_categories_all),
            onActionClick = onAllCategoriesClick
        )

        if (categories.isEmpty()) {
            Text(
                stringResource(
                    if (loading) R.string.home_categories_loading else R.string.home_categories_unavailable
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Categories carry their own icons from the source; a horizontal rail shows more of
            // them than a wrapped chip grid and keeps the section a fixed height.
            // GitHub is a section here rather than a place of its own: from the rail it is the
            // same kind of choice as any other - somewhere else to look.
            val githubTitle = stringResource(R.string.categories_github_tile)
            val tiles = remember(categories, githubEnabled, githubTitle) {
                buildList {
                    // First, not last: at the end of thirty sections nobody would ever reach it.
                    if (githubEnabled) {
                        add(StoreCategory(slug = GITHUB_CATEGORY_SLUG, title = githubTitle, iconUrl = null))
                    }
                    addAll(categories)
                }.distinctBy { it.slug }
            }
            // Which tiles may draw a watermark: the first to use a given picture, and no other.
            val watermarked = remember(tiles) {
                val seen = mutableSetOf<String>()
                tiles.map { it.iconUrl == null || seen.add(it.iconUrl) }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(tiles, key = { _, category -> category.slug }) { index, category ->
                    CategoryTile(
                        category = category,
                        accent = index,
                        showWatermark = watermarked.getOrElse(index) { true },
                        fallbackIcon = if (category.slug == GITHUB_CATEGORY_SLUG) R.drawable.ic_github else null,
                        onClick = {
                            if (category.slug == GITHUB_CATEGORY_SLUG) onGitHubClick()
                            else onCategoryClick(category)
                        },
                        previewIcons = previews[category.slug].orEmpty(),
                        previewLimit = 3,
                        onNeedPreview = onNeedPreview
                    )
                }
            }
        }
    }
}

/**
 * One catalog section.
 *
 * The tiles used to be identical grey squares with a small monochrome glyph, which read as one
 * undifferentiated block of chrome. Each one now carries its own tone from the theme's container
 * colours, so the rail is scannable by colour as well as by reading every label.
 *
 * The tone alone left them flat. The tile is built up instead: a gradient that settles into the
 * page at the bottom, light falling from the top-left corner, two rings bleeding off the opposite
 * one, and the icon on a lit disc. The rings are drawn rather than borrowed from the section's own
 * picture - those are small bitmaps, and one blown up to fill a tile is a smudge, however low the
 * opacity.
 *
 * Only the light is a fixed colour, white above and black below, because that is what light does
 * in a light theme and a dark one alike. Everything else comes from the container colour, so
 * Material You gets tiles that belong to it.
 */
@Composable
internal fun CategoryTile(
    category: StoreCategory,
    accent: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(fontScaledWidth(124.dp)),
    /** For a tile that stands for something other than a section of the source, such as GitHub. */
    fallbackIcon: Int? = null,
    /** Icons of the first few apps in the section; the tile shows these instead of its own glyph. */
    previewIcons: List<String> = emptyList(),
    previewLimit: Int = 3,
    onNeedPreview: (String) -> Unit = {},
    /**
     * Off for a tile whose glyph an earlier tile has already used. Sections that Wy Store builds
     * out of a source section share that section's picture, and the two sit next to each other on
     * purpose - drawing the same watermark on both makes them look like one tile duplicated.
     */
    showWatermark: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val (container, onContainer) = categoryTone(accent)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Decorative, so it follows the system animation setting like everything else that is.
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "categoryTile")
    Surface(
        // Wide enough for the longest curated category name to break between words rather than
        // mid-word ("Здоровье и / аптеки"), and it grows with the system font size so that stays
        // true when the text does not fit the width it was measured for.
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier.background(
                // Deepening towards the ink would invert in dark mode, where the ink is the
                // lighter colour. The scrim is black in both, so the tile settles either way.
                Brush.verticalGradient(
                    listOf(container, lerp(container, scheme.scrim, 0.12f))
                )
            )
        ) {
            // The section's own glyph, in the tile's ink, half out of the corner. Big enough to
            // be the tile's face and small enough not to blur: these are little bitmaps, and one
            // stretched across the whole tile is a stain whatever the opacity.
            if (showWatermark) Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 16.dp, y = 14.dp)
                    .size(68.dp)
                    .rotate(-8f)
                    .alpha(0.18f)
            ) {
                CategoryGlyph(
                    category = category,
                    tint = onContainer,
                    modifier = Modifier.fillMaxSize(),
                    tintSourceIcon = true,
                    fallbackIcon = fallbackIcon
                )
            }
            // Ranged along the leading edge rather than centred on it. The disc that used to sit
            // behind the icon was a second shape competing with the tile's own, and centring left
            // every label floating between two ragged margins.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // The horizontal padding is what the tile width was measured against: at 12dp
                    // "Маркетплейсы" no longer fits the line and breaks mid-word.
                    .padding(vertical = 14.dp, horizontal = 10.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // What is inside the section, not the section's own pictogram: that already fills
                // the corner as the watermark, and drawing it twice made a grid of tiles look like
                // twenty of the same thing.
                LaunchedEffect(category.slug) {
                    if (fallbackIcon == null) onNeedPreview(category.slug)
                }
                if (previewIcons.isEmpty()) {
                    CategoryGlyph(
                        category = category,
                        tint = onContainer,
                        modifier = Modifier.size(30.dp),
                        fallbackIcon = fallbackIcon
                    )
                } else {
                    CategoryPreviewRow(
                        icons = previewIcons.take(previewLimit),
                        ring = container
                    )
                }
                Text(
                    // Wy Store's own sections are translated; sections read from the source keep
                    // the name the catalogue publishes.
                    category.titleRes?.let { stringResource(it) } ?: category.title,
                    // Hyphenated rather than split: a name longer than the tile is wide -
                    // "Государственные" - was broken wherever the line ran out, mid-syllable.
                    style = MaterialTheme.typography.labelLarge.copy(hyphens = Hyphens.Auto),
                    color = onContainer,
                    textAlign = TextAlign.Start,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    minLines = 2,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

/**
 * One of eight tile colours, and an ink that is legible on it.
 *
 * The palette gives three container colours, and three tones over thirty tiles is a wall of the
 * same three. Blending pairs of them to widen the set looked varied and read badly: the ink belongs
 * to one of the two and halfway between them it no longer has the contrast it was chosen for.
 *
 * The hue is rotated instead, keeping the saturation and lightness of the theme's own container -
 * so a light theme gets eight pastels, a dark one eight deep tones, and a Material You phone eight
 * of its own colour rather than eight of ours. The ink is then decided by measuring the result:
 * near-black on a light tile, near-white on a dark one, in the tile's own hue so it reads as part
 * of it. Nothing here can come out illegible, whatever the theme turns out to be.
 */
@Composable
private fun categoryTone(index: Int): Pair<Color, Color> {
    val base = MaterialTheme.colorScheme.primaryContainer
    return remember(base, index) {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(base.toArgb(), hsl)
        val hue = (hsl[0] + index.mod(TONE_COUNT) * (360f / TONE_COUNT)) % 360f
        val saturation = hsl[1].coerceIn(0.18f, 0.62f)
        val container = Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation, hsl[2])))
        val ink = if (ColorUtils.calculateLuminance(container.toArgb()) > 0.42) {
            Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation.coerceAtMost(0.55f), 0.17f)))
        } else {
            Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation.coerceAtMost(0.40f), 0.93f)))
        }
        container to ink
    }
}

private const val TONE_COUNT = 8

/**
 * The first apps of a section, overlapped like a hand of cards.
 *
 * Overlapped rather than spaced out because the tile is 124dp wide and has a name to fit under
 * them: five icons in a row would each be too small to recognise, and the point of showing them is
 * that they are recognised.
 */
@Composable
private fun CategoryPreviewRow(icons: List<String>, ring: Color) {
    Row(
        modifier = Modifier.height(30.dp),
        horizontalArrangement = Arrangement.spacedBy((-8).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icons.forEach { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, ring, CircleShape)
            )
        }
    }
}

/** The section's picture: what the source publishes, or a stand-in when it publishes none. */
@Composable
internal fun CategoryGlyph(
    category: StoreCategory,
    tint: Color,
    modifier: Modifier = Modifier,
    tintSourceIcon: Boolean = false,
    fallbackIcon: Int? = null
) {
    val iconUrl = category.iconUrl
    if (iconUrl == null && fallbackIcon != null) {
        Icon(
            painter = painterResource(fallbackIcon),
            contentDescription = null,
            modifier = modifier,
            tint = tint
        )
    } else if (iconUrl != null) {
        AsyncImage(
            model = iconUrl,
            contentDescription = null,
            modifier = modifier,
            colorFilter = if (tintSourceIcon) ColorFilter.tint(tint) else null
        )
    } else {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.List,
            contentDescription = null,
            modifier = modifier,
            tint = tint
        )
    }
}

/** A compact GitHub entry for the Home rail: icon, name and owner. */
@Composable
internal fun GitHubPickTile(
    iconUrl: String?,
    title: String,
    publisher: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.width(fontScaledWidth(150.dp)).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppIcon(model = iconUrl, contentDescription = null, size = 48.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    publisher,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** The tile that stands for the GitHub catalogue rather than a section of the source. */
internal const val GITHUB_CATEGORY_SLUG = "wy-github"
