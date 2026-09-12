package dev.wystore.ui.components

/** What the one button on a catalogue row should actually do. */
enum class RowAction {
    /** Put it in the queue and start fetching. */
    Enqueue,

    /** The verified file is already on disk; hand it to Android's installer. */
    InstallDownloaded,

    /** It is installed and current; open it. */
    OpenApp,

    /** The queue is busy with it, or there is nothing to do. */
    Nothing
}

/**
 * Home and the category pages wired this button to the same handler as the row itself, so
 * "Install" opened the app's page instead of installing anything - the one control that says what
 * it will do was the one that did not do it.
 */
object RowActionPolicy {

    fun actionFor(action: PrimaryAction, status: StatusCode): RowAction = when (action) {
        PrimaryAction.Open -> RowAction.OpenApp
        // The download is finished and verified; what is left is Android's own dialog.
        PrimaryAction.Install ->
            if (status == StatusCode.READY_TO_INSTALL) RowAction.InstallDownloaded
            else RowAction.Enqueue
        PrimaryAction.Update,
        PrimaryAction.Resume,
        PrimaryAction.Retry -> RowAction.Enqueue
        // A transfer already under way answers to the row's own controls, not to this one.
        PrimaryAction.Pause,
        PrimaryAction.Installing,
        PrimaryAction.None -> RowAction.Nothing
    }
}
