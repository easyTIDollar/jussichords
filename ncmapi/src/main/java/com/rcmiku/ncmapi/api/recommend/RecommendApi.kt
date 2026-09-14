package com.rcmiku.ncmapi.api.recommend

import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.model.*

object RecommendApi {
    suspend fun recommendSongs(): Result<DailySongsResponse> =
        apiGet("/recommend/songs")

    suspend fun personalFm(mode: String? = null, submode: String? = null): Result<PersonalFmResponse> =
        if (mode == null) {
            apiGet("/personal_fm", mapOf("timestamp" to System.currentTimeMillis()))
        } else {
            apiGet(
                "/personal/fm/mode",
                buildMap {
                    put("mode", mode)
                    submode?.let { put("submode", it) }
                    put("timestamp", System.currentTimeMillis())
                }
            )
        }

    suspend fun recommendPlaylist(): Result<RecommendPlaylistResponse> {
        // getOrNull: a dead/misconfigured API server surfaces as Result.failure
        // instead of throwing getOrThrow() into the caller's coroutine scope.
        val raw = apiGet<RecommendRawResponse>(
            "/personalized",
            mapOf("limit" to 10)
        ).getOrNull()
            ?: return Result.failure(Exception("API 服务器不可用"))
        return Result.success(
            RecommendPlaylistResponse(
                result = raw.result,
                recommend = raw.result
            )
        )
    }

    suspend fun personalizedPlaylist(): Result<PersonalizedPlaylistResponse> =
        apiGet("/personalized", mapOf("limit" to 10))

    suspend fun newAlbum(): Result<NewAlbumResponse> =
        apiGet("/album/new", mapOf("limit" to 10))

    @kotlinx.serialization.Serializable
    data class RecommendRawResponse(
        val result: List<Playlist> = emptyList()
    )
}
