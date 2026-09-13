package com.jussicodes.music.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.jussicodes.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
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
import kotlin.system.measureTimeMillis
import java.util.concurrent.TimeUnit

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/easyTIDollar/jussichords/releases/latest"
private const val RELEASES_URL =
    "https://api.github.com/repos/easyTIDollar/jussichords/releases"

object AppUpdateManager {
    private const val UPDATE_DIR = "updates"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val DOWNLOAD_CONNECT_TIMEOUT_SECONDS = 30L
    private const val DOWNLOAD_READ_TIMEOUT_SECONDS = 120L
    private const val DOWNLOAD_CALL_TIMEOUT_SECONDS = 300L
    private const val DOWNLOAD_MAX_RETRIES = 2
    private const val SOURCE_TEST_TIMEOUT_SECONDS = 3L

    val downloadSources = listOf(
        GitHubDownloadSource("direct", "GitHub 直连", ""),
        GitHubDownloadSource("gh-proxy", "gh-proxy.com", "https://gh-proxy.com/"),
        GitHubDownloadSource("gh-llkk", "gh.llkk.cc", "https://gh.llkk.cc/"),
        GitHubDownloadSource("ghproxy-net", "ghproxy.net", "https://ghproxy.net/"),
        GitHubDownloadSource("ghfast", "ghfast.top", "https://ghfast.top/")
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

    private val sourceTestClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(SOURCE_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(SOURCE_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(SOURCE_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(false)
            .build()
    }

    /**
     * 渠道感知的更新检查：
     * - canary 构建（BuildConfig.BUILD_TYPE == "canary"）只跟踪 pre-release（canary 通道）
     * - 其他构建只跟踪正式 release
     */
    suspend fun checkUpdate(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val (latestStable, latestPre) = fetchLatestReleases()
            val release = if (BuildConfig.BUILD_TYPE == "canary") {
                checkNotNull(latestPre) { "No pre-release found for canary channel" }
            } else {
                checkNotNull(latestStable) { "No published release found" }
            }
            val asset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: error("Release ${release.tagName} does not contain an APK asset")
            val latestTag = release.tagName.trim().trimStart('v', 'V')
            // tag 不同即视为有更新（canary 每个构建 tag 都带 run 编号递增）
            if (latestTag == BuildConfig.VERSION_NAME) return@runCatching null
            UpdateInfo(
                versionName = latestTag,
                releaseName = release.name.takeIf { it.isNotBlank() } ?: release.tagName,
                body = release.body,
                apkName = asset.name,
                apkSize = asset.size,
                downloadUrl = asset.downloadUrl
            )
        }
    }

    /** 旧版检查入口，保留以兼容既有调用 */
    @Deprecated("Replaced by checkUpdate(context) which is channel-aware", ReplaceWith("checkUpdate(context)"))
    suspend fun checkLatestRelease(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val release = fetchLatestRelease()
            val asset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: error("Latest release does not contain an APK asset")
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
        sourceId: String = downloadSources.first().id,
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

        if (targetFile.exists() && updateInfo.apkSize > 0L && targetFile.length() == updateInfo.apkSize) {
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

        if (updateInfo.apkSize > 0L && tempFile.exists() && tempFile.length() > updateInfo.apkSize) {
            tempFile.delete()
        }

        val existingBytes = tempFile.takeIf(File::exists)?.length() ?: 0L
        onProgress(
            DownloadProgress(
                downloadedBytes = existingBytes,
                totalBytes = updateInfo.apkSize,
                progress = if (updateInfo.apkSize > 0L) {
                    existingBytes.toFloat() / updateInfo.apkSize
                } else {
                    0f
                }
            )
        )

        downloadWithRetry(updateInfo, tempFile, sourceId, onProgress)

        val expectedSize = updateInfo.apkSize.takeIf { it > 0 } ?: tempFile.length()
        val actualSize = tempFile.length()
        if (expectedSize > 0 && actualSize != expectedSize) {
            error("Downloaded APK is incomplete: expected $expectedSize bytes, got $actualSize bytes")
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

    suspend fun measureDownloadSources(sourceUrl: String): List<GitHubDownloadSourceStatus> =
        withContext(Dispatchers.IO) {
            supervisorScope {
                downloadSources.map { source ->
                    async {
                        runCatching {
                            val elapsed = measureTimeMillis {
                                val request = Request.Builder()
                                    .url(source.apply(sourceUrl))
                                    .head()
                                    .header("Accept", "application/octet-stream")
                                    .header("User-Agent", "jussichords/${BuildConfig.VERSION_NAME}")
                                    .build()
                                sourceTestClient.newCall(request).execute().use { response ->
                                    if (!response.isSuccessful && response.code !in 300..399) {
                                        throw HttpStatusException(response.code)
                                    }
                                }
                            }
                            GitHubDownloadSourceStatus(source, elapsed, true, "可用")
                        }.getOrElse { throwable ->
                            GitHubDownloadSourceStatus(
                                source = source,
                                latencyMs = null,
                                available = false,
                                message = throwable.message ?: "不可用",
                            )
                        }
                    }
                }.awaitAll()
            }
        }

    private fun requestText(url: String): String {
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
            throw exception ?: error("Failed to check update")
        }

        val releasesUrl = LATEST_RELEASE_URL.removeSuffix("/latest")
        val releases = json.decodeFromString<List<GitHubRelease>>(requestText(releasesUrl))
            .filterNot { it.draft || it.prerelease }
        return releases.firstOrNull()
            ?: error("No published release with APK was found")
    }

    /**
     * 一次拉取全部（非 draft）release，返回 (最新正式版, 最新 pre-release)。
     * canary 通道用后者，正式通道用前者；列表拉取失败时回退到 /releases/latest 拿正式版。
     */
    private fun fetchLatestReleases(): Pair<GitHubRelease?, GitHubRelease?> {
        val releases = runCatching {
            json.decodeFromString<List<GitHubRelease>>(requestText(RELEASES_URL))
                .filterNot { it.draft }
        }.getOrElse { t ->
            // 列表接口异常（403 限流等）时，至少保证正式通道还能工作
            val stable = runCatching {
                json.decodeFromString<GitHubRelease>(requestText(LATEST_RELEASE_URL))
            }.getOrNull()
            return if (stable == null) throw t else Pair(stable, null)
        }
        val latestStable = releases.firstOrNull { !it.prerelease }
        val latestPre = releases.firstOrNull { it.prerelease }
        return Pair(latestStable, latestPre)
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

    private fun buildCandidateUrls(url: String, preferredSourceId: String): List<String> {
        val preferred = downloadSources.firstOrNull { it.id == preferredSourceId }
        return (listOfNotNull(preferred) + downloadSources)
            .map { it.apply(url) }
            .distinct()
    }

    private fun downloadWithRetry(
        updateInfo: UpdateInfo,
        tempFile: File,
        sourceId: String,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        val candidateUrls = buildCandidateUrls(updateInfo.downloadUrl, sourceId)
        var lastException: Throwable? = null

        repeat(DOWNLOAD_MAX_RETRIES + 1) { attempt ->
            candidateUrls.forEachIndexed { index, candidateUrl ->
                runCatching {
                    downloadOnce(candidateUrl, updateInfo, tempFile, onProgress)
                }.onSuccess {
                    return
                }.onFailure { throwable ->
                    lastException = throwable
                    val hasMoreCandidates = index < candidateUrls.lastIndex
                    val canRetry = attempt < DOWNLOAD_MAX_RETRIES && throwable.isRetryableDownloadError()
                    if (!hasMoreCandidates && !canRetry) {
                        throw throwable
                    }
                }
            }
        }

        throw lastException ?: error("Failed to download update")
    }

    private fun downloadOnce(
        url: String,
        updateInfo: UpdateInfo,
        tempFile: File,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        var existingBytes = tempFile.takeIf(File::exists)?.length() ?: 0L
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "jussichords/${BuildConfig.VERSION_NAME}")

        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        downloadClient.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 416 &&
                updateInfo.apkSize > 0 &&
                existingBytes == updateInfo.apkSize
            ) {
                onProgress(
                    DownloadProgress(
                        downloadedBytes = existingBytes,
                        totalBytes = updateInfo.apkSize,
                        progress = 1f,
                        done = true
                    )
                )
                return
            }

            if (!response.isSuccessful) {
                throw HttpStatusException(response.code)
            }

            val append = response.code == HttpURLConnection.HTTP_PARTIAL && existingBytes > 0L
            if (!append && existingBytes > 0L) {
                tempFile.delete()
                existingBytes = 0L
            }

            val body = response.body ?: error("Response body for APK download is empty")
            val totalBytes = when {
                updateInfo.apkSize > 0L -> updateInfo.apkSize
                append && body.contentLength() > 0L -> existingBytes + body.contentLength()
                else -> body.contentLength().coerceAtLeast(0L)
            }

            body.byteStream().use { input ->
                FileOutputStream(tempFile, append).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = existingBytes
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(
                            DownloadProgress(
                                downloadedBytes = downloaded,
                                totalBytes = totalBytes,
                                progress = if (totalBytes > 0L) downloaded.toFloat() / totalBytes else 0f
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

    private class HttpStatusException(val code: Int) : Exception("HTTP $code")
}

@Serializable
data class GitHubDownloadSource(
    val id: String,
    val name: String,
    val prefix: String,
) {
    fun apply(url: String): String = if (prefix.isBlank()) url else "$prefix$url"
}

data class GitHubDownloadSourceStatus(
    val source: GitHubDownloadSource,
    val latencyMs: Long?,
    val available: Boolean,
    val message: String,
)

@Serializable
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
