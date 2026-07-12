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
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/easyTIDollar/jussichords/releases/latest"

object AppUpdateManager {
    private const val UPDATE_DIR = "updates"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun checkLatestRelease(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val release = fetchLatestRelease()
            val asset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: error("最新 Release 中没有找到 APK 安装包")
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
            ?.filter { it.extension.equals("apk", ignoreCase = true) || it.extension.equals("part", ignoreCase = true) }
            ?.forEach { if (it.name != updateInfo.apkName && it.name != "${updateInfo.apkName}.part") it.delete() }

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

        val connection = (URL(updateInfo.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "jussichords/${BuildConfig.VERSION_NAME}")
        }

        try {
            if (connection.responseCode !in 200..299) {
                throw HttpStatusException(connection.responseCode)
            }

            val contentLength = connection.contentLengthLong.takeIf { it > 0 } ?: updateInfo.apkSize
            connection.inputStream.use { input ->
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
                                progress = if (contentLength > 0) downloaded.toFloat() / contentLength else 0f
                            )
                        )
                    }
                    output.fd.sync()
                }
            }

            val expectedSize = updateInfo.apkSize.takeIf { it > 0 } ?: contentLength
            val actualSize = tempFile.length()
            if (expectedSize > 0 && actualSize != expectedSize) {
                tempFile.delete()
                error("下载不完整，期望 ${expectedSize} 字节，实际 ${actualSize} 字节")
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
        } finally {
            connection.disconnect()
        }
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
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
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
            throw exception ?: error("检查更新失败")
        }

        val releasesUrl = LATEST_RELEASE_URL.removeSuffix("/latest")
        val releases = json.decodeFromString<List<GitHubRelease>>(requestText(releasesUrl))
            .filterNot { it.draft || it.prerelease }
        return releases.firstOrNull()
            ?: error("当前仓库暂无 Release，请先发布包含 APK 的 Release")
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

    private class HttpStatusException(val code: Int) : Exception("检查更新失败: HTTP $code")
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
