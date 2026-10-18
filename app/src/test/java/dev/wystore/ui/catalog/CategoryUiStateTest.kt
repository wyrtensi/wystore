package dev.wystore.ui.catalog

import dev.wystore.data.StoreApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryUiStateTest {

    private val shown = listOf(
        StoreApp(
            appId = 0, packageName = "ru.example", name = "Example", publisher = "", categories = emptyList(),
            shortDescription = "", fullDescription = "", iconUrl = null, screenshots = emptyList(), rating = null,
            ratingCount = null, downloadsText = null, versionName = "", versionCode = 0, updatedAt = null,
            sizeBytes = 0, minAndroidVersion = null, signatureHint = null, sourceVersionId = null
        )
    )

    @Test
    fun aFailedNextPageIsWhatRetryLoads() {
        // "Next" failed: page 1 is still on screen, page 2 is the one that did not come.
        val state = CategoryUiState(apps = shown, page = 1, error = "offline", failedPage = 2)
        assertEquals(2, state.retryPage)
        assertTrue(state.failedOverList)
    }

    @Test
    fun withNothingFailedRetryReloadsThePageOnScreen() {
        val state = CategoryUiState(apps = shown, page = 3)
        assertEquals(3, state.retryPage)
        assertFalse(state.failedOverList)
    }

    @Test
    fun anEmptyListLeavesTheFailureToTheEmptyState() {
        val state = CategoryUiState(page = 1, error = "offline", failedPage = 1)
        assertFalse(state.failedOverList)
    }
}
