package com.rcmiku.ncmapi.api

import com.rcmiku.ncmapi.utils.CookieProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
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
    defaultRequest {
        header("User-Agent", "JetMelo/1.0")
        header("Accept", "application/json")
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
            params.forEach { (key, value) ->
                parameter(key, value)
            }
        }
        if (response.status.isSuccess()) {
            val body = response.bodyAsText()
            val result = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }.decodeFromString<T>(body)
            Result.success(result)
        } else {
            Result.failure(Exception("API error: ${response.status}"))
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
            body.forEach { (key, value) ->
                // form parameters
            }
            setBody(body.map { "${it.key}=${it.value}" }.joinToString("&"))
        }
        if (response.status.isSuccess()) {
            val responseBody = response.bodyAsText()
            val result = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }.decodeFromString<T>(responseBody)
            Result.success(result)
        } else {
            Result.failure(Exception("API error: ${response.status}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
