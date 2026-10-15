package dev.wystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Adopt" on an app from somewhere other than Google recorded it as RuStore's whatever it was: a
 * GitHub APK and a vendor store both came out labelled "RuStore".
 */
class AdoptionPolicyTest {

    private val key = "a".repeat(64)
    private val otherKey = "b".repeat(64)

    private fun installed(packageName: String, source: InstallSource = InstallSource.OTHER) = InstalledApp(
        packageName = packageName,
        label = packageName,
        versionName = "1.0",
        versionCode = 1,
        lastUpdateTime = 0,
        source = source,
        signingDigests = setOf(key)
    )

    private fun store(packageName: String, signature: String? = key) = StoreApp(
        appId = 1,
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
        versionName = "1.0",
        versionCode = 1,
        updatedAt = null,
        sizeBytes = 0,
        minAndroidVersion = null,
        signatureHint = signature,
        sourceVersionId = null
    )

    private val repository = GitHubRepository("amnezia-vpn", "amnezia-client")
    private fun catalog(packageName: String) =
        GitHubCatalogEntry(repository, "AmneziaVPN", "amnezia", "", packageName = packageName)

    @Test
    fun `the same package in RuStore is kept in place and needs no question`() {
        val options = AdoptionPolicy.options(installed("ru.app"), store("ru.app"), catalogEntry = null)
        assertTrue(options.single() is AdoptionOption.RuStore)
        assertEquals(options.single(), AdoptionPolicy.automatic(options))
    }

    @Test
    fun `a RuStore copy signed with another key can only replace the app`() {
        val options = AdoptionPolicy.options(installed("ru.app"), store("ru.app", otherKey), catalogEntry = null)
        assertTrue(options.single() is AdoptionOption.Replace)
        assertNull(AdoptionPolicy.automatic(options))
    }

    @Test
    fun `a package the catalogue knows from GitHub is offered its repository`() {
        val options = AdoptionPolicy.options(installed("org.amnezia.vpn"), null, catalog("org.amnezia.vpn"))
        assertEquals(repository, (options.single() as AdoptionOption.GitHub).repository)
        assertEquals(options.single(), AdoptionPolicy.automatic(options))
    }

    @Test
    fun `two sources for one package are a choice, not an automatic answer`() {
        val options = AdoptionPolicy.options(installed("org.amnezia.vpn"), store("org.amnezia.vpn"), catalog("org.amnezia.vpn"))
        assertEquals(2, options.size)
        assertNull(AdoptionPolicy.automatic(options))
    }

    @Test
    fun `apps found by name are replacements and only offered when nothing matches the package`() {
        val byName = listOf(store("com.xiaomi.other"), store("com.xiaomi.mipicks"))
        val options = AdoptionPolicy.options(installed("com.xiaomi.mipicks"), null, null, byName)
        assertEquals(listOf("com.xiaomi.other"), options.map { (it as AdoptionOption.Replace).candidate.packageName })
        assertNull(AdoptionPolicy.automatic(options))
        assertEquals(1, AdoptionPolicy.options(installed("ru.app"), store("ru.app"), null, byName).size)
    }

    @Test
    fun `nothing found means nothing to accept`() {
        val options = AdoptionPolicy.options(installed("com.xiaomi.mipicks"), null, null)
        assertTrue(options.isEmpty())
        assertNull(AdoptionPolicy.automatic(options))
    }

    @Test
    fun `a Google app is still only ever replaced`() {
        val options = AdoptionPolicy.options(installed("ru.app", InstallSource.GOOGLE_PLAY), store("ru.app"), catalog("ru.app"))
        assertTrue(options.single() is AdoptionOption.Replace)
        assertNull(AdoptionPolicy.automatic(options))
    }
}
