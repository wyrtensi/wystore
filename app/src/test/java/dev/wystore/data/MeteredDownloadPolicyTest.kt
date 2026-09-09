package dev.wystore.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeteredDownloadPolicyTest {

    /**
     * The case the setting was written for, and the one it was silently losing: a transfer the user
     * asked for runs on any connection, so "Wi-Fi only" was spent rather than honoured.
     */
    @Test
    fun wifiOnlyOnAMeteredConnectionIsWorthAQuestion() {
        assertTrue(
            MeteredDownloadPolicy.requiresConsent(
                wifiOnly = true,
                allowMobileData = false,
                isMetered = true,
                allowedThisSession = false
            )
        )
    }

    @Test
    fun nothingIsAskedOnAnUnmeteredConnection() {
        assertFalse(
            MeteredDownloadPolicy.requiresConsent(
                wifiOnly = true,
                allowMobileData = false,
                isMetered = false,
                allowedThisSession = false
            )
        )
    }

    /** Someone who chose "mobile data is fine" has already answered this question. */
    @Test
    fun nothingIsAskedOfSomeoneWhoAllowedMobileData() {
        assertFalse(
            MeteredDownloadPolicy.requiresConsent(
                wifiOnly = false,
                allowMobileData = true,
                isMetered = true,
                allowedThisSession = false
            )
        )
    }

    /**
     * The queue moves one item at a time, and the settings used to apply to the first download of a
     * round only: everything the queue started behind it ran on whatever connection there was.
     */
    @Test
    fun theYesInTheDialogCarriesTheRestOfTheRound() {
        assertTrue(
            MeteredDownloadPolicy.allowsMobileData(allowMobileData = false, allowedThisSession = true)
        )
        assertTrue(
            MeteredDownloadPolicy.allowsMobileData(allowMobileData = true, allowedThisSession = false)
        )
        assertFalse(
            MeteredDownloadPolicy.allowsMobileData(allowMobileData = false, allowedThisSession = false)
        )
    }

    /** Once per run of the app. Asked for every download, it would be a nuisance, not a warning. */
    @Test
    fun theAnswerHoldsForTheRestOfTheSession() {
        assertFalse(
            MeteredDownloadPolicy.requiresConsent(
                wifiOnly = true,
                allowMobileData = false,
                isMetered = true,
                allowedThisSession = true
            )
        )
    }
}
