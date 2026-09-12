package dev.wystore.ui.components

sealed interface PrimaryAction {
    data object Install : PrimaryAction
    data object Update : PrimaryAction
    data object Open : PrimaryAction
    data object Pause : PrimaryAction
    data object Resume : PrimaryAction
    data object Retry : PrimaryAction
    data object Installing : PrimaryAction
    data object None : PrimaryAction
}

sealed interface SecondaryAction {
    data object Cancel : SecondaryAction
    data object CheckUpdate : SecondaryAction
    data object None : SecondaryAction
}
