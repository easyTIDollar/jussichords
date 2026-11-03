package com.jussicodes.music.data.offline

import android.content.Context
import android.util.Log
import com.rcmiku.ncmapi.model.PlaylistDetailResponse
import com.rcmiku.ncmapi.model.PlaylistInfoResponse
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 歌单离线快照管理器：
 * 负责将歌单详情（曲目列表/元数据）与基本信息自动持久化到本地，
 * 彻底解决断网或弱网环境下打开歌单页空白、无法点播已下载歌曲的问题。
 */
object PlaylistSnapshotStore {

    private const val TAG = "PlaylistSnapshotStore"
    private const val DIR_NAME = "playlist_snapshots"

    private fun getSnapshotDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

    /**
     * 保存歌单详情快照到本地磁盘
     */
    fun saveSnapshot(context: Context, playlistId: Long, detail: PlaylistDetailResponse) {
        if (playlistId <= 0L) return
        try {
            val dir = getSnapshotDir(context)
            val tempFile = File(dir, "${playlistId}_detail.tmp")
            val targetFile = File(dir, "${playlistId}_detail.json")
            val jsonStr = json.encodeToString(PlaylistDetailResponse.serializer(), detail)
            tempFile.writeText(jsonStr, Charsets.UTF_8)
            if (tempFile.renameTo(targetFile)) {
                Log.d(TAG, "Saved snapshot for playlist $playlistId (${detail.playlist.getAllTracks().size} tracks)")
            } else {
                targetFile.writeText(jsonStr, Charsets.UTF_8)
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save snapshot for playlist $playlistId", e)
        }
    }

    /**
     * 读取歌单详情快照
     */
    fun getSnapshot(context: Context, playlistId: Long): PlaylistDetailResponse? {
        if (playlistId <= 0L) return null
        return try {
            val file = File(getSnapshotDir(context), "${playlistId}_detail.json")
            if (file.exists() && file.length() > 0) {
                val jsonStr = file.readText(Charsets.UTF_8)
                json.decodeFromString(PlaylistDetailResponse.serializer(), jsonStr)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load snapshot for playlist $playlistId", e)
            null
        }
    }

    /**
     * 保存歌单基本信息快照
     */
    fun saveInfoSnapshot(context: Context, playlistId: Long, info: PlaylistInfoResponse) {
        if (playlistId <= 0L) return
        try {
            val dir = getSnapshotDir(context)
            val file = File(dir, "${playlistId}_info.json")
            val jsonStr = json.encodeToString(PlaylistInfoResponse.serializer(), info)
            file.writeText(jsonStr, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save info snapshot for playlist $playlistId", e)
        }
    }

    /**
     * 读取歌单基本信息快照
     */
    fun getInfoSnapshot(context: Context, playlistId: Long): PlaylistInfoResponse? {
        if (playlistId <= 0L) return null
        return try {
            val file = File(getSnapshotDir(context), "${playlistId}_info.json")
            if (file.exists() && file.length() > 0) {
                val jsonStr = file.readText(Charsets.UTF_8)
                json.decodeFromString(PlaylistInfoResponse.serializer(), jsonStr)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load info snapshot for playlist $playlistId", e)
            null
        }
    }
}
