package dev.wystore.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import dev.wystore.ui.components.ReviewCard
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.wystore.R
import dev.wystore.data.InstalledApp
import dev.wystore.data.StoreApp
import dev.wystore.localization.StatusTextResolver
import dev.wystore.ui.components.AppIcon
import dev.wystore.ui.components.PackageUiState
import dev.wystore.ui.components.PrimaryAction
import dev.wystore.ui.components.formatSize

/**
 * An app's page on a TV.
 *
 * The one thing people open it for - install, update, open - holds the focus when it appears, so
 * the common path from Home is two presses of OK. Everything below it is reachable by pressing down:
 * the description, the screenshots, which open full screen.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvDetailsScreen(
    app: StoreApp,
    state: PackageUiState,
    installed: InstalledApp?,
    loading: Boolean,
    /** An app from the phone catalogue shown among TV apps, marked as its card was. */
    forPhone: Boolean,
    actions: TvPackageActions,
    onUninstall: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Told when a screenshot opens full screen and closes, so the menu can make way for it. */
    onFullScreenChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val primaryFocus = remember { FocusRequester() }
    var viewerIndex by rememberSaveable(app.packageName) { mutableStateOf<Int?>(null) }
    val primaryLabel = tvPrimaryLabel(state.primaryAction)

    LaunchedEffect(app.packageName, primaryLabel != null) {
        runCatching { primaryFocus.requestFocus() }
    }

    val viewerOpen = viewerIndex != null
    LaunchedEffect(viewerOpen) { onFullScreenChange(viewerOpen) }
    DisposableEffect(Unit) { onDispose { onFullScreenChange(false) } }

    viewerIndex?.let { index ->
        TvScreenshotViewer(
            screenshots = app.screenshots,
            startIndex = index,
            appName = app.name,
            onClose = { viewerIndex = null }
        )
        return
    }

    // Pages keep their header in place; see TvMinimalBringIntoViewSpec.
    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides TvMinimalBringIntoViewSpec
    ) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = TvOverscanHorizontal,
                end = TvOverscanHorizontal,
                top = 8.dp,
                bottom = TvOverscanVertical + 48.dp
            ),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item(key = "header") {
                // The phone page's parts, side by side for a wide screen: who and what on the left with
                // the one button, the facts in a card on the right.
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(model = app.iconUrl, contentDescription = null, size = 112.dp, fallbackPackageName = app.packageName)
                            Column(Modifier.padding(start = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    app.name.ifBlank { app.packageName },
                                    style = MaterialTheme.typography.headlineMedium,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                if (app.publisher.isNotBlank()) {
                                    Text(app.publisher, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TvBadge(stringResource(R.string.source_rustore))
                                    if (forPhone) {
                                        TvBadge(
                                            stringResource(R.string.tv_badge_phone),
                                            container = MaterialTheme.colorScheme.tertiary,
                                            content = MaterialTheme.colorScheme.onTertiary
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            StatusTextResolver.resolve(context, state.status),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (primaryLabel != null) {
                                TvPrimaryButton(
                                    text = primaryLabel,
                                    onClick = { actions.perform(state) },
                                    enabled = state.primaryAction != PrimaryAction.Installing && !loading,
                                    modifier = Modifier.focusRequester(primaryFocus)
                                )
                            }
                            if (installed != null) {
                                TvSecondaryButton(
                                    text = stringResource(R.string.details_uninstall),
                                    onClick = { onUninstall(app.packageName) },
                                    modifier = if (primaryLabel == null) Modifier.focusRequester(primaryFocus) else Modifier
                                )
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = androidx.compose.material3.MaterialTheme.shapes.large,
                        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TvFact(stringResource(R.string.details_label_version), app.versionName)
                            installed?.versionName?.let { TvFact(stringResource(R.string.tv_details_installed), it) }
                            app.sizeBytes.takeIf { it > 0 }?.let { TvFact(stringResource(R.string.details_label_size), formatSize(context.resources, it)) }
                            app.rating?.let { rating ->
                                TvFact(
                                    stringResource(R.string.details_label_rating),
                                    String.format(java.util.Locale.getDefault(), "%.1f", rating) +
                                        (app.ratingCount?.let { " ($it)" } ?: "")
                                )
                            }
                            app.downloadsText?.let { TvFact(stringResource(R.string.details_label_downloads), it) }
                            app.minAndroidVersion?.let {
                                TvFact(stringResource(R.string.details_label_min_android), stringResource(R.string.details_min_android_value, it))
                            }
                        }
                    }
                }
            }

            val description = app.fullDescription.ifBlank { app.shortDescription }
            if (description.isNotBlank()) {
                item(key = "description") {
                    TvSectionTitle(stringResource(R.string.details_description))
                    // Focusable so pressing down reaches it and brings it into view; long text on a TV
                    // is otherwise out of reach of a remote.
                    Surface(
                        modifier = Modifier.fillMaxWidth().focusable(),
                        shape = androidx.compose.material3.MaterialTheme.shapes.large,
                        colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            description,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 12,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }

            if (app.screenshots.isNotEmpty()) {
                item(key = "screenshots") {
                    TvSectionTitle(stringResource(R.string.details_screenshots))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier.height(300.dp)
                    ) {
                        itemsIndexed(app.screenshots, key = { index, url -> "$index-$url" }) { index, url ->
                            Card(
                                onClick = { viewerIndex = index },
                                modifier = Modifier.height(260.dp).tvPointerClick { viewerIndex = index },
                                scale = CardDefaults.scale(focusedScale = 1.05f),
                                border = CardDefaults.border(focusedBorder = tvFocusBorder())
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = stringResource(R.string.common_screenshot, app.name),
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.height(260.dp)
                                )
                            }
                        }
                    }
                }
            }

            // The reviews the page came with, in a row: each focusable, so the remote can move along
            // them and a long one is brought fully into view.
            if (app.reviews.isNotEmpty()) {
                item(key = "reviews") {
                    TvSectionTitle(stringResource(R.string.details_reviews))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        itemsIndexed(app.reviews, key = { index, review -> "$index-${review.author}" }) { _, review ->
                            val shape = androidx.compose.material3.MaterialTheme.shapes.large
                            Surface(
                                onClick = {},
                                modifier = Modifier.width(TvReviewWidth),
                                shape = ClickableSurfaceDefaults.shape(shape),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                                border = ClickableSurfaceDefaults.border(focusedBorder = tvFocusBorder(shape)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                                )
                            ) {
                                ReviewCard(review)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val TvReviewWidth = 420.dp

/** Full screen, left and right to page, Back to return to the screenshot that was opened. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvScreenshotViewer(
    screenshots: List<String>,
    startIndex: Int,
    appName: String,
    onClose: () -> Unit
) {
    var index by rememberSaveable { mutableStateOf(startIndex.coerceIn(0, screenshots.lastIndex)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .focusRequester(focus)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> { index = (index + 1).coerceAtMost(screenshots.lastIndex); true }
                    Key.DirectionLeft -> { index = (index - 1).coerceAtLeast(0); true }
                    else -> false
                }
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = screenshots[index],
            contentDescription = stringResource(R.string.common_screenshot, appName),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(TvOverscanVertical).clip(RoundedCornerShape(12.dp))
        )
        Text(
            "${index + 1} / ${screenshots.size}",
            style = MaterialTheme.typography.titleMedium,
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).padding(TvOverscanVertical).width(120.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvFact(label: String, value: String) {
    if (value.isBlank()) return
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(180.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
    }
}
