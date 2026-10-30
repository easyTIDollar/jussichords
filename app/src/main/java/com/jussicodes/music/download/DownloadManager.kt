package com.jussicodes.music.download

import android.content.Context
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.jussicodes.music.constants.downloadCoverKey
import com.jussicodes.music.constants.downloadCustomDirUriKey
import com.jussicodes.music.constants.downloadLrcKey
import com.jussicodes.music.constants.downloadWifiOnlyKey
import com.jussicodes.music.data.download.DownloadStatus
import com.jussicodes.music.data.download.DownloadStore
import com.jussicodes.music.data.download.DownloadTask
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.api.player.PlayerApi
import com.rcmiku.ncmapi.api.player.isTrialClip
import com.rcmiku.ncmapi.api.player.SongLevel
import com.rcmiku.ncmapi.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object DownloadManager {

    private const val TAG = "DownloadManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // 限制最多 2 个任务同时执行下载/解析
    private val concurrencySemaphore = Semaphore(2)
    private val activeCalls = ConcurrentHashMap<Long, Call>()
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    // 用于通知 DownloadService 更新通知栏状态
    private val _serviceEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val serviceEvents: SharedFlow<Unit> = _serviceEvents.asSharedFlow()

    private var loopRunning = false

    fun enqueue(context: Context, song: Song, quality: String) {
        enqueueBatch(context, listOf(song), quality)
    }

    fun enqueueBatch(context: Context, songs: List<Song>, quality: String) {
        val appContext = context.applicationContext
        scope.launch {
            for (song in songs) {
                // 如果已经有相同的成功任务且本地文件仍在，跳过
                if (DownloadStore.getCompletedPlayableUri(appContext, song.id) != null) {
                    continue
                }
                val artistStr = song.ar.joinToString("/") { it.name }
                val task = DownloadTask(
                    songId = song.id,
                    title = song.name,
                    artist = artistStr,
                    album = song.al?.name.orEmpty(),
                    coverUrl = song.al?.picUrl.orEmpty(),
                    durationMs = song.dt,
                    quality = quality,
                    status = DownloadStatus.PENDING
                )
                DownloadStore.upsertTask(appContext, task)
            }
            triggerLoop(appContext)
        }
    }

    fun pause(context: Context, songId: Long) {
        val appContext = context.applicationContext
        scope.launch {
            activeCalls[songId]?.cancel()
            activeJobs[songId]?.cancel()
            DownloadStore.markStatus(appContext, songId, DownloadStatus.PAUSED)
            _serviceEvents.tryEmit(Unit)
        }
    }

    fun resume(context: Context, songId: Long) {
        val appContext = context.applicationContext
        scope.launch {
            DownloadStore.markStatus(appContext, songId, DownloadStatus.PENDING)
            triggerLoop(appContext)
        }
    }

    fun cancel(context: Context, songId: Long) {
        val appContext = context.applicationContext
        scope.launch {
            activeCalls[songId]?.cancel()
            activeJobs[songId]?.cancel()
            DownloadStore.removeTask(appContext, songId)
            _serviceEvents.tryEmit(Unit)
        }
    }

    fun deleteDownloaded(context: Context, songId: Long, deleteFile: Boolean = true) {
        val appContext = context.applicationContext
        scope.launch {
            activeCalls[songId]?.cancel()
            activeJobs[songId]?.cancel()
            val task = DownloadStore.getCompletedTask(songId)
            if (deleteFile && task != null) {
                runCatching {
                    if (!task.filePath.isNullOrBlank()) {
                        val f = File(task.filePath)
                        if (f.exists()) f.delete()
                    }
                    if (!task.fileUri.isNullOrBlank()) {
                        val uri = Uri.parse(task.fileUri)
                        if (uri.scheme == "content") {
                            appContext.contentResolver.delete(uri, null, null)
                        }
                    }
                }
            }
            DownloadStore.removeTask(appContext, songId)
            _serviceEvents.tryEmit(Unit)
        }
    }

    fun pauseAll(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            activeCalls.values.forEach { runCatching { it.cancel() } }
            activeJobs.values.forEach { runCatching { it.cancel() } }
            val tasks = DownloadStore.getAllTasks()
            tasks.forEach { task ->
                if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PENDING || task.status == DownloadStatus.RESOLVING) {
                    DownloadStore.markStatus(appContext, task.songId, DownloadStatus.PAUSED)
                }
            }
            _serviceEvents.tryEmit(Unit)
        }
    }

    fun resumeAll(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val tasks = DownloadStore.getAllTasks()
            tasks.forEach { task ->
                if (task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED) {
                    DownloadStore.markStatus(appContext, task.songId, DownloadStatus.PENDING)
                }
            }
            triggerLoop(appContext)
        }
    }

    fun triggerLoop(context: Context) {
        val appContext = context.applicationContext
        DownloadService.start(appContext)
        synchronized(this) {
            if (loopRunning) return
            loopRunning = true
        }

        scope.launch {
            try {
                while (isActive) {
                    val tasks = DownloadStore.getAllTasks()
                    val pendingTasks = tasks.filter { it.status == DownloadStatus.PENDING }
                    if (pendingTasks.isEmpty()) {
                        val hasRunning = tasks.any { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.RESOLVING }
                        if (!hasRunning) {
                            break
                        }
                        delay(1000)
                        continue
                    }

                    // 检查 Wi-Fi 设置
                    val wifiOnly = appContext.dataStore.data.first()[downloadWifiOnlyKey] ?: false
                    if (wifiOnly && !isWifiConnected(appContext)) {
                        delay(3000)
                        continue
                    }

                    for (task in pendingTasks) {
                        if (!isActive) break
                        concurrencySemaphore.acquire()
                        val job = scope.launch {
                            try {
                                processTask(appContext, task)
                            } finally {
                                activeCalls.remove(task.songId)
                                activeJobs.remove(task.songId)
                                concurrencySemaphore.release()
                                _serviceEvents.tryEmit(Unit)
                            }
                        }
                        activeJobs[task.songId] = job
                    }
                    delay(500)
                }
            } finally {
                synchronized(this@DownloadManager) {
                    loopRunning = false
                }
                _serviceEvents.tryEmit(Unit)
            }
        }
    }

    private suspend fun processTask(context: Context, task: DownloadTask) {
        // 1. 状态置为 RESOLVING
        DownloadStore.markStatus(context, task.songId, DownloadStatus.RESOLVING)
        _serviceEvents.tryEmit(Unit)

        val songLevel = when (task.quality) {
            "standard" -> SongLevel.STANDARD
            "higher" -> SongLevel.HIGHER
            "exhigh" -> SongLevel.EXHIGH
            "hires" -> SongLevel.HIRES
            else -> SongLevel.LOSSLESS
        }

        // 2. 解析直链（优先走已有音源体系）
        val songUrlObj = runCatching {
            PlayerApi.songPlayUrlV1(task.songId.toString(), songLevel).getOrNull()?.data?.firstOrNull()
        }.getOrNull()

        val downloadUrl = songUrlObj?.url?.takeIf { it.isNotBlank() }
        val isTrial = songUrlObj?.isTrialClip() == true || downloadUrl?.contains("musicrep-ts", ignoreCase = true) == true

        if (downloadUrl == null || isTrial) {
            DownloadStore.markStatus(
                context,
                task.songId,
                DownloadStatus.FAILED,
                errorMsg = if (isTrial) "仅能获取试听片段，暂无完整音频" else "解析音频直链失败"
            )
            return
        }

        // 3. 开始下载流
        DownloadStore.markStatus(context, task.songId, DownloadStatus.DOWNLOADING)
        _serviceEvents.tryEmit(Unit)

        val req = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val call = okHttpClient.newCall(req)
        activeCalls[task.songId] = call

        runCatching {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    DownloadStore.markStatus(
                        context,
                        task.songId,
                        DownloadStatus.FAILED,
                        errorMsg = "下载 HTTP 异常: ${resp.code}"
                    )
                    return@use
                }

                val body = resp.body ?: run {
                    DownloadStore.markStatus(context, task.songId, DownloadStatus.FAILED, errorMsg = "响应体为空")
                    return@use
                }

                val totalBytes = body.contentLength()
                val contentType = resp.header("Content-Type").orEmpty().lowercase()
                val isFlac = contentType.contains("flac") || task.quality in listOf("lossless", "hires") || downloadUrl.contains(".flac", ignoreCase = true)
                val ext = if (isFlac) "flac" else "mp3"
                val mimeType = if (isFlac) "audio/flac" else "audio/mpeg"
                val cleanTitle = sanitizeFileName("${task.artist} - ${task.title}")
                val fileName = "$cleanTitle.$ext"

                // 目标输出流
                val target = prepareOutputStream(context, fileName, mimeType)
                if (target == null) {
                    DownloadStore.markStatus(context, task.songId, DownloadStatus.FAILED, errorMsg = "创建本地文件失败")
                    return@use
                }

                val (outStream, filePath, fileUri) = target

                val buf = ByteArray(8192)
                var downloaded = 0L
                var lastSpeedCalcTime = System.currentTimeMillis()
                var bytesSinceLastCalc = 0L
                var speedText = ""

                outStream.use { out ->
                    body.byteStream().use { input ->
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            downloaded += read
                            bytesSinceLastCalc += read

                            val now = System.currentTimeMillis()
                            val interval = now - lastSpeedCalcTime
                            if (interval >= 500) {
                                val bytesPerSec = (bytesSinceLastCalc * 1000f) / interval
                                speedText = formatSpeed(bytesPerSec)
                                val progress = if (totalBytes > 0) downloaded.toFloat() / totalBytes else 0f
                                DownloadStore.updateProgress(
                                    context = context,
                                    songId = task.songId,
                                    progress = progress,
                                    speedText = speedText,
                                    downloadedBytes = downloaded,
                                    totalBytes = totalBytes
                                )
                                lastSpeedCalcTime = now
                                bytesSinceLastCalc = 0L
                            }
                        }
                        out.flush()
                    }
                }

                // 刷新系统媒体库
                if (filePath != null) {
                    MediaScannerConnection.scanFile(context, arrayOf(filePath), arrayOf(mimeType), null)
                }

                // 同步下载歌词与封面
                fetchLrcAndCover(context, task, cleanTitle)

                // 标记完成
                DownloadStore.markStatus(
                    context = context,
                    songId = task.songId,
                    status = DownloadStatus.COMPLETED,
                    filePath = filePath,
                    fileUri = fileUri,
                    mimeType = mimeType,
                    totalBytes = downloaded
                )
            }
        }.onFailure { err ->
            if (activeCalls[task.songId]?.isCanceled() == true) {
                // 用户主动暂停或取消，不标记 FAILED
                android.util.Log.d(TAG, "Task cancelled/paused by user: ${task.songId}")
            } else {
                DownloadStore.markStatus(
                    context,
                    task.songId,
                    DownloadStatus.FAILED,
                    errorMsg = err.localizedMessage ?: "下载发生未知错误"
                )
            }
        }
    }

    private data class OutputTarget(
        val stream: OutputStream,
        val filePath: String?,
        val fileUri: String?
    )

    private suspend fun prepareOutputStream(
        context: Context,
        fileName: String,
        mimeType: String
    ): OutputTarget? {
        val customUriStr = context.dataStore.data.first()[downloadCustomDirUriKey].orEmpty()
        if (customUriStr.isNotBlank()) {
            val customTarget = runCatching {
                val treeUri = Uri.parse(customUriStr)
                val treeDir = DocumentFile.fromTreeUri(context, treeUri)
                if (treeDir != null && treeDir.canWrite()) {
                    val docFile = treeDir.createFile(mimeType, fileName)
                    if (docFile != null) {
                        val stream = context.contentResolver.openOutputStream(docFile.uri)
                        if (stream != null) {
                            OutputTarget(stream, null, docFile.uri.toString())
                        } else null
                    } else null
                } else null
            }.getOrNull()

            if (customTarget != null) return customTarget
        }

        // 默认目录：公共 Music/Jussichords
        return runCatching {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val jussiDir = File(musicDir, "Jussichords")
            if (!jussiDir.exists()) {
                jussiDir.mkdirs()
            }
            val file = File(jussiDir, fileName)
            OutputTarget(FileOutputStream(file), file.absolutePath, Uri.fromFile(file).toString())
        }.getOrNull()
    }

    private suspend fun fetchLrcAndCover(context: Context, task: DownloadTask, baseFileName: String) {
        val saveLrc = context.dataStore.data.first()[downloadLrcKey] ?: true
        val saveCover = context.dataStore.data.first()[downloadCoverKey] ?: true

        // 仅在默认公共目录时保存同名 .lrc 与 .jpg（SAF DocumentFile 不干扰）
        val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Jussichords")
        if (!musicDir.exists()) musicDir.mkdirs()

        if (saveLrc) {
            runCatching {
                val lyricResp = PlayerApi.songLyric(task.songId).getOrNull()
                val lrcText = lyricResp?.lrc?.lyric.orEmpty()
                if (lrcText.isNotBlank()) {
                    val lrcFile = File(musicDir, "$baseFileName.lrc")
                    lrcFile.writeText(lrcText)
                }
            }
        }

        if (saveCover && task.coverUrl.isNotBlank()) {
            runCatching {
                val coverReq = Request.Builder().url(task.coverUrl).build()
                okHttpClient.newCall(coverReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.byteStream()?.use { input ->
                            val coverFile = File(musicDir, "$baseFileName.jpg")
                            FileOutputStream(coverFile).use { out ->
                                input.copyTo(out)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    private fun formatSpeed(bytesPerSec: Float): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024 * 1024))
            bytesPerSec >= 1024 -> String.format("%.0f KB/s", bytesPerSec / 1024)
            else -> String.format("%.0f B/s", bytesPerSec)
        }
    }

    private fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
