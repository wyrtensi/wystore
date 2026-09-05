package dev.wystore.ui.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import dev.wystore.InstallQueueItem
import dev.wystore.InstallQueueStatus
import dev.wystore.R
import dev.wystore.data.InstalledApp
import dev.wystore.data.PendingUpdate
import dev.wystore.data.StoreApp
import dev.wystore.ui.components.AppIcon
import dev.wystore.ui.components.DetailFacts
import dev.wystore.ui.components.OperationProgress
import dev.wystore.ui.components.RatingPill
import dev.wystore.ui.components.ReviewCard
import dev.wystore.ui.components.ScreenPadding
import dev.wystore.ui.components.SectionHeader
import dev.wystore.ui.components.shareLink
import dev.wystore.ui.components.SourceDisclaimer
import dev.wystore.ui.components.SourceLabel
import dev.wystore.ui.components.WyCard
import dev.wystore.ui.components.sourceLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailsScreen(
    app: StoreApp,
    installed: InstalledApp?,
    pendingUpdate: PendingUpdate?,
    rootAvailable: Boolean?,
    busy: Boolean,
    queueItem: InstallQueueItem?,
    onBack: () -> Unit,
    onLaunch: (String) -> Unit,
    onInstallPending: (String) -> Unit,
    onInstall: () -> Unit
) {
    var screenshotPreview by remember { mutableStateOf<String?>(null) }
    var shownReviews by remember(app.packageName) { mutableIntStateOf(REVIEW_PAGE) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val queueActive = queueItem?.status !in setOf(InstallQueueStatus.COMPLETE, InstallQueueStatus.FAILED, null)
    val canUpdate = (installed == null || app.versionCode > installed.versionCode) && !queueActive
    val installLabel = stringResource(
        when {
            pendingUpdate != null -> if (installed == null) R.string.details_install_downloaded else R.string.common_update
            queueActive -> R.string.queue_status_queued
            installed == null -> R.string.common_install
            canUpdate -> R.string.common_update
            else -> R.string.queue_status_complete
        }
    )
    val runInstall = {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        if (pendingUpdate != null) onInstallPending(pendingUpdate.packageName) else onInstall()
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.details_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.common_back)
                    )
                }
            },
            actions = {
                val shareSubject = stringResource(R.string.details_share_subject, app.name)
                val chooserTitle = stringResource(R.string.details_share_chooser)
                IconButton(
                    onClick = {
                        shareLink(
                            context = context,
                            subject = shareSubject,
                            url = "https://www.rustore.ru/catalog/app/${app.packageName}",
                            chooserTitle = chooserTitle
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.details_share)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
    }) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentPadding = PaddingValues(
                start = ScreenPadding,
                end = ScreenPadding,
                top = 4.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(
                        model = app.iconUrl,
                        contentDescription = stringResource(R.string.common_app_icon, app.name),
                        size = 84.dp
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            app.name,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            app.publisher,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            app.rating?.let { RatingPill(it, app.ratingCount) }
                            SourceLabel(text = "RuStore")
                        }
                    }
                }
            }

            app.shortDescription.takeIf { it.isNotBlank() }?.let { summary ->
                item { Text(summary, style = MaterialTheme.typography.bodyMedium) }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (installed == null) {
                        Button(
                            onClick = runInstall,
                            enabled = !busy && (canUpdate || pendingUpdate != null),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(installLabel) }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onLaunch(installed.packageName)
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.common_open)) }
                            if (canUpdate || queueActive || pendingUpdate != null) {
                                Button(
                                    onClick = runInstall,
                                    enabled = !busy && (canUpdate || pendingUpdate != null),
                                    modifier = Modifier.weight(1f)
                                ) { Text(installLabel) }
                            }
                        }
                    }

                    // Everything the user needs to know about the local state of this app, in one
                    // quiet block rather than four loose lines under the button.
                    val notes = buildList {
                        add(
                            if (installed != null) {
                                stringResource(
                                    R.string.details_installed_from,
                                    installed.versionName,
                                    sourceLabel(installed.source)
                                )
                            } else {
                                stringResource(R.string.details_not_installed)
                            }
                        )
                        if (pendingUpdate != null) {
                            add(stringResource(R.string.details_pending_downloaded, pendingUpdate.versionName))
                        } else if (installed != null && !canUpdate) {
                            add(stringResource(R.string.details_already_latest))
                        }
                        if (rootAvailable == false) add(stringResource(R.string.details_no_root))
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            notes.forEach {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            queueItem?.let { item { OperationProgress(it) } }

            item { DetailFacts(app) }

            if (app.screenshots.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(
                            title = stringResource(R.string.details_screenshots),
                            subtitle = stringResource(R.string.details_screenshots_hint)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(app.screenshots, key = { it }) { url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = stringResource(R.string.common_screenshot, app.name),
                                    modifier = Modifier
                                        .size(width = 210.dp, height = 374.dp)
                                        .clip(MaterialTheme.shapes.large)
                                        .clickable { screenshotPreview = url },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }

            if (app.reviews.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.details_reviews_count, app.reviews.size),
                        subtitle = stringResource(R.string.details_reviews_hint)
                    )
                }
                // Paged rather than rendered in one item: the parser no longer caps the list at
                // five, and a hundred cards inside a single LazyColumn item would all be composed
                // at once.
                items(
                    app.reviews.take(shownReviews),
                    key = { "review:${it.author}:${it.publishedAt}:${it.text.hashCode()}" }
                ) { review ->
                    ReviewCard(review)
                }
                if (app.reviews.size > REVIEW_PAGE) {
                    item {
                        val remaining = app.reviews.size - shownReviews
                        TextButton(
                            onClick = {
                                shownReviews = if (remaining > 0) {
                                    (shownReviews + REVIEW_PAGE).coerceAtMost(app.reviews.size)
                                } else {
                                    REVIEW_PAGE
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (remaining > 0) {
                                    stringResource(
                                        R.string.details_reviews_show_more,
                                        remaining.coerceAtMost(REVIEW_PAGE)
                                    )
                                } else {
                                    stringResource(R.string.details_reviews_show_less)
                                }
                            )
                        }
                    }
                }
            }

            // What changed in the version being offered - the thing a user actually wants before
            // accepting an update. Parsed from the page already fetched for reviews.
            app.changelog?.let { changelog ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(
                            title = stringResource(R.string.details_changelog),
                            subtitle = if (changelog.versionName != null || changelog.publishedAt != null) {
                                stringResource(
                                    R.string.details_changelog_meta,
                                    changelog.versionName.orEmpty(),
                                    changelog.publishedAt.orEmpty()
                                )
                            } else {
                                null
                            }
                        )
                        WyCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                changelog.notes,
                                modifier = Modifier.padding(14.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(title = stringResource(R.string.details_description))
                    Text(
                        app.fullDescription.ifBlank { app.shortDescription },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            item {
                SourceDisclaimer(sourceLine = stringResource(R.string.disclaimer_source_rustore))
            }
        }
    }

    screenshotPreview?.let { url ->
        Dialog(onDismissRequest = { screenshotPreview = null }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AsyncImage(
                        model = url,
                        contentDescription = stringResource(R.string.common_screenshot, app.name),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.large),
                        contentScale = ContentScale.Fit
                    )
                    TextButton(
                        onClick = { screenshotPreview = null },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text(stringResource(R.string.common_close)) }
                }
            }
        }
    }
}

/** How many reviews a page of the list shows. */
private const val REVIEW_PAGE = 5
