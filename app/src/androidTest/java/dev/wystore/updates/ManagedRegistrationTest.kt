package dev.wystore.updates

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.data.ManagedApp
import dev.wystore.data.ManagedSource
import dev.wystore.data.StoreRepository
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An app installed through Wy Store must stay registered, or it never receives an update.
 *
 * It previously did not: `getInstalledPackages` is cached per process and the app cannot refresh
 * that cache on install (manifest `PACKAGE_ADDED` receivers do not fire on API 26+), so a
 * just-installed package was missing from the list and pruning on that list alone deleted the
 * registration seconds after it was written.
 */
@RunWith(AndroidJUnit4::class)
class ManagedRegistrationTest {

    private lateinit var context: Context
    private lateinit var repository: StoreRepository
    private var saved: List<ManagedApp> = emptyList()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = StoreRepository(context)
        saved = repository.managedApps()
    }

    @After
    fun tearDown() {
        repository.managedApps().forEach { repository.removeManaged(it.packageName) }
        saved.forEach { repository.saveManaged(it) }
    }

    private fun managed(packageName: String) = ManagedApp(
        packageName = packageName,
        label = packageName,
        pinnedDigests = emptySet(),
        autoUpdate = true,
        forceWyStore = false,
        addedAt = 0L,
        lastUpdatedAt = 0L,
        source = ManagedSource.GITHUB,
        githubRepository = null,
        githubReleaseId = null
    )

    @Test
    fun anInstalledAppIsKeptEvenWhenTheCachedPackageListMissesIt() {
        // The device certainly has this package: it is the app running the test.
        val self = context.packageName
        repository.saveManaged(managed(self))

        // Exactly the state after an install: the app is on the device, but the cached list the
        // caller passes in does not mention it yet.
        val retained = repository.retainManagedInstalled(emptySet())

        assertTrue(
            "an installed app must survive pruning driven by a stale list",
            retained.any { it.packageName == self }
        )
        assertTrue(repository.managedApps().any { it.packageName == self })
    }

    @Test
    fun anAppThatIsGenuinelyGoneIsStillPruned() {
        repository.saveManaged(managed("dev.wystore.definitely.not.installed"))

        val retained = repository.retainManagedInstalled(emptySet())

        assertFalse(retained.any { it.packageName == "dev.wystore.definitely.not.installed" })
        assertFalse(repository.managedApps().any { it.packageName == "dev.wystore.definitely.not.installed" })
    }

    @Test
    fun theDirectInstallCheckDoesNotRelyOnTheCachedList() {
        assertTrue(repository.isInstalled(context.packageName))
        assertFalse(repository.isInstalled("dev.wystore.definitely.not.installed"))
    }

    @Test
    fun aManagedAppSurvivesAProcessBoundaryBecauseTheWriteIsSynchronous() {
        val packageName = context.packageName
        repository.saveManaged(managed(packageName))

        // A second repository reads the same backing store, as the receiver and the ViewModel do.
        assertTrue(StoreRepository(context).managedApps().any { it.packageName == packageName })
    }
}
