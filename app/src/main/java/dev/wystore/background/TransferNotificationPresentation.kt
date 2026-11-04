package dev.wystore.background

import androidx.core.app.NotificationCompat

/** Keep progress updates quiet and let Android retain the original ranking time. */
internal fun NotificationCompat.Builder.stableTransferPresentation(): NotificationCompat.Builder =
    setWhen(0L)
        .setShowWhen(false)
        .setSilent(true)
        .setOnlyAlertOnce(true)
