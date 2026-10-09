package dev.wystore.updates

import dev.wystore.data.local.UpdateQueueEntity
import dev.wystore.updates.model.QueueState

/**
 * The row an update check writes for an update it found.
 *
 * A check finds the same update again on every run until it is installed, so the row is often one
 * that was already downloaded and verified. The row used to be rebuilt from defaults each time:
 * the verified signing digests were wiped, so tapping Install discarded the APK as unverifiable;
 * a row someone had started by hand dropped back to a background priority; and a refusal lost the
 * error code its Confirm/Replace actions are offered from. An existing row now takes only what the
 * check knows better - the name and version the source publishes - and keeps everything else.
 */
object CheckedUpdateRow {

    /**
     * The row of [rows] (one package's) that a check's finding belongs to.
     *
     * By version first. A GitHub row is then matched by its release: the check names it by the
     * release id, and verification writes the APK's real version over that, so a version lookup
     * missed every downloaded GitHub row - Wy Store's own update included - and the release was
     * fetched again on each check, the earlier download deleted when the new one landed. Last, a
     * row started by hand that has no version yet.
     */
    fun existing(
        rows: List<UpdateQueueEntity>,
        versionCode: Long,
        source: String,
        githubReleaseId: Long?
    ): UpdateQueueEntity? {
        val sameSource = rows.filter { it.source == source }
        return sameSource.firstOrNull { it.versionCode == versionCode }
            ?: githubReleaseId?.let { release -> sameSource.firstOrNull { it.githubReleaseId == release } }
            ?: sameSource.firstOrNull { it.versionCode == 0L }
    }

    fun merge(
        existing: UpdateQueueEntity?,
        newId: String,
        packageName: String,
        label: String,
        versionName: String,
        versionCode: Long,
        source: String,
        priority: Int,
        githubRepositoryOwner: String?,
        githubRepositoryName: String?,
        githubReleaseId: Long?,
        now: Long
    ): UpdateQueueEntity = existing?.copy(
        label = label,
        versionName = versionName,
        // A row started by hand knows its version only once verified; until then it is zero.
        versionCode = if (existing.versionCode == 0L) versionCode else existing.versionCode,
        priority = maxOf(existing.priority, priority),
        githubRepositoryOwner = existing.githubRepositoryOwner ?: githubRepositoryOwner,
        githubRepositoryName = existing.githubRepositoryName ?: githubRepositoryName,
        githubReleaseId = existing.githubReleaseId ?: githubReleaseId,
        updatedAt = now
    ) ?: UpdateQueueEntity(
        id = newId,
        packageName = packageName,
        label = label,
        versionName = versionName,
        versionCode = versionCode,
        source = source,
        state = QueueState.AVAILABLE.name,
        priority = priority,
        githubRepositoryOwner = githubRepositoryOwner,
        githubRepositoryName = githubRepositoryName,
        githubReleaseId = githubReleaseId,
        createdAt = now,
        updatedAt = now
    )
}
