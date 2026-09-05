package dev.wystore.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.wystore.R
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
    modifier: Modifier = Modifier
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(categories, key = { it.slug }) { category ->
                    CategoryTile(category = category, onClick = { onCategoryClick(category) })
                }
            }
        }
    }
}

/** One catalog section: source icon over its title, sized so a row of them scans quickly. */
@Composable
private fun CategoryTile(category: StoreCategory, onClick: () -> Unit) {
    Surface(
        // Wide enough for the longest curated category name to break between words rather than
        // mid-word ("Супермаркет / ы и рестора...").
        modifier = Modifier.width(132.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (category.iconUrl != null) {
                    AsyncImage(
                        model = category.iconUrl,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.List,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Text(
                category.title,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2,
                lineHeight = 15.sp
            )
        }
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
        modifier = Modifier.width(150.dp).clickable(onClick = onClick),
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
