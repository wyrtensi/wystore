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
    actions: TvPackageActions,
    onUninstall: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val primaryFocus = remember { FocusRequester() }
    var viewerIndex by rememberSaveable(app.packageName) { mutableStateOf<Int?>(null) }
    val primaryLabel = tvPrimaryLabel(state.primaryAction)

    LaunchedEffect(app.packageName, primaryLabel != null) {
        runCatching { primaryFocus.requestFocus() }
    }

    viewerIndex?.let { index ->
        TvScreenshotViewer(
            screenshots = app.screenshots,
            startIndex = index,
            appName = app.name,
            onClose = { viewerIndex = null }
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TvOverscanHorizontal,
            end = TvOverscanHorizontal,
            top = TvOverscanVertical + 8.dp,
            bottom = TvOverscanVertical + 48.dp
        ),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(model = app.iconUrl, contentDescription = null, size = 112.dp, fallbackPackageName = app.packageName)
                Column(Modifier.padding(start = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(app.name.ifBlank { app.packageName }, style = MaterialTheme.typography.headlineMedium)
                    if (app.publisher.isNotBlank()) {
                        Text(app.publisher, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        listOfNotNull(
                            app.versionName.takeIf { it.isNotBlank() }?.let {
                                stringResource(R.string.details_label_version) + " " + it
                            },
                            app.sizeBytes.takeIf { it > 0 }?.let { formatSize(context.resources, it) },
                            app.rating?.let { rating ->
                                "★ " + String.format(java.util.Locale.getDefault(), "%.1f", rating)
                            },
                            installed?.versionName?.let { stringResource(R.string.search_installed_version, it) }
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        StatusTextResolver.resolve(context, state.status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
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

        val description = app.fullDescription.ifBlank { app.shortDescription }
        if (description.isNotBlank()) {
            item(key = "description") {
                TvSectionTitle(stringResource(R.string.details_description))
                // Focusable so pressing down reaches it and brings it into view; long text on a TV
                // is otherwise out of reach of a remote.
                Surface(
                    modifier = Modifier.fillMaxWidth().focusable(),
                    shape = RoundedCornerShape(16.dp),
                    colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
    }
}

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
