package dev.wystore.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.wystore.updates.model.QueueState
import kotlinx.coroutines.flow.Flow

/**
 * One transfer is already running.
 *
 * The queue moves one item at a time on purpose - two downloads over one connection help nobody,
 * and two installs at once are not a thing Android does. What was wrong was the reaction: starting
 * a second item threw a plain illegal-state error, which the app reported as "Wy Store itself
 * failed" over a queue that was simply doing its job. Waiting is the answer, so this is a distinct
 * exception rather than a message a caller would have to read.
 */
class QueueBusyException(val activeIds: List<String>) :
    IllegalStateException("Another queue item is active: $activeIds")


@Dao
abstract class UpdateQueueDao {

    @Query("SELECT * FROM update_queue ORDER BY priority DESC, position ASC, packageName ASC, id ASC")
    abstract fun observeAll(): Flow<List<UpdateQueueEntity>>

    @Query("SELECT * FROM update_queue ORDER BY priority DESC, position ASC, packageName ASC, id ASC")
    abstract suspend fun getAll(): List<UpdateQueueEntity>

    @Query("SELECT * FROM update_queue WHERE id = :id")
    abstract suspend fun getById(id: String): UpdateQueueEntity?

    @Query("SELECT * FROM update_queue WHERE packageName = :packageName")
    abstract suspend fun getByPackage(packageName: String): List<UpdateQueueEntity>

    @Query("SELECT * FROM update_queue WHERE packageName = :packageName AND versionCode = :versionCode AND source = :source LIMIT 1")
    abstract suspend fun find(packageName: String, versionCode: Long, source: String): UpdateQueueEntity?

    @Query(
        "SELECT * FROM update_queue WHERE packageName = :packageName AND versionCode = :versionCode " +
            "AND source = :source AND id != :excludeId"
    )
    abstract suspend fun findConflicting(
        packageName: String,
        versionCode: Long,
        source: String,
        excludeId: String
    ): List<UpdateQueueEntity>

    @Query("SELECT * FROM update_queue WHERE state = 'AVAILABLE' ORDER BY priority DESC, position ASC, packageName ASC, id ASC LIMIT 1")
    abstract suspend fun nextEligible(): UpdateQueueEntity?

    /**
     * The waiting rows in the order the queue would take them.
     *
     * For the caller that has to skip some of them: the queue starts the next row on its own when a
     * slot frees, and an app excluded from auto-updates must not be picked up that way.
     */
    @Query("SELECT * FROM update_queue WHERE state = 'AVAILABLE' ORDER BY priority DESC, position ASC, packageName ASC, id ASC LIMIT :limit")
    abstract suspend fun eligible(limit: Int): List<UpdateQueueEntity>

    /**
     * The next item whose APK is already downloaded and verified.
     *
     * The queue only ever looked for something to download, so once everything was on disk it
     * reported nothing to do and the chain of installs stopped after the first one.
     */
    @Query("SELECT * FROM update_queue WHERE state = 'READY_TO_INSTALL' ORDER BY priority DESC, position ASC, packageName ASC, id ASC LIMIT 1")
    abstract suspend fun nextInstallable(): UpdateQueueEntity?

    @Query("SELECT COUNT(*) FROM update_queue WHERE state IN (:activeStates)")
    abstract suspend fun countActive(activeStates: List<String>): Int

