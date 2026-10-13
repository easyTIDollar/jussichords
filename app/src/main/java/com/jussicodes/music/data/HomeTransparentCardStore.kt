package com.jussicodes.music.data

import android.content.Context
import com.jussicodes.music.constants.homeTransparentCardKey
import com.jussicodes.music.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.edit

/**
 * 主页透明卡片开关（进程级，与 PinnedAlbumStore.columnsState 同款模式）：
 * rememberPreference 每次重新组合首帧出默认值 false、下一帧才回流真值，
 * 探索划回主页时卡片容器色（实底↔透明）闪几帧。
 * 值提到进程级 StateFlow，Application 里 watcher 从 DataStore 灌入，
 * UI 读进程级值，跨 tab 切换稳定、首帧即真值。
 */
object HomeTransparentCardStore {

    private val _state = MutableStateFlow(false)
    val state: StateFlow<Boolean> = _state.asStateFlow()

    private val watcherScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 启动时调用一次：持续跟 DataStore 的开关回流进 [state]。 */
    fun watch(context: Context) {
        val ds = context.applicationContext.dataStore
        watcherScope.launch {
            ds.data.collect { prefs ->
                _state.value = prefs[homeTransparentCardKey] ?: false
            }
        }
    }

    /** 写开关（DataStore）；watcher 回流后 [state] 自动跟上，UI 无需手动 set。 */
    suspend fun set(context: Context, value: Boolean) {
        context.applicationContext.dataStore.edit { it[homeTransparentCardKey] = value }
    }
}
