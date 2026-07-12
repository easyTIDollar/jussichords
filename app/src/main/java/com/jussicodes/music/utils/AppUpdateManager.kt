package com.jussicodes.music.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.jussicodes.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.TimeUnit

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/easyTIDollar/jussichords/releases/latest"

object AppUpdateManager {
    private const val UPDATE_DIR = "updates"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val DOWNLOAD_CONNECT_TIMEOUT_SECONDS = 30L
    private const val DOWNLOAD_READ_TIMEOUT_SECONDS = 120L
    private const val DOWNLOAD_CALL_TIMEOUT_SECONDS = 300L
    private const val DOWNLOAD_MAX_RETRIES = 2

    // Mainland users often cannot reach GitHub reliably. Try the official URL first,
    // then fall back to a mirror-compatible proxy URL.
    private val githubProxyPrefixes = listOf(
        "",
        "https://gh-proxy.com/"
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val downloadClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(DOWNLOAD_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DOWNLOAD_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(DOWNLOAD_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun checkLatestRelease(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val release = fetchLatestRelease()
            val asset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: error("?? Release ????? APK ???")
            val latestVersion = release.tagName.trim().trimStart('v', 'V')
            if (!isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)) {
                return@runCatching null
            }
            UpdateInfo(
                versionName = latestVersion,
                releaseName = release.name.takeIf { it.isNotBlank() } ?: release.tagName,
                body = release.body,
                apkName = asset.name,
                apkSize = asset.size,
                downloadUrl = asset.downloadUrl
            )
        }
    }

    suspend fun downloadApk(
        context: Context,
        updateInfo: UpdateInfo,
        onProgress: (DownloadProgress) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val updateDir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
        updateDir.listFiles()
            ?.filter {
                it.extension.equals("apk", ignoreCase = true) ||
                    it.extension.equals("part", ignoreCase = true)
            }
            ?.forEach {
                if (it.name != updateInfo.apkName && it.name != "${updateInfo.apkName}.part") {
                    it.delete()
                }
            }

        val targetFile = File(updateDir, updateInfo.apkName)
        val tempFile = File(updateDir, "${updateInfo.apkName}.part")
        if (targetFile.exists() && targetFile.length() == updateInfo.apkSize) {
            onProgress(
                DownloadProgress(
                    downloadedBytes = updateInfo.apkSize,
                    totalBytes = updateInfo.apkSize,
                    progress = 1f,
                    done = true
                )
            )
            return@withContext targetFile
        }

        if (tempFile.exists()) {
            tempFile.delete()
        }

        onProgress(
            DownloadProgress(
                downloadedBytes = 0L,
                totalBytes = updateInfo.apkSize,
                progress = 0f
            )
        )

        downloadWithRetry(updateInfo, tempFile, onProgress)

        val expectedSize = updateInfo.apkSize.takeIf { it > 0 } ?: tempFile.length()
        val actualSize = tempFile.length()
        if (expectedSize > 0 && actualSize != expectedSize) {
            tempFile.delete()
            error("?????,?? $expectedSize ??,?? $actualSize ??")
        }

        if (targetFile.exists()) targetFile.delete()
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }

        onProgress(
            DownloadProgress(
                downloadedBytes = targetFile.length(),
                totalBytes = expectedSize,
                progress = 1f,
                done = true
            )
        )
        targetFile
    }

    fun createInstallIntent(context: Context, apkFile: File): Intent {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun installApk(context: Context, apkFile: File): Result<Unit> = runCatching {
        val intent = createInstallIntent(context, apkFile)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val fallbackIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    apkFile
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(fallbackIntent)
        }
    }

    private fun requestText(url: String): String {
        var lastException: Throwable? = null
        buildCandidateUrls(url).forEach { candidateUrl ->
            runCatching {
                requestTextOnce(candidateUrl)
            }.onSuccess {
                return it
            }.onFailure {
                lastException = it
            }
        }
        throw lastException ?: error("??????")
    }

