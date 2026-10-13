package com.jussicodes.music.lx

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jussicodes.music.utils.dataStore
import com.rcmiku.ncmapi.utils.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
 * 内存镜像 [latest] 供 UI 直接 collect；写盘顺序为「内存先生效、磁盘异步跟上」。
 */
object LxPluginStore {

    private val KEY = stringPreferencesKey("lxPlugins")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _latest = MutableStateFlow<List<LxPluginItem>>(emptyList())
    val latest: StateFlow<List<LxPluginItem>> = _latest.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var flow: Flow<List<LxPluginItem>>? = null

    /** Application 里调用一次；重复调用忽略。 */
    fun attach(context: Context) {
        if (flow != null) return
        val ctx = context.applicationContext
        appContext = ctx
        flow = ctx.dataStore.data
            .map { prefs ->
                val raw = prefs[KEY]
                if (raw == null) emptyList()
                else runCatching { json.decodeFromString<List<LxPluginItem>>(raw) }.getOrDefault(emptyList())
            }
            .distinctUntilChanged()
        scope.launch {
            val f = flow!!
            // 先把上次的内容灌进内存（管理页首帧就有），之后持续跟 DataStore 回流。
            _latest.value = f.first()
            f.collect { _latest.value = it }
        }
    }

    fun save(items: List<LxPluginItem>) {
        _latest.value = items
        scope.launch {
            val ctx = appContext ?: return@launch
            ctx.dataStore.edit { it[KEY] = json.encodeToString(items) }
        }
    }
}