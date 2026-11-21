package com.rcmiku.ncmapi.api.player

import android.util.Log
import com.rcmiku.ncmapi.api.API_BASE_URL
import com.rcmiku.ncmapi.api.AudioSourceSettings
import com.rcmiku.ncmapi.api.AudioSources
import com.rcmiku.ncmapi.api.LocalSourceFetcher
import com.rcmiku.ncmapi.api.LocalSources
import com.rcmiku.ncmapi.api.LxSourceBridge
import com.rcmiku.ncmapi.api.PlayUrlTrace
import com.rcmiku.ncmapi.api.UNBLOCK_SOURCE
import com.rcmiku.ncmapi.api.apiClient
import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.api.apiPost
import com.rcmiku.ncmapi.model.LyricResponse
import com.rcmiku.ncmapi.model.SongUrl
import com.rcmiku.ncmapi.model.SongUrlResponse
import com.rcmiku.ncmapi.utils.CookieProvider
import com.rcmiku.ncmapi.utils.json
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

enum class OfficialPlayState { PLAYABLE, NO_COPYRIGHT, MEMBERSHIP_ONLY, UNKNOWN }

object PlayerApi {

    /**
     * 只用官方接口判断完整播放权限。请求失败、限流或响应格式异常时返回未知。
     */
    suspend fun officialPlayState(songId: String, fee: Int = 0): OfficialPlayState {
        val raw = apiGet<JsonObject>(
            "/song/url/v1",
            songUrlParams(songId, SongLevel.STANDARD, unblock = false)
        ).getOrNull() ?: return OfficialPlayState.UNKNOWN
        val code = (raw["code"] as? JsonPrimitive)?.intOrNull
        if (code != 200) return OfficialPlayState.UNKNOWN
        val data = raw["data"] as? JsonArray ?: return OfficialPlayState.UNKNOWN
        val first = data.firstOrNull() as? JsonObject ?: return OfficialPlayState.UNKNOWN
        val urlValue = (first["url"] ?: JsonNull) as? JsonPrimitive ?: return OfficialPlayState.UNKNOWN
        if (urlValue != JsonNull && !urlValue.isString) return OfficialPlayState.UNKNOWN
        val url = urlValue.contentOrNull
        val time = (first["time"] as? JsonPrimitive)?.longOrNull ?: 0L

        return when {
            !url.isNullOrBlank() && !url.contains("outer/url") && time != 30_040L -> OfficialPlayState.PLAYABLE
            fee == 1 || fee == 4 -> OfficialPlayState.MEMBERSHIP_ONLY
            else -> OfficialPlayState.NO_COPYRIGHT
        }
    }