    private fun requestTextOnce(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "jussichords/${BuildConfig.VERSION_NAME}")
        }
        return try {
            if (connection.responseCode !in 200..299) {
                throw HttpStatusException(connection.responseCode)
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchLatestRelease(): GitHubRelease {
        val latestResult = runCatching {
            json.decodeFromString<GitHubRelease>(requestText(LATEST_RELEASE_URL))
        }
        latestResult.onSuccess { return it }

        val exception = latestResult.exceptionOrNull()
        if (exception !is HttpStatusException || exception.code != HttpURLConnection.HTTP_NOT_FOUND) {
            throw exception ?: error("??????")
        }

        val releasesUrl = LATEST_RELEASE_URL.removeSuffix("/latest")
        val releases = json.decodeFromString<List<GitHubRelease>>(requestText(releasesUrl))
            .filterNot { it.draft || it.prerelease }
        return releases.firstOrNull()
            ?: error("?????? Release,?????? APK ? Release")
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = versionParts(latest)
        val currentParts = versionParts(current)
        val maxSize = maxOf(latestParts.size, currentParts.size)
        for (index in 0 until maxSize) {
            val latestPart = latestParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return false
    }

    private fun versionParts(version: String): List<Int> =
        Regex("\\d+").findAll(version).map { it.value.toIntOrNull() ?: 0 }.toList()

    private fun buildCandidateUrls(url: String): List<String> =
        githubProxyPrefixes.map { prefix ->
            if (prefix.isEmpty()) url else "$prefix$url"
        }.distinct()

    private fun downloadWithRetry(
        updateInfo: UpdateInfo,
        tempFile: File,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        val candidateUrls = buildCandidateUrls(updateInfo.downloadUrl)
        var lastException: Throwable? = null

        repeat(DOWNLOAD_MAX_RETRIES + 1) { attempt ->
            candidateUrls.forEachIndexed { index, candidateUrl ->
                runCatching {
                    downloadOnce(candidateUrl, updateInfo, tempFile, onProgress)
                }.onSuccess {
                    return
                }.onFailure { throwable ->
                    tempFile.delete()
                    lastException = throwable

                    val hasMoreCandidates = index < candidateUrls.lastIndex
                    val canRetry = attempt < DOWNLOAD_MAX_RETRIES && throwable.isRetryableDownloadError()
                    if (!hasMoreCandidates && !canRetry) {
                        throw throwable
                    }
                }
            }
        }

        throw lastException ?: error("??????")
    }

    private fun downloadOnce(
        url: String,
        updateInfo: UpdateInfo,
        tempFile: File,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "jussichords/${BuildConfig.VERSION_NAME}")
            .build()

        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpStatusException(response.code)
            }

            val body = response.body ?: error("??????:?????????")
            val contentLength = body.contentLength().takeIf { it > 0 } ?: updateInfo.apkSize
            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(
                            DownloadProgress(
                                downloadedBytes = downloaded,
                                totalBytes = contentLength,
                                progress = if (contentLength > 0) {
                                    downloaded.toFloat() / contentLength
                                } else {
                                    0f
                                }
                            )
                        )
                    }
                    output.fd.sync()
                }
            }
        }
    }

    private fun Throwable.isRetryableDownloadError(): Boolean =
        this is SocketTimeoutException || this is IOException

    private class HttpStatusException(val code: Int) : Exception("??????: HTTP $code")
}

data class UpdateInfo(
    val versionName: String,
    val releaseName: String,
    val body: String,
    val apkName: String,
    val apkSize: Long,
    val downloadUrl: String,
)

data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val progress: Float,
    val done: Boolean = false,
)

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String = "",
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
private data class GitHubAsset(
    val name: String,
    val size: Long = 0,
    @SerialName("browser_download_url") val downloadUrl: String,
)
