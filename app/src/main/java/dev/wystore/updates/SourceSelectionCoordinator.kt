package dev.wystore.updates

import android.content.Context
import android.os.Build
import dev.wystore.data.*
import dev.wystore.data.library.*
import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.data.local.WyStoreDatabase
import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface SourceSwitchResult {
    data class Saved(val revision: Long) : SourceSwitchResult
    data class VerificationRequired(val choice: AppSourceChoice, val revision: Long) : SourceSwitchResult
    data object InstallInProgress : SourceSwitchResult
}

class SourceSelectionCoordinator(private val context: Context) {
    private val repository = StoreRepository(context)
    private val dao = WyStoreDatabase.getInstance(context).updateQueueDao

    suspend fun select(packageName: String, choice: AppSourceChoice): SourceSwitchResult {
        require(choice.packageName == packageName)
        // Source lookups happen outside the install gate, which must never wait for the network.
        val certificates = when (choice) {
            is AppSourceChoice.RuStore -> RuStoreSource.getInstance(context).details(packageName, false).signatureHint
                ?.takeIf { Regex("[0-9a-fA-F]{64}").matches(it) }?.lowercase()?.let(::setOf).orEmpty()
            is AppSourceChoice.Library -> {
                val app = LibraryRepository.getInstance(context).index().requireFresh().applications[packageName]
                    ?: throw LibraryApplicationMissing(packageName)
                val selected = LibraryVariantPolicy.select(app, LibraryDeviceSpec.current(context))
                    as? LibrarySelection.Compatible ?: throw TransferFailure(false, QueueErrorCode.INCOMPATIBLE)
                selected.version.preliminaryCertificates(Build.VERSION.SDK_INT)
            }
            is AppSourceChoice.GitHub -> emptySet()
        }
        return change(packageName, choice, certificates, null, null)
    }

    suspend fun selectVerified(packageName: String, choice: AppSourceChoice, identity: ArchiveIdentity,
                               keepQueueId: String): SourceSwitchResult =
        change(packageName, choice, identity.signingDigests, identity.signingLineage, keepQueueId)

    private suspend fun change(packageName: String, choice: AppSourceChoice, certificates: Set<String>,
                               lineage: Set<String>?, keepQueueId: String?): SourceSwitchResult = withContext(Dispatchers.IO) {
        SourceSelectionGate.locked {
            val rows = dao.getByPackage(packageName)
            val sessionStates = rows.mapNotNull { dao.getSession(it.id)?.state }
            if (SourceRequestPolicy.blocksSwitch(rows.map { QueueState.valueOf(it.state) }, sessionStates)) {
                return@locked SourceSwitchResult.InstallInProgress
            }
            val installed = repository.installedAppFresh(packageName)
            val current = repository.managedApps().firstOrNull { it.packageName == packageName }
            if (installed == null) {
                for (row in rows.filter { it.id != keepQueueId }) {
                    InstallSessionWriter(context).abandon(row.id)
                    dao.update(row.copy(state = QueueState.CANCELED.name, errorCode = QueueErrorCode.SOURCE_CHANGED.name))
                    dev.wystore.background.TransferDispatcher.cancel(context, row.id)
                }
                repository.removeManaged(packageName)
                return@locked SourceSwitchResult.Saved(repository.advanceSourceRevision(packageName))
            }
            val signature = when {
                certificates.isEmpty() || installed.signingDigests.isEmpty() -> SignatureCompatibility.UNKNOWN
                certificates == installed.signingDigests -> SignatureCompatibility.COMPATIBLE
                lineage != null && SigningContinuity.allows(installed.signingDigests, certificates, lineage) -> SignatureCompatibility.COMPATIBLE
                else -> SignatureCompatibility.MISMATCH
            }
            if (AppSourceSwitchPolicy.decide(installed, choice, signature, false) == SourceSwitchDecision.VERIFY_FOR_REPLACEMENT) {
                return@locked SourceSwitchResult.VerificationRequired(choice, repository.sourceRevision(packageName))
            }
            val targetRepo = (choice as? AppSourceChoice.GitHub)?.repository
            if (current?.source == choice.source && current.githubRepository == targetRepo) {
                return@locked SourceSwitchResult.Saved(current.sourceRevision)
            }
            for (row in rows.filter { it.id != keepQueueId && it.state !in setOf(QueueState.INSTALLED.name, QueueState.OFFER_NEXT.name) }) {
                InstallSessionWriter(context).abandon(row.id)
                dao.update(row.copy(state = QueueState.CANCELED.name, errorCode = QueueErrorCode.SOURCE_CHANGED.name,
                    updatedAt = System.currentTimeMillis()))
                dev.wystore.background.TransferDispatcher.cancel(context, row.id)
            }
            val changed = repository.updateManaged(packageName) { existing ->
                (existing ?: ManagedApp(packageName, installed.label, installed.signingDigests)).copy(
                    source = choice.source, githubRepository = targetRepo, githubReleaseId = null,
                    sourceRevision = Math.addExact(repository.sourceRevision(packageName), 1))
            }
            keepQueueId?.let { id -> dao.getById(id)?.let { row ->
                dao.update(row.copy(sourceRevision = changed.sourceRevision, sourceReplacement = false))
            } }
            SourceSwitchResult.Saved(changed.sourceRevision)
        }
    }

    companion object {
        fun requireCurrent(context: Context, row: UpdateQueueEntity) {
            val selected = StoreRepository(context).managedApps().firstOrNull { it.packageName == row.packageName }
            if (!SourceRequestPolicy.isCurrent(selected, ManagedSource.valueOf(row.source), row.sourceRevision, row.sourceReplacement,
                    StoreRepository(context).sourceRevision(row.packageName))) {
                throw TransferFailure(false, QueueErrorCode.SOURCE_CHANGED, detail = "The selected application source changed")
            }
        }
    }
}
