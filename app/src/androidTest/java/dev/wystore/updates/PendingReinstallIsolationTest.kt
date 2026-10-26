package dev.wystore.updates

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingReinstallIsolationTest {
    private val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences("pending_isolation_test_$name", mode)
    }

    @After fun clear() = PendingReinstallStore(context).clear()

    @Test fun acceptingAnotherAppDoesNotLoseTheFirstPendingRemoval() {
        val first = PendingReinstall("example.first", "example.first", "First", 1L, requiresUpdateOwnership = true)
        val second = PendingReinstall("example.second", "example.second", "Second", 2L, requiresUpdateOwnership = true)
        PendingReinstallStore(context).save(first)
        PendingReinstallStore(context).save(second)

        val reopened = PendingReinstallStore(context)
        assertEquals(first, reopened.read(first.removedPackageName))
        assertEquals(second, reopened.read(second.removedPackageName))
        reopened.clear(first.removedPackageName)
        assertEquals(listOf(second), reopened.readAll())
    }

    @Test fun legacyPendingRequestRemainsRecoverableBesideANewRequest() {
        context.getSharedPreferences("wy_store_pending_reinstall", Context.MODE_PRIVATE).edit()
            .putString("removed_package", "example.legacy")
            .putString("install_package", "example.legacy")
            .putString("label", "Legacy")
            .putLong("started_at", 1L).commit()
        val newer = PendingReinstall("example.newer", "example.newer", "Newer", 2L)
        PendingReinstallStore(context).save(newer)

        assertEquals("Legacy", PendingReinstallStore(context).read("example.legacy")?.label)
        PendingReinstallStore(context).clear("example.legacy")
        assertEquals(listOf(newer), PendingReinstallStore(context).readAll())
    }
}