    /**
     * 解析可播放直链。
     *
     * @param skipUrls 上次交给播放器但被判定「不是这首歌的完整音频」的直链（播放器侧时长校验失败后写入，
     *   见 app 的 `ResolveGuard`）。客户端音源命中的 url 落在这个集合里就当作未命中，继续试下一个插件 /
     *   下一个直连源，这样「换源重试」才真的会换到别的东西，而不是把同一根坏链再刷一遍。
     */
    suspend fun songPlayUrlV1(
        songId: String,
        songLevel: SongLevel = SongLevel.STANDARD,
        source: String? = null,
        skipUrls: Set<String> = emptySet(),
        /**
         * 调用方手上已有的 Song JSON（队列 MediaItem extras 里那份）。
         * 交给 LX 音源直接拼 musicInfo，省掉一次 `/song/detail/batch` 往返；
         * 为空或字段不齐时解析层自己请求，行为与以前一致。
         */
        songJson: String? = null
    ): Result<SongUrlResponse> {
        val parsed = parseSongId(songId)
        val realId = parsed.first
        val fee = parsed.second

        // 起播链路的每一步都在这里留痕：面板上「哪条分支、等了多久、最后落在哪个域名」全部来自这几行。
        val startedAt = System.currentTimeMillis()
        // 带 songId 打点：预取下一首也会走这里，面板靠它区分「当前歌」和「别的歌」。
        fun trace(name: String, detail: String, tags: List<String>) =
            PlayUrlTrace.emit(realId, name, detail, tags)
        trace(
            "开始解析",
            if (fee == 1) "VIP 曲目 · 走第三方音源" else "普通曲目 · 官方接口直取",
            listOf("网易云", songLevel.value)
        )

        // 只有 fee==1（VIP/黑胶）需要第三方解灰。fee=0（免费）、fee=8（普通）等其它值
        // 官方直连即可播放，完全不碰 API 服务器。
        // 若开启了 AudioSourceSettings.RESOLVE_ALL，则所有歌曲均走第三方音源解析，未命中再兜底官方。
        if (fee != 1 && !AudioSourceSettings.RESOLVE_ALL) {
            val result = apiGet<SongUrlResponse>("/song/url/v1", songUrlParams(realId, songLevel, unblock = false))
            val url = result.getOrNull()?.data?.firstOrNull()?.url
            trace(
                if (url.isNullOrBlank()) "官方接口未返回" else "官方接口命中",
                "${PlayUrlTrace.hostOf(url).ifBlank { "没有可播地址" }} · ${System.currentTimeMillis() - startedAt}ms",
                listOf("网易云", songLevel.value)
            )
            return result
        }

        // VIP（fee==1）或全量解析模式：三选一音源（LX 脚本 / 内置直连 / 代理解灰），互斥不串联。
        // 客户端侧（LX / 内置直连）开启时不再走 API 解灰链；只有代理模式才用服务器解灰。
        val selected = AudioSourceSettings.SELECTED
        val clientSideSelected = selected == AudioSources.LX || selected == AudioSources.DIRECT
        val qqTried = AtomicBoolean(false)
        var officialResult: Result<SongUrlResponse>? = null
        if (clientSideSelected) {
            // 不单独播报「尝试客户端音源」：音源通道在面板头部已经写了，
            // 只有命中/未命中才是新信息。
            // 试听链（musicrep-ts）不能当命中：脚本降级到官方源时会给 30 秒片段，
            // 直接返回会让播放器「解析成功」然后播一半就断。
            suspend fun resolveClientUrl(racing: Boolean = false): String? = when {
                selected == AudioSources.LX -> LxSourceBridge.resolver?.invoke(realId, songLevel.value, skipUrls, songJson)
                racing -> runInterruptible(Dispatchers.IO) { tryDirectSourceUrl(realId, songLevel, skipUrls) }
                else -> tryDirectSourceUrl(realId, songLevel, skipUrls)
            }?.takeIf { it.isNotBlank() && !it.contains("musicrep-ts", ignoreCase = true) }
                ?.takeUnless { PlayUrlSkip.blocks(skipUrls, it) }

            val qqId = realId.toLongOrNull()
            val clientUrl = run {
                data class SourceHit(
                    val url: String,
                    val qqElapsedMs: Long? = null,
                    val br: Int = 0,
                    val entryHost: String = "",
                )

                val winner = coroutineScope {
                    val officialStartedAt = System.currentTimeMillis()
                    val officialJob = async {
                        apiGet<SongUrlResponse>("/song/url/v1", songUrlParams(realId, songLevel, unblock = false))
                    }
                    val lxJob = async {
                        runCatching { resolveClientUrl(racing = true)?.let { SourceHit(it) } }
                            .onFailure { currentCoroutineContext().ensureActive() }
                            .getOrNull()
                    }
                    val official = officialJob.await()
                    officialResult = official
                    val officialUrl = official.getOrNull()?.data?.firstOrNull { it.isPlayableFullUrl() }?.url
                    trace(
                        if (officialUrl != null) "官方接口命中" else "官方接口未返回",
                        if (officialUrl != null) {
                            "${PlayUrlTrace.hostOf(officialUrl)} · ${System.currentTimeMillis() - officialStartedAt}ms"
                        } else {
                            "无版权或需会员 · 转入第三方音源"
                        },
                        listOf("网易云", songLevel.value)
                    )
                    val qqAllowed = QqSourceSettings.ENABLED && officialUrl == null && qqId != null
                    val qqJob = if (qqAllowed) async {
                        runCatching {
                            val qqStartedAt = System.currentTimeMillis()
                            val deadline = minOf(qqStartedAt + 8_000L, startedAt + 19_500L)
                            if (deadline <= qqStartedAt) return@runCatching null

                            val resolution = withTimeoutOrNull(deadline - qqStartedAt) {
                                resolveQqUrl(realId, songJson, songLevel, skipUrls)
                            }
                            currentCoroutineContext().ensureActive()
                            val qqFinishedAt = System.currentTimeMillis()
                            val elapsedMs = qqFinishedAt - qqStartedAt
                            if (resolution != null && qqFinishedAt < deadline) {
                                qqTried.set(true)
                                SourceHit(resolution.url, elapsedMs, resolution.br, resolution.entryHost)
                            } else {
                                val status = if (qqFinishedAt >= deadline) "预算耗尽" else "没有可播地址"
                                trace("QQ 音源未命中", "$status · ${elapsedMs}ms", listOf("QQ", songLevel.value))
                                null
                            }
                        }.onFailure { currentCoroutineContext().ensureActive() }.getOrNull()
                    } else null

                    val hit = if (qqJob == null) {
                        lxJob.await()
                    } else {
                        var lxPending = true
                        var qqPending = true
                        var winner: SourceHit? = null
                        while ((lxPending || qqPending) && winner == null) {
                            val completed = select {
                                if (lxPending) lxJob.onAwait { true to it }
                                if (qqPending) qqJob.onAwait { false to it }
                            }
                            if (completed.first) lxPending = false else qqPending = false
                            winner = completed.second
                        }
                        winner
                    }
                    // 已完成的任务取消为空操作；作用域会等另一条任务退出再返回。
                    lxJob.cancel()
                    qqJob?.cancel()
                    hit
                }
                val qqElapsedMs = winner?.qqElapsedMs
                if (winner != null && qqElapsedMs != null && qqId != null) {
                    trace(
                        "QQ 音源命中",
                        qqHitDetail(winner.url, winner.entryHost, winner.br, qqElapsedMs),
                        listOf("QQ", songLevel.value)
                    )
                    return Result.success(SongUrlResponse(data = listOf(
                        SongUrl(
                            id = qqId, url = winner.url,
                            br = if (winner.br > 0) winner.br * 1000 else 0
                        )
                    )))
                }
                winner?.url
            }
            if (!clientUrl.isNullOrBlank()) {
                Log.d("PlayerApi", "Client source ($selected) resolved songId=$realId")
                trace(
                    "第三方音源命中",
                    "${PlayUrlTrace.hostOf(clientUrl)} · ${sourceLabel(selected)} · " +
                        "${System.currentTimeMillis() - startedAt}ms",
                    listOf(selected, songLevel.value)
                )
                return Result.success(SongUrlResponse(data = listOf(
                    SongUrl(id = realId.toLong(), url = clientUrl, br = 320000)
                )))
            }
            trace(
                "第三方音源未命中",
                "${sourceLabel(selected)} 没给出可播地址 · 跳过 ${skipUrls.size} 条坏链",
                listOf(selected)
            )
            Log.w("PlayerApi", "Client source ($selected) missed songId=$realId skipped=${skipUrls.size}, fall back to official url")
        } else {
            trace("代理解灰", "来源 ${source ?: UNBLOCK_SOURCE}", listOf("网易云", "解灰"))
            val unblockResult = tryUnblockUrl(realId, source, songLevel)
            // 上次被判定为错源的 url 不再用（否则重试等于原地打转），交给官方兜底。
            val blocked = PlayUrlSkip.blocks(
                skipUrls,
                unblockResult.getOrNull()?.data?.firstOrNull()?.url
            )
            if (unblockResult.hasPlayableUrl() && !blocked) {
                trace(
                    "代理解灰命中",
                    "${PlayUrlTrace.hostOf(unblockResult.getOrNull()?.data?.firstOrNull()?.url)} · ${System.currentTimeMillis() - startedAt}ms",
                    listOf("解灰", songLevel.value)
                )
                return unblockResult
            }
            trace(
                "代理解灰未命中",
                if (blocked) "返回的是已被拉黑的坏链" else "没有可播地址",
                listOf("解灰")
            )
        }

        // 网易云官方兜底。客户端音源开启时 VIP 歌在此回 30 秒试听占位（账号有会员则完整 url）；
        // 代理音源模式下解灰失败时也是走这条官方直连。
        val fallback = officialResult
            ?: apiGet<SongUrlResponse>("/song/url/v1", songUrlParams(realId, songLevel, unblock = false))
                .also { result ->
                    val fallbackUrl = result.getOrNull()?.data?.firstOrNull()?.url
                    trace(
                        if (fallbackUrl.isNullOrBlank()) "官方兜底未返回" else "官方兜底命中",
                        "${PlayUrlTrace.hostOf(fallbackUrl).ifBlank { "没有可播地址" }} · " +
                            "${System.currentTimeMillis() - startedAt}ms",
                        listOf("网易云", songLevel.value)
                    )
                }
        if (QqSourceSettings.ENABLED && !fallback.hasPlayableUrl() && !qqTried.get()) {
            val qqStartedAt = System.currentTimeMillis()
            // 详情查询、搜索和取流共用 QQ 预算，并扣掉此前解析已经花掉的时间。
            val deadline = minOf(qqStartedAt + QqMusicSource.BUDGET_MS, startedAt + 19_500L)
            val id = realId.toLongOrNull()
            if (id != null && deadline > qqStartedAt) {
                val qqResolution = withTimeoutOrNull(deadline - qqStartedAt) {
                    resolveQqUrl(realId, songJson, songLevel, skipUrls)
                }
                val qqFinishedAt = System.currentTimeMillis()
                if (qqResolution != null && qqFinishedAt < deadline) {
                    trace(
                        "QQ 音源命中",
                        qqHitDetail(qqResolution.url, qqResolution.entryHost, qqResolution.br, qqFinishedAt - qqStartedAt),
                        listOf("QQ", songLevel.value)
                    )
                    return Result.success(SongUrlResponse(data = listOf(
                        SongUrl(
                            id = id, url = qqResolution.url,
                            br = if (qqResolution.br > 0) qqResolution.br * 1000 else 0
                        )
                    )))
                }
                val status = if (qqFinishedAt >= deadline) "预算耗尽" else "没有可播地址"
                trace("QQ 音源未命中", "$status · ${qqFinishedAt - qqStartedAt}ms", listOf("QQ", songLevel.value))
            }
        }
        return fallback
    }

