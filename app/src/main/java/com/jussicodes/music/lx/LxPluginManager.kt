package com.jussicodes.music.lx

import android.content.Context
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
            scope.launch { ensureBuiltinScript(context.applicationContext) }
        }
    }

    /**
     * 内置 LX 脚本：打进 assets 的「玉宁熙 Pro」，随应用分发，用户无需自己导入。
     * 版本号变化才覆盖，避免每次启动重写 DataStore。
     */
    const val BUILTIN_ID = "builtin-lx-yuanningxi"
    private const val BUILTIN_ASSET = "lx/lx-builtin.js"

    private suspend fun ensureBuiltinScript(context: Context) {
        // 注意：这里必须用 snapshot()（等磁盘读完）判断，不能用 latest.value 空镜像，
        // 否则启动瞬间读到空列表 → 把整份脚本列表覆盖成只剩内置脚本（用户脚本全丢）。
        if (LxPluginStore.snapshot().any { it.id == BUILTIN_ID }) return
        val script = runCatching {
            context.assets.open(BUILTIN_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return
        LxPluginStore.update { list ->
            if (list.any { it.id == BUILTIN_ID }) list
            else list + LxPluginItem(
                id = BUILTIN_ID,
                name = "玉宁熙 Pro（内置）",
                rawScript = script,
                isEnabled = true
            )
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
        val sources = runCatching { engine?.verify(candidate).orEmpty() }.getOrDefault(emptyList())
        val item = candidate.copy(sources = sources)
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

    /** 批量删除；内置脚本不参与删除。 */
    suspend fun removeAll(ids: Collection<String>) {
        val targets = ids.filter { it != BUILTIN_ID }
        if (targets.isEmpty()) return
        targets.forEach { engine?.deactivate(it) }
        LxPluginStore.update { list -> list.filterNot { it.id in targets } }
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
            android.util.Log.w(TAG, "no enabled plugin, skip lx songId=$songId")
            return null
        }
        // 元信息取不到就不试解析；网络抖动导致的单次失败很常见，重试一次再放弃。
        val song = fetchSong(songId) ?: fetchSong(songId)
        if (song == null) {
            android.util.Log.w(TAG, "song detail unavailable after 2 attempts, skip lx songId=$songId")
            return null
        }
        val musicInfo = buildMusicInfo(songId, song)
        val quality = mapQuality(songLevel)
        for (item in enabled) {
            val t0 = System.currentTimeMillis()
            val url = engine?.resolveUrl(item.id, musicInfo, quality)
            val cost = System.currentTimeMillis() - t0
            when {
                url.isNullOrBlank() ->
                    android.util.Log.w(TAG, "plugin '${item.name}' miss songId=$songId quality=$quality cost=${cost}ms")
                // 脚本降级到官方试听链时不能当命中，继续下一个插件。
                url.contains("musicrep-ts", ignoreCase = true) ->
                    android.util.Log.w(TAG, "plugin '${item.name}' returned trial clip, try next songId=$songId")
                // 播放器侧时长校验判定这条链不是这首歌的完整音频：跳过它，换下一个脚本。
                PlayUrlSkip.blocks(skipUrls, url) ->
                    android.util.Log.w(
                        TAG,
                        "plugin '${item.name}' url rejected by duration check, try next songId=$songId"
                    )
                else -> {
                    android.util.Log.i(TAG, "plugin '${item.name}' hit songId=$songId quality=$quality cost=${cost}ms")
                    return url
                }
            }
        }
        android.util.Log.w(
            TAG,
            "all plugins missed songId=$songId quality=$quality plugins=${enabled.size} " +
                "total=${System.currentTimeMillis() - startedAt}ms"
        )
        return null
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

    private fun mapQuality(songLevel: String): String = when (songLevel) {
        "standard" -> "128k"
        "higher" -> "128k"
        "exhigh" -> "320k"
        "lossless" -> "flac"
        else -> "flac24bit"
    }

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

    private const val MAX_WARM_PLUGINS = 5
}