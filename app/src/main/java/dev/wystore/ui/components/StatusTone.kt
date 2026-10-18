package dev.wystore.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.wystore.InstallQueueStatus

/**
 * What kind of state an app is in, which decides the colour it is told in.
 *
 * Every screen used to pick its own: the accent for one state on Home, grey on the queue card, the
 * error container for a whole failed card. The Wy Hub concept tells states by a word in a colour
 * that means the same everywhere - green for ready, yellow for something to tune, blue for a
 * question - so the colour is decided once, here.
 */
enum class StatusTone {
    /** Nothing happening, or waiting its turn: grey. */
    NEUTRAL,

    /** Work under way - checking, downloading, verifying, installing: the accent. */
    ACTIVE,

    /** Downloaded and verified, one step from installed: green. */
    READY,

    /** Stopped by the user, and carried on by them: yellow. */
    PAUSED,

    /** Waiting on an answer - Android's dialog, a permission, the user about a file: blue. */
    ASKING,

    /** Failed: red, and told by a sentence rather than a tag. */
    FAILED
}

object StatusTones {

    fun of(code: StatusCode): StatusTone = when (code) {
        StatusCode.CHECKING,
        StatusCode.DOWNLOADING,
        StatusCode.VERIFYING,
        StatusCode.INSTALLING -> StatusTone.ACTIVE
        StatusCode.READY_TO_INSTALL -> StatusTone.READY
        StatusCode.PAUSED -> StatusTone.PAUSED
        StatusCode.AWAITING_UNKNOWN_SOURCES_PERMISSION,
        StatusCode.AWAITING_USER_CONFIRMATION,
        StatusCode.FAILED_SOURCE_UNCONFIRMED,
        StatusCode.OFFER_NEXT -> StatusTone.ASKING
        StatusCode.FAILED_NETWORK,
        StatusCode.FAILED_STORAGE,
        StatusCode.FAILED_SIGNATURE,
        StatusCode.FAILED_GENERIC -> StatusTone.FAILED
        StatusCode.NOT_INSTALLED,
        StatusCode.QUEUED,
        StatusCode.INSTALLED,
        StatusCode.CANCELLED,
        StatusCode.SKIPPED -> StatusTone.NEUTRAL
    }

    /** A queue row's state; one waiting on Android's dialog is a question, not an install. */
    fun of(status: InstallQueueStatus, awaitingConfirmation: Boolean): StatusTone = when (status) {
        InstallQueueStatus.RESOLVING,
        InstallQueueStatus.DOWNLOADING,
        InstallQueueStatus.VERIFYING -> StatusTone.ACTIVE
        InstallQueueStatus.INSTALLING -> if (awaitingConfirmation) StatusTone.ASKING else StatusTone.ACTIVE
        InstallQueueStatus.READY -> StatusTone.READY
        InstallQueueStatus.PAUSED -> StatusTone.PAUSED
        InstallQueueStatus.FAILED -> StatusTone.FAILED
        InstallQueueStatus.QUEUED,
        InstallQueueStatus.COMPLETE,
        InstallQueueStatus.CANCELED -> StatusTone.NEUTRAL
    }
}

/** The colour of [tone] in the current palette, or null for one that is told in grey. */
@Composable
fun StatusTone.color(): Color? {
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    return when (this) {
        StatusTone.NEUTRAL -> null
        StatusTone.ACTIVE -> colors.primary
        StatusTone.READY -> if (dark) Color(0xFF6FCF97) else Color(0xFF1E7F48)
        StatusTone.PAUSED -> if (dark) Color(0xFFF2C94C) else Color(0xFF8A6A00)
        StatusTone.ASKING -> if (dark) Color(0xFF6CC7F0) else Color(0xFF1F6A96)
        StatusTone.FAILED -> colors.error
    }
}
