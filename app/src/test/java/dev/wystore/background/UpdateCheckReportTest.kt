package dev.wystore.background

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A check that finishes without saying anything reads as a check that never ran. These pin the
 * payload the ViewModel's observer actually reads.
 */
class UpdateCheckReportTest {

    @Test
    fun progressCarriesTheCountsTheCardDisplays() {
        val data = UpdateCheckReport.progress(checked = 3, total = 12, updates = 1)

        assertEquals(UpdateCheckReport.STATUS_CHECKING, data.getString(UpdateCheckReport.KEY_STATUS))
        assertEquals(3, data.getInt(UpdateCheckReport.KEY_CHECKED, -1))
        assertEquals(12, data.getInt(UpdateCheckReport.KEY_TOTAL, -1))
        assertEquals(1, data.getInt(UpdateCheckReport.KEY_UPDATES, -1))
    }

    @Test
    fun resultCarriesTheLineTheSnackbarShows() {
        val data = UpdateCheckReport.result(
            checked = 12,
            total = 12,
            updates = 2,
            detail = "Checked 12 apps, 2 updates"
        )

        assertEquals(UpdateCheckReport.STATUS_COMPLETE, data.getString(UpdateCheckReport.KEY_STATUS))
        assertEquals("Checked 12 apps, 2 updates", data.getString(UpdateCheckReport.KEY_DETAIL))
        assertEquals(12, data.getInt(UpdateCheckReport.KEY_CHECKED, -1))
        assertEquals(2, data.getInt(UpdateCheckReport.KEY_UPDATES, -1))
    }

    @Test
    fun aQuietLibraryIsReportedAsUpToDate() {
        assertEquals(CheckOutcome.UP_TO_DATE, UpdateCheckReport.outcome(updates = 0, problems = 0))
    }

    @Test
    fun updatesAreReportedWhenNothingWentWrong() {
        assertEquals(CheckOutcome.UPDATES_FOUND, UpdateCheckReport.outcome(updates = 2, problems = 0))
    }

    @Test
    fun unreachableAppsOutrankGoodNews() {
        // Half the library answering says nothing about the half that did not, so "2 updates" on
        // its own would read as a clean bill of health.
        assertEquals(CheckOutcome.PROBLEMS, UpdateCheckReport.outcome(updates = 2, problems = 1))
        assertEquals(CheckOutcome.PROBLEMS, UpdateCheckReport.outcome(updates = 0, problems = 3))
    }
}
