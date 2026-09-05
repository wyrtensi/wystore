package dev.wystore.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that stop Wy Store from spamming.
 *
 * Before these existed, a bulk update posted one notification per app plus a group summary, and
 * every four-hourly background check re-announced the same pending updates.
 */
class NotificationPolicyTest {

    @Test
    fun aQuietWindowThatCrossesMidnightCoversBothSidesOfIt() {
        val quiet = { hour: Int -> NotificationPolicy.isQuietHour(hour, enabled = true, startHour = 23, endHour = 8) }

        assertTrue(quiet(23))
        assertTrue(quiet(0))
        assertTrue(quiet(7))
        assertFalse(quiet(8))
        assertFalse(quiet(12))
        assertFalse(quiet(22))
    }

    @Test
    fun aDaytimeWindowDoesNotWrap() {
        val quiet = { hour: Int -> NotificationPolicy.isQuietHour(hour, enabled = true, startHour = 9, endHour = 18) }

        assertFalse(quiet(8))
        assertTrue(quiet(9))
        assertTrue(quiet(17))
        assertFalse(quiet(18))
        assertFalse(quiet(23))
    }

    @Test
    fun quietHoursOffMeansNeverQuiet() {
        (0..23).forEach { hour ->
            assertFalse(NotificationPolicy.isQuietHour(hour, enabled = false, startHour = 0, endHour = 23))
        }
    }

    @Test
    fun anEmptyWindowCannotSilenceTheAppForever() {
        // Start == end would otherwise be read as "the whole day", leaving the user with an app
        // that never makes a sound and no obvious way to tell why.
        (0..23).forEach { hour ->
            assertFalse(NotificationPolicy.isQuietHour(hour, enabled = true, startHour = 3, endHour = 3))
        }
    }

    @Test
    fun theDigestIgnoresOrderAndDuplicates() {
        val a = NotificationPolicy.digestOf(listOf("one", "two", "three"))
        val b = NotificationPolicy.digestOf(listOf("three", "one", "two", "one"))

        assertEquals(a, b)
    }

    @Test
    fun blankEntriesDoNotChangeTheDigest() {
        assertEquals(
            NotificationPolicy.digestOf(listOf("one")),
            NotificationPolicy.digestOf(listOf("one", "", "   "))
        )
    }

    @Test
    fun aChangedSetAlerts() {
        val decision = NotificationPolicy.decide(
            categoryEnabled = true,
            digest = "a|b",
            lastPublishedDigest = "a",
            quietHours = false
        )

        assertTrue(decision.post)
        assertTrue(decision.alert)
    }

    @Test
    fun repeatingTheSameSetPostsSilently() {
        // The shade entry has to stay accurate, but a periodic check finding the same three
        // updates must not buzz every time it runs.
        val decision = NotificationPolicy.decide(
            categoryEnabled = true,
            digest = "a|b",
            lastPublishedDigest = "a|b",
            quietHours = false
        )

        assertTrue(decision.post)
        assertFalse(decision.alert)
    }

    @Test
    fun quietHoursPostWithoutAlerting() {
        val decision = NotificationPolicy.decide(
            categoryEnabled = true,
            digest = "a|b",
            lastPublishedDigest = null,
            quietHours = true
        )

        assertTrue(decision.post)
        assertFalse(decision.alert)
    }

    @Test
    fun aDisabledCategoryPostsNothing() {
        val decision = NotificationPolicy.decide(
            categoryEnabled = false,
            digest = "a",
            lastPublishedDigest = null,
            quietHours = false
        )

        assertFalse(decision.post)
        assertFalse(decision.alert)
    }

    @Test
    fun anEmptySetRemovesTheNotification() {
        val decision = NotificationPolicy.decide(
            categoryEnabled = true,
            digest = "",
            lastPublishedDigest = "a",
            quietHours = false
        )

        assertFalse(decision.post)
    }

    @Test
    fun aBackgroundCheckThatFoundNothingSaysNothing() {
        assertFalse(
            NotificationPolicy.shouldReportCheck(
                categoryEnabled = true,
                manual = false,
                updatesFound = 0,
                problems = 0
            )
        )
    }

    @Test
    fun aManualCheckAlwaysReportsBack() {
        // The user pressed the button and is waiting for an answer, so this ignores the category
        // switch, which governs unattended background reports.
        assertTrue(
            NotificationPolicy.shouldReportCheck(
                categoryEnabled = false,
                manual = true,
                updatesFound = 0,
                problems = 0
            )
        )
    }

    @Test
    fun aBackgroundCheckReportsFindingsAndProblems() {
        assertTrue(
            NotificationPolicy.shouldReportCheck(
                categoryEnabled = true, manual = false, updatesFound = 2, problems = 0
            )
        )
        assertTrue(
            NotificationPolicy.shouldReportCheck(
                categoryEnabled = true, manual = false, updatesFound = 0, problems = 1
            )
        )
    }
}