    private fun qqHitDetail(url: String, entryHost: String, br: Int, elapsedMs: Long): String {
        val host = PlayUrlTrace.hostOf(url)
        val route = if (entryHost.isNotEmpty() && entryHost != host) "$entryHost → $host" else host
        val quality = if (br > 0) "${br}k" else "无损"
        return "$route · $quality · ${elapsedMs}ms"
    }

    private suspend fun resolveQqUrl(
        songId: String,
        songJson: String?,
        level: SongLevel,
        skipUrls: Set<String>
    ): QqResolution? {
        fun JsonObject?.songName(): String? = (this?.get("name") as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it.isNotBlank() }
        fun JsonObject?.songArtist(): String? = (this?.get("ar") as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject).songName() }
            .joinToString(" / ").takeIf { it.isNotBlank() }
        fun JsonObject?.songDuration(): Long? = (this?.get("dt") as? JsonPrimitive)?.longOrNull
            ?.takeIf { it > 0 }
        val cached = runCatching { songJson?.let { json.parseToJsonElement(it) as? JsonObject } }.getOrNull()
        val name = cached.songName()
        val artist = cached.songArtist()
        val duration = cached.songDuration()
        val detail = if (name == null || artist == null || duration == null) {
            val detailStartedAt = System.currentTimeMillis()
            val response = try {
                apiGet<JsonElement>("/song/detail/batch", mapOf("c" to "[{\"id\":$songId}]")).getOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            val song = ((response as? JsonObject)?.get("songs") as? JsonArray)?.firstOrNull() as? JsonObject
            val status = if (song != null) "已获取" else "未获取"
            PlayUrlTrace.emit(
                songId, "QQ 音源 · 歌曲详情", "$status · ${System.currentTimeMillis() - detailStartedAt}ms",
                listOf("QQ", level.value)
            )
            song ?: return null
        } else {
            null
        }

        return QqMusicSource.resolve(
            songId, name ?: detail.songName() ?: return null, artist ?: detail.songArtist() ?: return null,
            duration ?: detail.songDuration() ?: 0L, level, skipUrls
        )
    }

