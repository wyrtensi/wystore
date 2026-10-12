package dev.wystore.background

import android.net.NetworkCapabilities
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferNetworkPolicyTest {

    @Test
    fun aTransferNeedsTheInternetButNotAValidatedNetwork() {
        // A split-tunnel VPN that Android never marks validated left every download waiting for
        // good, though the connection worked; Russia's users of a RuStore client often run one.
        val required = TransferNetworkPolicy.required(unmeteredOnly = false)
        assertTrue(NetworkCapabilities.NET_CAPABILITY_INTERNET in required)
        assertFalse(NetworkCapabilities.NET_CAPABILITY_VALIDATED in required)
    }

    @Test
    fun aVpnIsAnAcceptableNetwork() {
        assertTrue(NetworkCapabilities.NET_CAPABILITY_NOT_VPN in TransferNetworkPolicy.removed())
        assertFalse(NetworkCapabilities.NET_CAPABILITY_NOT_VPN in TransferNetworkPolicy.required(unmeteredOnly = true))
    }

    @Test
    fun wifiOnlyStillMeansUnmetered() {
        assertTrue(NetworkCapabilities.NET_CAPABILITY_NOT_METERED in TransferNetworkPolicy.required(unmeteredOnly = true))
        assertFalse(NetworkCapabilities.NET_CAPABILITY_NOT_METERED in TransferNetworkPolicy.required(unmeteredOnly = false))
    }
}
