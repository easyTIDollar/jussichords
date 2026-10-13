package com.jussicodes.music.data

import android.content.Context
import com.jussicodes.music.constants.ncmCookieKey
import com.jussicodes.music.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * NCM 登录 cookie（进程级，HomeTransparentCardStore/HomeUserInfoStore 同款模式）。
 * LibraryScreen 原来用 rememberNullablePreference(ncmCookieKey) 读 cookie：
 * 那是冷流 collectAsState(initial = null)，而 LibraryScreen 每次"探索划回主页"
 * 都会随路由切换整块重组（两个 home 路由各自渲染 HomePager）——重组后首帧
 * cookie = null，先渲染一帧"点击登录"卡片、LazyColumn 整块不渲染，
 * 几帧后 DataStore 才回流真值 → 用户看到的"闪出未登录提示框 + 信息卡片闪白"。
 * 值提到进程级 StateFlow：Application 里 watcher 从 DataStore 持续灌入，
 * UI 读进程级值，跨 tab 切换/重组首帧即真值。
 */
object NcmCookieStore {

    private val _cookie = MutableStateFlow<String?>(null)
    val cookie: StateFlow<String?> = _cookie.asStateFlow()

    private val watcherScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 启动时调用一次：持续跟 DataStore 的 cookie 回流进 [cookie]。 */
    fun watch(context: Context) {
        val ds = context.applicationContext.dataStore
        watcherScope.launch {
            ds.data.collect { prefs ->
                _cookie.value = prefs[ncmCookieKey]
            }
        }
    }
}
