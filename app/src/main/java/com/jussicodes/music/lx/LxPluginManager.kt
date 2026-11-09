package com.jussicodes.music.lx

import android.content.Context
import com.jussicodes.music.playback.PlaybackPath
import com.rcmiku.ncmapi.api.player.PlayUrlSkip
import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** 导入结果：区分成功 / 重名待确认 / 失败。 */
sealed interface ImportResult {
    data class Success(val item: LxPluginItem) : ImportResult
    data class Duplicate(val existing: LxPluginItem, val newItem: LxPluginItem) : ImportResult
    data class Failure(val message: String) : ImportResult
}

data class QualityTestResult(
    val quality: String,
    val success: Boolean,
    val url: String? = null,
    val costMs: Long = 0,
    val isTrial: Boolean = false,
    val httpCode: Int? = null,
    val message: String = ""
)

data class TestResult(
    val success: Boolean,
    val bestUrl: String? = null,
    val bestQuality: String? = null,
    val costMs: Long = 0,
    val qualityResults: List<QualityTestResult> = emptyList(),
    val message: String = ""
)

/**
 * LX 脚本的编排层（移植 Melodia LxPluginManager）：导入校验、增删、启停、解析。
 * 依赖 [LxPluginStore]（持久化）与 [LxPluginEngine]（沙盒），二者都在 Application 里单例化。
 */
object LxPluginManager {

