package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultPolicyTest {

    private fun page(vararg packages: String, page: Int = 1, total: Int? = null) =
        SearchPage(packages.map(::app), page, total)

    @Test
    fun anExactPackageMatchIsMovedToTheFront() {
        val result = SearchResultPolicy.promoteExactPackage(
            page("com.other.app", "org.telegram.messenger", "com.third.app"),
            "org.telegram.messenger"
        )

        assertEquals(
            listOf("org.telegram.messenger", "com.other.app", "com.third.app"),
            result.apps.map { it.packageName }
        )
    }

    @Test
    fun promotionIsCaseInsensitiveAndIgnoresSurroundingSpace() {
        val result = SearchResultPolicy.promoteExactPackage(
            page("com.other.app", "org.Telegram.Messenger"),
            "  org.telegram.messenger  "
        )

        assertEquals("org.Telegram.Messenger", result.apps.first().packageName)
    }

    @Test
    fun aPlainTextQueryIsLeftAlone() {
        val original = page("com.other.app", "org.telegram.messenger")

        val result = SearchResultPolicy.promoteExactPackage(original, "telegram")

        assertEquals(original.apps.map { it.packageName }, result.apps.map { it.packageName })
    }

    @Test
    fun aPartialPackageMatchIsNotPromoted() {
        val original = page("com.other.app", "org.telegram.messenger.web")

        val result = SearchResultPolicy.promoteExactPackage(original, "org.telegram.messenger")

        assertEquals(original.apps.map { it.packageName }, result.apps.map { it.packageName })
    }

    @Test
    fun anAlreadyFirstMatchIsNotReordered() {
        val original = page("org.telegram.messenger", "com.other.app")

        val result = SearchResultPolicy.promoteExactPackage(original, "org.telegram.messenger")

        assertEquals(original.apps.map { it.packageName }, result.apps.map { it.packageName })
    }

    @Test
    fun appendingKeepsOrderAndDropsRepeats() {
        val first = page("a.one", "a.two", page = 1, total = 5)
        val second = page("a.two", "a.three", page = 2, total = 5)

        val merged = SearchResultPolicy.appendPage(first, second)

        assertEquals(listOf("a.one", "a.two", "a.three"), merged.apps.map { it.packageName })
        assertEquals(2, merged.page)
        assertEquals(5, merged.total)
    }

    @Test
    fun appendingKeepsTheKnownTotalWhenTheNextPageOmitsIt() {
        val first = page("a.one", page = 1, total = 7)
        val second = page("a.two", page = 2, total = null)

        assertEquals(7, SearchResultPolicy.appendPage(first, second).total)
    }

    @Test
    fun exhaustionNeedsAKnownTotal() {
        assertFalse("an unknown total cannot prove exhaustion", SearchResultPolicy.isExhausted(page("a.one")))
        assertFalse(SearchResultPolicy.isExhausted(page("a.one", "a.two", total = 5)))
        assertTrue(SearchResultPolicy.isExhausted(page("a.one", "a.two", total = 2)))
    }
}

private fun app(packageName: String) = StoreApp(
    appId = 0,
    packageName = packageName,
    name = packageName,
    publisher = "",
    categories = emptyList(),
    shortDescription = "",
    fullDescription = "",
    iconUrl = null,
    screenshots = emptyList(),
    rating = null,
    ratingCount = null,
    downloadsText = null,
    versionName = "",
    versionCode = 0,
    updatedAt = null,
    sizeBytes = 0,
    minAndroidVersion = null,
    signatureHint = null,
    sourceVersionId = null
)
