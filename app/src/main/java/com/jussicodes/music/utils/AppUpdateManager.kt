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
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/** 预设的 ncmapi 后端地址。点按「API 服务器」会并行 Ping 这三个，并把延迟最低的设为活动。 */
val apiServers = listOf(
    "https://api.jussichords.indevs.in",
    "http://8.134.163.111:3000",
    "https://api.jussichords.kdns.fr",
)

/** 应用仓库固定坐标（Gitee 镜像与 GitHub 同源 tag，可用于跨源换算下载地址）。 */
const val REPO_OWNER = "easyTIDollar"
const val REPO_NAME = "jussichords"
const val GITHUB_RELEASES_URL =
    "https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases"
const val GITHUB_CANONICAL_DOWNLOAD_BASE =
    "https://github.com/${REPO_OWNER}/${REPO_NAME}/releases/download"
const val GITHUB_RELEASES_LATEST_PAGE =
    "https://github.com/${REPO_OWNER}/${REPO_NAME}/releases/latest"

/**
 * 更新源。用户可在「设置 · 检查更新」与开屏更新弹窗里自由选择，下载时首选所选源、
 * 失败自动回落到其余源。Gitee 为主源（国内 CDN 快），GitHub 直连 / gh-proxy 兜底。
 *
 * 各源各自声明：如何拉 release 列表（fetchReleasesText）、如何把「GitHub 规范下载链接」
 * 换算成自己可直连的 URL（toDownloadUrl）、以及测速探测（probeRequest）。GitHub 系与
 * Gitee 系 URL 结构不同，不可互套前缀，故按 family 隔离。
 */
sealed class UpdateSource {
    abstract val id: String
    abstract val name: String
    /** 给选择器 UI 展示的一行小字提示（如「国内直连」「gh-proxy.com」）。 */
    abstract val detail: String
    /** github / gitee：同一族的下载链接可互相对照，跨族不行。 */
    abstract val family: String

    /** 阻塞拉取 release 列表 JSON（按 created 倒序）；失败抛 IOException/HttpStatusException。 */
    abstract fun fetchReleasesText(apiClient: OkHttpClient): String

    /** 把 GitHub 规范的 release 下载链接换算成本源可直连的 URL（默认原样透传）。 */
    open fun toDownloadUrl(canonicalGithubDownloadUrl: String): String = canonicalGithubDownloadUrl

    /** 测速探测请求（短超时；Gitee 必须 GET+Accept:json，HEAD 会 401）。 */
    abstract fun probeRequest(): Request
}

class GiteeUpdateSource : UpdateSource() {
    override val id = "gitee"
    override val name = "Gitee"
    override val detail = "国内直连 · CDN"
    override val family = "gitee"

    private val releasesUrl =
        "https://gitee.com/api/v5/repos/${REPO_OWNER}/${REPO_NAME}/releases?per_page=30&sort=created&direction=desc"
    private val probeUrl =
        "https://gitee.com/api/v5/repos/${REPO_OWNER}/${REPO_NAME}/releases?per_page=1"

    override fun fetchReleasesText(apiClient: OkHttpClient): String {
        val response = apiClient.newCall(
            Request.Builder()
                .url(releasesUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "jussichords/${BuildConfig.VERSION_NAME}")
                .build()
        ).execute().use { resp ->
            if (resp.code !in 200..299) throw HttpStatusException(resp.code, "Gitee API HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("Empty Gitee API response")
        }
        return response
    }

    /** GitHub 规范下载链接形如 https://github.com/{o}/{r}/releases/download/{tag}/{name}，
     *  把 {tag}/{name} 抽出来拼到 Gitee 下载端点。 */
    override fun toDownloadUrl(canonicalGithubDownloadUrl: String): String {
        if (canonicalGithubDownloadUrl.startsWith("https://gitee.com/")) {
            return canonicalGithubDownloadUrl
        }
        val marker = "/releases/download/"
        val idx = canonicalGithubDownloadUrl.indexOf(marker)
        return if (idx >= 0) {
            val rel = canonicalGithubDownloadUrl.substring(idx + marker.length)
            "https://gitee.com/${REPO_OWNER}/${REPO_NAME}/releases/download/$rel"
        } else {
            canonicalGithubDownloadUrl
        }
    }

    override fun probeRequest(): Request =
        Request.Builder()
            .url(probeUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "jussichords/${BuildConfig.VERSION_NAME}")
            .build()
}

class GitHubUpdateSource(
    override val id: String,
    override val name: String,
    override val detail: String,
    private val prefix: String,
) : UpdateSource() {
    override val family = "github"

    override fun fetchReleasesText(apiClient: OkHttpClient): String {
        val response = apiClient.newCall(
            Request.Builder()
                .url(prefix + GITHUB_RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "jussichords/${BuildConfig.VERSION_NAME}")
                .build()
        ).execute().use { resp ->
            if (resp.code !in 200..299) throw HttpStatusException(resp.code, "GitHub API HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("Empty GitHub API response")
        }
        return response
    }

    override fun toDownloadUrl(canonicalGithubDownloadUrl: String): String =
        if (prefix.isBlank()) canonicalGithubDownloadUrl else "$prefix$canonicalGithubDownloadUrl"

    override fun probeRequest(): Request =
        Request.Builder()
            .url(prefix + GITHUB_RELEASES_LATEST_PAGE)
            .head()
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "jussichords/${BuildConfig.VERSION_NAME}")
            .build()
}

/** 全局下载源列表：Gitee 优先，GitHub 直连 / gh-proxy 兜底。 */
val downloadSources = listOf(
    GiteeUpdateSource(),
    GitHubUpdateSource("direct", "GitHub 直连", "直连", ""),
    GitHubUpdateSource("gh-proxy", "gh-proxy.com", "gh-proxy.com", "https://gh-proxy.com/"),
)

fun sourceById(sourceId: String?): UpdateSource? =
    downloadSources.firstOrNull { it.id == sourceId }

/** 首选源在前、其余按固定序排后（用于下载回退）；preferred 为 null 时返回固定序。 */
fun orderedSources(preferredSourceId: String?): List<UpdateSource> {
    val preferred = sourceById(preferredSourceId) ?: return downloadSources
    return listOf(preferred) + downloadSources.filterNot { it === preferred }
}

/** 把 GitHub 规范下载链接拼出来（checkUpdate 统一产出，各源再换算成自己的直连地址）。 */
fun canonicalGitHubDownloadUrl(tag: String, apkName: String): String =
    "$GITHUB_CANONICAL_DOWNLOAD_BASE/$tag/$apkName"

private class HttpStatusException(val code: Int, val detail: String = "HTTP $code") :
    Exception(detail)

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
     * 渠道感知的更新检查（源无关：按 Gitee→GitHub 固定序逐个源拉列表，取首个成功者）。
     * - canary 构建（BuildConfig.BUILD_TYPE == "canary"）只跟踪 pre-release（canary 通道）
     * - 其他构建只跟踪正式 release
     * 返回 null 表示已最新。
     */
    suspend fun checkUpdate(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching { resolveUpdate() }
    }

