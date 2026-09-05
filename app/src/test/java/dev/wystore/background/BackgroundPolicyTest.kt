package dev.wystore.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the app is allowed to spend battery on when nobody is looking. */
class BackgroundPolicyTest {

    @Test
    fun batterySaverStopsTheBackgroundCheck() {
        assertFalse(
            BackgroundPolicy.shouldRunCheck(
                manual = false,
                powerSaveMode = true,
                respectBatterySaver = true,
                managedAppCount = 5
            )
        )
    }

    @Test
    fun theUserCanOptOutOfThatDeference() {
        assertTrue(
            BackgroundPolicy.shouldRunCheck(
                manual = false,
                powerSaveMode = true,
                respectBatterySaver = false,
                managedAppCount = 5
            )
        )
    }

    @Test
    fun aManualCheckRunsEvenInBatterySaver() {
        // The user is standing there waiting; refusing silently would look like a bug.
        assertTrue(
            BackgroundPolicy.shouldRunCheck(
                manual = true,
                powerSaveMode = true,
                respectBatterySaver = true,
                managedAppCount = 0
            )
        )
    }

    @Test
    fun nothingAdoptedMeansNothingToCheck() {
        assertFalse(
            BackgroundPolicy.shouldRunCheck(
                manual = false,
                powerSaveMode = false,
                respectBatterySaver = true,
                managedAppCount = 0
            )
        )
        assertFalse(BackgroundPolicy.shouldSchedulePeriodicWork(0))
        assertTrue(BackgroundPolicy.shouldSchedulePeriodicWork(1))
    }

    @Test
    fun theFlexWindowIsHalfThePeriod() {
        assertEquals(720L, BackgroundPolicy.flexMinutesFor(24))
        assertEquals(360L, BackgroundPolicy.flexMinutesFor(12))
        assertEquals(180L, BackgroundPolicy.flexMinutesFor(6))
    }

    @Test
    fun aNonsensicalPeriodStillProducesAValidFlex() {
        // Android rejects a flex below five minutes, which would make the whole request invalid,
        // and a zero or negative interval must not turn into a zero flex.
        listOf(-5L, 0L, 1L, 6L, 24L).forEach { hours ->
            assertTrue(
                "flex for $hours h must clear Android's minimum",
                BackgroundPolicy.flexMinutesFor(hours) >= BackgroundPolicy.MIN_FLEX_MINUTES
            )
        }
        // The interval itself is floored at one hour, so the shortest real window is half of that.
        assertEquals(30L, BackgroundPolicy.flexMinutesFor(1))
        assertEquals(30L, BackgroundPolicy.flexMinutesFor(0))
    }
}
