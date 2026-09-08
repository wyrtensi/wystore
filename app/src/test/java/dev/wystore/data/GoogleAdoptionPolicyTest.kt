package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAdoptionPolicyTest {

    private fun app(packageName: String, name: String) = StoreApp(
        appId = 1,
        packageName = packageName,
        name = name,
        publisher = "",
        categories = emptyList(),
        shortDescription = "",
        fullDescription = "",
        iconUrl = null,
        screenshots = emptyList(),
        rating = null,
        ratingCount = null,
        downloadsText = null,
        versionName = "1.0",
        versionCode = 1,
        updatedAt = null,
        sizeBytes = 0,
        minAndroidVersion = null,
        signatureHint = null,
        sourceVersionId = null
    )

    /** The same package name is an identity, so there is nothing for the user to choose between. */
    @Test
    fun theSamePackageIsOfferedAlone() {
        val candidates = GoogleAdoptionPolicy.candidates(
            installedPackageName = "ru.yandex.music",
            exactMatch = app("ru.yandex.music", "Яндекс Музыка"),
            byName = listOf(app("com.other.music", "Music"))
        )

        assertEquals(1, candidates.size)
        assertEquals("ru.yandex.music", candidates.single().packageName)
        assertTrue(candidates.single().exact)
    }

    @Test
    fun withoutAnExactMatchTheNearestThreeAreOffered() {
        val candidates = GoogleAdoptionPolicy.candidates(
            installedPackageName = "com.example.notes",
            exactMatch = null,
            byName = listOf(
                app("com.notes.one", "Notes"),
                app("com.notes.two", "Notes Pro"),
                app("com.notes.three", "Notes Lite"),
                app("com.notes.four", "Notes Max")
            )
        )

        assertEquals(3, candidates.size)
        assertEquals("com.notes.one", candidates.first().packageName)
        assertFalse(candidates.any { it.exact })
    }

    /** A lookup answering with someone else's package is not an exact match, whatever it claims. */
    @Test
    fun aLookupForAnotherPackageIsNotTreatedAsExact() {
        val candidates = GoogleAdoptionPolicy.candidates(
            installedPackageName = "com.example.notes",
            exactMatch = app("com.somebody.else", "Notes"),
            byName = listOf(app("com.notes.one", "Notes"))
        )

        assertEquals(listOf("com.notes.one"), candidates.map { it.packageName })
    }

    @Test
    fun aHandoverExpiresRatherThanFiringLater() {
        val now = 10_000_000L
        assertTrue(GoogleAdoptionPolicy.isPendingFresh(now - 60_000, now))
        assertFalse(
            GoogleAdoptionPolicy.isPendingFresh(now - GoogleAdoptionPolicy.PENDING_WINDOW_MS, now)
        )
        // A record from the future is a clock change, not a handover in progress.
        assertFalse(GoogleAdoptionPolicy.isPendingFresh(now + 60_000, now))
    }
}
