package dev.wystore.ui.updates

import dev.wystore.R
import dev.wystore.UpdateCheckTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTextTest {

    @Test
    fun theStatesTheViewModelReportsEachHaveTheirOwnTitle() {
        // StoreViewModel maps WorkManager's states to these four; a failed run must not read as
        // "up to date", which is where an unknown status falls.
        assertEquals(R.string.update_check_queued, UpdateCheckText.titleRes("QUEUED"))
        assertEquals(R.string.update_check_checking, UpdateCheckText.titleRes("CHECKING"))
        assertEquals(R.string.update_check_complete, UpdateCheckText.titleRes("COMPLETE"))
        assertEquals(R.string.update_check_error, UpdateCheckText.titleRes("FAILED"))
    }

    @Test
    fun theCountReplacesTheDetailOnlyOnceTheAppsAreCounted() {
        assertFalse(UpdateCheckText.showsCount(UpdateCheckTask("QUEUED", "Queued", active = true)))
        assertTrue(UpdateCheckText.showsCount(UpdateCheckTask("CHECKING", "0 of 7", total = 7, active = true)))
    }
}
