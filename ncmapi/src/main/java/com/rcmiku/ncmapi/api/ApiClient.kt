package com.rcmiku.ncmapi.api

import android.util.Log
import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.utils.json
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
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
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.CacheControl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType

// NCM 数据面已改为直连官方接口（music.163.com / interfacepc.music.163.com），
// 不再经 ncmapi 代理。API_BASE_URL / UNBLOCK_SOURCE 仍保留给「解灰音源 unblock」与「文件上传」
// 两条仍走代理的通道（见 PlayerApi.tryUnblockUrl、apiPostFile*）。
var API_BASE_URL = "http://8.134.163.111:3000"
var UNBLOCK_SOURCE = "AUTO"
@PublishedApi
internal val okHttpUploadClient = OkHttpClient()

// 匿名（未登录）时给 eapi header 用的稳定 deviceId（每进程一个 32-hex）。
@PublishedApi
internal val anonymousDeviceId: String =
    UUID.randomUUID().toString().replace("-", "")

// NCM 官方域名（对齐 3.0.0）。
private const val NCM_WEB_DOMAIN = "https://music.163.com"
// 对齐代理 config.json 的 eapiDomain（当前生产在用的 eapi 目标域），不是 interface。
private const val NCM_API_DOMAIN = "https://interfacepc.music.163.com"

// 移动端（默认 weapi/eapi）User-Agent。
@PublishedApi
internal const val NCM_MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 10; Mi A3 Build/QQ3A.200705.002; wv) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Version/4.0 Chrome/143.0.7499.34 Mobile Safari/537.36 NeteaseMusic/9.4.32.251222163637"
// eapi 播放历史上报专用 macOS 桌面 UA（对齐 MeiloX playback-history profile）。
internal const val NCM_OSX_UA =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36"
// eapi 播放历史上报专用域（对齐 MeiloX：interface 域 + osx 桌面 profile）。
internal const val NCM_EAPI_HISTORY_DOMAIN = "https://interface.music.163.com"

// 一起听 eapi 设备身份 cookie（逐字对齐 MeiloX NeteaseInterceptor 非播放历史的
// buildCookieString(cookieMap)：os/appver/osver/channel/versioncode/mobilename/buildver/
// resolution + deviceId/sDeviceId + ntes_kaola_ad/_ntes_nuid/WNMCID/URS_APPID/WEVNSM/
// __csrf/NMDI/NMTID + MUSIC_U，共 19 字段；明文值、插入序，不做 URL-encode）。
// 官方客户端指纹常量（MeiloX 抓包值，逐字照抄）：
private const val LT_NMDI =
    "Q1NKTQkBDAAMIEF4coQMHcb6TLA7AAAAciOiJ%2F%2FOO4VQ7m%2FLvLJ1pD9CIsJP5mfzI4SusB%2BaNScGLpThEYBcPxGzj0pL5hLdZ7LqB2UVULdYgc0%3D"
private const val LT_URS_APPID =
    "F2219AE9D7828A7D73E2006D000C61031D196A37DB497E3885B8298504867886B6F0E44087D61EFC06BE92279CD6EEC6"
// MeiloX 一起听 eapi 的 __csrf 为硬编码常量（header 与 cookie 同源），不用登录 cookie 里的。
private const val LT_CSRF = "40ab38f0a305fc4c7ff68e636bcf34aa"

// 会话级缓存（MeiloX 同形：app 生命周期 lazy 一次，会话内保持稳定）。
private fun alnumKey(n: Int): String =
    (1..n).map { (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() }.joinToString("")
private val LT_NUID = alnumKey(32)
private val LT_NMTID = alnumKey(16)
private val LT_WNMCID =
    (1..6).joinToString("") { "abcdefghijklmnopqrstuvwxyz".toCharArray().random().toString() } +
        ".${System.currentTimeMillis()}.01.0"

val apiClient = HttpClient(OkHttp) {
    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) {
                Log.d("KtorClient", message)
            }
        }
        level = LogLevel.INFO
    }
    defaultRequest {
        header("User-Agent", NCM_MOBILE_UA)
        header("Accept", "application/json")
        header("Cache-Control", "no-cache, no-store, max-age=0")
        header("Pragma", "no-cache")
    }
}

/** NCM 数据面入口：按路由表把 jussichords 风格路径映射到官方 weapi/eapi 端点直连。 */
@PublishedApi
internal suspend fun requestNetease(path: String, params: Map<String, Any> = emptyMap()): String {
    if (path == "/api") return sendGenericPassthrough(params)
    val route = resolveRoute(path, params)
    return send(route)
}

