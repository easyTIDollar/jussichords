package com.rcmiku.ncmapi.api.playlist

import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.api.apiPost
import com.rcmiku.ncmapi.api.uploadFileToNos
import com.rcmiku.ncmapi.model.*
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

    /**
     * 歌单封面更新：NOS 直连三步链路（对齐 api-enhanced 代理 plugins/upload.js + playlist_cover_update.js）。
     *  1) uploadFileToNos：eapi /nos/token/alloc 申请预签名 token + 裸 POST 图片字节到 NOS → docId；
     *  2) eapi /playlist/cover/update（coverImgId=docId）落库。
     * 不再依赖代理 API_BASE_URL。imgSize/imgX/imgY 参数保留仅为兼容旧调用方，直连链路 NCM 端点不收。
     */
    suspend fun updatePlaylistCover(
        id: Long,
        file: File,
        imgSize: Int = 300,
        imgX: Int = 0,
        imgY: Int = 0
    ): Result<ApiCodeResponse> =
        runCatching {
            val nos = uploadFileToNos(file).getOrThrow()
            apiPost<ApiCodeResponse>(
                "/playlist/cover/update",
                mapOf("id" to id, "coverImgId" to nos.docId)
            ).getOrThrow()
        }.onSuccess { clearPlaylistCaches(id) }

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
