package com.rcmiku.ncmapi.api.player

import com.rcmiku.ncmapi.api.UNBLOCK_BASE_URL
import com.rcmiku.ncmapi.api.UNBLOCK_SOURCE
import com.rcmiku.ncmapi.api.apiClient
import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.model.LyricResponse
import com.rcmiku.ncmapi.model.SongUrl
import com.rcmiku.ncmapi.model.SongUrlResponse
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object PlayerApi {

    suspend fun songPlayUrlV1(songId: String, songLevel: SongLevel = SongLevel.STANDARD): Result<SongUrlResponse> {
        val parsed = parseSongId(songId)
        val realId = parsed.first
        val fee = parsed.second
        val privilegeLevel = parsed.third
        val shouldTryUnblock = fee != 0 || privilegeLevel <= 0

        return if (shouldTryUnblock) {
            // Restricted song: use unblock service first
            val unblockResult = tryUnblockUrl(realId, songLevel)
            if (unblockResult.isSuccess) {
                val data = unblockResult.getOrNull()
                if (data != null && data.data.isNotEmpty() && data.data.any { !it.url.isNullOrEmpty() }) {
                    return unblockResult
                }
            }
            // Fall back to main API if unblock fails
            apiGet("/song/url/v1", songUrlParams(realId, songLevel, unblock = true))
        } else {
            // Free song: use main API directly
            apiGet("/song/url/v1", songUrlParams(realId, songLevel, unblock = false))
        }
    }

    private fun songUrlParams(songId: String, songLevel: SongLevel, unblock: Boolean): Map<String, Any> =
        mapOf("id" to songId, "level" to songLevel.value, "unblock" to unblock)

    private fun parseSongId(raw: String): Triple<String, Int, Int> {
        val queryIndex = raw.indexOf('?')
        if (queryIndex == -1) return Triple(raw, 0, 0)
        val id = raw.substring(0, queryIndex)
        val query = raw.substring(queryIndex + 1)
        val params = query.split("&").associate {
            val eq = it.indexOf('=')
            if (eq > 0) it.substring(0, eq) to it.substring(eq + 1) else it to ""
        }
        val fee = params["fee"]?.toIntOrNull() ?: 0
        val pl = params["pl"]?.toIntOrNull() ?: 0
        return Triple(id, fee, pl)
    }

    private suspend fun tryUnblockUrl(songId: String, songLevel: SongLevel): Result<SongUrlResponse> {
        return try {
            val response = apiClient.request("$UNBLOCK_BASE_URL/match") {
                method = HttpMethod.Get
                parameter("id", songId)
                if (UNBLOCK_SOURCE != "AUTO") {
                    parameter("source", UNBLOCK_SOURCE)
                }
            }
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                val unblockData = parseUnblockResponse(body)
                if (unblockData != null) {
                    Result.success(SongUrlResponse(data = listOf(
                        SongUrl(id = songId.toLong(), url = unblockData.url, br = unblockData.br)
                    )))
                } else {
                    Result.failure(Exception("No unblock URL found"))
                }
            } else {
                Result.failure(Exception("Unblock service returned ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private data class UnblockData(val url: String, val br: Int)

    private fun parseUnblockResponse(body: String): UnblockData? {
        return try {
            val json = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
            val jsonObj = json.parseToJsonElement(body).jsonObject
            val dataObj = jsonObj["data"]?.jsonObject
            val url = dataObj?.get("url")?.jsonPrimitive?.toString()?.removeSurrounding("\"")
            if (!url.isNullOrEmpty()) UnblockData(url, 320000) else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun songLyric(musicId: Long): Result<LyricResponse> =
        apiGet("/lyric/new", mapOf("id" to musicId))
}
