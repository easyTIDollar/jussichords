package com.jussicodes.music.download

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.jussicodes.music.constants.downloadCoverKey
import com.jussicodes.music.constants.downloadCustomDirUriKey
import com.jussicodes.music.constants.downloadLrcKey
import com.jussicodes.music.constants.downloadWifiOnlyKey
import com.jussicodes.music.data.download.DownloadStatus
import com.jussicodes.music.data.download.DownloadStore
import com.jussicodes.music.data.download.DownloadTask
import com.jussicodes.music.extensions.isTrialClip
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.api.player.PlayerApi
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
import java.io.FileInputStream
import java.io.FileOutputStream
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
                    artistId = song.ar.firstOrNull()?.id ?: 0L,
                    albumId = song.al?.id ?: 0L,
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

        // 候选音质顺降（高音质没有就顺降到可用音质，确保 VIP 歌不因缺 hires 而下不了）
        val candidateLevels = when (task.quality) {
            "hires" -> listOf("hires", "lossless", "exhigh", "standard")
            "lossless" -> listOf("lossless", "exhigh", "standard")
            "exhigh" -> listOf("exhigh", "standard")
            else -> listOf("standard")
        }

        var downloadUrl: String? = null

        // 1. 优先直接走洛雪音源插件
        for (lvl in candidateLevels) {
            val url = runCatching {
                com.jussicodes.music.lx.LxPluginManager.resolveUrl(task.songId.toString(), lvl)
            }.getOrNull()
            if (!url.isNullOrBlank() && !url.contains("musicrep-ts", ignoreCase = true)) {
                downloadUrl = url
                android.util.Log.i(TAG, "Download resolved via LX plugin: songId=${task.songId} quality=$lvl url=$url")
                break
            }
        }

        // 2. 若 LX 未命中，走第三方解灰（显式传 fee=1 强制开启第三方解析）
        if (downloadUrl.isNullOrBlank()) {
            for (lvl in candidateLevels) {
                val sLevel = when (lvl) {
                    "standard" -> SongLevel.STANDARD
                    "higher" -> SongLevel.HIGHER
                    "exhigh" -> SongLevel.EXHIGH
                    "hires" -> SongLevel.HIRES
                    else -> SongLevel.LOSSLESS
                }
                val resp = runCatching {
                    PlayerApi.songPlayUrlV1("${task.songId}?fee=1", sLevel).getOrNull()?.data?.firstOrNull()
                }.getOrNull()
                val u = resp?.url?.takeIf { it.isNotBlank() }
                if (u != null && resp.isTrialClip() != true && !u.contains("musicrep-ts", ignoreCase = true)) {
                    downloadUrl = u
                    android.util.Log.i(TAG, "Download resolved via unblock: songId=${task.songId} quality=$lvl url=$u")
                    break
                }
            }
        }

        // 3. 最终官方兜底（若账号有 VIP 则可下，否则只拿试听会被拦截）
        if (downloadUrl.isNullOrBlank()) {
            val sLevel = when (task.quality) {
                "standard" -> SongLevel.STANDARD
                "higher" -> SongLevel.HIGHER
                "exhigh" -> SongLevel.EXHIGH
                "hires" -> SongLevel.HIRES
                else -> SongLevel.LOSSLESS
            }
            val resp = runCatching {
                PlayerApi.songPlayUrlV1(task.songId.toString(), sLevel).getOrNull()?.data?.firstOrNull()
            }.getOrNull()
            val u = resp?.url?.takeIf { it.isNotBlank() }
            if (u != null && resp.isTrialClip() != true && !u.contains("musicrep-ts", ignoreCase = true)) {
                downloadUrl = u
            }
        }

        if (downloadUrl == null) {
            DownloadStore.markStatus(
                context,
                task.songId,
                DownloadStatus.FAILED,
                errorMsg = "当前音源未能获取到完整可下载音频"
            )
            return
        }

        // 3. 开始下载流到临时文件
        DownloadStore.markStatus(context, task.songId, DownloadStatus.DOWNLOADING)
        _serviceEvents.tryEmit(Unit)

        val req = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val call = okHttpClient.newCall(req)
        activeCalls[task.songId] = call

        // 建立 cache 临时文件
        val tempAudioFile = File(context.cacheDir, "dl_${task.songId}_${System.currentTimeMillis()}.tmp")

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
                val buf = ByteArray(8192)
                var downloaded = 0L
                var lastSpeedCalcTime = System.currentTimeMillis()
                var bytesSinceLastCalc = 0L
                var speedText = ""

                FileOutputStream(tempAudioFile).use { out ->
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

                // 4. 分析真实音频格式与计算真实码率
                val header4 = ByteArray(4)
                FileInputStream(tempAudioFile).use { it.read(header4) }
                val isFlac = header4[0] == 'f'.code.toByte() &&
                        header4[1] == 'L'.code.toByte() &&
                        header4[2] == 'a'.code.toByte() &&
                        header4[3] == 'C'.code.toByte()

                val durationSec = (task.durationMs / 1000).coerceAtLeast(1)
                val kbps = ((downloaded * 8) / durationSec / 1000).toInt()
                val actualQualityText = if (isFlac) {
                    if (kbps > 1500) "FLAC Hi-Res" else "FLAC 无损"
                } else {
                    when {
                        kbps >= 280 -> "MP3 320K"
                        kbps >= 180 -> "MP3 192K"
                        else -> "MP3 128K"
                    }
                }

                // 5. 抓取歌词与封面并内嵌至音频文件
                val saveLrc = context.dataStore.data.first()[downloadLrcKey] ?: true
                val saveCover = context.dataStore.data.first()[downloadCoverKey] ?: true

                var lyricsText: String? = null
                if (saveLrc) {
                    runCatching {
                        val lyricResp = PlayerApi.songLyric(task.songId).getOrNull()
                        lyricsText = lyricResp?.lrc?.lyric?.takeIf { it.isNotBlank() }
                    }
                }

                var coverBytes: ByteArray? = null
                if (saveCover && task.coverUrl.isNotBlank()) {
                    runCatching {
                        val coverReq = Request.Builder().url(task.coverUrl).build()
                        okHttpClient.newCall(coverReq).execute().use { cResp ->
                            if (cResp.isSuccessful) {
                                coverBytes = cResp.body?.bytes()
                            }
                        }
                    }
                }

                // 真正执行内嵌到同一个音频文件！
                AudioTagWriter.embedMetadata(
                    file = tempAudioFile,
                    title = task.title,
                    artist = task.artist,
                    album = task.album,
                    lyrics = lyricsText,
                    coverBytes = coverBytes
                )

                // 6. 确定最终文件名与目标存储
                val ext = if (isFlac) "flac" else "mp3"
                val mimeType = if (isFlac) "audio/flac" else "audio/mpeg"
                val cleanTitle = sanitizeFileName("${task.artist} - ${task.title}")
                val fileName = "$cleanTitle.$ext"

                val subDirName = if (task.album.isNotBlank()) {
                    sanitizeFileName(if (task.artist.isNotBlank()) "${task.artist} - ${task.album}" else task.album)
                } else null

                val target = prepareOutputStream(context, fileName, mimeType, subDirName)
                if (target == null) {
                    tempAudioFile.delete()
                    DownloadStore.markStatus(context, task.songId, DownloadStatus.FAILED, errorMsg = "创建目标存储文件失败")
                    return@use
                }

                val (outStream, filePath, fileUri) = target
                outStream.use { dest ->
                    FileInputStream(tempAudioFile).use { src ->
                        src.copyTo(dest)
                    }
                    dest.flush()
                }
                tempAudioFile.delete()

                // 刷新系统媒体库
                if (filePath != null) {
                    MediaScannerConnection.scanFile(context, arrayOf(filePath), arrayOf(mimeType), null)
                }

                // 标记完成
                DownloadStore.markStatus(
                    context = context,
                    songId = task.songId,
                    status = DownloadStatus.COMPLETED,
                    filePath = filePath,
                    fileUri = fileUri,
                    mimeType = mimeType,
                    totalBytes = downloaded,
                    actualQualityText = actualQualityText
                )
            }
        }.onFailure { err ->
            tempAudioFile.delete()
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
        mimeType: String,
        subDirName: String? = null
    ): OutputTarget? {
        val customUriStr = context.dataStore.data.first()[downloadCustomDirUriKey].orEmpty()

        // 若用户设置了 SAF 自定义目录
        if (customUriStr.isNotBlank()) {
            val customTarget = runCatching {
                val treeUri = Uri.parse(customUriStr)
                val treeDir = DocumentFile.fromTreeUri(context, treeUri)
                if (treeDir != null && treeDir.canWrite()) {
                    val targetDir = if (!subDirName.isNullOrBlank()) {
                        treeDir.findFile(subDirName)?.takeIf { it.isDirectory }
                            ?: treeDir.createDirectory(subDirName)
                            ?: treeDir
                    } else {
                        treeDir
                    }
                    val docFile = targetDir.createFile(mimeType, fileName)
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

        // 默认目录：公共 Music/Jussichords/[歌手 - 专辑/]
        return runCatching {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val jussiDir = File(musicDir, "Jussichords")
            val targetDir = if (!subDirName.isNullOrBlank()) {
                File(jussiDir, subDirName)
            } else {
                jussiDir
            }
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val file = File(targetDir, fileName)
            OutputTarget(FileOutputStream(file), file.absolutePath, Uri.fromFile(file).toString())
        }.getOrNull()
    }

    /**
     * 一键打开本地下载文件夹，优先调起系统文件管理器
     */
    fun openDownloadFolder(context: Context) {
        val appContext = context.applicationContext
        scope.launch(Dispatchers.Main) {
            val customUriStr = appContext.dataStore.data.first()[downloadCustomDirUriKey].orEmpty()
            if (customUriStr.isNotBlank()) {
                val opened = runCatching {
                    val treeUri = Uri.parse(customUriStr)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(treeUri, "vnd.android.document/directory")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                    true
                }.getOrDefault(false)
                if (opened) return@launch
            }

            val defaultDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "Jussichords"
            )
            if (!defaultDir.exists()) {
                defaultDir.mkdirs()
            }

            val openedDefault = runCatching {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    defaultDir
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "resource/folder")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
                true
            }.getOrDefault(false)

            if (!openedDefault) {
                // 回退到系统通用文档选择界面或提示绝对路径
                runCatching {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(
                            Uri.parse("content://media/external/file"),
                            "vnd.android.document/directory"
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                }.onFailure {
                    Toast.makeText(context, "下载目录: ${defaultDir.absolutePath}", Toast.LENGTH_LONG).show()
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
