package dev.wystore.background

import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.Constraints
import androidx.work.NetworkType

/**
 * The network a download or an update check waits for: one that reaches the internet.
 *
 * JobScheduler's NETWORK_TYPE_ANY, and WorkManager's CONNECTED built on it, also ask for a network
 * Android has validated by reaching Google's connectivity check. A split-tunnel VPN whose tunnel
 * that check cannot pass is never validated, though everything works through it, and every transfer
 * then waited with "connectivity" unsatisfied - nothing on screen said why. RuStore is reached
 * however the system routes it; this only stops waiting for a mark the download does not need.
 */
object TransferNetworkPolicy {

    fun required(unmeteredOnly: Boolean): Set<Int> = buildSet {
        add(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (unmeteredOnly) add(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** NetworkRequest.Builder starts out excluding VPNs; a VPN is as good a way out as any. */
    fun removed(): Set<Int> = setOf(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)

    fun request(unmeteredOnly: Boolean): NetworkRequest = NetworkRequest.Builder().apply {
        removed().forEach(::removeCapability)
        required(unmeteredOnly).forEach(::addCapability)
    }.build()

    /** For WorkManager, which reads [NetworkType] only below API 28 - never, with this app's minimum. */
    fun Constraints.Builder.requireTransferNetwork(unmeteredOnly: Boolean): Constraints.Builder =
        setRequiredNetworkRequest(
            request(unmeteredOnly),
            if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        )
}
