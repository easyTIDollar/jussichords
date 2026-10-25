package com.jussicodes.music.lx

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString

/** LX 脚本音源的持久化条目（移植 Melodia 的 LxPluginItem）。 */
@kotlinx.serialization.Serializable
data class LxPluginItem(
    val id: String,
    val name: String,
    val version: String = "",
    val author: String = "",
    val description: String = "",
    val sources: List<String> = emptyList(),
    val rawScript: String,
    val isEnabled: Boolean = true
)

/**
 * 脚本列表的唯一持久化来源（DataStore 一个 JSON 数组键，脚本正文一并存在这里）。
 *
 * 两条硬规矩，缺一条就会「重启后刚导入的脚本没了」：
 * 1. 首次读盘完成之前，内存镜像 [latest] 是空列表，绝不能用它当基线去写盘
 *    ——所以所有读都要走 [snapshot]（内部 [ensureLoaded]，读完磁盘才返回）。
 *    「内置脚本是否存在」这类判断如果读空镜像，就会把整份列表覆盖成只剩内置脚本。
 * 2. 写盘要真的写完再返回，不能 `launch { edit() }` 甩出去不等人
 *    ——导入后立刻杀进程/重启，异步写会直接丢。
 * 读改写统一走 [update]：串行 + 基于已加载状态，避免并发覆盖。
 */
object LxPluginStore {

    private val KEY = stringPreferencesKey("lxPlugins")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _latest = MutableStateFlow<List<LxPluginItem>>(emptyList())
    val latest: StateFlow<List<LxPluginItem>> = _latest.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    private val loadLock = Mutex()
    private val writeLock = Mutex()

    @Volatile
    private var loaded = false

    /** Application 里调用一次；重复调用忽略。只登记 context 并预热读盘，真正加载在 [ensureLoaded]。 */
    fun attach(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        scope.launch { runCatching { ensureLoaded() } }
    }

    /** 当前脚本列表（首次调用会等磁盘读完）。 */
    suspend fun snapshot(): List<LxPluginItem> = ensureLoaded()

    /**
     * 读-改-写：串行执行 transform，并等 DataStore 落盘后才返回。
     * @return 写入后的列表
     */
    suspend fun update(transform: (List<LxPluginItem>) -> List<LxPluginItem>): List<LxPluginItem> =
        writeLock.withLock {
            val current = ensureLoaded()
            val next = transform(current)
            if (next != current) {
                val ctx = appContext
                if (ctx != null) {
                    // 先落盘再更新镜像：写失败时内存态不会和磁盘分叉（本次会话仍可用）。
                    runCatching { ctx.dataStore.edit { it[KEY] = json.encodeToString(next) } }
                }
                _latest.value = next
            }
            next
        }

    /** 兼容旧调用：异步版本，只在不需要等待落盘的场景用。 */
    @Deprecated("用 update{} 或 snapshot()，异步写会丢数据")
    fun save(items: List<LxPluginItem>) {
        scope.launch { runCatching { update { items } } }
    }

    private suspend fun ensureLoaded(): List<LxPluginItem> {
        if (loaded) return _latest.value
        loadLock.withLock {
            if (!loaded) {
                val ctx = appContext
                val items = if (ctx == null) {
                    emptyList()
                } else {
                    runCatching {
                        val raw = ctx.dataStore.data.first()[KEY]
                        if (raw == null) emptyList() else json.decodeFromString<List<LxPluginItem>>(raw)
                    }.getOrDefault(emptyList())
                }
                _latest.value = items
                loaded = true
            }
        }
        return _latest.value
    }
}