suspend inline fun <reified T> apiGet(path: String, params: Map<String, Any> = emptyMap()): Result<T> {
    return runCatching {
        val body = requestNetease(path, params)
        json.decodeFromString<T>(body)
    }
}

suspend inline fun <reified T> apiPost(path: String, body: Map<String, Any> = emptyMap()): Result<T> {
    return apiGet(path, body)
}

// ---------------------------------------------------------------------------
// 路由表：jussichords 代理路径 -> NCM 官方 uri + 加密方式 + data 形状。
// data 形状逐条对齐 api-enhanced 代理 module/*.js 的默认参数。
// ---------------------------------------------------------------------------

internal enum class Encryption { WEAPI, EAPI }

internal data class Route(
    val uri: String,
    val data: Map<String, Any>,
    val encryption: Encryption,
    /**
     * eapi 请求 profile（对齐 MeiloX 的 eapi profile 机制）：
     * 普通请求用 null（Android 移动端参数 + interfacepc 域 + 移动 UA）；
     * 播放历史上报用 [EAPI_PLAYBACK_HISTORY_PROFILE]（osx 桌面参数 + interface 域 + macOS UA，
     * 不带 __csrf、不发 X-Real-IP/X-Forwarded-For 假 IP 头）。
     */
    val eapiProfile: Map<String, Any>? = null
)

/** 播放历史上报 eapi profile（osx 桌面伪装，参数对齐 MeiloX NeteaseInterceptor）。 */
val EAPI_PLAYBACK_HISTORY_PROFILE: Map<String, Any> = mapOf(
    "os" to "osx",
    "osver" to "15.5",
    "appver" to "3.1.10.5100",
    "versioncode" to "140",
    "channel" to "netease",
    "resolution" to "1920x1080",
    "userAgent" to NCM_OSX_UA,
    "domain" to NCM_EAPI_HISTORY_DOMAIN,
)

/**
 * 用户资料修改（jussichords 路由 /user/update）eapi profile。
 * 对齐代理 createHeaderCookie：eapi 写接口按 Cookie 形态做风控——
 * 发完整登录 cookie 会被 NCM 静默 403 "illegal request"，
 * 发「合成 cookie」（eapi header 字段 URL-encode 后按 key 排序）才进入业务层（250 受理）。
 * os/域名保持移动端默认（interfacepc 域、移动 UA）；synthesizedCookie 开关让 send() 对该 profile
 * 发合成 cookie 而非完整登录 cookie（域名走默认 eapi 分支，无需在这里覆盖 domain）。
 */
val EAPI_USER_UPDATE_PROFILE: Map<String, Any> = mapOf(
    "synthesizedCookie" to true,
)

/**
 * 一起听 eapi 桌面 profile（对齐 MeiloX NeteaseInterceptor.EAPI_CONFIG：os=pc 桌面客户端
 * + interface 域 + NeteaseMusicDesktop UA + 合成 cookie）。
 * 一起听共享队列 REPLACE 写（/sync/list/command/report）在移动信封下被 NCM 静默拒
 * （result=false，而 play/command 仍 true）；MeiloX 桌面信封全通。
 * synthesizedCookie=true：Cookie 头与加密 header 同源（12 字段排序），避免
 * 「完整登录 cookie os=android 与 header os=pc 矛盾 → 静默丢弃」的打卡同坑。
 */
val EAPI_LISTEN_TOGETHER_PROFILE: Map<String, Any> = mapOf(
    "os" to "pc",
    "osver" to "Microsoft-Windows-10-Professional-build-22631-64bit",
    "appver" to "3.0.18.203152",
    "channel" to "netease",
    "versioncode" to "6006066",
    "mobilename" to "Mi+A3",
    "resolution" to "2268x1080",
    "userAgent" to "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/3.0.18.203152",
    "domain" to "https://interface.music.163.com",
    // MeiloX 同款 19 字段设备身份 cookie（NMDI/URS_APPID/WNMCID/NMTID/_ntes_nuid 等），
    // 与加密 header 同源（__csrf 同用常量）。风控对共享队列写校验的是这组身份字段。
    "ltDeviceCookie" to true,
)