    /** 音源代号的用户可读名：面板上不该出现 "lx" / "direct" 这种内部值。 */
    private fun sourceLabel(selected: String): String = when (selected) {
        AudioSources.LX -> "LX 脚本"
        AudioSources.DIRECT -> "内置直连"
        AudioSources.PROXY -> "API 代理"
        else -> selected
    }

    private fun songUrlParams(songId: String, songLevel: SongLevel, unblock: Boolean): Map<String, Any> =
        mapOf("id" to songId, "level" to songLevel.value, "unblock" to unblock)

    /**
     * 直连音源：用户指定单源就只试它；「自动选择」按 [LocalSources.DIRECT_KEYS] 顺序轮询
     * 六条，命中即返回（照 Melodia 六源）。
     */
    private fun tryDirectSourceUrl(
        songId: String,
        songLevel: SongLevel = SongLevel.STANDARD,
        skipUrls: Set<String> = emptySet()
    ): String? {
        val chosen = LocalSources.fromKey(DirectSourceSettings.SOURCE)?.value
            ?: LocalSources.AUTO
        val chain = if (chosen == LocalSources.AUTO) LocalSources.DIRECT_KEYS else listOf(chosen)
        for (m in chain) {
            val t0 = System.currentTimeMillis()
            val url = LocalSourceFetcher.fetch(m, songId, songLevel)
            val cost = System.currentTimeMillis() - t0
            if (url == null) {
                PlayUrlTrace.emit(songId, "直连源 $m", "未命中 · ${cost}ms", listOf("直连", songLevel.value))
                continue
            }
            if (PlayUrlSkip.blocks(skipUrls, url)) {
                PlayUrlTrace.emit(songId, "直连源 $m", "返回已拉黑的坏链，跳过 · ${cost}ms", listOf("直连", songLevel.value))
                Log.d("PlayerApi", "Local source '$m' skipped (rejected by duration check) songId=$songId")
                continue
            }
            PlayUrlTrace.emit(
                songId,
                "直连源 $m",
                "命中 ${PlayUrlTrace.hostOf(url)} · ${cost}ms",
                listOf("直连", songLevel.value)
            )
            Log.d("PlayerApi", "Local source '$m' hit for songId=$songId")
            return url
        }
        return null
    }


