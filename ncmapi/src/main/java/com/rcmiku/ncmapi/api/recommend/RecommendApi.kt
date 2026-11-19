package com.rcmiku.ncmapi.api.recommend

import android.util.Log
import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.api.requestNetease
import com.rcmiku.ncmapi.model.*
import com.rcmiku.ncmapi.utils.json

object RecommendApi {
    private const val TAG = "NcmIntelligence"

    suspend fun recommendSongs(): Result<DailySongsResponse> =
        apiGet("/recommend/songs")

    suspend fun intelligenceSongs(
        songId: Long,
        playlistId: Long,
        count: Int = 20
    ): Result<IntelligenceSongsResponse> {
        val params = mapOf("id" to songId, "pid" to playlistId, "sid" to songId, "count" to count)
        // 心动模式在部分账号/参数组合下会返回 500，原始请求与响应体只在 logcat 可见，
        // 方便真机排查（失败提示里也会带一段响应原文）。
        val raw = runCatching { requestNetease("/playmode/intelligence/list", params) }
            .getOrElse { error ->
                Log.w(TAG, "request failed params=$params error=${error.message}")
                return Result.failure(error)
            }
        Log.i(TAG, "params=$params resp=${raw.take(400)}")

        val response = runCatching {
            json.decodeFromString<IntelligenceSongsResponse>(raw)
        }.getOrElse {
            return Result.failure(IllegalStateException("心动模式响应解析失败：${raw.take(200)}"))
        }

        if (response.codeValue != 200) {
            val detail = response.message.takeIf { it.isNotBlank() }?.let { "，$it" }.orEmpty()
            return Result.failure(
                IllegalStateException(
                    "心动模式请求失败（错误码：${response.codeValue}$detail）参数=$params 响应=${raw.take(160)}"
                )
            )
        }
        return Result.success(response)
    }

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

    /**
     * 私人 FM「不喜欢」：把当前曲加进垃圾桶（weapi /api/radio/trash/add）。
     * [playedSeconds] 传实际已播秒数；0 秒（刚切就点）回落到上游缺省 25。
     * 失败不影响 UI（调用方 fire-and-forget）。
     */
    suspend fun trashPersonalFm(songId: Long, playedSeconds: Long = 0L): Result<PersonalFmTrashResponse> =
        apiGet(
            "/fm/trash",
            mapOf(
                "id" to songId,
                "time" to (playedSeconds.takeIf { it > 0L } ?: 25L)
            )
        )

    @kotlinx.serialization.Serializable
    data class PersonalFmTrashResponse(
        val code: Int = 0,
        val message: String = ""
    )

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