@PublishedApi
internal fun resolveRoute(path: String, p: Map<String, Any>): Route {
    fun req(key: String): Any = requireNotNull(p[key]) { "Missing $key for $path" }
    fun long(key: String, def: Long): Long =
        (p[key] as? Number)?.toLong() ?: (p[key] as? String)?.toLongOrNull() ?: def
    fun int(key: String, def: Int): Int =
        (p[key] as? Number)?.toInt() ?: (p[key] as? String)?.toIntOrNull() ?: def
    fun str(key: String): String = p[key]?.toString().orEmpty()

    fun weapi(uri: String, data: Map<String, Any> = emptyMap()) = Route(uri, data, Encryption.WEAPI)
    fun eapi(uri: String, data: Map<String, Any> = emptyMap()) = Route(uri, data, Encryption.EAPI)
    // 播放历史上报专用：eapi + osx 桌面 profile（interface 域 + macOS UA + 空 csrf）。
    fun eapiHistory(uri: String, data: Map<String, Any> = emptyMap()) =
        Route(uri, data, Encryption.EAPI, eapiProfile = EAPI_PLAYBACK_HISTORY_PROFILE)
    // 用户资料修改：eapi + 合成 cookie profile（移动端参数，interfacepc 域）。
    fun eapiUserUpdate(uri: String, data: Map<String, Any> = emptyMap()) =
        Route(uri, data, Encryption.EAPI, eapiProfile = EAPI_USER_UPDATE_PROFILE)
    // 一起听：eapi + 桌面 pc 信封（对齐 MeiloX EAPI_CONFIG：interface 域 + 桌面 UA + 合成 cookie）。
    fun eapiListenTogether(uri: String, data: Map<String, Any> = emptyMap()) =
        Route(uri, data, Encryption.EAPI, eapiProfile = EAPI_LISTEN_TOGETHER_PROFILE)

    val commentTypePrefix = when (int("type", 0)) {
        0 -> "R_SO_4_"
        1 -> "R_MV_5_"
        2 -> "A_PL_0_"
        3 -> "R_AL_3_"
        4 -> "A_DJ_1_"
        5 -> "R_VI_62_"
        6 -> "A_EV_2_"
        7 -> "A_DR_14_"
        else -> "R_SO_4_"
    }

    return when (path) {
        // 专辑
        "/album" -> weapi("/api/v1/album/${req("id")}", emptyMap())
        "/album/detail/dynamic" -> weapi("/api/album/detail/dynamic", mapOf("id" to req("id")))
        "/album/new" -> weapi(
            "/api/album/new",
            mapOf(
                "limit" to int("limit", 30),
                "offset" to long("offset", 0),
                "total" to true,
                "area" to (str("area").ifBlank { "ALL" })
            )
        )
        "/album/sub" -> {
            val sub = (int("t", 0) == 1)
            weapi("/api/album/${if (sub) "sub" else "unsub"}", mapOf("id" to req("id")))
        }
        "/album/sublist" -> weapi(
            "/api/album/sublist",
            mapOf(
                "limit" to int("limit", 25),
                "offset" to long("offset", 0),
                "total" to true
            )
        )
        // 歌手
        "/artist/album" -> weapi(
            "/api/artist/albums/${req("id")}",
            mapOf(
                "limit" to int("limit", 30),
                "offset" to long("offset", 0),
                "total" to true
            )
        )
        "/artist/detail" -> eapi("/api/artist/head/info/get", mapOf("id" to req("id")))
        "/artist/follow/count" -> eapi("/api/artist/follow/count/get", mapOf("id" to req("id")))
        "/artist/top/song" -> weapi("/api/artist/top/song", mapOf("id" to req("id")))
        "/artist/songs" -> eapi(
            "/api/v1/artist/songs",
            mapOf(
                "id" to req("id"),
                "private_cloud" to "true",
                "work_type" to 1,
                "order" to (str("order").ifBlank { "hot" }),
                "offset" to long("offset", 0),
                "limit" to int("limit", 100)
            )
        )
        "/artist/sub" -> {
            val sub = (int("t", 0) == 1)
            weapi(
                "/api/artist/${if (sub) "sub" else "unsub"}",
                mapOf("artistId" to req("id"), "artistIds" to ("[" + str("id") + "]"))
            )
        }
        "/artist/sublist" -> weapi(
            "/api/artist/sublist",
            mapOf(
                "limit" to int("limit", 25),
                "offset" to long("offset", 0),
                "total" to true
            )
        )
        "/simi/artist" -> weapi("/api/discovery/simiArtist", mapOf("artistid" to req("id")))
        // 搜索
        "/cloudsearch" -> eapi(
            "/api/cloudsearch/pc",
            mapOf(
                "s" to req("keywords"),
                "type" to int("type", 1),
                "limit" to int("limit", 30),
                "offset" to long("offset", 0),
                "total" to true
            )
        )
        "/search/suggest" -> weapi("/api/search/suggest/web", mapOf("s" to req("keywords")))
        // 电台
        "/dj/detail" -> weapi("/api/djradio/v2/get", mapOf("id" to req("rid")))
        "/dj/program" -> weapi(
            "/api/dj/program/byradio",
            mapOf(
                "radioId" to req("rid"),
                "limit" to int("limit", 30),
                "offset" to long("offset", 0),
                "asc" to (str("asc").toBoolean())
            )
        )
        // 歌词 / 歌曲链接
        "/lyric/new" -> eapi(
            "/api/song/lyric/v1",
            mapOf(
                "id" to req("id"),
                "cp" to false,
                "tv" to 0, "lv" to 0, "rv" to 0, "kv" to 0, "yv" to 0, "ytv" to 0, "yrv" to 0
            )
        )
        "/song/url/v1" -> eapi(
            "/api/song/enhance/player/url/v1",
            mapOf(
                "ids" to ("[" + str("id") + "]"),
                "level" to (str("level").ifBlank { "standard" }),
                "encodeType" to "flac"
            )
        )
        // 排行榜
        "/toplist" -> eapi("/api/toplist", emptyMap())
        // 账号 / 云盘 / 收藏
        "/user/account" -> weapi("/api/nuser/account/get", emptyMap())
        // 用户资料修改（我的页点昵称）：eapi 写接口，需合成 cookie profile（见 send 的 cookie 分支）。
        // 字段对齐代理 user_update.js：只透传调用方给的字段。本功能只改昵称/签名，
        // 故只发 nickname + signature —— gender/birthday/province/city 不带，NCM 视作不动，避免误清账号地区/生日。
        "/user/update" -> eapiUserUpdate(
            "/api/user/profile/update",
            mapOf(
                "nickname" to req("nickname"),
                "signature" to str("signature")
            )
        )
        "/user/cloud" -> weapi(
            "/api/v1/cloud/get",
            mapOf("limit" to int("limit", 30), "offset" to long("offset", 0))
        )
        "/user/detail" -> weapi("/api/v1/user/detail/${req("uid")}", emptyMap())
        "/user/follows" -> weapi(
            "/api/user/getfollows/${req("uid")}",
            mapOf(
                "offset" to long("offset", 0),
                "limit" to int("limit", 30),
                "order" to true
            )
        )
        "/user/record" -> weapi(
            "/api/v1/play/record",
            mapOf("uid" to req("uid"), "type" to int("type", 0))
        )
        "/user/playlist" -> weapi(
            "/api/user/playlist",
            mapOf(
                "uid" to req("uid"),
                "limit" to int("limit", 30),
                "offset" to long("offset", 0),
                "includeVideo" to true
            )
        )
        "/likelist" -> {
            val uid = p["uid"]
            eapi(
                "/api/song/like/get",
                if (uid == null) emptyMap() else mapOf("uid" to long("uid", 0))
            )
        }
        "/follow" -> {
            val follow = (int("t", 0) == 1)
            weapi("/api/user/${if (follow) "follow" else "delfollow"}/${req("id")}", emptyMap())
        }
        // 歌曲点赞
        "/song/like" -> {
            val data = mutableMapOf<String, Any>(
                "trackId" to req("id"),
                "like" to str("like").toBoolean()
            )
            if (p["uid"] != null) data["userid"] = long("uid", 0)
            eapi("/api/song/like", data)
        }
        "/song/like/check" -> eapi("/api/song/like/check", mapOf("trackIds" to req("ids")))
        // 歌单
        "/playlist/detail" -> eapi(
            "/api/v6/playlist/detail",
            mapOf("id" to req("id"), "n" to 100000, "s" to int("s", 8))
        )
        "/playlist/detail/dynamic" -> eapi(
            "/api/playlist/detail/dynamic",
            mapOf("id" to req("id"), "n" to 100000, "s" to int("s", 8))
        )
        "/playlist/subscribe" -> {
            val sub = (int("t", 0) == 1)
            eapi("/api/playlist/${if (sub) "subscribe" else "unsubscribe"}", mapOf("id" to req("id")))
        }
        "/playlist/create" -> weapi(
            "/api/playlist/create",
            mapOf(
                "name" to req("name"),
                "privacy" to (str("privacy").ifBlank { "0" }),
                "type" to (str("type").ifBlank { "NORMAL" })
            )
        )
        "/playlist/delete" -> weapi("/api/playlist/remove", mapOf("ids" to ("[" + str("id") + "]")))
        "/playlist/name/update" -> eapi(
            "/api/playlist/update/name",
            mapOf("id" to req("id"), "name" to req("name"))
        )
        "/playlist/desc/update" -> eapi(
            "/api/playlist/desc/update",
            mapOf("id" to req("id"), "desc" to str("desc"))
        )
        "/playlist/order/update" -> weapi("/api/playlist/order/update", mapOf("ids" to req("ids")))
        "/song/order/update" -> eapi(
            "/api/playlist/manipulate/tracks",
            mapOf("pid" to req("pid"), "trackIds" to str("ids"), "op" to "update")
        )
        "/playlist/tracks" -> {
            val ids = str("tracks").split(',').filter { it.isNotBlank() }
            val trackIds = if (ids.isEmpty()) "" else "[" + ids.joinToString(",") + "]"
            eapi(
                "/api/playlist/manipulate/tracks",
                mapOf(
                    "op" to (str("op").ifBlank { "add" }),
                    "pid" to req("pid"),
                    "trackIds" to trackIds,
                    "imme" to "true"
                )
            )
        }
        // 推荐
        "/recommend/songs" -> {
            val data = mutableMapOf<String, Any>()
            if (p["afresh"] != null) data["afresh"] = str("afresh").toBoolean()
            weapi("/api/v3/discovery/recommend/songs", data)
        }
        "/personalized" -> weapi(
            "/api/personalized/playlist",
            mapOf(
                "limit" to int("limit", 30),
                "total" to true,
                "n" to 1000
            )
        )
        "/personal_fm" -> weapi("/api/v1/radio/get", emptyMap())
        "/personal/fm/mode" -> {
            val data = mutableMapOf<String, Any>(
                "mode" to req("mode"),
                "limit" to int("limit", 3)
            )
            if (p["submode"] != null) data["subMode"] = str("submode")
            eapi("/api/v1/radio/get", data)
        }
        // 听歌记录（读端点）
        "/record/recent/song" -> weapi(
            "/api/play-record/song/list",
            mapOf("limit" to int("limit", 100))
        )
        "/record/recent/album" -> weapi(
            "/api/play-record/album/list",
            mapOf("limit" to int("limit", 100))
        )
        "/record/recent/playlist" -> weapi(
            "/api/play-record/playlist/list",
            mapOf("limit" to int("limit", 100))
        )
        // 听歌打卡（写端点）：eapi feedback/weblog，osx 桌面 profile。
        // 调用方一次性提交 startplay + play 两条日志（对齐 MeiloX 双事件模型）。
        "/scrobble/weblog" -> eapiHistory(
            "/api/feedback/weblog",
            mapOf("logs" to str("logs"))
        )
        // 评论
        "/comment/new" -> {
            val id = req("id")
            var sortType = int("sortType", 99)
            if (sortType == 1) sortType = 99
            val pageNo = int("pageNo", 1)
            val pageSize = int("pageSize", 20)
            val cursor = when (sortType) {
                99 -> ((pageNo - 1) * pageSize).toString()
                2 -> "normalHot#" + ((pageNo - 1) * pageSize)
                3 -> str("cursor").ifBlank { "0" }
                else -> ""
            }
            val threadId = if (str("type") == "6") str("threadId") else commentTypePrefix + id
            eapi(
                "/api/v2/resource/comments",
                mapOf(
                    "threadId" to threadId,
                    "pageNo" to pageNo,
                    "showInner" to true,
                    "pageSize" to pageSize,
                    "cursor" to cursor,
                    "sortType" to sortType
                )
            )
        }
        "/comment/like" -> {
            val id = req("id")
            val like = (int("t", 0) == 1)
            val threadId = if (str("type") == "6") str("threadId") else commentTypePrefix + id
            weapi(
                "/api/v1/comment/${if (like) "like" else "unlike"}",
                mapOf("threadId" to threadId, "commentId" to req("cid"))
            )
        }
        // 消息中心
        "/msg/comments" -> weapi(
            "/api/v1/user/comments/${req("uid")}",
            mapOf(
                "beforeTime" to (str("before").ifBlank { "-1" }),
                "limit" to int("limit", 30),
                "total" to "true",
                "uid" to req("uid")
            )
        )
        "/msg/forwards" -> weapi(
            "/api/forwards/get",
            mapOf("offset" to long("offset", 0), "limit" to int("limit", 30), "total" to "true")
        )
        "/msg/notices" -> weapi(
            "/api/msg/notices",
            mapOf("limit" to int("limit", 30), "time" to long("lasttime", -1))
        )
        "/msg/private" -> weapi(
            "/api/msg/private/users",
            mapOf("offset" to long("offset", 0), "limit" to int("limit", 30), "total" to "true")
        )
        "/msg/private/history" -> weapi(
            "/api/msg/private/history",
            mapOf(
                "userId" to req("uid"),
                "limit" to int("limit", 30),
                "time" to long("before", 0),
                "total" to "true"
            )
        )
        "/msg/recentcontact" -> weapi("/api/msg/recentcontact/get", emptyMap())
        "/send/song" -> eapi(
            "/api/msg/private/send",
            mapOf(
                "id" to req("id"),
                "msg" to str("msg"),
                "type" to "song",
                "userIds" to ("[" + str("user_ids") + "]")
            )
        )
        "/send/album" -> eapi(
            "/api/msg/private/send",
            mapOf(
                "id" to req("id"),
                "msg" to str("msg"),
                "type" to "album",
                "userIds" to ("[" + str("user_ids") + "]")
            )
        )
        "/send/playlist" -> eapi(
            "/api/msg/private/send",
            mapOf(
                "id" to req("playlist"),
                "type" to "playlist",
                "msg" to str("msg"),
                "userIds" to ("[" + str("user_ids") + "]")
            )
        )
        "/send/text" -> eapi(
            "/api/msg/private/send",
            mapOf(
                "type" to "text",
                "msg" to str("msg"),
                "userIds" to ("[" + str("user_ids") + "]")
            )
        )
        // 一起听（listen together）：状态读走 weapi，房间写 / 同步 / 心跳走 eapi（对齐 MeiloX 通道选择）。
        // 上游路径逐条对齐 api-enhanced 代理 module/listentogether_*.js（heartbeat 拼写为 NCM 原样 /heartbeat）。
        "/listen/together/status" -> weapi("/api/listen/together/status/get", emptyMap())
        "/listen/together/room/create" -> eapiListenTogether(
            "/api/listen/together/room/create",
            mapOf("refer" to str("refer").ifBlank { "songplay_more" })
        )
        "/listen/together/room/check" -> eapiListenTogether(
            "/api/listen/together/room/check",
            mapOf("roomId" to req("roomId"))
        )
        "/listen/together/invitation/accept" -> eapiListenTogether(
            "/api/listen/together/play/invitation/accept",
            mapOf(
                "refer" to str("refer").ifBlank { "inbox_invite" },
                "roomId" to req("roomId"),
                "inviterId" to req("inviterId")
            )
        )
        "/listen/together/play/command" -> eapiListenTogether(
            "/api/listen/together/play/command/report",
            mapOf("roomId" to req("roomId"), "commandInfo" to req("commandInfo"))
        )
        "/listen/together/sync/playlist" -> eapiListenTogether(
            "/api/listen/together/sync/playlist/get",
            mapOf("roomId" to req("roomId"))
        )
        "/listen/together/sync/list" -> eapiListenTogether(
            "/api/listen/together/sync/list/command/report",
            mapOf("roomId" to req("roomId"), "playlistParam" to req("playlistParam"))
        )
        "/listen/together/heartbeat" -> eapiListenTogether(
            "/api/listen/together/heartbeat",
            mapOf(
                "roomId" to req("roomId"),
                "songId" to req("songId"),
                "playStatus" to req("playStatus"),
                "progress" to req("progress")
            )
        )
        "/listen/together/end" -> eapiListenTogether(
            "/api/listen/together/end/v2",
            mapOf("roomId" to req("roomId"))
        )
        // 一起听远端队列重建：歌曲批量详情（NCM /v3/song/detail，c 为 [{id:...}] JSON 字符串）
        "/song/detail/batch" -> weapi(
            "/api/v3/song/detail",
            mapOf("c" to str("c"))
        )
        else -> error("Unsupported NetEase route: $path")
    }
}

