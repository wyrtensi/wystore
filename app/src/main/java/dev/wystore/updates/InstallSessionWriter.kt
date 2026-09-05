package dev.wystore.updates

import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.FileProvider
import dev.wystore.BuildConfig
import dev.wystore.data.SigningVerifier
import dev.wystore.localization.VerificationTextResolver
import dev.wystore.data.StoreRepository
import dev.wystore.data.local.InstallSessionEntity
import dev.wystore.data.local.WyStoreDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface PreparedInstall {
    val queueId: String
    val packageName: String

    data class LegacySingleApk(
        override val queueId: String,
        override val packageName: String,
        val intent: Intent
    ) : PreparedInstall

    data class Session(
        override val queueId: String,
        override val packageName: String,
        val sessionId: Int,
        val callbackToken: String
    ) : PreparedInstall
}

class InstallSessionWriter(private val context: Context) {
    private val appContext = context.applicationContext
    private val database = WyStoreDatabase.getInstance(appContext)
    private val packageInstaller = appContext.packageManager.packageInstaller
    private val storeRepository = StoreRepository(appContext)

    suspend fun prepare(queueId: String): PreparedInstall = withContext(Dispatchers.IO) {
        val entity = database.updateQueueDao.getById(queueId)
            ?: throw IllegalArgumentException("Queue item not found: $queueId")

        val artifacts = database.updateQueueDao.getArtifactsForQueue(queueId)
        val files = artifacts.map { File(it.path) }
        require(files.isNotEmpty() && files.all { it.isFile && it.length() > 0L }) {
            "Downloaded APK files are missing or unreadable"
        }

        val installed = storeRepository.installedApps().firstOrNull { it.packageName == entity.packageName }
        val verification = SigningVerifier.verifyArtifacts(
            packageManager = appContext.packageManager,
            files = files,
            installed = installed,
            expectedPackageName = entity.packageName
        )
        if (!verification.isValid) {
            throw IllegalStateException(
                verification.error?.let { VerificationTextResolver.resolve(appContext, it) }
                    ?: appContext.getString(dev.wystore.R.string.msg_install_reverify_failed)
            )
        }

        when (UserInstallRouting.select(Build.VERSION.SDK_INT, files.size)) {
            UserInstallRoute.LEGACY_SINGLE_APK -> prepareLegacySingleApk(queueId, entity.packageName, files.single())
            UserInstallRoute.PACKAGE_INSTALLER_SESSION -> prepareSession(queueId, entity.packageName, files)
        }
    }

    private fun prepareLegacySingleApk(queueId: String, packageName: String, file: File): PreparedInstall.LegacySingleApk {
        val uri = FileProvider.getUriForFile(appContext, "${BuildConfig.APPLICATION_ID}.pending-updates", file)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            clipData = ClipData.newRawUri("APK", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PreparedInstall.LegacySingleApk(queueId, packageName, intent)
    }

    private suspend fun prepareSession(queueId: String, packageName: String, files: List<File>): PreparedInstall.Session {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
        }
        val sessionId = packageInstaller.createSession(params)
        val callbackToken = UUID.randomUUID().toString()

        try {
            packageInstaller.openSession(sessionId).use { session ->
                files.forEachIndexed { index, file ->
                    file.inputStream().use { input ->
                        session.openWrite("artifact_$index.apk", 0, file.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                }
            }

            database.updateQueueDao.insertOrReplaceSession(
                InstallSessionEntity(
                    queueId = queueId,
                    packageName = packageName,
                    sessionId = sessionId,
                    callbackToken = callbackToken,
                    state = "PREPARED"
                )
            )

            return PreparedInstall.Session(queueId, packageName, sessionId, callbackToken)
        } catch (e: Exception) {
            packageInstaller.abandonSession(sessionId)
            throw e
        }
    }

    suspend fun commitSession(session: PreparedInstall.Session) = withContext(Dispatchers.IO) {
        val callbackIntent = Intent(appContext, InstallResultReceiver::class.java).apply {
            action = ACTION_INSTALL_RESULT
            putExtra(EXTRA_QUEUE_ID, session.queueId)
            putExtra(EXTRA_PACKAGE_NAME, session.packageName)
            putExtra(EXTRA_CALLBACK_TOKEN, session.callbackToken)
            putExtra(EXTRA_SESSION_ID, session.sessionId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(appContext, session.sessionId, callbackIntent, flags)

        packageInstaller.openSession(session.sessionId).use { openSession ->
            openSession.commit(pendingIntent.intentSender)
        }
    }

    /**
     * Drops a prepared-but-never-committed session. Without this the staged bytes stay charged to
     * the app's session quota until Android decides to reap them.
     */
    suspend fun abandon(queueId: String) = withContext(Dispatchers.IO) {
        val session = database.updateQueueDao.getSession(queueId) ?: return@withContext
        runCatching { packageInstaller.abandonSession(session.sessionId) }
        database.updateQueueDao.deleteSession(queueId)
    }

    companion object {
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val ACTION_INSTALL_RESULT = "dev.wystore.action.INSTALL_RESULT"
        const val EXTRA_QUEUE_ID = "extra_queue_id"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_CALLBACK_TOKEN = "extra_callback_token"
        const val EXTRA_SESSION_ID = "extra_session_id"
    }
}
