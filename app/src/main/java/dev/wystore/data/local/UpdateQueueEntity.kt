package dev.wystore.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.wystore.data.ManagedSource
import dev.wystore.updates.model.QueueErrorCode
import dev.wystore.updates.model.QueueItemSnapshot
import dev.wystore.updates.model.QueueState

@Entity(
    tableName = "update_queue",
    indices = [
        Index(value = ["packageName", "versionCode", "source"], unique = true)
    ]
)
data class UpdateQueueEntity(
    @PrimaryKey
    val id: String,
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val source: String,
    val state: String,
    val priority: Int = 0,
    val position: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val errorCode: String? = null,
    val errorDetail: String? = null,
    val signingDigests: String = "",
    val githubRepositoryOwner: String? = null,
    val githubRepositoryName: String? = null,
    val githubReleaseId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "update_artifacts",
    primaryKeys = ["queueId", "path"],
    foreignKeys = [
        ForeignKey(
            entity = UpdateQueueEntity::class,
            parentColumns = ["id"],
            childColumns = ["queueId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["queueId"])
    ]
)
data class UpdateArtifactEntity(
    val queueId: String,
    val path: String,
    val size: Long,
    val sourceHash: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "install_sessions",
    primaryKeys = ["queueId"],
    foreignKeys = [
        ForeignKey(
            entity = UpdateQueueEntity::class,
            parentColumns = ["id"],
            childColumns = ["queueId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["packageName"])
    ]
)
data class InstallSessionEntity(
    val queueId: String,
    val packageName: String,
    val sessionId: Int,
    val callbackToken: String,
    val state: String,
    val updatedAt: Long = System.currentTimeMillis()
)

fun UpdateQueueEntity.toSnapshot(): QueueItemSnapshot = QueueItemSnapshot(
    id = id,
    packageName = packageName,
    label = label,
    versionName = versionName,
    versionCode = versionCode,
    source = runCatching { ManagedSource.valueOf(source) }.getOrDefault(ManagedSource.RUSTORE),
    state = runCatching { QueueState.valueOf(state) }.getOrDefault(QueueState.AVAILABLE),
    priority = priority,
    position = position,
    downloadedBytes = downloadedBytes,
    totalBytes = totalBytes,
    errorCode = errorCode?.let { code -> runCatching { QueueErrorCode.valueOf(code) }.getOrNull() },
    errorDetail = errorDetail
)
