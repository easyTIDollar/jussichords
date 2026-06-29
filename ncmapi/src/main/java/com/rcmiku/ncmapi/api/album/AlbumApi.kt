package com.rcmiku.ncmapi.api.album

import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.model.*

object AlbumApi {
    suspend fun albumDetail(albumId: Long): Result<AlbumDetailResponse> =
        apiGet("/album", mapOf("id" to albumId))

    suspend fun albumInfo(albumId: Long): Result<AlbumInfoResponse> =
        apiGet("/album/detail/dynamic", mapOf("id" to albumId))

    suspend fun albumSub(id: Long, isSub: Boolean): Result<ApiCodeResponse> {
        val t = if (isSub) 1 else 0
        return apiGet("/album/sub", mapOf("id" to id, "t" to t))
    }
}