    private fun Result<SongUrlResponse>.hasPlayableUrl(): Boolean =
        getOrNull()?.data?.any { it.isPlayableFullUrl() } == true

    private fun SongUrl.isPlayableFullUrl(): Boolean =
        !url.isNullOrEmpty() &&
            // NCM 占位/外链（未真正解锁成功时返回），实测返回反爬 HTML 而非音频
            !url.contains("outer/url") &&
            // 试听 30 秒占位（time 单位 ms：30040）
            time != 30_040L
            // 注意：freeTrial* 字段 NCM 对可播歌曲也常回非 null 的空对象，不能用作判据

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

    private suspend fun tryUnblockUrl(
        songId: String,
        sourceOverride: String?,
        songLevel: SongLevel = SongLevel.STANDARD
    ): Result<SongUrlResponse> {
        // Per-song override wins; otherwise fall back to the global setting.
        // "AUTO" (or null) means "let the server pick".
        val primary = sourceOverride?.takeIf { it.isNotBlank() && it != "AUTO" }
            ?: UNBLOCK_SOURCE.takeIf { it != "AUTO" }

        if (primary == null) {
            val matchResult = requestUnblockUrl("$API_BASE_URL/song/url/match", songId, source = null, songLevel = songLevel)
            if (matchResult.isSuccess) return matchResult
            return requestUnblockUrl("$API_BASE_URL/match", songId, source = null, songLevel = songLevel)
        }

        // Explicit source: try it first, then the remaining known sources in
        // picker order until one yields a playable URL (auto-fallback).
        val chain = listOfNotNull(primary) +
            KNOWN_UNBLOCK_SOURCES.filter { it != primary }
        var last = Result.failure<SongUrlResponse>(Exception("No unblock URL found for any source"))
        for (src in chain) {
            val matchResult = requestUnblockUrl("$API_BASE_URL/song/url/match", songId, source = src, songLevel = songLevel)
            if (matchResult.isSuccess) {
                Log.d("PlayerApi", "Unblock fallback settled on source=$src for songId=$songId")
                return matchResult
            }
            val legacyResult = requestUnblockUrl("$API_BASE_URL/match", songId, source = src, songLevel = songLevel)
            if (legacyResult.isSuccess) {
                Log.d("PlayerApi", "Unblock fallback settled on source=$src (legacy) for songId=$songId")
                return legacyResult
            }
            last = legacyResult
        }
        return last
    }

