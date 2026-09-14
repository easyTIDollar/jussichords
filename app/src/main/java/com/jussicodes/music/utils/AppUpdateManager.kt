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
    private const val API_CONNECT_TIMEOUT_SECONDS = 6L
    private const val API_READ_TIMEOUT_SECONDS = 10L
    private const val API_CALL_TIMEOUT_SECONDS = 15L
    private const val API_PING_CONNECT_TIMEOUT_SECONDS = 4L
    private const val API_PING_READ_TIMEOUT_SECONDS = 6L
    private const val API_PING_CALL_TIMEOUT_SECONDS = 8L

    val downloadSources = listOf(
        GitHubDownloadSource("direct", "GitHub 直连", ""),
        GitHubDownloadSource("gh-proxy", "gh-proxy.com", "https://gh-proxy.com/"),
    )

    /** 预设的 ncmapi 后端地址。点按「API 服务器」会并行 Ping 这三个，并把延迟最低的设为活动。 */
    val apiServers = listOf(
        "https://api.jussichords.indevs.in",
        "http://8.134.163.111:3000",
        "https://api.jussichords.kdns.fr",
    )

    /** 仅用于走代理拉 GitHub API（检查更新/测速）；下载 APK 仍复用 downloadSources。 */
    private val apiSources = downloadSources

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

    /** 探测 ncmapi 后端用的短超时客户端：连接/读取都快失败，避免 Ping 卡住。 */
    private val apiPingClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(API_PING_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(API_PING_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(API_PING_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(false)
            .build()
    }

    private val apiClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(API_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(API_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(API_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
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

    /**
     * 并行 Ping 预设的三个 ncmapi 后端，返回每个的延迟与可用性。
     * 后端可能没有专用健康检查端点，只要 TCP/HTTP 层在短超时内通即记为可用
     * （任何 2xx/3xx/4xx 都说明地址是活的，4xx 是「在但路径错」也接受）。
     * 调用方拿到结果后取延迟最低且可用的那个设为活动 API。
     */
    suspend fun measureApiServers(): List<ApiServerStatus> =
        withContext(Dispatchers.IO) {
            supervisorScope {
                apiServers.map { server ->
                    async {
                        runCatching {
                            val elapsed = measureTimeMillis {
                                val request = Request.Builder()
                                    .url(server)
                                    .head()
                                    .header("User-Agent", "jussichords/${BuildConfig.VERSION_NAME}")
                                    .build()
                                apiPingClient.newCall(request).execute().use { response ->
                                    // 任何 HTTP 响应（含 4xx/5xx）都代表地址可达；
                                    // 真正不可达会走 catch 分支。
                                    Unit
                                }
                            }
                            ApiServerStatus(server, elapsed, true, "可用 · ${elapsed} ms")
                        }.getOrElse { throwable ->
                            ApiServerStatus(
                                server = server,
                                latencyMs = null,
                                available = false,
                                message = pingFailureDetail(throwable),
                            )
                        }
                    }
                }.awaitAll()
            }
        }

    /** 取三个后端中「可用且延迟最低」的，全不可用时返回 null。 */
    fun pickFastestApiServer(statuses: List<ApiServerStatus>): String? =
        statuses
            .filter { it.available && it.latencyMs != null }
            .minByOrNull { it.latencyMs!! }
            ?.server

    /** 判断某个 URL 是否为预设后端之一（用于区分「预设」与「自定义」。 */
    fun isPresetApiServer(server: String): Boolean =
        server.trim().let { it.isNotEmpty() && apiServers.any { p -> p.trim() == it } }

    /**
     * 拉取 GitHub API 文本：apiSources（GitHub 直连 + gh-proxy.com）**并发竞速**，
     * 谁先成功立即返回；全部失败才抛错。OkHttp 短超时（connect 6s / read 10s / call 15s），
     * 墙内直连 hang 也最多 6s 就快速放弃，因此检查更新通常 1~2s 出结果，不再逐个源串行拖慢。
     */
    private suspend fun requestText(baseApiUrl: String): String =
        supervisorScope {
            val results = apiSources.map { source ->
                async {
                    runCatching { requestTextOnce(source.apply(baseApiUrl)) }
                }
            }
            var firstError: String? = null
            for (deferred in results) {
                val outcome = deferred.await()
                if (outcome.isSuccess) {
                    return@supervisorScope outcome.getOrThrow()
                }
                if (firstError == null) {
                    firstError = outcome.exceptionOrNull()?.message
                }
            }
            throw HttpStatusException(0, "无法连接 GitHub API：${firstError ?: "所有源均失败"}")
        }

    /** 走单个源（可能带代理前缀）拉一次 GitHub API 文本；成功返回正文，失败抛带说明的异常。 */
    private suspend fun requestTextOnce(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "jussicodes.music/${BuildConfig.VERSION_NAME}")
            .build()
        apiClient.newCall(request).execute().use { response ->
            val code = response.code
            if (code in 200..299) {
                return response.body?.string()
                    ?: throw HttpStatusException(0, "GitHub API 响应体为空")
            }
            val errBody = runCatching { response.body?.string() }.getOrNull().orEmpty()
            val rateLimited = code in intArrayOf(403, 503) &&
                (errBody.contains("rate limit", ignoreCase = true) ||
                    errBody.contains("abuse", ignoreCase = true) ||
                    response.header("X-RateLimit-Remaining") == "0")
            val detail = when {
                rateLimited -> "GitHub API 限流，稍后再试"
                code == 403 -> "GitHub 拒绝请求（403）：网络代理/防火墙拦截，或限流"
                else -> "GitHub 请求失败（$code）"
            }
            throw HttpStatusException(code, detail)
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

    /** Ping 失败时的简短说明，给对话框里逐行展示用。 */
    private fun pingFailureDetail(throwable: Throwable): String = when (throwable) {
        is SocketTimeoutException -> "超时"
        is java.net.UnknownHostException -> "无法解析"
        is java.net.ConnectException -> "连接被拒"
        else -> "不可用"
    }

    private class HttpStatusException(val code: Int, val detail: String = "HTTP $code") :
        Exception(detail)
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

/** 预设 ncmapi 后端的 Ping 结果：URL、延迟（ms）、是否可用、说明文案。 */
data class ApiServerStatus(
    val server: String,
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
