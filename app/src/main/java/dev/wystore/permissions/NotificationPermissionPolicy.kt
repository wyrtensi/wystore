package dev.wystore.permissions

import android.os.Build

/** What to do when somebody, or something, wants notifications turned on. */
enum class NotificationPermissionAction {
    /** Nothing to do: they are already allowed. */
    NONE,

    /** Android can still show its own dialog, which is one tap instead of a trip to Settings. */
    ASK_SYSTEM,

    /** The dialog is spent or does not exist on this version; Settings is the only way back. */
    OPEN_SETTINGS
}

/**
 * Whether Android's notification dialog can still be raised, and whether it is worth raising.
 *
 * From Android 13 a new install has notifications denied until it asks, and this store never
 * asked: the permission was declared in the manifest, checked on every screen, and the only way to
 * grant it was to find the permission screen in Settings and turn it on there. So a store whose
 * whole background story is "it will tell you" said nothing at all on a new phone, and gave no
 * sign that it was going to.
 *
 * The dialog is effectively one-shot - after a refusal Android will not show it again for the life
 * of the install - so it is spent carefully: never at first launch, when nobody yet knows what
 * this app is, and only once there is something to be notified about. Asking too early does not
 * just annoy; it burns the single chance and leaves Settings as the only route for good.
 */
object NotificationPermissionPolicy {

    /**
     * The action behind the button in the permission screen.
     *
     * Below Android 13 there is no permission to ask for, and notifications can still be off
     * because the user switched them off - which is a Settings matter either way.
     */
    fun actionForRequest(
        sdkInt: Int = Build.VERSION.SDK_INT,
        granted: Boolean,
        alreadyAsked: Boolean
    ): NotificationPermissionAction = when {
        granted -> NotificationPermissionAction.NONE
        sdkInt < Build.VERSION_CODES.TIRAMISU -> NotificationPermissionAction.OPEN_SETTINGS
        alreadyAsked -> NotificationPermissionAction.OPEN_SETTINGS
        else -> NotificationPermissionAction.ASK_SYSTEM
    }

    /**
     * Whether to raise the dialog on the app's own initiative.
     *
     * Only when the store has actually taken something on - an app adopted, or a row in the queue.
     * Until then there is nothing to notify about, and the question would be about a future the
     * user has not chosen yet.
     */
    fun shouldOfferUnprompted(
        sdkInt: Int = Build.VERSION.SDK_INT,
        granted: Boolean,
        alreadyAsked: Boolean,
        hasSomethingToReportAbout: Boolean
    ): Boolean =
        hasSomethingToReportAbout &&
            actionForRequest(sdkInt, granted, alreadyAsked) == NotificationPermissionAction.ASK_SYSTEM
}
