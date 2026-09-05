package dev.wystore.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val artifactIndex: Int,
    val artifactCount: Int,
    val bytesPerSecond: Long = 0,
    val etaSeconds: Long? = null
) {
    val fraction: Float get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
}

class SecureArtifactDownloader(context: Context? = null) {
    // Long read timeout for a multi-megabyte body, but the same connection pool as everything
    // else: the download often follows a metadata request to the very same host.
    private val client = context?.let { HttpClients.withTimeouts(it, 20, 90) }
        ?: RussianTrustStore.createClient(null, 20, 90)

    /**
     * GitHub redirects release downloads to its asset CDN. OkHttp follows redirects itself by
     * default, which meant the hop was never checked against [GITHUB_DOWNLOAD_HOSTS] — the loop
     * below only ever saw the final 200. Redirects are followed manually so the allowlist applies.
     */
    private val redirectAwareClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun download(
        artifacts: List<DownloadArtifact>,
        directory: File,
        onProgress: suspend (DownloadProgress) -> Unit = {}
    ): List<File> = withContext(Dispatchers.IO) {
        require(artifacts.isNotEmpty()) { "Источник не отдал APK" }
        artifacts.forEach(::validate)
        directory.mkdirs()
        val totalExpected = artifacts.sumOf { it.sizeBytes }
        var completedBefore = 0L
        var lastSpeedSampleBytes = 0L
        var lastSpeedSampleAt = System.nanoTime()
        artifacts.flatMapIndexed { index, artifact ->
            val temporary = File(directory, "artifact_$index.part")
            val destination = File(directory, "artifact_$index.apk")
            temporary.delete()
            destination.delete()
            try {
                client.newCall(Request.Builder().url(artifact.url).build()).execute().use { response ->
                    check(response.isSuccessful) { "Не удалось скачать APK: HTTP ${response.code}" }
                    val declared = response.body?.contentLength() ?: -1L
                    check(declared < 0 || declared == artifact.sizeBytes) { "Размер APK не совпадает с данными источника" }
                    val body = response.body ?: error("Ответ не содержит APK")
                    body.byteStream().use { input ->
                        temporary.outputStream().use { output ->
                            var total = 0L
                            var lastReportedAt = 0L
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                check(total <= MAX_ARTIFACT_BYTES) { "APK превышает допустимый размер" }
                                output.write(buffer, 0, count)
                                val now = System.nanoTime()
                                if (now - lastReportedAt >= PROGRESS_INTERVAL_NANOS || total == artifact.sizeBytes) {
                                    val downloaded = completedBefore + total
                                    val elapsed = (now - lastSpeedSampleAt).coerceAtLeast(1L)
                                    val speed = ((downloaded - lastSpeedSampleBytes) * 1_000_000_000L / elapsed).coerceAtLeast(0L)
                                    val eta = if (speed > 0) ((totalExpected - downloaded).coerceAtLeast(0L) + speed - 1) / speed else null
                                    onProgress(DownloadProgress(downloaded, totalExpected, index + 1, artifacts.size, speed, eta))
                                    lastReportedAt = now
                                    lastSpeedSampleAt = now
                                    lastSpeedSampleBytes = downloaded
                                }
                            }
                            check(total == artifact.sizeBytes) { "Загрузка APK завершилась с неверным размером" }
                        }
                    }
                }
                check(temporary.renameTo(destination)) { "Не удалось сохранить APK" }
                completedBefore += artifact.sizeBytes
                extractApksFromBundleIfNeeded(destination, directory, index)
            } catch (error: Throwable) {
                temporary.delete()
                destination.delete()
                throw error
            }
        }
    }

    suspend fun downloadGitHubApk(
        asset: GitHubAsset,
        directory: File,
        onProgress: suspend (DownloadProgress) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val initial = URI(asset.downloadUrl)
        check(initial.scheme == "https" && initial.host == "github.com" && initial.path.contains("/releases/download/")) {
            "GitHub отдал некорректную ссылку на APK"
        }
        check(asset.sizeBytes in 1..MAX_ARTIFACT_BYTES) { "GitHub отдал APK с недопустимым размером" }
        directory.mkdirs()
        val temporary = File(directory, "github_asset.part")
        val destination = File(directory, "github_asset.apk")
        temporary.delete()
        destination.delete()
        try {
            var url = asset.downloadUrl
            repeat(6) { redirect ->
                val response = redirectAwareClient
                    .newCall(Request.Builder().url(url).header("User-Agent", "WyStore/1.0").build())
                    .execute()
                if (response.isRedirect) {
                    val next = response.header("Location") ?: throw SourceFormatException("GitHub не указал адрес загрузки")
                    response.close()
                    val uri = URI(next)
                    check(uri.scheme == "https" && uri.host in GITHUB_DOWNLOAD_HOSTS) { "GitHub перенаправил на недоверенный домен" }
                    url = next
                    return@repeat
                }
                response.use {
                    check(it.isSuccessful) { "Не удалось скачать APK с GitHub: HTTP ${it.code}" }
                    val declared = it.body?.contentLength() ?: -1L
                    check(declared < 0 || declared == asset.sizeBytes) { "Размер APK не совпадает с данными GitHub" }
                    val body = it.body ?: error("GitHub не вернул APK")
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
                                check(downloaded <= MAX_ARTIFACT_BYTES) { "APK превышает допустимый размер" }
                                output.write(buffer, 0, count)
                                val now = System.nanoTime()
                                if (now - lastAt >= PROGRESS_INTERVAL_NANOS || downloaded == asset.sizeBytes) {
                                    val speed = ((downloaded - lastBytes) * 1_000_000_000L / (now - lastAt).coerceAtLeast(1L)).coerceAtLeast(0L)
                                    val eta = if (speed > 0) ((asset.sizeBytes - downloaded).coerceAtLeast(0L) + speed - 1) / speed else null
                                    onProgress(DownloadProgress(downloaded, asset.sizeBytes, 1, 1, speed, eta))
                                    lastAt = now
                                    lastBytes = downloaded
                                }
                            }
                            check(downloaded == asset.sizeBytes) { "Загрузка APK завершилась с неверным размером" }
                        }
                    }
                }
                check(temporary.renameTo(destination)) { "Не удалось сохранить APK" }
                asset.digest?.removePrefix("sha256:")?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }?.let { expected ->
                    val actual = MessageDigest.getInstance("SHA-256").digest(destination.readBytes()).joinToString("") { "%02x".format(it) }
                    check(actual == expected) { "Хеш APK не совпадает с данными GitHub" }
                }
                return@withContext destination
            }
            throw SourceFormatException("GitHub отправил слишком много перенаправлений")
        } catch (error: Throwable) {
            temporary.delete()
            destination.delete()
            throw error
        }
    }

    private fun validate(artifact: DownloadArtifact) {
        val uri = runCatching { URI(artifact.url) }.getOrElse { throw SourceFormatException("Источник отдал некорректную ссылку на APK") }
        val host = uri.host.orEmpty()
        check(uri.scheme == "https" && (host == "rustore.ru" || host.endsWith(".rustore.ru")) && uri.port in setOf(-1, 443)) {
            "Источник отдал файл с недоверенного домена"
        }
        check(artifact.sizeBytes in 1..MAX_ARTIFACT_BYTES) { "Источник отдал APK с недопустимым размером" }
    }

    private fun extractApksFromBundleIfNeeded(file: File, directory: File, artifactIndex: Int): List<File> = runCatching {
        ZipFile(file).use { archive ->
            if (archive.getEntry("AndroidManifest.xml") != null) return listOf(file)
            val apkEntries = archive.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
                .toList()
            check(apkEntries.isNotEmpty()) { "RuStore отдал архив без APK" }
            check(apkEntries.size <= MAX_BUNDLE_APKS) { "RuStore отдал слишком много APK в одном архиве" }
            val totalSize = apkEntries.sumOf { it.size.coerceAtLeast(0L) }
            check(totalSize in 1..MAX_ARTIFACT_BYTES) { "APK в архиве RuStore имеют недопустимый размер" }
            val files = apkEntries.mapIndexed { entryIndex, entry ->
                val output = File(directory, "artifact_${artifactIndex}_$entryIndex.apk")
                archive.getInputStream(entry).use { input -> output.outputStream().use { input.copyTo(it) } }
                check(isApkContainer(output)) { "RuStore отдал архив с недопустимым APK" }
                output
            }
            file.delete()
            files
        }
    }.getOrElse { error ->
        if (error is IllegalStateException) throw error
        throw SourceFormatException("RuStore отдал недопустимый архив APK")
    }

    private fun isApkContainer(file: File): Boolean = runCatching {
        ZipFile(file).use { archive -> archive.getEntry("AndroidManifest.xml") != null }
    }.getOrDefault(false)

    private companion object {
        const val MAX_ARTIFACT_BYTES = 1_073_741_824L
        const val MAX_BUNDLE_APKS = 16
        const val PROGRESS_INTERVAL_NANOS = 500_000_000L
        val GITHUB_DOWNLOAD_HOSTS = setOf(
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
            "github-releases.githubusercontent.com"
        )
    }
}
