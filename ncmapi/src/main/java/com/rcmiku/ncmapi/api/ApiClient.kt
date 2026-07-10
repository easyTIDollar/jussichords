package com.rcmiku.ncmapi.api

import android.util.Log
import com.rcmiku.ncmapi.utils.CookieProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

var API_BASE_URL = "https://ncm-api.prod.gbclstudio.cn"
var UNBLOCK_BASE_URL = "https://unlock.depresskid.top"
var UNBLOCK_SOURCE = "AUTO"

val apiClient = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        })
    }
    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) {
                Log.d("KtorClient", message)
            }
        }
        level = LogLevel.ALL
    }
    defaultRequest {
        header(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 10; Mi A3 Build/QQ3A.200705.002; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/143.0.7499.34 Mobile Safari/537.36 NeteaseMusic/9.4.32.251222163637"
        )
        header("Accept", "application/json")
        header("Cache-Control", "no-cache, no-store, max-age=0")
        header("Pragma", "no-cache")
        val cookie = CookieProvider.cookie
        if (cookie.isNotEmpty()) {
            header("Cookie", cookie)
        }
    }
}

suspend inline fun <reified T> apiGet(path: String, params: Map<String, Any> = emptyMap()): Result<T> {
    return try {
        val response = apiClient.request("$API_BASE_URL$path") {
            method = HttpMethod.Get
            val finalParams = params.toMutableMap().apply {
                put("timestamp", System.currentTimeMillis())
                putIfAbsent("randomCNIP", true)
            }
            finalParams.forEach { (key, value) ->
                parameter(key, value)
            }
        }
        val body = response.bodyAsText()
        if (response.status.isSuccess()) {
            val result = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }.decodeFromString<T>(body)
            Result.success(result)
        } else {
            Result.failure(Exception("API error: ${response.status}: $body"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

suspend inline fun <reified T> apiPost(path: String, body: Map<String, Any> = emptyMap()): Result<T> {
    return try {
        val response = apiClient.request("$API_BASE_URL$path") {
            method = HttpMethod.Post
            contentType(ContentType.Application.FormUrlEncoded)
            parameter("timestamp", System.currentTimeMillis())
            parameter("_", System.nanoTime())
            parameter("randomCNIP", true)
            val finalBody = body.toMutableMap().apply {
                val csrf = CookieProvider.getCookieMap()["__csrf"]
                if (!csrf.isNullOrEmpty()) {
                    put("csrf_token", csrf)
                }
            }
            setBody(
                finalBody.map { (key, value) ->
                    "${key.encodeURLParameter()}=${value.toString().encodeURLParameter()}"
                }.joinToString("&")
            )
        }
        val responseBody = response.bodyAsText()
        if (response.status.isSuccess()) {
            val result = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }.decodeFromString<T>(responseBody)
            Result.success(result)
        } else {
            Result.failure(Exception("API error: ${response.status}: $responseBody"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
