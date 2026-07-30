package com.rcmiku.ncmapi.api.playlist

import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.api.apiPostFileOkHttp
import com.rcmiku.ncmapi.model.*
import io.ktor.http.ContentType
import java.io.File

object PlaylistApi {
    private const val PLAYLIST_DETAIL_CACHE_TTL_MS = 5 * 60 * 1000L
    private const val PLAYLIST_INFO_CACHE_TTL_MS = 60 * 1000L
    private val playlistDetailCache = mutableMapOf<Long, Pair<Long, PlaylistDetailResponse>>()
    private val playlistInfoCache = mutableMapOf<Long, Pair<Long, PlaylistInfoResponse>>()

    suspend fun playlistDetail(id: Long, limit: Int): Result<PlaylistDetailResponse> =
        cachedPlaylistDetail(id)

    suspend fun playlistV6Detail(id: Long): Result<PlaylistDetailResponse> =
        cachedPlaylistDetail(id)

    suspend fun playlistInfo(id: Long): Result<PlaylistInfoResponse> =
        playlistInfoCache[id]
            ?.takeIf { System.currentTimeMillis() - it.first < PLAYLIST_INFO_CACHE_TTL_MS }
            ?.let { Result.success(it.second) }
            ?: apiGet<PlaylistInfoResponse>("/playlist/detail/dynamic", mapOf("id" to id))
                .onSuccess { playlistInfoCache[id] = System.currentTimeMillis() to it }

    suspend fun topList(): Result<TopListResponse> =
        apiGet("/toplist")

    suspend fun playlistSub(id: Long, isSub: Boolean): Result<ApiCodeResponse> {
        val t = if (isSub) 1 else 0
        return apiGet<ApiCodeResponse>("/playlist/subscribe", mapOf("id" to id, "t" to t))
            .onSuccess { playlistInfoCache.remove(id) }
    }

    suspend fun createPlaylist(
        name: String,
        privacy: String? = null,
        type: String? = null
    ): Result<ApiCodeResponse> =
        apiGet(
            "/playlist/create",
            buildMap {
                put("name", name)
                privacy?.let { put("privacy", it) }
                type?.let { put("type", it) }
            }
        )

    suspend fun deletePlaylist(id: Long): Result<ApiCodeResponse> =
        apiGet("/playlist/delete", mapOf("id" to id))

    suspend fun updatePlaylistName(id: Long, name: String): Result<ApiCodeResponse> =
        apiGet<ApiCodeResponse>("/playlist/name/update", mapOf("id" to id, "name" to name))
            .onSuccess { clearPlaylistCaches(id) }

    suspend fun updatePlaylistDescription(id: Long, description: String): Result<ApiCodeResponse> =
        apiGet<ApiCodeResponse>("/playlist/desc/update", mapOf("id" to id, "desc" to description))
            .onSuccess { clearPlaylistCaches(id) }

    suspend fun updatePlaylistCover(
        id: Long,
        file: File,
        imgSize: Int = 300,
        imgX: Int = 0,
        imgY: Int = 0
    ): Result<ApiCodeResponse> =
        apiPostFileOkHttp<ApiCodeResponse>(
            "/playlist/cover/update",
            "imgFile",
            file,
            contentType = ContentType.Image.JPEG,
            params = mapOf(
                "id" to id,
                "imgSize" to imgSize,
                "imgX" to imgX,
                "imgY" to imgY
            ),
            includeCommonParams = true
        ).onSuccess { clearPlaylistCaches(id) }

    suspend fun updatePlaylistOrder(ids: List<Long>): Result<ApiCodeResponse> =
        apiGet<ApiCodeResponse>("/playlist/order/update", mapOf("ids" to ids.joinToString(prefix = "[", postfix = "]")))

    suspend fun updateSongOrder(playlistId: Long, ids: List<Long>): Result<ApiCodeResponse> =
        apiGet<ApiCodeResponse>(
            "/song/order/update",
            mapOf(
                "pid" to playlistId,
                "ids" to ids.joinToString(prefix = "[", postfix = "]")
            )
        ).onSuccess { clearPlaylistCaches(playlistId) }

    fun clearPlaylistCaches(id: Long? = null) {
        if (id == null) {
            playlistDetailCache.clear()
            playlistInfoCache.clear()
        } else {
            playlistDetailCache.remove(id)
            playlistInfoCache.remove(id)
        }
    }

    private suspend fun cachedPlaylistDetail(id: Long): Result<PlaylistDetailResponse> {
        playlistDetailCache[id]
            ?.takeIf { System.currentTimeMillis() - it.first < PLAYLIST_DETAIL_CACHE_TTL_MS }
            ?.let { return Result.success(it.second) }

        return apiGet<PlaylistDetailResponse>("/playlist/detail", mapOf("id" to id))
            .onSuccess { playlistDetailCache[id] = System.currentTimeMillis() to it }
    }
}