/** 通用 /api 透传（现仅用于 /user/getfolloweds）：uri 直传、data 为 JSON 字符串，走 eapi。 */
@PublishedApi
internal suspend fun sendGenericPassthrough(p: Map<String, Any>): String {
    val uri = p["uri"]?.toString() ?: error("Generic /api passthrough missing uri")
    val dataStr = p["data"]?.toString()?.ifBlank { "{}" } ?: "{}"
    val element = runCatching { json.parseToJsonElement(dataStr) }.getOrNull()
    val data = (element as? JsonObject)?.mapValues { it.value } ?: emptyMap<String, JsonElement>()
    val finalData = data.entries.associate { it.key to (it.value as Any) }
    return send(Route(uri, finalData, Encryption.EAPI))
}

/** MeiloX encodeCookieComponent：cookie 键值 URL-encode（+→%20，%7E→~）。 */
private fun encodeCookieComponent(value: String): String =
    java.net.URLEncoder.encode(value, "UTF-8")
        .replace("+", "%20")
        .replace("%7E", "~")

@PublishedApi
internal suspend fun send(route: Route): String {
    val cookie = CookieProvider.getCookieMap()
    val csrf = cookie["__csrf"].orEmpty()
    val musicU = cookie["MUSIC_U"]
    val osValue = cookie["os"] ?: "android"
    val profile = route.eapiProfile
    // 仅「播放历史 osx profile」(os=osx) 走 osx 域/UA/空 csrf/去 mobilename。
    // 其余 eapi profile（如用户资料修改）保持移动端默认，只借用「合成 cookie」行为。
    val isHistoryProfile = profile?.get("os")?.toString() == "osx"
    // 一起听 eapi 的 MeiloX 同款 19 字段设备身份 cookie（NMDI/URS_APPID/WNMCID/NMTID/
    // _ntes_nuid/sDeviceId/WEVNSM/ntes_kaola_ad/__csrf=常量，与加密 header 同源）。
    val isLtProfile = profile?.get("ltDeviceCookie") == true
    // 合成 cookie（对齐代理 createHeaderCookie）：播放历史 + 用户资料修改都发它，
    // 完整登录 cookie 只在普通 eapi/weapi 读接口用。eapi 写接口发完整登录 cookie 会被 NCM 403。
    val synthesizedCookie = isHistoryProfile || profile?.get("synthesizedCookie") == true

    // eapi 身份 header（对齐代理 createHeaderCookie 的字段集）
    // 播放历史 profile：osx 桌面参数（对齐 MeiloX），__csrf 置空、buildver 用秒级时间戳。
    val header = buildMap<String, Any> {
        put("osver", profile?.get("osver")?.toString() ?: cookie["osver"] ?: "14")
        put("deviceId", cookie["deviceId"] ?: anonymousDeviceId)
        put("os", profile?.get("os")?.toString() ?: osValue)
        put("appver", profile?.get("appver")?.toString() ?: cookie["appver"] ?: "9.4.32.251222163637")
        put("versioncode", profile?.get("versioncode")?.toString() ?: cookie["versioncode"] ?: "6006066")
        if (!isHistoryProfile) {
            // 一起听桌面 profile 带 mobilename（对齐 MeiloX EAPI_CONFIG）；否则移动端默认。
            put("mobilename", profile?.get("mobilename")?.toString() ?: cookie["mobilename"] ?: "")
        }
        put("buildver", (System.currentTimeMillis() / 1000).toString())
        put("resolution", profile?.get("resolution")?.toString() ?: cookie["resolution"] ?: "2268x1080")
        put("channel", profile?.get("channel")?.toString() ?: cookie["channel"] ?: "xiaomi")
        put("requestId", "${System.currentTimeMillis()}_${(0..9999).random()}")
        put("__csrf", when {
            isHistoryProfile -> ""
            isLtProfile -> LT_CSRF
            else -> csrf
        })
        if (musicU != null) put("MUSIC_U", musicU)
    }

    val finalData = route.data.toMutableMap().apply {
        if (route.encryption == Encryption.EAPI) put("header", header) else put("csrf_token", csrf)
        // 对齐代理：e_r=false 强制明文响应（config.json 的 encryptResponse=false），否则 NCM 可能回加密体。
        put("e_r", false)
    }
    val jsonBody = toJsonString(finalData)
    val fields = when (route.encryption) {
        Encryption.WEAPI -> NeteaseCrypto.weapi(jsonBody)
        Encryption.EAPI -> NeteaseCrypto.eapi(route.uri, jsonBody)
    }

    val rest = route.uri.removePrefix("/api/")
    val targetDomain = when {
        profile?.get("domain") != null -> profile["domain"].toString()
        route.encryption == Encryption.WEAPI -> NCM_WEB_DOMAIN
        else -> NCM_API_DOMAIN
    }
    val url = when (route.encryption) {
        Encryption.WEAPI -> "$targetDomain/weapi/$rest"
        Encryption.EAPI -> "$targetDomain/eapi/$rest"
    }

    // 请求 Cookie：
    // - 普通 eapi/weapi：对齐 3.0.0 发完整登录 cookie（含 MUSIC_U）；未登录时补稳定 deviceId，
    //   保证匿名读接口（toplist / 搜索等）也能握手成功。
    // - 播放历史 + 用户资料修改（synthesizedCookie）：对齐代理 createHeaderCookie 发「合成 cookie」
    //   （header 字段 URL-encode、按 key 排序）。eapi 写接口发完整登录 cookie 会被 NCM 403 静默丢弃。
    val cookieHeader = when {
        isLtProfile -> {
            // 逐字对齐 MeiloX NeteaseInterceptor.buildCookieString(cookieMap)（插入序、明文值、
            // 不做 URL-encode）：基础 8 + 身份/设备字段 + MUSIC_U。与加密 header 同源（同 os/
            // appver 桌面参数、同 __csrf 常量），避免「cookie os 与 header os 矛盾→静默丢弃」。
            val cfg = profile!!
            buildMap {
                put("os", cfg["os"].toString())
                put("appver", cfg["appver"].toString())
                put("osver", cfg["osver"].toString())
                put("channel", cfg["channel"].toString())
                put("versioncode", cfg["versioncode"].toString())
                put("mobilename", cfg["mobilename"].toString())
                put("buildver", (System.currentTimeMillis() / 1000).toString())
                put("resolution", cfg["resolution"].toString())
                put("deviceId", cookie["deviceId"] ?: anonymousDeviceId)
                put("sDeviceId", cookie["deviceId"] ?: anonymousDeviceId)
                put("ntes_kaola_ad", "1")
                put("_ntes_nuid", LT_NUID)
                put("WNMCID", LT_WNMCID)
                put("URS_APPID", LT_URS_APPID)
                put("WEVNSM", "1.0.0")
                put("__csrf", LT_CSRF)
                put("NMDI", LT_NMDI)
                put("NMTID", LT_NMTID)
                musicU?.let { put("MUSIC_U", it) }
            }.entries.joinToString("; ") { (k, v) -> "$k=$v" }
        }
        synthesizedCookie -> header.entries
            .sortedBy { it.key }
            .joinToString("; ") { (k, v) -> "${encodeCookieComponent(k)}=${encodeCookieComponent(v.toString())}" }
        else -> buildMap {
            putAll(cookie)
            if (cookie["deviceId"] == null) put("deviceId", anonymousDeviceId)
        }.entries.joinToString("; ") { (k, v) -> "$k=$v" }
    }

    val response = apiClient.request(url) {
        method = HttpMethod.Post
        contentType(ContentType.Application.FormUrlEncoded)
        val ua = profile?.get("userAgent")?.toString()
            ?: when {
                isHistoryProfile -> NCM_OSX_UA
                else -> NCM_MOBILE_UA
            }
        header("User-Agent", ua)
        if (route.encryption == Encryption.WEAPI) {
            header("Referer", NCM_WEB_DOMAIN)
        }
        if (isHistoryProfile) {
            header("Accept", "*/*")
        }
        header("Cookie", cookieHeader)
        setBody(fields.entries.joinToString("&") { (k, v) -> "${k.encodeURLParameter()}=${v.encodeURLParameter()}" })
    }
    var body = response.bodyAsText()
    if (!response.status.isSuccess()) {
        throw Exception("HTTP ${response.status} ${response.status.description}: ${body.take(500)}")
    }
    // eapi 响应兜底：若 NCM 仍回 hex 加密体（非 JSON 开头），用 EAPI-key 解密（对齐 MeiloX 解密分支）。
    if (route.encryption == Encryption.EAPI) {
        val firstChar = body.firstOrNull { it > ' ' }
        if (firstChar != '{' && firstChar != '[') {
            val decoded = NeteaseCrypto.eapiDecryptHex(body.trim())
            if (decoded.isNotEmpty()) body = decoded
        }
    }
    return body
}

