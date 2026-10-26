package dev.wystore.updates

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wystore.data.InstallSource
import dev.wystore.data.InstalledApp
import dev.wystore.data.ManagedApp
import dev.wystore.data.ManagedSource
import dev.wystore.data.StoreSettings
import dev.wystore.data.local.UpdateArtifactEntity
import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.updates.model.QueueState
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutomaticInstallOriginTest {
    @Test fun historicalManualRowsDoNotAuthorizeANewAutomaticReadyUpdate() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, WyStoreDatabase::class.java).build()
        val file = File.createTempFile("automatic-install-origin", ".apk", context.cacheDir)
        try {
            file.writeText("verified fixture")
            val dao = database.updateQueueDao
            val packageName = "test.automatic.install.origin"
            for ((index, state) in listOf(QueueState.INSTALLED, QueueState.FAILED).withIndex()) {
                dao.insertOrReplace(UpdateQueueEntity("manual-history-$index", packageName, "Test", "$index",
                    index.toLong(), ManagedSource.RUSTORE.name, state.name, priority = QueueOrigin.USER_REQUESTED_PRIORITY))
            }
            val ready = UpdateQueueEntity("automatic-ready", packageName, "Test", "3", 3,
                ManagedSource.RUSTORE.name, QueueState.READY_TO_INSTALL.name)
            dao.insertOrReplace(ready)
            dao.insertArtifact(UpdateArtifactEntity(ready.id, file.absolutePath, file.length()))
            val pending = QueueRepository(context, database).getPendingUpdates().single()
            val managed = ManagedApp(packageName, "Test", emptySet(), googlePlayUpdateDetected = true)
            val installed = InstalledApp(packageName, "Test", "2", 2, 0, InstallSource.WY_STORE, emptySet())

            assertFalse(pending.userRequested)
            assertFalse(AutomaticInstallPolicy.mayOffer(managed, installed,
                StoreSettings(autoInstallUpdates = true), pending.userRequested))
        } finally {
            database.close()
            file.delete()
        }
    }
}
