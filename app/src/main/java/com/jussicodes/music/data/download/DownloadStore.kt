package com.jussicodes.music.data.download

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import java.io.File

object DownloadStore {

    private val DOWNLOAD_TASKS_KEY = stringPreferencesKey("download_tasks_v1")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext
        scope.launch {
            mutex.withLock {
                if (initialized) return@withLock
                runCatching {
                    val raw = appContext.dataStore.data.first()[DOWNLOAD_TASKS_KEY].orEmpty()
                    if (raw.isNotBlank()) {
                        val parsed = json.decodeFromString<List<DownloadTask>>(raw)
                        // 重启后如果仍有任务处于 DOWNLOADING/RESOLVING，重置为 PAUSED 避免假死
                        val sanitized = parsed.map { task ->
                            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.RESOLVING) {
                                task.copy(status = DownloadStatus.PAUSED, speedText = "")
                            } else {
                                task
                            }
                        }
                        _tasks.value = sanitized
                    }
                }
                initialized = true
            }
        }
    }

    suspend fun getAllTasks(): List<DownloadTask> {
        return _tasks.value
    }

    fun isSongDownloaded(songId: Long): Boolean {
        return _tasks.value.any { it.songId == songId && it.status == DownloadStatus.COMPLETED }
    }

    fun getCompletedTask(songId: Long): DownloadTask? {
        return _tasks.value.firstOrNull { it.songId == songId && it.status == DownloadStatus.COMPLETED }
    }

    /**
     * 供播放器 ResolvingDataSource 使用：若已下载且本地文件依然存在，返回本地播放 URI。
     */
    fun getCompletedPlayableUri(context: Context, songId: Long): Uri? {
        val task = getCompletedTask(songId) ?: return null
        val uriStr = task.fileUri
        val pathStr = task.filePath

        if (!pathStr.isNullOrBlank()) {
            val file = File(pathStr)
            if (file.exists() && file.length() > 0) {
                return Uri.fromFile(file)
            }
        }

        if (!uriStr.isNullOrBlank()) {
            val uri = Uri.parse(uriStr)
            return runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use {
                    if (it.statSize > 0) uri else null
                }
            }.getOrNull()
        }

        return null
    }

    suspend fun upsertTask(context: Context, task: DownloadTask) {
        val appContext = context.applicationContext
        mutex.withLock {
            val current = _tasks.value.toMutableList()
            val index = current.indexOfFirst { it.songId == task.songId }
            if (index >= 0) {
                current[index] = task
            } else {
                current.add(0, task)
            }
            _tasks.value = current
            save(appContext, current)
        }
    }

    suspend fun updateProgress(
        context: Context,
        songId: Long,
        progress: Float,
        speedText: String,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        val current = _tasks.value.toMutableList()
        val index = current.indexOfFirst { it.songId == songId }
        if (index >= 0) {
            val old = current[index]
            current[index] = old.copy(
                status = DownloadStatus.DOWNLOADING,
                progress = progress,
                speedText = speedText,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes
            )
            _tasks.value = current
            // 进度高频更新只更新内存，不写 DataStore，避免 IO 轰炸
        }
    }

    suspend fun markStatus(
        context: Context,
        songId: Long,
        status: DownloadStatus,
        errorMsg: String? = null,
        filePath: String? = null,
        fileUri: String? = null,
        mimeType: String? = null,
        totalBytes: Long? = null,
        actualQualityText: String? = null
    ) {
        val appContext = context.applicationContext
        mutex.withLock {
            val current = _tasks.value.toMutableList()
            val index = current.indexOfFirst { it.songId == songId }
            if (index >= 0) {
                val old = current[index]
                current[index] = old.copy(
                    status = status,
                    errorMsg = errorMsg,
                    filePath = filePath ?: old.filePath,
                    fileUri = fileUri ?: old.fileUri,
                    mimeType = mimeType ?: old.mimeType,
                    totalBytes = totalBytes ?: old.totalBytes,
                    actualQualityText = actualQualityText ?: old.actualQualityText,
                    speedText = "",
                    finishTime = if (status == DownloadStatus.COMPLETED) System.currentTimeMillis() else old.finishTime
                )
                _tasks.value = current
                save(appContext, current)
            }
        }
    }

    suspend fun removeTask(context: Context, songId: Long) {
        val appContext = context.applicationContext
        mutex.withLock {
            val current = _tasks.value.filterNot { it.songId == songId }
            _tasks.value = current
            save(appContext, current)
        }
    }

    private suspend fun save(context: Context, list: List<DownloadTask>) {
        runCatching {
            val raw = json.encodeToString(list)
            context.dataStore.edit { prefs ->
                prefs[DOWNLOAD_TASKS_KEY] = raw
            }
        }
    }
}