@PublishedApi
internal fun toJsonString(map: Map<String, Any>): String =
    map.toJsonObject().toString()

@PublishedApi
internal fun Map<String, Any>.toJsonObject(): JsonObject =
    buildJsonObject {
        forEach { (k, v) -> put(k, toJsonElement(v)) }
    }

@PublishedApi
internal fun toJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value.toLong())
    is Boolean -> JsonPrimitive(value)
    is List<*> -> JsonArray(value.map { toJsonElement(it) })
    is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to toJsonElement(it.value) })
    else -> JsonPrimitive(value.toString())
}

// ---------------------------------------------------------------------------
// 文件上传（头像 / 歌单封面）：仍走代理 API_BASE_URL，保持不变。
// ---------------------------------------------------------------------------

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
            CookieProvider.cookie.takeIf { it.isNotEmpty() }?.let {
                header("Cookie", it)
            }
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
            val result = json.decodeFromString<T>(responseBody)
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
            .header("User-Agent", NCM_MOBILE_UA)
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
            val result = json.decodeFromString<T>(responseBody)
            Result.success(result)
        } else {
            Result.failure(Exception("API error: ${response.code}: $responseBody"))
        }
    } catch (e: Exception) {
        Log.e("ApiClient", "apiPostFileOkHttp failed path=$path file=${file.name}", e)
        Result.failure(e)
    }
}
