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
import com.jussicodes.music.constants.downloadTlyricKey
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

    fun enqueue(
        context: Context,
        song: Song,
        quality: String,
        sourceName: String = "",
        sourceType: String = "single"
    ) {
        enqueueBatch(context, listOf(song), quality, sourceName, sourceType)
    }

    fun enqueueBatch(
        context: Context,
        songs: List<Song>,
        quality: String,
        sourceName: String = "",
        sourceType: String = if (sourceName.isNotBlank()) "playlist" else "single"
    ) {
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
                    status = DownloadStatus.PENDING,
                    isAlbumFolder = false,
                    sourceName = sourceName,
                    sourceType = sourceType
                )
                DownloadStore.upsertTask(appContext, task)
            }
            triggerLoop(appContext)
        }
    }

    /**
     * 专供整张专辑下载：统一专辑文件夹名与专辑名，避免先行单曲导致被拆分到不同文件夹
     */
    fun enqueueAlbum(
        context: Context,
        songs: List<Song>,
        quality: String,
        albumName: String,
        albumArtist: String
    ) {
        val appContext = context.applicationContext
        val cleanAlbum = albumName.trim()
        val cleanArtist = albumArtist.trim()
        val unifiedFolder = sanitizeFileName(
            if (cleanArtist.isNotBlank()) "$cleanArtist - $cleanAlbum" else cleanAlbum
        )
        scope.launch {
            for (song in songs) {
                if (DownloadStore.getCompletedPlayableUri(appContext, song.id) != null) {
                    continue
                }
                val artistStr = song.ar.joinToString("/") { it.name }.ifBlank { cleanArtist }
                val task = DownloadTask(
                    songId = song.id,
                    title = song.name,
                    artist = artistStr,
                    album = cleanAlbum, // 强制统一为当前大专辑名，抹平先行单曲自带的假专辑名
                    artistId = song.ar.firstOrNull()?.id ?: 0L,
                    albumId = song.al?.id ?: 0L,
                    coverUrl = song.al?.picUrl.orEmpty(),
                    durationMs = song.dt,
                    quality = quality,
                    status = DownloadStatus.PENDING,
                    isAlbumFolder = true,
                    groupFolder = unifiedFolder,
                    sourceName = cleanAlbum,
                    sourceType = "album"
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
        deleteDownloadedBatch(context, listOf(songId), deleteFile)
    }

    fun deleteDownloadedBatch(context: Context, songIds: Collection<Long>, deleteFiles: Boolean = true) {
        val appContext = context.applicationContext
        scope.launch {
            songIds.forEach { id ->
                activeCalls[id]?.cancel()
                activeJobs[id]?.cancel()
            }
            val tasks = DownloadStore.getAllTasks().filter { it.songId in songIds }
            if (deleteFiles) {
                tasks.forEach { task ->
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
            }
            DownloadStore.removeTasks(appContext, songIds.toSet())
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

        val sLevel = when (task.quality) {
            "hires" -> SongLevel.HIRES
            "lossless" -> SongLevel.LOSSLESS
            "exhigh" -> SongLevel.EXHIGH
            else -> SongLevel.STANDARD
        }

        var downloadUrl: String? = null

        // 1. 优先标准接口请求（VIP账号有完整权限，或免费歌曲直接获取对应音质）
        val resp = runCatching {
            PlayerApi.songPlayUrlV1(task.songId.toString(), sLevel).getOrNull()?.data?.firstOrNull()
        }.getOrNull()
        val u = resp?.url?.takeIf { it.isNotBlank() }
        if (u != null && resp?.isTrialClip() != true && !u.contains("musicrep-ts", ignoreCase = true)) {
            downloadUrl = u
        }

        // 2. 若官方未获取到完整音频（VIP歌曲），尝试配置的第三方解灰音源
        if (downloadUrl.isNullOrBlank()) {
            val unblockResp = runCatching {
                PlayerApi.songPlayUrlV1("${task.songId}?fee=1", sLevel).getOrNull()?.data?.firstOrNull()
            }.getOrNull()
            val unblockU = unblockResp?.url?.takeIf { it.isNotBlank() }
            if (unblockU != null && unblockResp?.isTrialClip() != true && !unblockU.contains("musicrep-ts", ignoreCase = true)) {
                downloadUrl = unblockU
            }
        }

        if (downloadUrl == null) {
            val errorReason = if (task.quality in setOf("lossless", "hires", "exhigh")) {
                "此音质需要 VIP 账号或该歌曲暂无可下载音源"
            } else {
                "当前歌曲暂无可用音频下载直链"
            }
            DownloadStore.markStatus(
                context,
                task.songId,
                DownloadStatus.FAILED,
                errorMsg = errorReason
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
                val saveTlyric = context.dataStore.data.first()[downloadTlyricKey] ?: true
                val saveCover = context.dataStore.data.first()[downloadCoverKey] ?: true

                var lyricsText: String? = null
                if (saveLrc) {
                    runCatching {
                        val lyricResp = PlayerApi.songLyric(task.songId).getOrNull()
                        val origLrc = lyricResp?.lrc?.lyric?.takeIf { it.isNotBlank() }
                        val transLrc = if (saveTlyric) lyricResp?.tlyric?.lyric?.takeIf { it.isNotBlank() } else null
                        lyricsText = if (origLrc != null && !transLrc.isNullOrBlank()) {
                            mergeLrcWithTranslation(origLrc, transLrc)
                        } else {
                            origLrc
                        }
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

                val subDirName = if (task.isAlbumFolder) {
                    task.groupFolder ?: if (task.album.isNotBlank()) sanitizeFileName(if (task.artist.isNotBlank()) "${task.artist} - ${task.album}" else task.album) else null
                } else null

                val target = prepareOutputStream(context, task.songId, cleanTitle, ext, mimeType, subDirName)
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
        songId: Long,
        cleanTitle: String,
        ext: String,
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
                    val fileName = "$cleanTitle.$ext"
                    val existingFile = targetDir.findFile(fileName)
                    val finalName = if (existingFile != null && DownloadStore.getCompletedTask(songId)?.fileUri != existingFile.uri.toString()) {
                        "${cleanTitle}_$songId.$ext"
                    } else {
                        fileName
                    }
                    val docFile = targetDir.findFile(finalName) ?: targetDir.createFile(mimeType, finalName)
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
            val primaryFile = File(targetDir, "$cleanTitle.$ext")
            val completed = DownloadStore.getCompletedTask(songId)
            val finalFile = if (primaryFile.exists() && completed?.filePath != primaryFile.absolutePath) {
                File(targetDir, "${cleanTitle}_$songId.$ext")
            } else {
                primaryFile
            }
            OutputTarget(FileOutputStream(finalFile), finalFile.absolutePath, Uri.fromFile(finalFile).toString())
        }.getOrNull()
    }

    /**
     * 合并原文歌词与翻译歌词为双语 LRC 格式
     */
    private fun mergeLrcWithTranslation(origLrc: String, transLrc: String): String {
        if (transLrc.isBlank()) return origLrc
        val origLines = origLrc.lines()
        val transMap = mutableMapOf<String, String>()
        val timeTagRegex = """\[(\d+:\d+\.\d{2,3})\]""".toRegex()
        for (line in transLrc.lines()) {
            val m = timeTagRegex.find(line)
            if (m != null) {
                val tag = m.value
                val content = line.substring(m.range.last + 1).trim()
                if (content.isNotBlank()) {
                    transMap[tag] = content
                }
            }
        }
        if (transMap.isEmpty()) return origLrc
        val result = StringBuilder()
        for (line in origLines) {
            result.append(line).append("\n")
            val m = timeTagRegex.find(line)
            if (m != null) {
                val transText = transMap[m.value]
                if (!transText.isNullOrBlank()) {
                    result.append(m.value).append(" ").append(transText).append("\n")
                }
            }
        }
        return result.toString().trimEnd()
    }

    /**
     * 将歌曲移入指定分组（同时物理同步本地文件夹）
     */
    suspend fun moveSongsToGroup(
        context: Context,
        songIds: Set<Long>,
        targetGroupName: String?
    ): Boolean {
        val appContext = context.applicationContext
        val tasks = DownloadStore.tasks.value.filter { it.songId in songIds && it.status == DownloadStatus.COMPLETED }
        val updatedPaths = mutableMapOf<Long, Pair<String?, String?>>()

        val rootDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Jussichords"
        )
        val targetDir = if (!targetGroupName.isNullOrBlank()) {
            File(rootDir, sanitizeFileName(targetGroupName)).also { if (!it.exists()) it.mkdirs() }
        } else {
            rootDir
        }

        for (task in tasks) {
            val oldPath = task.filePath
            if (!oldPath.isNullOrBlank()) {
                val oldFile = File(oldPath)
                if (oldFile.exists()) {
                    val destFile = File(targetDir, oldFile.name)
                    if (oldFile.absolutePath != destFile.absolutePath) {
                        val moved = oldFile.renameTo(destFile) || runCatching {
                            oldFile.copyTo(destFile, overwrite = true)
                            oldFile.delete()
                            true
                        }.getOrDefault(false)
                        if (moved) {
                            val newUri = Uri.fromFile(destFile).toString()
                            updatedPaths[task.songId] = Pair(destFile.absolutePath, newUri)
                            MediaScannerConnection.scanFile(
                                appContext,
                                arrayOf(destFile.absolutePath),
                                arrayOf(task.mimeType),
                                null
                            )
                        }
                    }
                }
            }
        }

        DownloadStore.assignCustomGroup(appContext, songIds, targetGroupName, updatedPaths)
        return true
    }

    /**
     * 重命名自定义分组（同时物理同步重命名本地文件夹）
     */
    suspend fun renameCustomGroup(
        context: Context,
        oldName: String,
        newName: String
    ): Boolean {
        val appContext = context.applicationContext
        val rootDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Jussichords"
        )
        val oldDir = File(rootDir, sanitizeFileName(oldName))
        val newDir = File(rootDir, sanitizeFileName(newName))
        val updatedPaths = mutableMapOf<Long, Pair<String?, String?>>()

        if (oldDir.exists()) {
            if (oldDir.renameTo(newDir)) {
                val tasks = DownloadStore.tasks.value.filter { it.customGroup == oldName }
                for (task in tasks) {
                    val oldPath = task.filePath
                    if (!oldPath.isNullOrBlank()) {
                        val file = File(oldPath)
                        val newPath = File(newDir, file.name).absolutePath
                        val newUri = Uri.fromFile(File(newPath)).toString()
                        updatedPaths[task.songId] = Pair(newPath, newUri)
                    }
                }
            }
        }

        DownloadStore.renameCustomGroup(appContext, oldName, newName, updatedPaths)
        return true
    }

    /**
     * 删除自定义分组（将内部文件移回根目录并删除文件夹）
     */
    suspend fun deleteCustomGroup(
        context: Context,
        groupName: String
    ): Boolean {
        val appContext = context.applicationContext
        val rootDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Jussichords"
        )
        val dir = File(rootDir, sanitizeFileName(groupName))
        val updatedPaths = mutableMapOf<Long, Pair<String?, String?>>()

        if (dir.exists()) {
            dir.listFiles()?.forEach { file ->
                val destFile = File(rootDir, file.name)
                if (file.renameTo(destFile)) {
                    val songId = DownloadStore.tasks.value.find { it.filePath == file.absolutePath }?.songId
                    if (songId != null) {
                        updatedPaths[songId] = Pair(destFile.absolutePath, Uri.fromFile(destFile).toString())
                    }
                }
            }
            dir.delete()
        }

        DownloadStore.deleteCustomGroup(appContext, groupName, updatedPaths)
        return true
    }

    /**
     * 一键打开本地下载文件夹，优先调起系统文件管理器
     */
    fun openDownloadFolder(context: Context) {
        val appContext = context.applicationContext
        scope.launch(Dispatchers.Main) {
            val defaultDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "Jussichords"
            )
            if (!defaultDir.exists()) {
                defaultDir.mkdirs()
            }

            // 1. 复制路径到剪贴板兜底
            runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Jussichords Music", defaultDir.absolutePath)
                cm?.setPrimaryClip(clip)
            }

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
                if (opened) {
                    Toast.makeText(context, "已打开下载目录 (路径已复制)", Toast.LENGTH_SHORT).show()
                    return@launch
                }
            }

            // 2. 优先通过包名唤起主流厂商及常用文件管理器
            val knownFileManagerPkgs = listOf(
                "com.android.fileexplorer",               // 小米 / 红米 / HyperOS / MIUI
                "com.coloros.filemanager",                // OPPO / OnePlus / Realme
                "com.vivo.filemanager",                   // vivo / iQOO
                "com.huawei.hidisk",                      // 华为
                "com.hihonor.filemanager",                // 荣耀
                "com.sec.android.app.myfiles",            // 三星
                "com.google.android.apps.nbu.files",      // Google Files
                "com.android.documentsui",                // AOSP 原生文件系统
                "bin.mt.plus",                            // MT 管理器
                "ru.zdevs.zarchiver"                      // ZArchiver
            )

            var launched = false
            val pm = context.packageManager
            for (pkg in knownFileManagerPkgs) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val ok = runCatching {
                        context.startActivity(launchIntent)
                        true
                    }.getOrDefault(false)
                    if (ok) {
                        launched = true
                        break
                    }
                }
            }

            // 3. 兜底尝试通过 SAF 协议或系统 Downloads 视图查看
            if (!launched) {
                val fallbackIntents = listOf(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(
                            Uri.parse("content://com.android.externalstorage.documents/document/primary%3AMusic%2FJussichords"),
                            "vnd.android.document/directory"
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )

                for (intent in fallbackIntents) {
                    val ok = runCatching {
                        context.startActivity(intent)
                        true
                    }.getOrDefault(false)
                    if (ok) {
                        launched = true
                        break
                    }
                }
            }

            if (launched) {
                Toast.makeText(context, "已打开文件管理器，目录已复制：\n${defaultDir.absolutePath}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "下载目录: ${defaultDir.absolutePath} (已复制)", Toast.LENGTH_LONG).show()
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
