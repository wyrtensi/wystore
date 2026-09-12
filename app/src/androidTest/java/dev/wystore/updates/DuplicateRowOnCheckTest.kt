package dev.wystore.updates

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.data.ManagedSource
import dev.wystore.data.local.WyStoreDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A row started by hand and a check finding the same app must be one row, not two.
 *
 * A manual install knows only the package name: the version is read from the source while the
 * download runs and written onto the row when verification succeeds. A row that never got that far
 * - refused, cancelled, still waiting - carries no version, and the check looked rows up by version
 * only. It found nothing, opened a second row, and fetched the same file again.
 */
@RunWith(AndroidJUnit4::class)
class DuplicateRowOnCheckTest {

    private lateinit var context: Context
    private lateinit var database: WyStoreDatabase
    private lateinit var repository: QueueRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = WyStoreDatabase.getInstance(context)
        database.clearAllTables()
        repository = QueueRepository.getInstance(context)
    }

    @After
    fun tearDown() {
        database.clearAllTables()
    }

    @Test
    fun aCheckAdoptsTheRowStartedByHandInsteadOfOpeningItsOwn(): Unit = runBlocking {
        val manual = repository.enqueueManualInstall(
            packageName = "ru.gdemoideti.parent",
            label = "Где мои дети",
            source = ManagedSource.RUSTORE
        )
        assertEquals(0L, manual?.versionCode)

        repository.enqueueAvailableUpdate(
            packageName = "ru.gdemoideti.parent",
            label = "Где мои дети",
            versionName = "2.12.7-rustore-gms",
            versionCode = 2012071,
            source = ManagedSource.RUSTORE
        )

        val rows = repository.snapshotAll().filter { it.packageName == "ru.gdemoideti.parent" }
        assertEquals("one app, one row", 1, rows.size)
        assertEquals(2012071L, rows.single().versionCode)
        assertEquals(manual?.id, rows.single().id)
    }

    @Test
    fun aRowForAnotherVersionStillGetsItsOwn(): Unit = runBlocking {
        repository.enqueueAvailableUpdate(
            packageName = "ru.ok.android",
            label = "OK",
            versionName = "26.9.10",
            versionCode = 26091000,
            source = ManagedSource.RUSTORE
        )
        repository.enqueueAvailableUpdate(
            packageName = "ru.ok.android",
            label = "OK",
            versionName = "26.10.0",
            versionCode = 26100000,
            source = ManagedSource.RUSTORE
        )

        val rows = repository.snapshotAll().filter { it.packageName == "ru.ok.android" }
        assertEquals(2, rows.size)
    }
}
