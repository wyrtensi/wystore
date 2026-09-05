package dev.wystore.ui.components

data class PackageUiState(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long?,
    val iconUrl: String? = null,
    val publisher: String? = null,
    val status: StatusMessage,
    val primaryAction: PrimaryAction,
    val secondaryAction: SecondaryAction? = null,
    val progress: Float? = null,
    val transferInfo: String? = null,
    val sourceProvenance: String? = null,
    val isCompatible: Boolean = true
)
