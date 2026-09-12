package dev.wystore.ui.components

sealed interface PrimaryAction {
    data object Install : PrimaryAction
    data object Update : PrimaryAction
    data object Open : PrimaryAction
    data object Pause : PrimaryAction
    data object Resume : PrimaryAction
    data object Retry : PrimaryAction

    /**
     * The source could not confirm its own file, and the user is the one who decides. Retry is
     * offered beside it, because a fetch that went wrong on the way looks the same from the row.
     */
    data object ConfirmSource : PrimaryAction

    /** Waiting in the queue: take the transfer slot for this app now. */
    data object DownloadNow : PrimaryAction
    data object Installing : PrimaryAction
    data object None : PrimaryAction
}

sealed interface SecondaryAction {
    data object Cancel : SecondaryAction
    data object Retry : SecondaryAction
    data object CheckUpdate : SecondaryAction
    data object None : SecondaryAction
}