    /**
     * Fallback chain for unblock sources, mirroring the order of the app's
     * source picker (shared in ncmapi's UnblockSources so UI and fallback
     * never drift).
     */
    private val KNOWN_UNBLOCK_SOURCES: List<String> =
        com.rcmiku.ncmapi.api.UnblockSources.FALLBACK_VALUES

    /** 直连源选择（「音源设置」里选中「直连音源」时的子设置）。 */
    object DirectSourceSettings {
        var SOURCE: String = LocalSources.AUTO
    }

    private suspend fun requestUnblockUrl(
        url: String,
        songId: String,
        source: String?,
        songLevel: SongLevel = SongLevel.STANDARD
    ): Result<SongUrlResponse> {
        return try {
            val response = apiClient.request(url) {
                method = HttpMethod.Get
                parameter("id", songId)
                if (!source.isNullOrEmpty()) {
                    parameter("source", source)
                }
                parameter("level", songLevel.value)
                val br = when (songLevel) {
                    SongLevel.STANDARD, SongLevel.HIGHER -> 128000
                    SongLevel.EXHIGH -> 320000
                    else -> 999000
                }
                parameter("br", br)
                CookieProvider.cookie.takeIf { it.isNotEmpty() }?.let {
                    header("Cookie", it)
                }
            }
            if (response.status.isSuccess()) {
                val body = response.bodyAsText()
                val unblockData = parseUnblockResponse(body)
                if (unblockData != null) {
                    Log.d("PlayerApi", "Unblock resolved songId=$songId source=$source url=${unblockData.url}")
                    Result.success(SongUrlResponse(data = listOf(
                        SongUrl(id = songId.toLong(), url = unblockData.url, br = unblockData.br)
                    )))
                } else {
                    Log.w("PlayerApi", "No unblock URL found for songId=$songId source=$source body=$body")
                    Result.failure(Exception("No unblock URL found: $body"))
                }
            } else {
                val body = response.bodyAsText()
                Log.w("PlayerApi", "Unblock service returned ${response.status} for songId=$songId body=$body")
                Result.failure(Exception("Unblock service returned ${response.status}: $body"))
            }
        } catch (e: Exception) {
            Log.w("PlayerApi", "Unblock request failed for songId=$songId source=$UNBLOCK_SOURCE url=$url", e)
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
            parseUnblockData(jsonObj)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseUnblockData(jsonObj: JsonObject): UnblockData? {
        val candidates = listOfNotNull(
            jsonObj["data"],
            jsonObj["result"],
            jsonObj["url"],
        )
        return candidates.firstNotNullOfOrNull(::parseUnblockElement)
    }

    private fun parseUnblockElement(element: JsonElement): UnblockData? =
        when (element) {
            is JsonPrimitive -> {
                val url = element.contentOrNull
                if (!url.isNullOrEmpty()) UnblockData(url, 320000) else null
            }
            is JsonArray -> element.firstNotNullOfOrNull(::parseUnblockElement)
            is JsonObject -> {
                if (element.isTrialUrl()) {
                    null
                } else {
                    val url = element["url"]?.jsonPrimitive?.contentOrNull
                        ?: element["data"]?.let(::parseUnblockElement)?.url
                    val br = element["br"]?.jsonPrimitive?.intOrNull ?: 320000
                    if (!url.isNullOrEmpty()) UnblockData(url, br) else null
                }
            }
            else -> null
        }

    private fun JsonObject.isTrialUrl(): Boolean =
        containsKey("freeTrialInfo") ||
            containsKey("freeTrialPrivilege") ||
            containsKey("freeTimeTrialPrivilege") ||
            this["time"]?.jsonPrimitive?.contentOrNull == "30040"

    suspend fun songLyric(musicId: Long): Result<LyricResponse> =
        apiGet("/lyric/new", mapOf("id" to musicId))

    /**
     * 获取歌曲百科（含回忆坐标、曲风、制作人员等）
     */
    suspend fun songWiki(
        songId: Long,
        albumName: String = "",
        publishTimeStr: String = ""
    ): Result<com.rcmiku.ncmapi.model.SongWikiData> {
        val summaryResult = apiPost<com.rcmiku.ncmapi.model.SongWikiSummaryResponse>(
            "/song/wiki/summary",
            mapOf("songId" to songId)
        ).getOrNull()
        val creatorsResult = apiPost<com.rcmiku.ncmapi.model.SongCreatorsResponse>(
            "/song/creators",
            mapOf("songId" to songId)
        ).getOrNull()

        var style = ""
        var language = ""
        var bpm = ""
        var entertainment = ""
        var awards = emptyList<String>()
        var awardTotal = 0
        var musicMemory: com.rcmiku.ncmapi.model.SongMusicMemory? = null

        summaryResult?.data?.blocks?.forEach { block ->
            when (block.code) {
                "SONG_PLAY_ABOUT_SONG_BASIC" -> {
                    block.creatives.forEach { creative ->
                        when (creative.creativeType) {
                            "songTag" -> {
                                style = creative.resources.mapNotNull { it.uiElement?.mainTitle?.title }
                                    .filter { it.isNotEmpty() }
                                    .joinToString(" / ")
                            }
                            "language" -> {
                                language = creative.uiElement?.textLinks?.firstOrNull()?.text.orEmpty()
                            }
                            "bpm" -> {
                                bpm = creative.uiElement?.textLinks?.firstOrNull()?.text.orEmpty()
                            }
                            "entertainment" -> {
                                entertainment = creative.resources.mapNotNull { it.uiElement?.mainTitle?.title }
                                    .filter { it.isNotEmpty() }
                                    .joinToString(" / ")
                            }
                            "songAward" -> {
                                awards = creative.resources.mapNotNull { it.uiElement?.mainTitle?.title }
                                    .filter { it.isNotEmpty() }
                                awardTotal = creative.uiElement?.buttons?.firstOrNull()?.text
                                    ?.filter { it.isDigit() }
                                    ?.toIntOrNull() ?: 0
                            }
                        }
                    }
                }
                "SONG_PLAY_ABOUT_MUSIC_MEMORY" -> {
                    val resources = block.creatives.flatMap { it.resources }
                    val firstListen = resources.firstOrNull { it.resourceType == "FIRST_LISTEN" }
                        ?.resourceExt?.musicFirstListenDto
                    val totalPlay = resources.firstOrNull { it.resourceType == "TOTAL_PLAY" }
                        ?.resourceExt?.musicTotalPlayDto
                    val date = firstListen?.date?.trim().orEmpty()
                    val playCount = totalPlay?.playCount?.coerceAtLeast(0) ?: 0
                    if (date.isNotEmpty() || playCount > 0) {
                        musicMemory = com.rcmiku.ncmapi.model.SongMusicMemory(
                            firstListenDate = date,
                            season = firstListen?.season?.trim().orEmpty(),
                            period = firstListen?.period?.trim().orEmpty(),
                            playCount = playCount,
                            playCountText = totalPlay?.text?.trim().orEmpty()
                        )
                    }
                }
            }
        }

        val creatorRoles = (creatorsResult?.data?.songCreatorsRoleVos ?: emptyList())
            .mapNotNull { role ->
                val artists = role.creatorMetaVOS.map { it.artistName }.filter { it.isNotEmpty() }
                if (artists.isNotEmpty()) com.rcmiku.ncmapi.model.SongWikiCreatorRole(role.roleName, artists) else null
            }
        val creatorsStr = creatorRoles.joinToString(" / ") { "${it.roleName} ${it.artistNames.joinToString(" ")}" }

        return Result.success(
            com.rcmiku.ncmapi.model.SongWikiData(
                style = style,
                album = albumName,
                language = language,
                publishTime = publishTimeStr,
                bpm = bpm,
                creators = creatorsStr,
                creatorRoles = creatorRoles,
                entertainment = entertainment,
                awards = awards,
                awardTotal = awardTotal,
                musicMemory = musicMemory
            )
        )
    }
}
