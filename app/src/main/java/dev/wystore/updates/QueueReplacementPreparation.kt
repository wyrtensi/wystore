package dev.wystore.updates

import dev.wystore.data.GitHubAsset
import dev.wystore.data.GitHubRelease
import dev.wystore.data.ManagedSource
import dev.wystore.data.TransferFailure
import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.updates.model.QueueErrorCode
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Prepares a stopped queue row before Android can remove the installed application. */
object QueueReplacementPreparation {
    suspend fun prepare(
        expectedRow: UpdateQueueEntity,
        loadRow: suspend () -> UpdateQueueEntity?,
        chosenId: () -> Long?,
        exactSelection: () -> GitHubAsset?,
        releases: suspend () -> List<GitHubRelease>,
        persistExact: (GitHubAsset) -> Unit,
        savePending: () -> Unit
    ) {
        requireCurrent(expectedRow, loadRow)
        if (expectedRow.source == ManagedSource.GITHUB.name) {
            val originalChoice = chosenId()
            val originalExact = exactSelection()
            val asset = GitHubAssetChoice.forReplacement(
                originalChoice, originalExact, expectedRow.githubReleaseId, releases
            )
            currentCoroutineContext().ensureActive()
            requireCurrent(expectedRow, loadRow)
            if (chosenId() != originalChoice || exactSelection() != originalExact) sourceChanged()
            // Version 0.5.1 only persisted the chosen id. The full selection must survive process
            // death before removal; otherwise the ownership download deliberately refuses it.
            persistExact(asset)
            requireCurrent(expectedRow, loadRow)
            if (chosenId() != asset.id || exactSelection() != asset) sourceChanged()
        } else if (expectedRow.source != ManagedSource.RUSTORE.name) {
            sourceChanged()
        }
        currentCoroutineContext().ensureActive()
        savePending()
    }

    private suspend fun requireCurrent(expected: UpdateQueueEntity, load: suspend () -> UpdateQueueEntity?) {
        if (load() != expected) sourceChanged()
    }

    private fun sourceChanged(): Nothing = throw TransferFailure(
        retryable = false, code = QueueErrorCode.SOURCE_CHANGED,
        detail = "The selected queue replacement changed before removal"
    )
}
