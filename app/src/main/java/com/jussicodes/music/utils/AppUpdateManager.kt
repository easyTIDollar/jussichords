package com.jussicodes.music.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.jussicodes.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/easyTIDollar/jussichords/releases/latest"

object AppUpdateManager {
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
        downloadProxy: String,
        onProgress: (Int) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val url = proxiedUrl(updateInfo.downloadUrl, downloadProxy)
            val mainHandler = Handler(Looper.getMainLooper())
            val targetDir = File(context.cacheDir, "update_apks").apply { mkdirs() }
            val targetFile = File(targetDir, updateInfo.apkName)
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "jussichords/${BuildConfig.VERSION_NAME}")
            }

            try {
                if (connection.responseCode !in 200..299) {
                    error("下载安装包失败: HTTP ${connection.responseCode}")
                }
                val totalBytes = connection.contentLengthLong
                var downloadedBytes = 0L
                connection.inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0) {
                                reportProgress(
                                    mainHandler,
                                    onProgress,
                                    ((downloadedBytes * 100) / totalBytes).toInt()
                                )
                            }
                        }
                    }
                }
                reportProgress(mainHandler, onProgress, 100)
                targetFile
            } finally {
                connection.disconnect()
            }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(permissionIntent)
            Toast.makeText(context, "请先允许安装未知应用，再重新安装更新", Toast.LENGTH_LONG).show()
            return
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
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
            ?: error("当前仓库暂无 Release，请先发布包含 APK 安装包的 Release")
    }

    private fun proxiedUrl(downloadUrl: String, downloadProxy: String): String {
        val proxy = downloadProxy.trim()
        if (proxy.isBlank()) return downloadUrl
        val normalizedProxy = if (proxy.endsWith("/")) proxy else "$proxy/"
        return normalizedProxy + downloadUrl
    }

    private fun reportProgress(
        mainHandler: Handler,
        onProgress: (Int) -> Unit,
        progress: Int,
    ) {
        mainHandler.post { onProgress(progress.coerceIn(0, 100)) }
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
