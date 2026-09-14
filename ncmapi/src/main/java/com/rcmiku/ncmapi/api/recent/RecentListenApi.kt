package com.rcmiku.ncmapi.api.recent

import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.model.RecentAlbumResource
import com.rcmiku.ncmapi.model.RecentListResponse
import com.rcmiku.ncmapi.model.RecentPlaylistResource
import com.rcmiku.ncmapi.model.RecentSongResource
import com.rcmiku.ncmapi.model.Song
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 最近听歌列表。
 *
 * GET /recent/listen/list —— 实际映射到 NCM weapi 的 /api/pc/recent/listen/list，
 * 原样透传响应。响应里歌曲数组可能落在 data.list / data.songs / list / songs /
 * result 等位置、每项可能是 Song 本身或包在 song / songInfo / simpleSong 字段里，
 * 具体形状未确认，故这里用 JsonElement 兜底抽取，命中任一处即返回；拿到真实
 * 样本后可收紧为强类型 model（仿照 Models.kt 里的 ArtistSongsResponse 写法）。
 */
object RecentListenApi {

    /** 返回最近听歌列表（歌曲数组）。未登录或无记录时返回空列表而非失败。 */
    suspend fun listenList(): Result<List<Song>> {
        val root = apiGet<JsonElement>("/recent/listen/list").getOrThrow()
        return Result.success(extractSongs(root))
    }

    // ── 最近播放（/record/recent/*）──

    /** 最近播放歌曲（data.list 中每项 data 为完整 Song）。 */
    suspend fun recentSongs(limit: Int = 50): Result<RecentListResponse<RecentSongResource>> =
        apiGet("/record/recent/song", mapOf("limit" to limit))

    /** 最近播放歌单。 */
    suspend fun recentPlaylists(limit: Int = 50): Result<RecentListResponse<RecentPlaylistResource>> =
        apiGet("/record/recent/playlist", mapOf("limit" to limit))

    /** 最近播放专辑。 */
    suspend fun recentAlbums(limit: Int = 50): Result<RecentListResponse<RecentAlbumResource>> =
        apiGet("/record/recent/album", mapOf("limit" to limit))

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun extractSongs(root: JsonElement): List<Song> {
        val rootObj = root as? JsonObject ?: return emptyList()
        val dataEl = rootObj["data"]
        val dataObj = dataEl as? JsonObject
        // 候选容器按可能性排序；`data` 本身也可能是数组。
        val candidates = listOfNotNull(
            dataEl,
            dataObj?.get("list"),
            dataObj?.get("songs"),
            dataObj?.get("result"),
            rootObj["list"],
            rootObj["songs"],
            rootObj["result"]
        )
        val array = candidates.firstOrNull { it.isSongArray() } ?: return emptyList()
        return array.jsonArray.map { decodeSong(it) }
    }

    private fun JsonElement?.isSongArray(): Boolean {
        val arr = this as? JsonArray ?: return false
        return arr.isNotEmpty() && arr.all { it is JsonObject }
    }

    private fun decodeSong(entry: JsonElement): Song {
        val obj = entry as? JsonObject
        if (obj != null) {
            val inner = obj["song"] ?: obj["songInfo"] ?: obj["simpleSong"]
            val target = if (inner is JsonObject) inner else entry
            return lenientJson.decodeFromJsonElement(target)
        }
        return lenientJson.decodeFromJsonElement(entry)
    }
}
