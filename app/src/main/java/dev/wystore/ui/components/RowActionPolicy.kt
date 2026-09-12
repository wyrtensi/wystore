package dev.wystore.ui.components

/** What the one button on a catalogue row should actually do. */
enum class RowAction {
    /** Put it in the queue and start fetching. */
    Enqueue,

    /** The verified file is already on disk; hand it to Android's installer. */
    InstallDownloaded,

    /** It is installed and current; open it. */
    OpenApp,

    /** It is queued behind something else; take the transfer slot for it now. */
    DownloadNow,

    /** It is downloading; stop it and keep the bytes. */
    Pause,

    /** It is paused; carry on from the bytes on disk. */
    Resume,

    /** The source did not confirm the file; ask the user whether to take it anyway. */
    ConfirmSource,

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
        PrimaryAction.DownloadNow -> RowAction.DownloadNow
        // Both used to end in Nothing, so the two most obvious buttons on a transferring row did
        // nothing at all - see QueueCoordinator.pause.
        PrimaryAction.Pause -> RowAction.Pause
        PrimaryAction.Resume -> RowAction.Resume
        PrimaryAction.Update,
        PrimaryAction.Retry -> RowAction.Enqueue
        PrimaryAction.ConfirmSource -> RowAction.ConfirmSource
        PrimaryAction.Installing,
        PrimaryAction.None -> RowAction.Nothing
    }
}
