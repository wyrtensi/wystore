package dev.wystore.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.updates.InstalledAppRegistrar
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GooglePlayPausePersistenceTest {
    private lateinit var context: Context
    private lateinit var repository: StoreRepository
    private var saved = emptyList<ManagedApp>()
    private val app = ManagedApp("test.play.pause", "Test", setOf("certificate"),
        autoUpdate = false, source = ManagedSource.GITHUB,
        githubRepository = GitHubRepository("owner", "repo"))
    private val installed = InstalledApp("test.play.pause", "Test", "2", 2, 0,
        InstallSource.WY_STORE, setOf("certificate"), "com.android.vending",
        updateOwnerPackageName = "app.wystore")

    @Before fun setUp() {
        // A real library scan correctly removes these deliberately uninstalled fixture packages.
        // Keep their real SharedPreferences separate from the application's background workers.
        context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences("google_play_pause_persistence_$name", mode)
        }
        repository = StoreRepository(context)
        saved = repository.managedApps()
        repository.saveManaged(app, clearGooglePlayUpdate = true)
    }

    @After fun tearDown() {
        repository.managedApps().forEach { repository.removeManaged(it.packageName) }
        saved.forEach { repository.saveManaged(it) }
    }

    @Test fun staleUiSaveCannotEraseAnObservationOrChangeTheSavedSource() {
        val stale = repository.managedApps().first { it.packageName == app.packageName }
        StoreRepository(context).updateManaged(app.packageName) {
            requireNotNull(it).copy(googlePlayUpdateDetected = true)
        }
        repository.saveManaged(stale.copy(label = "Renamed"))

        val restored = StoreRepository(context).managedApps().first { it.packageName == app.packageName }
        assertTrue(restored.googlePlayUpdateDetected)
        assertFalse(restored.autoUpdate)
        assertEquals(ManagedSource.GITHUB, restored.source)
        assertEquals(app.githubRepository, restored.githubRepository)
        assertEquals("Renamed", restored.label)
    }

    @Test fun registrationAndObservationAcrossInstancesDoNotLoseUpdates() {
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val registrations = executor.submit {
                ready.countDown()
                check(start.await(10, TimeUnit.SECONDS))
                repeat(40) { index ->
                    StoreRepository(context).updateManaged(app.packageName) { previous ->
                        requireNotNull(previous).copy(lastUpdatedAt = index.toLong())
                    }
                }
            }
            val observations = executor.submit {
                ready.countDown()
                check(start.await(10, TimeUnit.SECONDS))
                repeat(40) {
                    StoreRepository(context).updateManaged(app.packageName) {
                        requireNotNull(it).copy(googlePlayUpdateDetected = true)
                    }
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            registrations.get(20, TimeUnit.SECONDS)
            observations.get(20, TimeUnit.SECONDS)
            val restored = repository.managedApps().first { it.packageName == app.packageName }
            assertTrue(restored.googlePlayUpdateDetected)
            assertEquals(39L, restored.lastUpdatedAt)
            assertFalse(restored.autoUpdate)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun onlyExplicitVerifiedHandoverWriteClearsTheStickyRecord() {
        repository.updateManaged(app.packageName) { requireNotNull(it).copy(googlePlayUpdateDetected = true) }
        repository.updateManaged(app.packageName) { requireNotNull(it).copy(lastUpdatedAt = 3L) }
        assertTrue(repository.managedApps().first { it.packageName == app.packageName }.googlePlayUpdateDetected)

        repository.updateManaged(app.packageName, clearGooglePlayUpdate = true) { requireNotNull(it) }
        assertFalse(StoreRepository(context).managedApps().first { it.packageName == app.packageName }.googlePlayUpdateDetected)
    }

    @Test fun observingAnUnmanagedPackageDoesNotAdoptIt() {
        repository.removeManaged(app.packageName)
        repository.observeGooglePlayUpdates(listOf(installed))
        assertFalse(repository.managedApps().any { it.packageName == app.packageName })
    }

    @Test fun stalePlayScanAfterVerifiedHandoverDoesNotRestoreThePause() {
        val actual = requireNotNull(repository.installedAppFresh(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.packageName))
        assertFalse("the test APK must be installed outside Google Play",
            actual.installerPackageName == "com.android.vending")
        val managed = ManagedApp(actual.packageName, actual.label, actual.signingDigests,
            googlePlayUpdateDetected = true)
        repository.saveManaged(managed)
        val staleScan = actual.copy(installerPackageName = "com.android.vending")
        repository.updateManaged(actual.packageName, clearGooglePlayUpdate = true) { requireNotNull(it) }

        val refreshed = StoreRepository(context).observeGooglePlayUpdates(listOf(staleScan))

        assertFalse(refreshed.first { it.packageName == actual.packageName }.googlePlayUpdateDetected)
        assertFalse(StoreRepository(context).managedApps()
            .first { it.packageName == actual.packageName }.googlePlayUpdateDetected)
    }

    @Test fun ordinaryVerifiedRegistrationPreservesPauseAndPerAppChoices() = runBlocking {
        val actual = requireNotNull(repository.installedAppFresh(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.packageName))
        repository.saveManaged(ManagedApp(actual.packageName, actual.label, actual.signingDigests,
            autoUpdate = false, forceWyStore = true, googlePlayUpdateDetected = true))
        val row = UpdateQueueEntity("play-pause-registration", actual.packageName, actual.label,
            actual.versionName, actual.versionCode, ManagedSource.GITHUB.name, "INSTALLED",
            signingDigests = actual.signingDigests.joinToString(","))

        assertTrue(InstalledAppRegistrar.register(context, row))

        val registered = repository.managedApps().first { it.packageName == actual.packageName }
        assertTrue(registered.googlePlayUpdateDetected)
        assertFalse(registered.autoUpdate)
        assertTrue(registered.forceWyStore)
    }

    @Test fun aHandoverWithoutVerifiedSigningIdentityCannotClearThePause() = runBlocking {
        val actual = requireNotNull(repository.installedAppFresh(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.packageName))
        repository.saveManaged(ManagedApp(actual.packageName, actual.label, actual.signingDigests,
            googlePlayUpdateDetected = true))
        val row = UpdateQueueEntity("play-pause-unverified-handover", actual.packageName, actual.label,
            actual.versionName, actual.versionCode, ManagedSource.RUSTORE.name, "INSTALLED",
            requiresUpdateOwnership = true)

        InstalledAppRegistrar.register(context, row)

        assertTrue(repository.managedApps().first { it.packageName == actual.packageName }.googlePlayUpdateDetected)
    }
}
