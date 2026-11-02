package dev.wystore.ui.details

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import dev.wystore.data.ManagedSource
import dev.wystore.R
import dev.wystore.data.StoreApp
import dev.wystore.data.StoreReview
import dev.wystore.settings.AppSettings
import dev.wystore.ui.components.PackageUiStateReducer
import dev.wystore.ui.theme.WyStoreTheme
import dev.wystore.ui.tv.TvDetailsScreen
import dev.wystore.ui.tv.TvPackageActions
import org.junit.Rule
import org.junit.Test

class ReviewSourceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun phoneShowsReviewsOnlyForRuStore() {
        val source = mutableStateOf(ManagedSource.RUSTORE)
        compose.setContent {
            val app = app(source.value)
            WyStoreTheme(AppSettings()) {
                Box(Modifier.requiredHeight(10000.dp)) {
                    AppDetailsScreen(
                        app = app, installed = null, pendingUpdate = null, rootAvailable = false,
                        busy = false, queueItem = null, reviewsLoading = false, canLoadMoreReviews = true,
                        onLoadMoreReviews = {}, onBack = {}, onLaunch = {}, onInstallPending = {},
                        onInstall = {}, onReplace = {}
                    )
                }
            }
        }
        compose.onNodeWithText("Review fixture").assertExists()
        for (other in listOf(ManagedSource.GITHUB, ManagedSource.WY_STORE)) {
            compose.runOnIdle { source.value = other }
            compose.onNodeWithText("Review fixture").assertDoesNotExist()
        }
    }

    @Test fun tvShowsReviewsOnlyForRuStore() {
        val source = mutableStateOf(ManagedSource.RUSTORE)
        val actions = TvPackageActions({}, {}, {}, {}, {}, {}, {})
        compose.setContent {
            val app = app(source.value)
            WyStoreTheme(AppSettings(), tv = true) {
                TvDetailsScreen(
                    app = app, state = PackageUiStateReducer.reduce(app = app), installed = null,
                    loading = false, forPhone = false, actions = actions, onUninstall = {},
                    modifier = Modifier.requiredHeight(10000.dp)
                )
            }
        }
        compose.onNodeWithText("Review fixture").assertExists()
        for (other in listOf(ManagedSource.GITHUB, ManagedSource.WY_STORE)) {
            compose.runOnIdle { source.value = other }
            compose.onNodeWithText("Review fixture").assertDoesNotExist()
        }
    }

    @Test fun phoneOffersLoadingEmptyReviewsOnlyForRuStore() {
        val source = mutableStateOf(ManagedSource.RUSTORE)
        compose.setContent {
            val app = app(source.value).copy(reviews = emptyList())
            WyStoreTheme(AppSettings()) {
                Box(Modifier.requiredHeight(10000.dp)) {
                    AppDetailsScreen(
                        app = app, installed = null, pendingUpdate = null, rootAvailable = false,
                        busy = false, queueItem = null, reviewsLoading = false, canLoadMoreReviews = true,
                        onLoadMoreReviews = {}, onBack = {}, onLaunch = {}, onInstallPending = {},
                        onInstall = {}, onReplace = {}
                    )
                }
            }
        }
        val loadAll = compose.activity.getString(R.string.details_reviews_load_all)
        compose.onNodeWithText(loadAll).assertExists()
        for (other in listOf(ManagedSource.GITHUB, ManagedSource.WY_STORE)) {
            compose.runOnIdle { source.value = other }
            compose.onNodeWithText(loadAll).assertDoesNotExist()
        }
    }

    private fun app(source: ManagedSource) = StoreApp(
        appId = null, packageName = "example.reviews", name = "Example", publisher = "Example",
        categories = emptyList(), shortDescription = "Description", fullDescription = "Description",
        iconUrl = null, screenshots = emptyList(), rating = null, ratingCount = null,
        downloadsText = null, versionName = "1", versionCode = 1, updatedAt = null, sizeBytes = 0,
        minAndroidVersion = null, signatureHint = null, sourceVersionId = null,
        reviews = listOf(StoreReview("Author", null, 5, "Review fixture")), source = source
    )
}
