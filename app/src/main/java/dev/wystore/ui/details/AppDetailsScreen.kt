package dev.wystore.ui.details

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.StrokeCap
import androidx.core.net.toUri
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
import dev.wystore.isInFlight
import dev.wystore.R
import dev.wystore.data.AndroidSdkCompatibility
import dev.wystore.data.InstalledApp
import dev.wystore.data.PendingUpdate
import dev.wystore.data.ReviewSummary
import dev.wystore.data.StoreReview
import dev.wystore.data.StoreApp
import dev.wystore.ui.components.UninstallIconButton
import dev.wystore.data.SignatureCompatibility
import dev.wystore.data.SignatureCompatibilityPolicy
import dev.wystore.data.TakeoverObstacle
import dev.wystore.data.TakeoverPath
import dev.wystore.data.TakeoverPolicy
import dev.wystore.ui.components.installerLabel
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
    reviewsLoading: Boolean,
    canLoadMoreReviews: Boolean,
    onLoadMoreReviews: () -> Unit,
    onBack: () -> Unit,
    onLaunch: (String) -> Unit,
    onInstallPending: (String) -> Unit,
    onInstall: () -> Unit,
    /** Asks to remove the installed copy and install this one, when nothing can install in place. */
    onReplace: () -> Unit
) {
    var screenshotPreview by remember { mutableStateOf<String?>(null) }
    var shownReviews by remember(app.packageName) { mutableIntStateOf(REVIEW_PAGE) }
    // Which star the list is cut to, or null for all of them.
    var reviewStars by remember(app.packageName) { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val queueActive = queueItem?.status?.isInFlight == true
    // Most of what the catalogue carries needs a newer Android than this app's own minimum, and
    // the page used to offer "Install" all the same: the download ran to the end and the source
    // refused it. The requirement is on the page, so the answer belongs there too.
    val requiredSdk = app.minSdkVersion
        ?: app.minAndroidVersion?.let(AndroidSdkCompatibility::sdkForRelease)
    val deviceTooOld = requiredSdk != null && Build.VERSION.SDK_INT < requiredSdk
    val canUpdate = (installed == null || app.versionCode > installed.versionCode) &&
        !queueActive && !deviceTooOld
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
                            UninstallIconButton(installed.packageName)
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
                            // "You already have the latest" is true only when the card is not
                            // behind the phone. On an app the catalogue has not caught up with it
                            // read as reassurance while the take-over button below it was quietly
                            // impossible.
                            add(
                                if (app.versionCode < installed.versionCode) {
                                    stringResource(R.string.details_catalog_older, app.versionName)
                                } else {
                                    stringResource(R.string.details_already_latest)
                                }
                            )
                        }
                        if (deviceTooOld && requiredSdk != null) {
                            add(
                                stringResource(
                                    R.string.details_incompatible,
                                    AndroidSdkCompatibility.label(requiredSdk),
                                    AndroidSdkCompatibility.label(Build.VERSION.SDK_INT)
                                )
                            )
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
                            // Who updates this app is decided by Android, not by us, and until now
                            // the page never said so. It matters: only the installer of record may
                            // update an app without asking, so "why does this one still stop on a
                            // dialog?" has an answer, and something can be done about it.
                            if (installed != null) {
                                val owner = installed.installerPackageName
                                Text(
                                    stringResource(R.string.details_owner, installerLabel(owner)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                // Whether the handover can be an install at all, and if not, why.
                                // The page used to ask only about the certificate and offer the
                                // button to everyone else - including apps whose catalogue copy is
                                // older than the phone's, where the install is a downgrade Android
                                // refuses. That button downloaded the whole APK and did nothing.
                                val takeover = TakeoverPolicy.decide(
                                    installedVersionCode = installed.versionCode,
                                    installedDigests = installed.signingDigests,
                                    ownedByStore = owner == context.packageName,
                                    catalogVersionCode = app.versionCode,
                                    catalogSignatureHint = app.signatureHint
                                )
                                when (takeover.path) {
                                    // Nothing to hand over - but an app Wy Store already owns can
                                    // still be signed with a key the catalogue does not carry, and
                                    // then no update from here will ever install. The warning
                                    // predates the handover button and does not belong to it.
                                    TakeoverPath.NONE -> {
                                        val compatibility = SignatureCompatibilityPolicy.evaluate(
                                            installed.signingDigests,
                                            app.signatureHint
                                        )
                                        if (compatibility == SignatureCompatibility.MISMATCH) {
                                            Text(
                                                stringResource(R.string.details_signature_incompatible),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                    TakeoverPath.IN_PLACE -> {
                                        Text(
                                            stringResource(R.string.details_take_over_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        // Said before it happens: an app Wy Store did not install
                                        // stops on Android's dialog once, and people read that
                                        // dialog as something having gone wrong rather than as the
                                        // handover.
                                        Text(
                                            stringResource(R.string.details_first_update_manual),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        TextButton(
                                            onClick = runInstall,
                                            enabled = !busy,
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) { Text(stringResource(R.string.details_take_over)) }
                                    }
                                    TakeoverPath.REPLACE -> {
                                        Text(
                                            stringResource(
                                                if (takeover.obstacle == TakeoverObstacle.SIGNATURE) {
                                                    R.string.details_signature_incompatible
                                                } else {
                                                    R.string.details_catalog_older_blocks
                                                }
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        // The same offer the library makes for a Google-installed
                                        // app, made wherever the install cannot happen in place.
                                        TextButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                                onReplace()
                                            },
                                            enabled = !busy,
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) { Text(stringResource(R.string.details_replace)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Only while something is happening, or when it stopped with an error. A finished row
            // stays in the queue until cleanup, and rendering it left a transfer card sitting on the
            // page of an app that had been up to date for days.
            queueItem
                ?.takeIf { it.status.isInFlight || it.status == InstallQueueStatus.FAILED }
                ?.let { item { OperationProgress(it) } }

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

            // Shown even with nothing embedded in the card: the source publishes reviews on a page
            // of their own, and an app whose card carries none used to offer no way to reach them.
            if (app.reviews.isNotEmpty() || canLoadMoreReviews) {
                val filtered = ReviewSummary.filter(app.reviews, reviewStars)
                item {
                    SectionHeader(
                        title = if (app.reviews.isEmpty()) {
                            stringResource(R.string.details_reviews)
                        } else {
                            stringResource(R.string.details_reviews_count, app.reviews.size)
                        },
                        subtitle = stringResource(R.string.details_reviews_hint)
                    )
                }
                if (app.reviews.isNotEmpty()) {
                    item {
                        ReviewBreakdown(
                            reviews = app.reviews,
                            selected = reviewStars,
                            onSelect = { stars ->
                                reviewStars = stars
                                shownReviews = REVIEW_PAGE
                                // A star chosen over five embedded reviews would answer from a
                                // sample far too small; the rest are fetched before it can.
                                if (ReviewSummary.needsEveryReview(stars, canLoadMoreReviews)) {
                                    onLoadMoreReviews()
                                }
                            }
                        )
                    }
                }
                // Paged rather than rendered in one item: the parser no longer caps the list at
                // five, and a hundred cards inside a single LazyColumn item would all be composed
                // at once.
                items(
                    filtered.take(shownReviews),
                    key = { "review:${it.author}:${it.publishedAt}:${it.text.hashCode()}" }
                ) { review ->
                    ReviewCard(review)
                }
                if (filtered.isEmpty() && app.reviews.isNotEmpty() && !reviewsLoading) {
                    item {
                        Text(
                            stringResource(R.string.details_reviews_none_with_stars),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // The page the app card comes from embeds exactly five reviews, so this used to be
                // hidden behind `size > REVIEW_PAGE` and never appeared. The rest are fetched from
                // the source's own review page the first time the user asks for them.
                val remaining = filtered.size - shownReviews
                // Said rather than left to be inferred from a missing button. With a star chosen,
                // a rating with five reviews and one with fifty otherwise look the same at the
                // bottom of the list - one simply has no control under it.
                if (remaining <= 0 && !canLoadMoreReviews && reviewStars != null && filtered.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.details_reviews_all_shown, filtered.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (remaining > 0 || canLoadMoreReviews || shownReviews > REVIEW_PAGE) {
                    item {
                        TextButton(
                            onClick = {
                                when {
                                    remaining > 0 ->
                                        shownReviews = (shownReviews + REVIEW_PAGE).coerceAtMost(filtered.size)
                                    canLoadMoreReviews -> {
                                        onLoadMoreReviews()
                                        shownReviews += REVIEW_PAGE
                                    }
                                    else -> shownReviews = REVIEW_PAGE
                                }
                            },
                            enabled = !reviewsLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                when {
                                    reviewsLoading -> stringResource(R.string.details_reviews_loading)
                                    remaining > 0 -> stringResource(
                                        R.string.details_reviews_show_more,
                                        remaining.coerceAtMost(REVIEW_PAGE)
                                    )
                                    canLoadMoreReviews -> stringResource(R.string.details_reviews_load_all)
                                    else -> stringResource(R.string.details_reviews_show_less)
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
/**
 * How the reviews on screen are distributed, and the way to cut them by star.
 *
 * Deliberately labelled as the sample rather than the app's score: the source publishes its own
 * average over every review ever left, and that is what the rating at the top of the card shows.
 * These bars describe what has been loaded here, which is a different and smaller thing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewBreakdown(
    reviews: List<StoreReview>,
    selected: Int?,
    onSelect: (Int?) -> Unit
) {
    val distribution = remember(reviews) { ReviewSummary.distribution(reviews) }
    val rated = remember(distribution) { distribution.sumOf { it.second } }
    if (rated == 0) return
    WyCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            distribution.forEach { (stars, count) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$stars",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(12.dp)
                    )
                    Icon(
                        imageVector = Icons.Outlined.Star,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    LinearProgressIndicator(
                        progress = { count.toFloat() / rated },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp),
                        strokeCap = StrokeCap.Round,
                        drawStopIndicator = {}
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                stringResource(R.string.details_reviews_sample, rated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selected == null,
                    onClick = { onSelect(null) },
                    label = { Text(stringResource(R.string.details_reviews_all)) }
                )
                // Only the stars somebody actually gave: a chip that can only ever be empty is a
                // dead end dressed as a choice.
                distribution.filter { it.second > 0 }.forEach { (stars, _) ->
                    FilterChip(
                        selected = selected == stars,
                        onClick = { onSelect(if (selected == stars) null else stars) },
                        label = { Text("$stars") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Star,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

private const val REVIEW_PAGE = 5
