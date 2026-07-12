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
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.encodeURLParameter
import io.ktor.serialization.kotlinx.json.json
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType

var API_BASE_URL = "http://119.23.64.141:3000"
var UNBLOCK_SOURCE = "AUTO"
@PublishedApi
internal val okHttpUploadClient = OkHttpClient()

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

suspend inline fun <reified T> apiPostFile(
    path: String,
    fieldName: String,
    file: File,
    contentType: ContentType = ContentType.Image.Any,
    params: Map<String, Any> = emptyMap(),
    includeCommonParams: Boolean = true,
    includeCsrfToken: Boolean = includeCommonParams
): Result<T> {
    return try {
        val response = apiClient.request("$API_BASE_URL$path") {
            method = HttpMethod.Post
            if (includeCommonParams) {
                parameter("timestamp", System.currentTimeMillis())
                parameter("_", System.nanoTime())
                parameter("randomCNIP", true)
            }
            params.forEach { (key, value) -> parameter(key, value) }
            setBody(
                MultiPartFormDataContent(
                    formData {
                        val csrf = CookieProvider.getCookieMap()["__csrf"]
                        if (includeCsrfToken && !csrf.isNullOrEmpty()) {
                            append("csrf_token", csrf)
                        }
                        append(
                            fieldName,
                            file.readBytes(),
                            Headers.build {
                                append(
                                    HttpHeaders.ContentDisposition,
                                    "form-data; name=\"$fieldName\"; filename=\"${file.name}\""
                                )
                                append(HttpHeaders.ContentType, contentType.toString())
                            }
                        )
                    }
                )
            )
        }
        val responseBody = response.bodyAsText()
        Log.d(
            "ApiClient",
            "apiPostFile raw path=$path status=${response.status} headers=${response.headers} body=$responseBody"
        )
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
        Log.e("ApiClient", "apiPostFile failed path=$path file=${file.name}", e)
        Result.failure(e)
    }
}

suspend inline fun <reified T> apiPostFileOkHttp(
    path: String,
    fieldName: String,
    file: File,
    contentType: ContentType = ContentType.Image.Any,
    params: Map<String, Any> = emptyMap(),
    includeCommonParams: Boolean = true,
    includeCsrfToken: Boolean = includeCommonParams
): Result<T> = withContext(Dispatchers.IO) {
    try {
        val urlBuilder = "$API_BASE_URL$path".toHttpUrl().newBuilder()
        if (includeCommonParams) {
            urlBuilder.addQueryParameter("timestamp", System.currentTimeMillis().toString())
            urlBuilder.addQueryParameter("_", System.nanoTime().toString())
            urlBuilder.addQueryParameter("randomCNIP", "true")
        }
        params.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value.toString()) }

        val multipartBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        val csrf = CookieProvider.getCookieMap()["__csrf"]
        if (includeCsrfToken && !csrf.isNullOrEmpty()) {
            multipartBuilder.addFormDataPart("csrf_token", csrf)
        }
        multipartBuilder.addFormDataPart(
            fieldName,
            file.name,
            file.asRequestBody(contentType.toString().toMediaType())
        )

        val requestBuilder = Request.Builder()
            .url(urlBuilder.build())
            .post(multipartBuilder.build())
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10; Mi A3 Build/QQ3A.200705.002; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/143.0.7499.34 Mobile Safari/537.36 NeteaseMusic/9.4.32.251222163637"
            )
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache, no-store, max-age=0")
            .header("Pragma", "no-cache")
            .cacheControl(CacheControl.FORCE_NETWORK)

        CookieProvider.cookie.takeIf { it.isNotEmpty() }?.let { cookie ->
            requestBuilder.header("Cookie", cookie)
        }

        val request = requestBuilder.build()
        val response = okHttpUploadClient.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()
        Log.d(
            "ApiClient",
            "apiPostFileOkHttp raw path=$path url=${request.url} status=${response.code} headers=${response.headers} body=$responseBody file=${file.name} size=${file.length()}"
        )
        if (response.isSuccessful) {
            val result = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }.decodeFromString<T>(responseBody)
            Result.success(result)
        } else {
            Result.failure(Exception("API error: ${response.code}: $responseBody"))
        }
    } catch (e: Exception) {
        Log.e("ApiClient", "apiPostFileOkHttp failed path=$path file=${file.name}", e)
        Result.failure(e)
    }
}
