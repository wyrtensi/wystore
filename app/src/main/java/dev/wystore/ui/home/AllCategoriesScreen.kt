package dev.wystore.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.wystore.R
import dev.wystore.data.StoreCategory
import dev.wystore.ui.components.LocalBottomBarInset
import dev.wystore.ui.components.ScreenPadding

/**
 * Every section in one place, as the same tiles Home shows on its rail.
 *
 * "All" used to open a catalogue page listing every app the source has, which answers a different
 * question: the rail is a way of choosing where to look, and running out of rail should widen the
 * choice rather than replace it with one long list. GitHub is a tile among the rest, because from
 * here it is the same kind of thing - somewhere else to look.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllCategoriesScreen(
    categories: List<StoreCategory>,
    modifier: Modifier = Modifier,
    githubEnabled: Boolean = true,
    onBack: () -> Unit = {},
    onCategoryClick: (StoreCategory) -> Unit = {},
    onGitHubClick: () -> Unit = {},
    onAllAppsClick: () -> Unit = {},
    previews: Map<String, List<String>> = emptyMap(),
    onNeedPreview: (String) -> Unit = {}
) {
    val githubTitle = stringResource(R.string.categories_github_tile)
    val allAppsTitle = stringResource(R.string.home_all_apps)
    // Two synthetic tiles that are not sections of the source but are the same kind of choice.
    val github = StoreCategory(slug = GITHUB_CATEGORY_SLUG, title = githubTitle, iconUrl = null)
    val allApps = StoreCategory(slug = ALL_APPS_SLUG, title = allAppsTitle, iconUrl = null)
    // Deduplicated by slug: a LazyGrid throws on a repeated key, and the source has published a
    // section whose slug collides with one of these before.
    val tiles = buildList {
        if (githubEnabled) add(github)
        addAll(categories)
        add(allApps)
    }.distinctBy { it.slug }
    // The first tile to use a picture keeps it; a section built out of another one would otherwise
    // sit beside its source wearing the same watermark.
    val watermarked = remember(tiles) {
        val seen = mutableSetOf<String>()
        tiles.map { it.iconUrl == null || seen.add(it.iconUrl) }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_all_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { contentPadding ->
        LazyVerticalGrid(
            // Two across on a phone: the tile is built around a name of up to two lines, and three
            // columns start breaking those names mid-word on a compact screen.
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = ScreenPadding,
                end = ScreenPadding,
                top = 4.dp,
                bottom = 24.dp + LocalBottomBarInset.current
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(tiles, key = { _, category -> category.slug }) { index, category ->
                CategoryTile(
                    category = category,
                    accent = index,
                    fallbackIcon = if (category.slug == GITHUB_CATEGORY_SLUG) R.drawable.ic_github else null,
                    showWatermark = watermarked.getOrElse(index) { true },
                    previewIcons = previews[category.slug].orEmpty(),
                    // Wider cells than the rail's, so more of the section fits across them.
                    previewLimit = 5,
                    onNeedPreview = onNeedPreview,
                    onClick = {
                        when (category.slug) {
                            GITHUB_CATEGORY_SLUG -> onGitHubClick()
                            ALL_APPS_SLUG -> onAllAppsClick()
                            else -> onCategoryClick(category)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private const val ALL_APPS_SLUG = "wy-all-apps"
