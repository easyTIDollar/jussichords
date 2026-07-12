package com.rcmiku.ncmapi.api.playlist

import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.model.*

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

    private suspend fun cachedPlaylistDetail(id: Long): Result<PlaylistDetailResponse> {
        playlistDetailCache[id]
            ?.takeIf { System.currentTimeMillis() - it.first < PLAYLIST_DETAIL_CACHE_TTL_MS }
            ?.let { return Result.success(it.second) }

        return apiGet<PlaylistDetailResponse>("/playlist/detail", mapOf("id" to id))
            .onSuccess { playlistDetailCache[id] = System.currentTimeMillis() to it }
    }
}