    @Query("SELECT * FROM update_queue WHERE state IN (:activeStates)")
    abstract suspend fun getActive(activeStates: List<String>): List<UpdateQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertOrReplace(entity: UpdateQueueEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(entity: UpdateQueueEntity)

    @Update
    abstract suspend fun update(entity: UpdateQueueEntity)

    @Delete
    abstract suspend fun delete(entity: UpdateQueueEntity)

    @Query(
        "UPDATE update_queue SET priority = :priority, updatedAt = :now " +
            "WHERE state = 'AVAILABLE' AND priority < :priority"
    )
    abstract suspend fun raiseWaitingPriority(priority: Int, now: Long)

    @Query("DELETE FROM update_queue WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("DELETE FROM update_queue WHERE packageName = :packageName")
    abstract suspend fun deleteByPackage(packageName: String)

    @Transaction
    open suspend fun upsertAtomic(entity: UpdateQueueEntity) {
        if (entity.state in ACTIVE_STATES) {
            val active = getActive(ACTIVE_STATES).filter { it.id != entity.id }
            if (active.isNotEmpty()) throw QueueBusyException(active.map { it.id })
        }
        val existing = find(entity.packageName, entity.versionCode, entity.source)
        if (existing != null) {
            update(
                entity.copy(
                    id = existing.id,
                    createdAt = existing.createdAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            insertOrReplace(entity)
        }
    }

    /**
     * Takes the row for one runner, and answers false when something already carries it.
     *
     * The single transfer slot was only ever guarded against a *different* row being active, so
     * nothing stopped two runners of the same row. On Android 14 and newer a transfer can be
     * carried by a user-initiated job and by WorkManager at the same time, and the second to
     * arrive found the row already DOWNLOADING: the state machine refused the step and the refusal
     * came out on the card as "Action is not allowed while queue item ... is DOWNLOADING." The row
     * itself is the lock, and this is where it is taken.
     *
     * The error fields are cleared here rather than left to the reducer, whose result
     * [transitionToActive] does not write: a row that failed and was started again kept the error
     * it failed with.
     */
    @Transaction
    open suspend fun claimForTransfer(
        id: String,
        allowedFrom: Set<String>,
        targetState: String
    ): Boolean {
        val current = getById(id) ?: return false
        if (current.state !in allowedFrom) return false
        if (targetState in ACTIVE_STATES) {
            val active = getActive(ACTIVE_STATES).filter { it.id != id }
            if (active.isNotEmpty()) throw QueueBusyException(active.map { it.id })
        }
        update(
            current.copy(
                state = targetState,
                errorCode = null,
                errorDetail = null,
                updatedAt = System.currentTimeMillis()
            )
        )
        return true
    }

    @Transaction
    open suspend fun transitionToActive(id: String, targetState: String) {
        val current = getById(id) ?: throw IllegalArgumentException("Item not found: $id")
        if (targetState in ACTIVE_STATES) {
            val active = getActive(ACTIVE_STATES).filter { it.id != id }
            if (active.isNotEmpty()) throw QueueBusyException(active.map { it.id })
        }
        update(
            current.copy(
                state = targetState,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Stores the artifacts of a finished download together with the identity that verification
     * actually read out of the APK set. Both must land in the same transaction: an item that is
     * READY_TO_INSTALL without its verified version and signing digests cannot be re-checked at
     * install time and is rejected by the installer.
     *
     * Returns the artifact paths that are no longer referenced, so the caller can delete the files.
     */
    @Transaction
    open suspend fun saveVerifiedArtifacts(
        queueId: String,
        packageName: String,
        versionName: String,
        versionCode: Long,
        signingDigests: String,
        artifacts: List<UpdateArtifactEntity>
    ): List<String> {
        require(signingDigests.isNotBlank()) { "Verified artifacts must carry signing digests" }
        require(artifacts.isNotEmpty()) { "Verified artifacts must not be empty" }
        val current = getById(queueId) ?: throw IllegalArgumentException("Queue item not found: $queueId")
        val obsolete = mutableListOf<String>()
        // The row was created before the download with a placeholder version code, so adopting the
        // real one can collide with the unique (packageName, versionCode, source) index.
        for (conflict in findConflicting(packageName, versionCode, current.source, queueId)) {
            obsolete += getArtifactsForQueue(conflict.id).map { it.path }
            deleteById(conflict.id)
        }
        val keptPaths = artifacts.map { it.path }.toSet()
        obsolete += getArtifactsForQueue(queueId).map { it.path }.filterNot { it in keptPaths }
        deleteArtifactsForQueue(queueId)
        insertArtifacts(artifacts)
        update(
            current.copy(
                // A GitHub row is created before the APK exists, so its package name is a
                // placeholder until verification reads the real one out of the archive.
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode,
                signingDigests = signingDigests,
                updatedAt = System.currentTimeMillis()
            )
        )
        return obsolete
    }

    // Artifacts
    @Query("SELECT * FROM update_artifacts WHERE queueId = :queueId")
    abstract suspend fun getArtifactsForQueue(queueId: String): List<UpdateArtifactEntity>

    @Query("SELECT * FROM update_artifacts")
    abstract suspend fun getAllArtifacts(): List<UpdateArtifactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertArtifact(artifact: UpdateArtifactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertArtifacts(artifacts: List<UpdateArtifactEntity>)

    @Query("DELETE FROM update_artifacts WHERE queueId = :queueId AND path = :path")
    abstract suspend fun deleteArtifact(queueId: String, path: String)

    @Query("DELETE FROM update_artifacts WHERE queueId = :queueId")
    abstract suspend fun deleteArtifactsForQueue(queueId: String)

    @Query("UPDATE update_artifacts SET lastAccessedAt = :lastAccessedAt WHERE queueId = :queueId AND path = :path")
    abstract suspend fun updateArtifactLastAccessed(queueId: String, path: String, lastAccessedAt: Long)

    @Query("UPDATE update_artifacts SET lastAccessedAt = :lastAccessedAt WHERE queueId = :queueId")
    abstract suspend fun updateQueueArtifactsLastAccessed(queueId: String, lastAccessedAt: Long)

    // Install Sessions
    @Query("SELECT * FROM install_sessions WHERE queueId = :queueId")
    abstract suspend fun getSession(queueId: String): InstallSessionEntity?

    @Query("SELECT * FROM install_sessions WHERE packageName = :packageName LIMIT 1")
    abstract suspend fun getSessionByPackage(packageName: String): InstallSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertOrReplaceSession(session: InstallSessionEntity)

    @Query("DELETE FROM install_sessions WHERE queueId = :queueId")
    abstract suspend fun deleteSession(queueId: String)

    companion object {
        val ACTIVE_STATES = listOf(
            QueueState.DOWNLOADING.name,
            QueueState.VERIFYING.name,
            QueueState.INSTALLING.name
        )
    }
}
