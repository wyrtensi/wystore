package dev.wystore.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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
        if (artifacts.isEmpty()) fail(SourceError.RUSTORE_NO_DOWNLOAD_LINK, "No artifacts to download")
        artifacts.forEach(::validate)
        directory.mkdirs()
        val totalExpected = artifacts.sumOf { it.sizeBytes }
        var completedBefore = 0L
        var lastSpeedSampleBytes = 0L
        var lastSpeedSampleAt = System.nanoTime()
        artifacts.flatMapIndexed { index, artifact ->
            val temporary = File(directory, "artifact_$index.part")
            val destination = File(directory, "artifact_$index.apk")
            destination.delete()
            try {
                // Anything already on disk from an interrupted attempt is offered back to the
                // server as a range. A download that died at 90% of a 130 MB APK used to start
                // again from zero, on a connection that had just proved unreliable.
                val resumeFrom = ResumePolicy.resumableBytes(temporary.length(), artifact.sizeBytes)
                if (resumeFrom == 0L) temporary.delete()

                val request = Request.Builder().url(artifact.url).apply {
                    if (resumeFrom > 0L) header("Range", "bytes=$resumeFrom-")
                }.build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) fail(SourceError.DOWNLOAD_FAILED, "HTTP ${response.code}")
                    // 206 means the range was honoured. A 200 to a ranged request means the server
                    // ignored it and is sending the whole body, so whatever was on disk is stale.
                    val resumed = resumeFrom > 0L && response.code == 206
                    if (resumeFrom > 0L && !resumed) temporary.delete()
                    val alreadyOnDisk = if (resumed) resumeFrom else 0L

                    val declared = response.body?.contentLength() ?: -1L
                    val expectedBody = artifact.sizeBytes - alreadyOnDisk
                    if (declared >= 0 && declared != expectedBody) {
                        fail(SourceError.ARTIFACT_SIZE_MISMATCH, "Declared $declared, expected $expectedBody")
                    }
                    val body = response.body ?: fail(SourceError.DOWNLOAD_FAILED, "Response without a body")
                    body.byteStream().use { input ->
                        FileOutputStream(temporary, resumed).use { output ->
                            var total = alreadyOnDisk
                            var lastReportedAt = 0L
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > MAX_ARTIFACT_BYTES) fail(SourceError.ARTIFACT_TOO_LARGE, "Exceeded $MAX_ARTIFACT_BYTES bytes")
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
                            if (total != artifact.sizeBytes) {
                                fail(SourceError.ARTIFACT_SIZE_MISMATCH, "Got $total, expected ${artifact.sizeBytes}")
                            }
                        }
                    }
                }
                if (!temporary.renameTo(destination)) fail(SourceError.ARTIFACT_WRITE_FAILED, "Rename failed")
                completedBefore += artifact.sizeBytes
                extractApksFromBundleIfNeeded(destination, directory, index)
            } catch (error: Throwable) {
                destination.delete()
                // The partial file is kept on purpose so the next attempt can resume, unless the
                // failure says the bytes themselves are wrong.
                if (ResumePolicy.discardsPartialFile(error)) temporary.delete()
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
        if (initial.scheme != "https" ||
            initial.host != "github.com" ||
            !initial.path.contains("/releases/download/")
        ) {
            fail(SourceError.UNTRUSTED_HOST, "GitHub asset URL is not an https github release URL")
        }
        if (asset.sizeBytes !in 1..MAX_ARTIFACT_BYTES) {
            fail(SourceError.ARTIFACT_TOO_LARGE, "Asset size ${asset.sizeBytes}")
        }
        directory.mkdirs()
        val temporary = File(directory, "github_asset.part")
        val destination = File(directory, "github_asset.apk")
        destination.delete()
        // Whatever an interrupted attempt left behind, offered back as a range. GitHub's asset CDN
        // honours ranges, so a 70 MB APK that dropped near the end does not start over.
        val resumeFrom = ResumePolicy.resumableBytes(temporary.length(), asset.sizeBytes)
        if (resumeFrom == 0L) temporary.delete()
        try {
            var url = asset.downloadUrl
            repeat(6) { redirect ->
                val response = redirectAwareClient
                    .newCall(
                        Request.Builder().url(url)
                            .header("User-Agent", "WyStore/1.0")
                            .apply { if (resumeFrom > 0L) header("Range", "bytes=$resumeFrom-") }
                            .build()
                    )
                    .execute()
                if (response.isRedirect) {
                    val next = response.header("Location") ?: fail(SourceError.GITHUB_NO_DOWNLOAD_LOCATION, "Redirect without Location")
                    response.close()
                    val uri = URI(next)
                    if (uri.scheme != "https" || uri.host !in GITHUB_DOWNLOAD_HOSTS) {
                        fail(SourceError.UNTRUSTED_HOST, "Redirected to ${uri.host}")
                    }
                    url = next
                    return@repeat
                }
                response.use {
                    if (!it.isSuccessful) fail(SourceError.DOWNLOAD_FAILED, "GitHub HTTP ${it.code}")
                    // A 200 to a ranged request means the server ignored the range and is sending
                    // the whole body; the bytes on disk are then not a prefix of what is arriving.
                    val resumed = resumeFrom > 0L && it.code == 206
                    if (resumeFrom > 0L && !resumed) temporary.delete()
                    val alreadyOnDisk = if (resumed) resumeFrom else 0L
                    val declared = it.body?.contentLength() ?: -1L
                    val expectedBody = asset.sizeBytes - alreadyOnDisk
                    if (declared >= 0 && declared != expectedBody) {
                        fail(SourceError.ARTIFACT_SIZE_MISMATCH, "Declared $declared, expected $expectedBody")
                    }
                    val body = it.body ?: fail(SourceError.DOWNLOAD_FAILED, "GitHub response without a body")
                    body.byteStream().use { input ->
                        FileOutputStream(temporary, resumed).use { output ->
                            var downloaded = alreadyOnDisk
                            var lastAt = System.nanoTime()
                            var lastBytes = alreadyOnDisk
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                downloaded += count
                                if (downloaded > MAX_ARTIFACT_BYTES) {
                                    fail(SourceError.ARTIFACT_TOO_LARGE, "Exceeded $MAX_ARTIFACT_BYTES bytes")
                                }
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
                            if (downloaded != asset.sizeBytes) {
                                fail(SourceError.ARTIFACT_SIZE_MISMATCH, "Got $downloaded, expected ${asset.sizeBytes}")
                            }
                        }
                    }
                }
                if (!temporary.renameTo(destination)) fail(SourceError.ARTIFACT_WRITE_FAILED, "Rename failed")
                asset.digest?.removePrefix("sha256:")?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }?.let { expected ->
                    val actual = MessageDigest.getInstance("SHA-256").digest(destination.readBytes()).joinToString("") { "%02x".format(it) }
                    if (actual != expected) fail(SourceError.ARTIFACT_INTEGRITY_MISMATCH, "SHA-256 mismatch")
                }
                return@withContext destination
            }
            fail(SourceError.GITHUB_TOO_MANY_REDIRECTS, "Redirect limit reached")
        } catch (error: Throwable) {
            destination.delete()
            // Kept so the next attempt can resume, unless the failure says the bytes are wrong.
            if (ResumePolicy.discardsPartialFile(error)) temporary.delete()
            throw error
        }
    }

    private fun validate(artifact: DownloadArtifact) {
        val uri = runCatching { URI(artifact.url) }.getOrElse { fail(SourceError.INVALID_ARTIFACT_URL, "Unparseable artifact URL") }
        val host = uri.host.orEmpty()
        if (uri.scheme != "https" ||
            !(host == "rustore.ru" || host.endsWith(".rustore.ru")) ||
            uri.port !in setOf(-1, 443)
        ) {
            fail(SourceError.UNTRUSTED_HOST, "Artifact host is not allowed: $host")
        }
        if (artifact.sizeBytes !in 1..MAX_ARTIFACT_BYTES) {
            fail(SourceError.ARTIFACT_TOO_LARGE, "Artifact size ${artifact.sizeBytes}")
        }
    }

    private fun extractApksFromBundleIfNeeded(file: File, directory: File, artifactIndex: Int): List<File> = runCatching {
        ZipFile(file).use { archive ->
            if (archive.getEntry("AndroidManifest.xml") != null) return listOf(file)
            val apkEntries = archive.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
                .toList()
            if (apkEntries.isEmpty()) fail(SourceError.RUSTORE_BUNDLE_INVALID, "Bundle without APK entries")
            if (apkEntries.size > MAX_BUNDLE_APKS) {
                fail(SourceError.RUSTORE_BUNDLE_INVALID, "Bundle holds ${apkEntries.size} APKs")
            }
            val totalSize = apkEntries.sumOf { it.size.coerceAtLeast(0L) }
            if (totalSize !in 1..MAX_ARTIFACT_BYTES) {
                fail(SourceError.ARTIFACT_TOO_LARGE, "Bundle unpacks to $totalSize bytes")
            }
            val files = apkEntries.mapIndexed { entryIndex, entry ->
                val output = File(directory, "artifact_${artifactIndex}_$entryIndex.apk")
                archive.getInputStream(entry).use { input -> output.outputStream().use { input.copyTo(it) } }
                if (!isApkContainer(output)) fail(SourceError.RUSTORE_BUNDLE_INVALID, "Bundle entry is not an APK")
                output
            }
            file.delete()
            files
        }
    }.getOrElse { error ->
        if (error is IllegalStateException) throw error
        fail(SourceError.RUSTORE_BUNDLE_INVALID, "Bundle could not be read")
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

/**
 * Raises a typed source failure. Declared as [Nothing] so it can stand in an elvis branch and so
 * the compiler knows control does not continue past it.
 */
private fun fail(error: SourceError, detail: String): Nothing =
    throw SourceFormatException(error, detail)
