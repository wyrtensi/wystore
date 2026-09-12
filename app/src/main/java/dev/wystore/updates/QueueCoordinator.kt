package dev.wystore.updates

import android.content.Context
import dev.wystore.background.TransferDispatcher
import dev.wystore.data.QueueMode
import dev.wystore.data.StoreRepository
import dev.wystore.permissions.PermissionRepository
import dev.wystore.permissions.PermissionSnapshot
import dev.wystore.updates.model.QueueAction
import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.updates.model.QueueItemSnapshot
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow

private const val DISPLACE_POLLS = 20
private const val DISPLACE_POLL_MILLIS = 100L

class QueueCoordinator(
    private val context: Context,
    private val repository: QueueRepository = QueueRepository.getInstance(context),
    private val storeRepository: StoreRepository = StoreRepository(context),
    private val permissionRepository: PermissionRepository = PermissionRepository(context),
    private val promptState: QueuePromptState = QueuePromptState(context)
) {
    private val _offeredNext = MutableStateFlow<QueueItemSnapshot?>(null)
    val offeredNext = _offeredNext.asStateFlow()

    fun observeAll(): Flow<List<QueueItemSnapshot>> = repository.observeAll()

    fun permissionSnapshot(): PermissionSnapshot = permissionRepository.snapshot()

    suspend fun startQueue() {
        // Everything waiting, not only the row that goes first: see markWaitingAsUserRequested.
        runCatching { repository.markWaitingAsUserRequested() }
        val next = repository.nextEligible() ?: return
        download(next.id)
    }

    suspend fun download(id: String) {
        TransferDispatcher.dispatch(context, id)
    }

    /**
     * Makes this row the one being fetched, now, displacing whatever holds the transfer slot.
     *
     * The queue moves one item at a time, so asking for a download while another is running used
     * to do nothing visible at all: the worker started, found the slot taken, and left the row
     * waiting exactly where it was. "Download now" has to mean now, or it should not be offered.
     *
     * What it displaces goes back to waiting rather than to failed: it keeps its place in the
     * queue and starts again when the slot frees. It does start again from the beginning - the
     * cancelled transfer clears its partial files, the same as any other cancellation - which is
     * the price of jumping the line and the reason this is a button rather than something the
     * queue does on its own.
     *
     * An install is never interrupted: it is Android's operation by then, and there is no safe
     * moment to take a package away from it.
     */
    suspend fun downloadNow(id: String) {
        val target = repository.getById(id) ?: return
        if (target.state == QueueState.DOWNLOADING || target.state == QueueState.VERIFYING) return
        repository.snapshotAll()
            .filter { it.id != id && it.state == QueueState.DOWNLOADING }
            .forEach { active -> displace(active.id) }
        download(id)
    }

    /**
     * Stops a transfer and puts its row back in line.
     *
     * The reset waits for the worker to finish unwinding: cancellation runs in the worker, which
     * writes CANCELED on its way out, and resetting before that lands would be overwritten by it.
     */
    private suspend fun displace(id: String) {
        TransferDispatcher.cancel(context, id)
        var waited = 0
        while (waited < DISPLACE_POLLS && repository.getById(id)?.state == QueueState.DOWNLOADING) {
            delay(DISPLACE_POLL_MILLIS)
            waited++
        }
        repository.resetForRetry(id, errorCode = null, errorDetail = null)
    }

    suspend fun install(id: String, installer: UserConfirmedInstaller) {
        installer.install(id)
    }

    suspend fun skip(id: String) {
        // Skipping an in-flight item has to stop the transfer too, not just relabel the row.
        TransferDispatcher.cancel(context, id)
        repository.transition(id, QueueAction.Skip)
        advanceSmartPromptIfNeeded(id)
    }

    suspend fun retry(id: String) {
        // Retry is offered from FAILED, CANCELED and SKIPPED; only the first two are legal Retry
        // inputs, so a skipped item is put back through the same reset the worker uses.
        val current = repository.getById(id) ?: return
        when (current.state) {
            QueueState.FAILED, QueueState.CANCELED -> repository.transition(id, QueueAction.Retry)
            QueueState.SKIPPED -> repository.resetForRetry(id, errorCode = null, errorDetail = null)
            QueueState.AVAILABLE -> Unit
            else -> return
        }
        download(id)
    }

    suspend fun cancel(id: String) {
        TransferDispatcher.cancel(context, id)
        repository.transition(id, QueueAction.Cancel)
    }

    suspend fun acceptNext(installer: UserConfirmedInstaller) {
        val next = _offeredNext.value ?: return
        _offeredNext.value = null
        promptState.recordAnswered()
        if (next.state == QueueState.READY_TO_INSTALL) {
            install(next.id, installer)
        } else if (next.state == QueueState.AVAILABLE) {
            download(next.id)
        }
    }

    suspend fun dismissOfferedNext() {
        _offeredNext.value = null
        // Recorded, so a prompt the user has already turned down does not return on the next start.
        promptState.recordAnswered()
    }

    suspend fun onInstallResult(id: String, success: Boolean, message: String? = null) {
        // Same reasoning as InstallResultReceiver: the Activity callback can arrive for a row that
        // is no longer INSTALLING, so the outcome is reconciled rather than pushed through the
        // reducer, which would throw and drop the result.
        repository.reconcileInstallResult(
            id = id,
            success = success,
            errorCode = QueueErrorCode.INSTALL_FAILED,
            errorDetail = message
        )
        if (success) {
            advanceSmartPromptIfNeeded(id)
        }
    }

    private suspend fun advanceSmartPromptIfNeeded(justFinishedId: String) {
        val settings = storeRepository.settings()
        val next = repository.nextToOffer()
        val nextAction = QueueCoordinatorPolicy.determineNextAction(
            mode = settings.queueMode,
            justInstalledId = justFinishedId,
            nextEligible = next
        )
        if (nextAction == QueueAction.OfferNext && next != null) {
            _offeredNext.value = next
        } else {
            _offeredNext.value = null
        }
    }

    /**
     * Rebuilds the smart prompt after a cold start.
     *
     * The offered-next item used to live only in [_offeredNext], so a process death between one
     * install finishing and the user answering the prompt silently dropped the rest of the queue.
     *
     * It is restored only while that is actually the situation. The condition used to be "the
     * database holds a finished install", which stays true for the life of the row, so the dialog
     * arrived on every launch for as long as anything sat in the queue, unrelated to any install the
     * user had just done.
     */
    suspend fun restoreOfferedNext() {
        val settings = storeRepository.settings()
        if (settings.queueMode != QueueMode.SMART_PROMPTS) {
            _offeredNext.value = null
            return
        }
        val restore = QueueCoordinatorPolicy.shouldRestoreOffer(
            finishedAt = repository.lastFinishedInstallAt(),
            lastAnsweredAt = promptState.lastAnsweredAt(),
            now = System.currentTimeMillis()
        )
        _offeredNext.value = if (restore) repository.nextToOffer() else null
    }
}
