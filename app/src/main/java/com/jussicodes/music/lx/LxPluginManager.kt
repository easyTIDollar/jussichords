package com.jussicodes.music.lx

import android.content.Context
import com.rcmiku.ncmapi.api.apiGet
import com.rcmiku.ncmapi.model.Song
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
        if (LxPluginStore.latest.value.any { it.id == BUILTIN_ID }) return
        val script = runCatching {
            context.assets.open(BUILTIN_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return
        LxPluginStore.save(
            LxPluginStore.latest.value + LxPluginItem(
                id = BUILTIN_ID,
                name = "玉宁熙 Pro（内置）",
                rawScript = script,
                isEnabled = true
            )
        )
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
        val existing = LxPluginStore.latest.first().firstOrNull { it.name == item.name }
        return if (existing != null) {
            ImportResult.Duplicate(existing, item)
        } else {
            LxPluginStore.latest.first() + item
            ImportResult.Success(item)
        }
    }

    /** 确认导入：overwriteId 非空表示覆盖同名旧脚本。 */
    suspend fun confirmImport(newItem: LxPluginItem, overwriteId: String?) {
        val list = LxPluginStore.latest.first().toMutableList()
        val item = newItem.copy(id = overwriteId ?: newItem.id)
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx >= 0) list[idx] = item else list.add(item)
        LxPluginStore.save(list)
        engine?.deactivate(item.id)
    }

    suspend fun remove(id: String) {
        engine?.deactivate(id)
        LxPluginStore.save(LxPluginStore.latest.first().filterNot { it.id == id })
    }

    suspend fun toggle(id: String, enabled: Boolean) {
        LxPluginStore.save(
            LxPluginStore.latest.first().map { if (it.id == id) it.copy(isEnabled = enabled) else it }
        )
        if (!enabled) engine?.deactivate(id)
    }

    suspend fun setPriority(id: String, newIndex: Int) {
        val list = LxPluginStore.latest.first().toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val item = list.removeAt(idx)
        list.add(newIndex.coerceIn(0, list.size), item)
        LxPluginStore.save(list)
    }

    /** 预热：把已启用的插件脚本装进沙盒池（不命中时首播不再等建沙盒）。 */
    suspend fun warmUp() {
        val enabled = LxPluginStore.latest.first().filter { it.isEnabled }.take(MAX_WARM_PLUGINS)
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

    // ---- 解析 ----

    /**
     * 解析 NCM songId 的直链。按脚本列表顺序逐个试，全部未命中返回 null。
     * @param songLevel NCM 音质档位 value（standard/…/jymaster）
     */
    suspend fun resolveUrl(songId: String, songLevel: String): String? {
        val enabled = LxPluginStore.latest.first().filter { it.isEnabled }
        if (enabled.isEmpty()) return null
        val song = fetchSong(songId) ?: return null
        val musicInfo = buildMusicInfo(songId, song)
        val quality = mapQuality(songLevel)
        for (item in enabled) {
            val url = engine?.resolveUrl(item.id, musicInfo, quality)
            if (!url.isNullOrBlank()) return url
        }
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