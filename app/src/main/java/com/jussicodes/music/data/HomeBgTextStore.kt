package com.jussicodes.music.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.jussicodes.music.constants.homeBgTextColorKey
import com.jussicodes.music.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主页文字颜色（"auto"/"white"/"black"，进程级，与 HomeTransparentCardStore 同款模式）：
 * rememberPreference 每次重新组合首帧出默认值、下一帧才回流真值，切「我的↔探索」时
 * 卡片/正文文字色会闪回默认几帧。值提到进程级 StateFlow，Application 里 watcher
 * 从 DataStore 灌入，UI 读进程级值，跨 tab 切换稳定、首帧即真值。
 */
object HomeBgTextStore {

    private val _state = MutableStateFlow("auto")
    val state: StateFlow<String> = _state.asStateFlow()

    private val watcherScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 启动时调用一次：持续跟 DataStore 的颜色设置回流进 [state]。 */
    fun watch(context: Context) {
        val ds = context.applicationContext.dataStore
        watcherScope.launch {
            ds.data.collect { prefs ->
                _state.value = prefs[homeBgTextColorKey] ?: "auto"
            }
        }
    }

    /** 写颜色设置（DataStore）；watcher 回流后 [state] 自动跟上，UI 无需手动 set。 */
    suspend fun set(context: Context, value: String) {
        context.applicationContext.dataStore.edit { it[homeBgTextColorKey] = value }
    }
}
