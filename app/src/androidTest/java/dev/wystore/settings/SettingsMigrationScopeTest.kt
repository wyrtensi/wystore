package dev.wystore.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.data.GitHubRepository
import dev.wystore.data.ManagedApp
import dev.wystore.data.ManagedSource
import dev.wystore.data.StoreRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The legacy SharedPreferences file holds more than settings.
 *
 * `SharedPreferencesMigration` with no `keysToMigrate` copies every key out of the file and then
 * **deletes them**. Because `wy_store` also stores the managed-app list, the saved GitHub
 * repositories, the RuStore compatibility record and the last-check summary, the unrestricted
 * migration silently wiped all of them — an app adopted or installed through Wy Store vanished
 * from the library after the next restart, and with it any chance of it being updated.
 */
@RunWith(AndroidJUnit4::class)
class SettingsMigrationScopeTest {

    private lateinit var context: Context
    private lateinit var repository: StoreRepository
    private var savedApps: List<ManagedApp> = emptyList()
    private var savedRepos: List<GitHubRepository> = emptyList()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = StoreRepository(context)
        savedApps = repository.managedApps()
        savedRepos = repository.githubRepositories()
    }

    /**
     * Removes only what this test added.
     *
     * The previous version wiped every managed app and every saved repository and then restored the
     * list captured in [setUp]. That is a destructive round trip against the user's real
     * preferences: a process death between the two halves — which an instrumented run can easily
     * cause — left the device with an empty library and no way to tell that a test did it.
     */
    @After
    fun tearDown() {
        repository.removeManaged(PROBE_PACKAGE)
        repository.githubRepositories()
            .filter { it.name == PROBE_REPOSITORY && it !in savedRepos }
            .forEach { repository.removeGithubRepository(it) }
    }

    @Test
    fun theMigrationOnlyClaimsSettingsKeys() {
        listOf(
            "managed_apps",
            "github_repositories",
            "rustore_api_version_code",
            "rustore_verified_version_name",
            "last_update_check"
        ).forEach {
            assertFalse("$it must never be migrated away", it in LEGACY_SETTINGS_KEYS)
        }
        // The keys it does claim are the ones the DataStore settings actually read back.
        listOf("theme_mode", "language", "interval_hours", "queue_mode", "github_enabled")
            .forEach { assertTrue("$it should migrate", it in LEGACY_SETTINGS_KEYS) }
    }

    @Test
    fun libraryStateSurvivesTouchingTheSettingsDataStore() {
        repository.saveManaged(
            ManagedApp(
                packageName = PROBE_PACKAGE,
                label = "Probe",
                pinnedDigests = emptySet(),
                autoUpdate = true,
                forceWyStore = false,
                addedAt = 1L,
                lastUpdatedAt = 2L,
                source = ManagedSource.GITHUB,
                githubRepository = GitHubRepository("owner", "repo"),
                githubReleaseId = 42L
            )
        )
        repository.saveGithubRepository(GitHubRepository("probe-owner", PROBE_REPOSITORY))

        // Reading settings is what triggers any pending DataStore migration.
        SettingsRepository(context).currentSettings()

        assertTrue(
            "the managed app must not be migrated away",
            StoreRepository(context).managedApps().any { it.packageName == PROBE_PACKAGE }
        )
        assertTrue(
            "saved GitHub repositories must not be migrated away",
            StoreRepository(context).githubRepositories().any { it.name == PROBE_REPOSITORY }
        )
    }

    @Test
    fun settingsThemselvesStillReadBack() {
        // A sanity check that scoping the migration did not break settings themselves.
        val settings = SettingsRepository(context).currentSettings()

        assertEquals(settings, SettingsRepository(context).currentSettings())
    }

    private companion object {
        const val PROBE_PACKAGE = "dev.wystore.migration.probe"
        const val PROBE_REPOSITORY = "probe-repo"
    }
}