    private const val TAG = "LxPlugin"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var engine: LxPluginEngine? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            engine = LxPluginEngine(context.applicationContext).also { e ->
                // 沙盒懒创建时要能按 id 拿到脚本正文
                e.itemProvider = { id -> LxPluginStore.latest.value.firstOrNull { it.id == id } }
            }
            scope.launch { seedBuiltinScripts(context.applicationContext) }
        }
    }

    /**
     * 打进 assets 的内置脚本。
     *
     * 现在的默认音源是「墨澜聚合音源」；原来的「玉宁熙 Pro」降级成普通条目并默认不活动，
     * 用户想用可以自己打开。两个都允许删除——删掉后不会再被种回来（见 [LxPluginStore.SeedState]）。
     */
    private data class SeededScript(
        val id: String,
        val asset: String,
        val name: String,
        val enabled: Boolean,
        val qualities: List<String>
    )

    /** 老版本内置脚本 id：条目沿用，只把默认状态改成不活动。 */
    const val LEGACY_BUILTIN_ID = "builtin-lx-yuanningxi"

    private const val MOLAN_ID = "builtin-lx-molan"

    /** 「同名用户副本去重」的一次性迁移标记。 */
    private const val DEDUPE_MOLAN_KEY = "dedupe-molan"

    val DEFAULT_QUALITIES = listOf(
        "128k", "320k", "flac", "flac24bit", "hires",
        "master", "jymaster", "dolby", "atmos"
    )

    /** 墨澜脚本 `inited` 里对网易云声明的档位（不含 192k/exhigh 这类官方档位）。 */
    private val MOLAN_QUALITIES = listOf("128k", "320k", "flac", "flac24bit", "hires", "atmos", "master")

    private val SEEDED = listOf(
        SeededScript(MOLAN_ID, "lx/molan.js", "墨澜聚合音源（内置）", enabled = true, qualities = MOLAN_QUALITIES),
        SeededScript(LEGACY_BUILTIN_ID, "lx/lx-builtin.js", "玉宁熙 Pro（内置）", enabled = false, qualities = DEFAULT_QUALITIES)
    )

    private suspend fun seedBuiltinScripts(context: Context) {
        val state = LxPluginStore.seedState()
        val scripts = SEEDED.mapNotNull { seed ->
            runCatching {
                context.assets.open(seed.asset).bufferedReader().use { it.readText() }
            }.getOrNull()?.let { seed to it }
        }
        if (scripts.isEmpty()) return
        val needsLegacyMigration = LEGACY_BUILTIN_ID !in state.migrated
        val needsMolanDedupe = DEDUPE_MOLAN_KEY !in state.migrated
        // 用户之前自己导入过墨澜时，内置这条才是默认音源；同名旧副本只留一份生效。
        val molanName = scripts.firstOrNull { it.first.id == MOLAN_ID }?.let { parseHeader(it.second).name }
        LxPluginStore.update { list ->
            var next = list
            for ((seed, script) in scripts) {
                val existing = next.firstOrNull { it.id == seed.id }
                if (existing == null) {
                    // 用户删过就别复活
                    if (seed.id !in state.removed) {
                        val info = parseHeader(script)
                        val item = LxPluginItem(
                            id = seed.id,
                            name = seed.name.ifBlank { info.name },
                            version = info.version,
                            author = info.author,
                            description = info.description,
                            rawScript = script,
                            isEnabled = seed.enabled,
                            wyQualities = seed.qualities
                        )
                        // 默认音源放最前面：解析按列表顺序轮插件，排最后等于「最后才用」
                        next = if (seed.enabled) listOf(item) + next else next + item
                    }
                } else if (existing.rawScript != script) {
                    // 内置脚本随版本更新：正文变了就覆盖（启停沿用用户当前选择）
                    next = next.map {
                        if (it.id == seed.id) {
                            it.copy(rawScript = script, name = seed.name, wyQualities = seed.qualities)
                        } else {
                            it
                        }
                    }
                } else if (seed.id == LEGACY_BUILTIN_ID && needsLegacyMigration && existing.isEnabled) {
                    // 老内置脚本「默认启用」→「默认不活动」，只迁移一次
                    next = next.map {
                        if (it.id == seed.id) it.copy(isEnabled = false) else it
                    }
                }
            }
            // 同名的用户副本：内置生效，副本一次性置为不活动（条目留着，可删可再启用）
            if (needsMolanDedupe && molanName != null && next.any { it.id == MOLAN_ID }) {
                next = next.map {
                    if (it.id != MOLAN_ID && (it.name == molanName || it.name.startsWith(molanName))) {
                        it.copy(isEnabled = false)
                    } else {
                        it
                    }
                }
            }
            next
        }
        if (needsLegacyMigration || needsMolanDedupe) {
            LxPluginStore.updateSeedState {
                it.copy(
                    migrated = (it.migrated + listOfNotNull(
                        LEGACY_BUILTIN_ID.takeIf { needsLegacyMigration },
                        DEDUPE_MOLAN_KEY.takeIf { needsMolanDedupe }
                    )).distinct()
                )
            }
        }
    }

    val plugins: Flow<List<LxPluginItem>> get() = LxPluginStore.latest

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ---- 增删改 ----

    /** 导入脚本文本（已由调用方从文件/链接取好），先体检再入库。 */
    suspend fun importScript(name: String, script: String): ImportResult {
        if (script.isBlank()) return ImportResult.Failure("脚本内容为空")
        val info = parseHeader(script)
        val candidate = LxPluginItem(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { info.name },
            version = info.version,
            author = info.author,
            description = info.description,
            rawScript = script
        )
        val verifyRes = runCatching { engine?.verify(candidate) }.getOrNull()
        val item = candidate.copy(
            sources = verifyRes?.sources.orEmpty(),
            wyQualities = verifyRes?.wyQualities.orEmpty()
        )
        val existing = LxPluginStore.snapshot().firstOrNull { it.name == item.name }
        return if (existing != null) ImportResult.Duplicate(existing, item) else ImportResult.Success(item)
    }

    /** 确认导入：overwriteId 非空表示覆盖同名旧脚本。 */
    suspend fun confirmImport(newItem: LxPluginItem, overwriteId: String?) {
        val item = newItem.copy(id = overwriteId ?: newItem.id)
        // update{} 会等落盘完成再返回：导入后立刻杀进程也不会丢脚本。
        LxPluginStore.update { list ->
            val next = list.toMutableList()
            val idx = next.indexOfFirst { it.id == item.id }
            if (idx >= 0) next[idx] = item else next.add(item)
            next
        }
        // 体检（importScript → verify）是按新条目自己的 UUID 建的临时沙盒；
        // 覆盖同名旧脚本时它没有对应条目，一并销毁，避免 WebView 泄漏。
        if (newItem.id != item.id) engine?.deactivate(newItem.id)
        engine?.deactivate(item.id)
    }

    suspend fun remove(id: String) {
        engine?.deactivate(id)
        LxPluginStore.update { list -> list.filterNot { it.id == id } }
    }

    suspend fun toggle(id: String, enabled: Boolean) {
        LxPluginStore.update { list ->
            list.map { if (it.id == id) it.copy(isEnabled = enabled) else it }
        }
        if (!enabled) engine?.deactivate(id)
    }

    /**
     * 拖拽排序落盘：按传入的 id 顺序重排，未出现在 ids 里的条目保持原有相对顺序跟在后面。
     * 解析顺序就是列表顺序（见 [resolveUrl]），所以这里一次写完即可。
     */
    suspend fun reorder(ids: List<String>) {
        if (ids.isEmpty()) return
        LxPluginStore.update { list ->
            val byId = list.associateBy { it.id }
            val ordered = ids.mapNotNull { byId[it] }
            // 没排到的条目（理论上不该有）原序接在后面，绝不丢条目。
            ordered + list.filterNot { it.id in ids }
        }
    }

    /** 批量启用/停用；停用后顺手销毁对应沙盒。 */
    suspend fun setEnabled(ids: Collection<String>, enabled: Boolean) {
        if (ids.isEmpty()) return
        LxPluginStore.update { list ->
            list.map { if (it.id in ids) it.copy(isEnabled = enabled) else it }
        }
        if (!enabled) ids.forEach { engine?.deactivate(it) }
    }

    /** 批量删除；内置脚本也能删，删过的不会再被种回来。 */
    suspend fun removeAll(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val targets = ids.toList()
        targets.forEach { engine?.deactivate(it) }
        LxPluginStore.update { list -> list.filterNot { it.id in targets } }
        val seeded = targets.filter { id -> SEEDED.any { it.id == id } }
        if (seeded.isNotEmpty()) {
            LxPluginStore.updateSeedState { it.copy(removed = (it.removed + seeded).distinct()) }
        }
    }

    /** 预热：把已启用的插件脚本装进沙盒池（不命中时首播不再等建沙盒）。 */
    suspend fun warmUp() {
        val enabled = LxPluginStore.snapshot().filter { it.isEnabled }.take(MAX_WARM_PLUGINS)
        for (item in enabled) {
            runCatching { engine?.warmUp(item.id) }
        }
    }

    /** 下载远程脚本（https 链接导入）；失败返回 null。 */
    suspend fun downloadScript(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = okhttp3.Request.Builder().url(url).get().build()
            OkHttpClient().newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /**
     * 在线音源安装：下载 → 校验 → 入库。同名脚本按「更新」处理（覆盖旧条目），
     * 不再弹重名确认框（列表里点的就是那一项）。
     * @return 安装成功的脚本名；失败返回 null
     */
    suspend fun installOnlineSource(source: OnlineSource): String? {
        val script = LxOnlineSources.download(source) ?: return null
        val name = source.name.ifBlank { source.slug }
        return when (val r = importScript(name, script)) {
            is ImportResult.Success -> {
                confirmImport(r.item, null)
                r.item.name
            }
            is ImportResult.Duplicate -> {
                confirmImport(r.newItem, r.existing.id)
                r.newItem.name
            }
            is ImportResult.Failure -> null
        }
    }

    // ---- 解析 ----

    /**
     * 解析 NCM songId 的直链。按脚本列表顺序逐个试，全部未命中返回 null。
     * @param songLevel NCM 音质档位 value（standard/…/jymaster）
     *
     * 每个失败分支都要留日志：上层拿到 null 会静默回落官方直连（非会员即 30 秒试听），
     * 没有日志就无从判断是「歌曲元信息取不到」「插件没命中」还是「插件返回试听链」。
     */
    suspend fun resolveUrl(
        songId: String,
        songLevel: String,
        skipUrls: Set<String> = emptySet()
    ): String? {
        val startedAt = System.currentTimeMillis()
        val enabled = LxPluginStore.snapshot().filter { it.isEnabled }
        if (enabled.isEmpty()) {
            PlaybackPath.stepFor(songId, "LX 音源不可用", "没有启用的脚本", listOf("LX"), PlaybackPath.Status.FAIL)
            android.util.Log.w(TAG, "no enabled plugin, skip lx songId=$songId")
            return null
        }
        PlaybackPath.stepFor(songId, 
            "LX 脚本音源",
            "${enabled.size} 个启用脚本 · ${enabled.joinToString("/") { it.name }}",
            listOf("LX"),
            PlaybackPath.Status.RUNNING,
        )
        // 元信息取不到就不试解析；网络抖动导致的单次失败很常见，重试一次再放弃。
        val song = fetchSong(songId) ?: fetchSong(songId)
        if (song == null) {
            PlaybackPath.stepFor(songId, "歌曲元信息获取失败", "两次请求都没拿到 song detail", listOf("LX"), PlaybackPath.Status.FAIL)
            android.util.Log.w(TAG, "song detail unavailable after 2 attempts, skip lx songId=$songId")
            return null
        }
        val musicInfo = buildMusicInfo(songId, song)
        val qualityCandidates = getQualityCandidates(songLevel)
        // 单次起播的总预算：宁可回落官方直链，也不能让一次点歌被某个档位/某个脚本拖到十几秒。
        val deadline = startedAt + RESOLVE_BUDGET_MS
        for (item in enabled) {
            for (quality in candidatesFor(item, qualityCandidates)) {
                if (System.currentTimeMillis() >= deadline) {
                    PlaybackPath.stepFor(songId, 
                        "解析预算耗尽",
                        "${RESOLVE_BUDGET_MS}ms 内没命中，回落官方直连",
                        listOf("LX"),
                        PlaybackPath.Status.FAIL,
                    )
                    android.util.Log.w(
                        TAG,
                        "resolve budget ${RESOLVE_BUDGET_MS}ms exhausted songId=$songId " +
                            "quality=$quality plugin='${item.name}'"
                    )
                    return null
                }
                val t0 = System.currentTimeMillis()
                val url = engine?.resolveUrl(item.id, musicInfo, quality)
                val cost = System.currentTimeMillis() - t0
                when {
                    url.isNullOrBlank() -> {
                        PlaybackPath.stepFor(songId, 
                            item.name,
                            "未命中 · ${quality}",
                            listOf("LX", quality),
                            PlaybackPath.Status.INFO,
                            cost,
                        )
                        android.util.Log.w(TAG, "plugin '${item.name}' miss songId=$songId quality=$quality cost=${cost}ms")
                    }
                    // 脚本降级到官方试听链时不能当命中，继续下一个
                    url.contains("musicrep-ts", ignoreCase = true) -> {
                        PlaybackPath.stepFor(songId, 
                            item.name,
                            "返回试听片段，跳过 · ${quality}",
                            listOf("LX", quality),
                            PlaybackPath.Status.FAIL,
                            cost,
                        )
                        android.util.Log.w(TAG, "plugin '${item.name}' returned trial clip, try next songId=$songId")
                    }
                    // 播放器侧时长校验判定这条链不是这首歌的完整音频：跳过它
                    PlayUrlSkip.blocks(skipUrls, url) -> {
                        PlaybackPath.stepFor(songId, 
                            item.name,
                            "返回的地址已被时长校验拉黑 · ${quality}",
                            listOf("LX", quality),
                            PlaybackPath.Status.FAIL,
                            cost,
                        )
                        android.util.Log.w(
                            TAG,
                            "plugin '${item.name}' url rejected by duration check, try next songId=$songId"
                        )
                    }
                    else -> {
                        PlaybackPath.stepFor(songId, 
                            item.name,
                            "命中 · ${quality}",
                            listOf("LX", quality),
                            PlaybackPath.Status.OK,
                            cost,
                        )
                        android.util.Log.i(TAG, "plugin '${item.name}' hit songId=$songId quality=$quality cost=${cost}ms")
                        return url
                    }
                }
            }
        }
        PlaybackPath.stepFor(songId, "LX 全部未命中", "所有脚本/档位都试过了", listOf("LX"), PlaybackPath.Status.FAIL)
        android.util.Log.w(
            TAG,
            "all plugins missed songId=$songId levels=$qualityCandidates plugins=${enabled.size} " +
                "total=${System.currentTimeMillis() - startedAt}ms"
        )
        return null
    }

    const val TEST_SONG_ID = "2644776561"

    suspend fun testPlugin(
        pluginId: String,
        songId: String = TEST_SONG_ID
    ): TestResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val plugin = LxPluginStore.snapshot().firstOrNull { it.id == pluginId }
        if (plugin == null) {
            return@withContext TestResult(
                success = false,
                costMs = 0,
                message = "脚本不存在或已被删除"
            )
        }

        val song = fetchSong(songId)
        val musicInfo = if (song != null) {
            buildMusicInfo(songId, song)
        } else {
            buildJsonObject {
                put("id", songId)
                put("songmid", songId)
                put("name", "基准测试歌曲")
                put("singer", "测试歌手")
                put("albumName", "测试专辑")
                put("interval", "03:30")
            }.toString()
        }

        val testQualities = listOf("jymaster", "hires", "flac", "lossless", "320k", "128k")
        val qualityResults = mutableListOf<QualityTestResult>()

        val probeClient = OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        for (q in testQualities) {
            val t0 = System.currentTimeMillis()
            val url = runCatching { engine?.resolveUrl(pluginId, musicInfo, q) }.getOrNull()
            val stepCost = System.currentTimeMillis() - t0

            if (url.isNullOrBlank()) {
                qualityResults.add(
                    QualityTestResult(
                        quality = q,
                        success = false,
                        costMs = stepCost,
                        message = "未命中"
                    )
                )
                continue
            }

            if (url.contains("musicrep-ts", ignoreCase = true)) {
                qualityResults.add(
                    QualityTestResult(
                        quality = q,
                        success = false,
                        url = url,
                        costMs = stepCost,
                        isTrial = true,
                        message = "返回试听链 (30s)"
                    )
                )
                continue
            }

            // HTTP 探测
            var httpCode: Int? = null
            var isHttpOk = false
            runCatching {
                val req = okhttp3.Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-1024")
                    .header("User-Agent", "Mozilla/5.0")
                    .get()
                    .build()
                probeClient.newCall(req).execute().use { resp ->
                    httpCode = resp.code
                    isHttpOk = resp.isSuccessful || resp.code == 206 || resp.code == 200
                }
            }

            if (isHttpOk) {
                qualityResults.add(
                    QualityTestResult(
                        quality = q,
                        success = true,
                        url = url,
                        costMs = stepCost,
                        httpCode = httpCode,
                        message = "成功 (HTTP $httpCode)"
                    )
                )
            } else {
                qualityResults.add(
                    QualityTestResult(
                        quality = q,
                        success = false,
                        url = url,
                        costMs = stepCost,
                        httpCode = httpCode,
                        message = "直链异常 (HTTP ${httpCode ?: "超时"})"
                    )
                )
            }
        }

        val totalCost = System.currentTimeMillis() - startedAt
        val successfulItems = qualityResults.filter { it.success }
        val best = successfulItems.firstOrNull()

        TestResult(
            success = successfulItems.isNotEmpty(),
            bestUrl = best?.url,
            bestQuality = best?.quality,
            costMs = totalCost,
            qualityResults = qualityResults,
            message = if (successfulItems.isNotEmpty()) {
                "成功测出 ${successfulItems.size} 个可用音质档位"
            } else {
                "所有测试档位均未解析到可用直链"
            }
        )
    }

    /** 取歌曲元信息；拿不到就不试解析（脚本要靠歌名/歌手/时长匹配，不拿空壳去撞）。 */
    private suspend fun fetchSong(songId: String): Song? = withContext(Dispatchers.IO) {
        runCatching {
            apiGet<JsonElement>("/song/detail/batch", mapOf("c" to "[{\"id\":$songId}]"))
                .getOrNull()
                ?.let { el -> ((el as? JsonObject)?.get("songs") as? JsonArray).orEmpty().firstOrNull() }
                ?.let { runCatching { json.decodeFromString<Song>(it.toString()) }.getOrNull() }
        }.getOrNull()
    }

    // ---- LX 协议 ----

    /** NCM 的 songId 直接就是 LX 的 wy 平台标识，无需跨平台搜索匹配。 */
    private fun buildMusicInfo(songId: String, song: Song): String = buildJsonObject {
        put("id", songId)
        put("songmid", songId)
        put("name", song.name)
        put("singer", song.ar.joinToString("/") { it.name })
        put("albumName", song.al.name)
        put("interval", "%02d:%02d".format(song.dt / 60000, (song.dt / 1000) % 60))
    }.toString()

    /**
     * NCM 档位 → LX 脚本档位的候选序列。
     *
     * 脚本的档位是 LX 协议那一套：128k / 320k / flac / flac24bit（部分脚本额外支持 hires/master）。
     * **192k、exhigh、standard 不是脚本档位**，发过去脚本内部没有对应分支，会一直挂着等上游，
     * 直到单次解析超时——日志特征就是 `quality=192k cost=12000ms error=无响应/超时`，
     * 紧接着 `quality=320k cost≈100ms` 命中。所以 192k/higher 在这里直接落到 320k。
     */
    private fun getQualityCandidates(songLevel: String): List<String> = when (songLevel.lowercase()) {
        "jymaster", "master" -> listOf("master", "jymaster", "flac24bit", "hires", "flac", "320k", "128k")
        "dolby", "atmos", "atmos_plus" -> listOf("atmos_plus", "atmos", "dolby", "flac24bit", "hires", "flac", "320k")
        "hires", "flac24bit" -> listOf("flac24bit", "hires", "flac", "320k", "128k")
        "lossless", "flac" -> listOf("flac", "320k", "128k")
        "higher", "192k", "exhigh", "320k" -> listOf("320k", "128k")
        else -> listOf("128k")
    }

    /**
     * 叠加脚本自身能力（`inited` 上报的 `wyQualities`）后，这个脚本实际要试的档位。
     * 脚本没上报过能力（老条目）时按静态候选表来，不做过滤。
     */
    private fun candidatesFor(item: LxPluginItem, wanted: List<String>): List<String> {
        val advertised = item.wyQualities
        if (advertised.isEmpty()) return wanted
        return wanted.filter { it in advertised }.ifEmpty { wanted }
    }

    private fun mapQuality(songLevel: String): String = getQualityCandidates(songLevel).first()

    /** 脚本头部注释（照 Melodia LxScriptSupport 的 @name/@version/@author/@description）。 */
    private data class ScriptHeader(
        val name: String,
        val version: String,
        val author: String,
        val description: String
    )

    private fun parseHeader(script: String): ScriptHeader {
        var name = "未命名脚本"
        var version = ""
        var author = ""
        var desc = ""
        for (line in script.lines().take(50)) {
            val t = line.trim()
            when {
                t.contains("@name") -> name = t.substringAfter("@name").trim()
                t.contains("@version") -> version = t.substringAfter("@version").trim()
                t.contains("@author") -> author = t.substringAfter("@author").trim()
                t.contains("@description") -> desc = t.substringAfter("@description").trim()
            }
        }
        return ScriptHeader(name, version, author, desc)
    }

    /**
     * 智能获取当前全部启用音源支持的网易云档位集合。
     */
    fun getSupportedWyQualities(): Set<String> {
        val enabled = LxPluginStore.latest.value.filter { it.isEnabled }
        if (enabled.isEmpty()) {
            return setOf("128k", "320k", "flac", "flac24bit", "hires")
        }
        val set = mutableSetOf<String>()
        for (item in enabled) {
            set.addAll(item.wyQualities)
        }
        return if (set.isEmpty()) setOf("128k", "320k", "flac", "flac24bit", "hires") else set
    }

    private const val MAX_WARM_PLUGINS = 5

    /** 单次起播的解析总预算；超了就回落官方直链，不把点歌拖成十几秒。 */
    private const val RESOLVE_BUDGET_MS = 8_000L
}