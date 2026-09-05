package dev.wystore.updates

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

    override suspend fun getForegroundInfo(): ForegroundInfo = notification("Проверяем RuStore")

    override suspend fun doWork(): Result = runCatching {
        setForeground(notification("Подготавливаем проверку"))
        report("PREPARING", "Получаем официальный APK RuStore")
        val directory = File(applicationContext.cacheDir, "rustore-compatibility/${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val file = downloadOfficialApk(directory)
            report("VERIFYING", "Проверяем пакет и подпись RuStore")
            val identity = SigningVerifier.archiveIdentity(applicationContext.packageManager, file)
                ?: error("Не удалось прочитать APK RuStore")
            require(identity.packageName == RUSTORE_PACKAGE) { "Официальная ссылка отдала другой пакет" }
            require(RUSTORE_CERTIFICATE in identity.signingDigests) { "Подпись APK RuStore не совпадает с закреплённой" }
            require(identity.versionCode > 0L) { "APK RuStore не содержит versionCode" }
            val discoveredApiCode = RuStoreApiCompatibilityPolicy.fromOfficialVersionName(identity.versionName)
                ?: error("Не удалось определить API-код из версии RuStore ${identity.versionName.orEmpty()}")
            report("PROBING", "Проверяем API-код $discoveredApiCode")
            val acceptedApiCode = apiClient.probe(discoveredApiCode)
            repository.saveVerifiedRuStoreApk(
                versionName = identity.versionName,
                versionCode = identity.versionCode,
                verifiedAt = System.currentTimeMillis()
            )
            report("COMPLETE", "Используется API-код $acceptedApiCode")
            Result.success(workDataOf("detail" to "RuStore ${identity.versionName} · APK ${identity.versionCode} · API $acceptedApiCode"))
        } finally {
            directory.deleteRecursively()
        }
    }.getOrElse { error ->
        android.util.Log.e("WyStoreRuStore", "RuStore compatibility check failed", error)
        Result.failure(workDataOf("detail" to (error.message ?: "Не удалось проверить APK RuStore")))
    }

    private suspend fun downloadOfficialApk(directory: File): File = withContext(Dispatchers.IO) {
        val temporary = File(directory, "rustore.apk.part")
        val destination = File(directory, "rustore.apk")
        var url = DOWNLOAD_URL
        repeat(6) {
            val response = client.newCall(Request.Builder().url(url).header("User-Agent", "WyStore/1.0").build()).execute()
            if (response.isRedirect) {
                val next = response.header("Location") ?: error("RuStore не указал адрес загрузки")
                response.close()
                val uri = URI(next)
                require(uri.scheme == "https" && (uri.host == "rustore.ru" || uri.host?.endsWith(".rustore.ru") == true)) { "RuStore перенаправил на недоверенный домен" }
                url = next
                return@repeat
            }
            response.use {
                require(it.isSuccessful) { "Не удалось скачать APK RuStore: HTTP ${it.code}" }
                val total = it.body?.contentLength()?.takeIf { size -> size > 0L } ?: error("RuStore не указал размер APK")
                require(total <= MAX_APK_BYTES) { "APK RuStore слишком большой" }
                val body = it.body ?: error("RuStore не вернул APK")
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
                            require(downloaded <= MAX_APK_BYTES) { "APK RuStore слишком большой" }
                            output.write(buffer, 0, count)
                            val now = System.nanoTime()
                            if (now - lastAt >= PROGRESS_INTERVAL_NANOS || downloaded == total) {
                                val speed = ((downloaded - lastBytes) * 1_000_000_000L / (now - lastAt).coerceAtLeast(1L)).coerceAtLeast(0L)
                                report("DOWNLOADING", "Скачиваем APK RuStore", downloaded, total, speed)
                                setForeground(notification("Скачиваем RuStore: ${(downloaded * 100 / total).coerceIn(0, 100)}%"))
                                lastAt = now
                                lastBytes = downloaded
                            }
                        }
                        require(downloaded == total) { "Загрузка APK RuStore завершилась с неверным размером" }
                    }
                }
            }
            require(temporary.renameTo(destination)) { "Не удалось сохранить APK RuStore" }
            return@withContext destination
        }
        error("RuStore отправил слишком много перенаправлений")
    }

    private suspend fun report(status: String, detail: String, downloaded: Long = 0L, total: Long = 0L, speed: Long = 0L) {
        setProgress(workDataOf("status" to status, "detail" to detail, "downloaded" to downloaded, "total" to total, "speed" to speed))
    }

    private fun notification(text: String): ForegroundInfo {
        val channelId = dev.wystore.background.NotificationCoordinator.CHANNEL_CHECKS
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Проверка обновлений", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_stat_wystore)
            .setContentTitle("Wy Store · совместимость RuStore")
            .setContentText(text)
            .setOngoing(true)
            .build()
        return ForegroundInfo(7004, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private companion object {
        const val DOWNLOAD_URL = "https://www.rustore.ru/download"
        const val RUSTORE_PACKAGE = "ru.vk.store"
        const val RUSTORE_CERTIFICATE = "661f20828ef780de0b79bc59f26a30864316355f30e4f91cfa14a20791839914"
        const val MAX_APK_BYTES = 256L * 1024L * 1024L
        const val PROGRESS_INTERVAL_NANOS = 250_000_000L
        val DOWNLOAD_HOSTS = setOf("www.rustore.ru", "rustore.ru", "static.rustore.ru")
    }
}