    private fun resolveUpdate(): UpdateInfo? {
        var lastError: Throwable? = null
        for (source in downloadSources) {
            try {
                return fetchLatestReleases(source).let { (latestStable, latestPre) ->
                    val release = if (BuildConfig.BUILD_TYPE == "canary") {
                        checkNotNull(latestPre) { "No pre-release found for canary channel" }
                    } else {
                        checkNotNull(latestStable) { "No published release found" }
                    }
                    val asset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                        ?: error("Release ${release.tagName} does not contain an APK asset")
                    val latestTag = release.tagName.trim().trimStart('v', 'V')
                    if (latestTag == BuildConfig.VERSION_NAME) return null
                    UpdateInfo(
                        versionName = latestTag,
                        releaseName = release.name.takeIf { it.isNotBlank() } ?: release.tagName,
                        body = release.body,
                        apkName = asset.name,
                        apkSize = asset.size,
                        downloadUrl = canonicalGitHubDownloadUrl(release.tagName, asset.name),
                    )
                }
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw lastError ?: error("Failed to check update")
    }

    /** 拉取某个源的 release 列表并解析；返回 (最新正式版, 最新 pre-release)。 */
    private fun fetchLatestReleases(source: UpdateSource): Pair<GitHubRelease?, GitHubRelease?> {
        val raw = source.fetchReleasesText(apiClient)
        val releases = json.decodeFromString<List<GitHubRelease>>(raw).filterNot { it.draft }
        val latestStable = releases.firstOrNull { !it.prerelease }
        val latestPre = releases.firstOrNull { it.prerelease }
        return Pair(latestStable, latestPre)
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

    /** 并行探测全部下载源（各源自探测），返回延迟与可用性，供「测速」弹窗展示。 */
    suspend fun measureDownloadSources(): List<UpdateSourceStatus> =
        withContext(Dispatchers.IO) {
            supervisorScope {
                downloadSources.map { source ->
                    async {
                        runCatching {
                            val elapsed = measureTimeMillis {
                                sourceTestClient.newCall(source.probeRequest()).execute().use { response ->
                                    if (!response.isSuccessful && response.code !in 300..399) {
                                        throw HttpStatusException(response.code)
                                    }
                                }
                            }
                            UpdateSourceStatus(source, elapsed, true, "可用")
                        }.getOrElse { throwable ->
                            UpdateSourceStatus(
                                source = source,
                                latencyMs = null,
                                available = false,
                                message = pingFailureDetail(throwable),
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

    /** 构造下载候选 URL：所选源在前，其余源回落，各自把 GitHub 规范链接换算成自己的直连地址。 */
    private fun buildCandidateUrls(updateInfo: UpdateInfo, preferredSourceId: String): List<String> =
        orderedSources(preferredSourceId)
            .map { it.toDownloadUrl(updateInfo.downloadUrl) }
            .distinct()

    private fun downloadWithRetry(
        updateInfo: UpdateInfo,
        tempFile: File,
        sourceId: String,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        val candidateUrls = buildCandidateUrls(updateInfo, sourceId)
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

    /** Ping/探测失败时的简短说明，给对话框里逐行展示用。 */
    private fun pingFailureDetail(throwable: Throwable): String = when (throwable) {
        is SocketTimeoutException -> "超时"
        is java.net.UnknownHostException -> "无法解析"
        is java.net.ConnectException -> "连接被拒"
        else -> "不可用"
    }
}

data class UpdateSourceStatus(
    val source: UpdateSource,
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
