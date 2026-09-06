package dev.wystore.updates

import dev.wystore.BuildConfig
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.wystore.R
import dev.wystore.data.RuStoreApiClient
import dev.wystore.data.RuStoreApiCompatibilityPolicy
import dev.wystore.data.SigningVerifier
import dev.wystore.data.StoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

class RuStoreCompatibilityWorker(appContext: Context, parameters: WorkerParameters) : CoroutineWorker(appContext, parameters) {
    private val repository = StoreRepository(appContext)
    private val client = dev.wystore.data.RussianTrustStore.createClient(appContext, 20, 90)
    private val apiClient = RuStoreApiClient(client, repository)

    override suspend fun getForegroundInfo(): ForegroundInfo =
        notification(text(R.string.rustore_compat_preparing))

    override suspend fun doWork(): Result = runCatching {
        setForeground(notification(text(R.string.rustore_compat_preparing)))
        report("PREPARING", text(R.string.rustore_compat_fetching))
        val directory = File(applicationContext.cacheDir, "rustore-compatibility/${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val file = downloadOfficialApk(directory)
            report("VERIFYING", text(R.string.rustore_compat_verifying))
            val identity = SigningVerifier.archiveIdentity(applicationContext.packageManager, file)
                ?: error("RuStore APK could not be read")
            require(identity.packageName == RUSTORE_PACKAGE) { "Official link returned ${identity.packageName}" }
            require(RUSTORE_CERTIFICATE in identity.signingDigests) { "RuStore APK signature does not match the pinned certificate" }
            require(identity.versionCode > 0L) { "RuStore APK carries no versionCode" }
            val discoveredApiCode = RuStoreApiCompatibilityPolicy.fromOfficialVersionName(identity.versionName)
                ?: error("No API code derivable from RuStore version ${identity.versionName.orEmpty()}")
            report("PROBING", text(R.string.rustore_compat_probing, discoveredApiCode))
            val acceptedApiCode = apiClient.probe(discoveredApiCode)
            repository.saveVerifiedRuStoreApk(
                versionName = identity.versionName,
                versionCode = identity.versionCode,
                verifiedAt = System.currentTimeMillis()
            )
            report("COMPLETE", text(R.string.rustore_compat_using, acceptedApiCode))
            Result.success(
                workDataOf(
                    "detail" to text(
                        R.string.rustore_compat_result,
                        identity.versionName.orEmpty(),
                        identity.versionCode,
                        acceptedApiCode
                    )
                )
            )
        } finally {
            directory.deleteRecursively()
        }
    }.getOrElse { error ->
        android.util.Log.e("WyStoreRuStore", "RuStore compatibility check failed", error)
        Result.failure(workDataOf("detail" to text(R.string.rustore_compat_failed)))
    }

    private suspend fun downloadOfficialApk(directory: File): File = withContext(Dispatchers.IO) {
        val temporary = File(directory, "rustore.apk.part")
        val destination = File(directory, "rustore.apk")
        var url = DOWNLOAD_URL
        repeat(6) {
            val response = client.newCall(Request.Builder().url(url).header("User-Agent", "WyStore/${BuildConfig.VERSION_NAME}").build()).execute()
            if (response.isRedirect) {
                val next = response.header("Location") ?: error("Redirect without a Location header")
                response.close()
                val uri = URI(next)
                require(uri.scheme == "https" && (uri.host == "rustore.ru" || uri.host?.endsWith(".rustore.ru") == true)) { "Redirected to an untrusted host: ${uri.host}" }
                url = next
                return@repeat
            }
            response.use {
                require(it.isSuccessful) { "RuStore APK download failed: HTTP ${it.code}" }
                val total = it.body?.contentLength()?.takeIf { size -> size > 0L } ?: error("RuStore did not declare the APK size")
                require(total <= MAX_APK_BYTES) { "RuStore APK exceeds $MAX_APK_BYTES bytes" }
                val body = it.body ?: error("RuStore response had no body")
                body.byteStream().use { input ->
                    temporary.outputStream().use { output ->
                        var downloaded = 0L
                        var lastAt = System.nanoTime()
                        var lastBytes = 0L
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            downloaded += count
                            require(downloaded <= MAX_APK_BYTES) { "RuStore APK exceeds $MAX_APK_BYTES bytes" }
                            output.write(buffer, 0, count)
                            val now = System.nanoTime()
                            if (now - lastAt >= PROGRESS_INTERVAL_NANOS || downloaded == total) {
                                val speed = ((downloaded - lastBytes) * 1_000_000_000L / (now - lastAt).coerceAtLeast(1L)).coerceAtLeast(0L)
                                report("DOWNLOADING", text(R.string.rustore_compat_downloading), downloaded, total, speed)
                                setForeground(
                                    notification(
                                        text(
                                            R.string.rustore_compat_progress,
                                            (downloaded * 100 / total).coerceIn(0, 100).toInt()
                                        )
                                    )
                                )
                                lastAt = now
                                lastBytes = downloaded
                            }
                        }
                        require(downloaded == total) { "RuStore APK download ended at $downloaded of $total bytes" }
                    }
                }
            }
            require(temporary.renameTo(destination)) { "Could not move the downloaded RuStore APK into place" }
            return@withContext destination
        }
        error("RuStore redirect limit reached")
    }

    private suspend fun report(status: String, detail: String, downloaded: Long = 0L, total: Long = 0L, speed: Long = 0L) {
        setProgress(workDataOf("status" to status, "detail" to detail, "downloaded" to downloaded, "total" to total, "speed" to speed))
    }

    private fun notification(text: String): ForegroundInfo {
        // The channel belongs to NotificationCoordinator, which owns its name, importance and
        // group; re-creating it here gave the same id a second, untranslated definition.
        val channelId = dev.wystore.background.NotificationCoordinator.CHANNEL_CHECKS
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_stat_wystore)
            .setContentTitle(text(R.string.rustore_compat_title))
            .setContentText(text)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun text(@androidx.annotation.StringRes id: Int, vararg args: Any): String =
        applicationContext.getString(id, *args)

    private companion object {
        /**
         * Distinct from every id in [dev.wystore.background.NotificationCoordinator]. This used to
         * be 7004, which the consolidated check summary also uses: whichever posted second replaced
         * the other.
         */
        const val NOTIFICATION_ID = 7101
        const val DOWNLOAD_URL = "https://www.rustore.ru/download"
        const val RUSTORE_PACKAGE = "ru.vk.store"
        const val RUSTORE_CERTIFICATE = "661f20828ef780de0b79bc59f26a30864316355f30e4f91cfa14a20791839914"
        const val MAX_APK_BYTES = 256L * 1024L * 1024L
        const val PROGRESS_INTERVAL_NANOS = 250_000_000L
        val DOWNLOAD_HOSTS = setOf("www.rustore.ru", "rustore.ru", "static.rustore.ru")
    }
}
