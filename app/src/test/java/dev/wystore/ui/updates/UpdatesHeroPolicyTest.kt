package dev.wystore.ui.updates

import dev.wystore.ui.updates.UpdatesHeroPolicy.Headline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesHeroPolicyTest {

    private fun headline(
        tracked: Int = 0, ready: Int = 0, waiting: Int = 0, problems: Int = 0, checking: Boolean = false
    ) = UpdatesHeroPolicy.headline(tracked, ready, waiting, problems, checking)

    @Test
    fun noAcceptedAppsMeansNoPanelEvenAfterACheckOfOne() {
        // The reported case: one app checked, none accepted, and the panel said "up to date" 1/1.
        assertNull(headline(tracked = 0, problems = 0))
        assertNull(headline(tracked = 0, problems = 2))
    }

    @Test
    fun upToDateNeedsSomethingToBeUpToDate() {
        assertEquals(Headline.UP_TO_DATE, headline(tracked = 3))
    }

    @Test
    fun readyAndWaitingOutrankEverythingElse() {
        assertEquals(Headline.READY, headline(tracked = 3, ready = 1, waiting = 2, problems = 1, checking = true))
        assertEquals(Headline.WAITING, headline(tracked = 3, waiting = 2, problems = 1, checking = true))
        // Downloads can exist for an app that was installed from the catalogue and not accepted.
        assertEquals(Headline.READY, headline(tracked = 0, ready = 1))
    }

    @Test
    fun aRunningCheckKeepsThePanelAndBeatsProblemsOfTheLastOne() {
        assertEquals(Headline.CHECKING, headline(tracked = 3, problems = 1, checking = true))
        assertEquals(Headline.CHECKING, headline(tracked = 0, checking = true))
    }

    @Test
    fun problemsAreReportedWhenNothingIsHappening() {
        assertEquals(Headline.PROBLEMS, headline(tracked = 3, problems = 2))
    }

    @Test
    fun emptyStateOnlyWhenThereIsNothingToList() {
        assertTrue(UpdatesHeroPolicy.showsEmptyState(tracked = 0, pending = 0, waiting = 0))
        assertFalse(UpdatesHeroPolicy.showsEmptyState(tracked = 1, pending = 0, waiting = 0))
        assertFalse(UpdatesHeroPolicy.showsEmptyState(tracked = 0, pending = 1, waiting = 0))
        assertFalse(UpdatesHeroPolicy.showsEmptyState(tracked = 0, pending = 0, waiting = 1))
    }
}
