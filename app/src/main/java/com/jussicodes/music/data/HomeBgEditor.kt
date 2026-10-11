package com.jussicodes.music.data

import android.content.Context
import com.jussicodes.music.constants.homeBgBlurKey
import com.jussicodes.music.constants.homeBgImageKey
import com.jussicodes.music.constants.homeBgOffsetXKey
import com.jussicodes.music.constants.homeBgOffsetYKey
import com.jussicodes.music.constants.homeBgOpacityKey
import com.jussicodes.music.constants.homeBgScaleKey
import com.jussicodes.music.ui.components.HomeBackgroundConfig
import com.jussicodes.music.utils.dataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主页背景"沉浸式编辑模式"的跨屏持有者（settings 选图 → 跳回主页调参 → 保存/取消）。
 *
 * 状态拆两层，编辑期间全程不产生 DataStore I/O 直到保存：
 * - [draft] 非 null = 编辑中。MainScreen 用它覆盖已提交配置驱动实时重绘。
 * - 已提交配置 = DataStore 原值（rememberHomeBackground 读），保存时一次性原子写入。
 *
 * 选图流程：HomeBgStore.select 成功得到的是【未提交的临时文件】，用 ownsFile 标记。
 * 取消时若 ownsFile，删掉这个临时文件并还原为 DataStore 旧配置；
 * "在主页调整"（改已提交配置）ownsFile=false，取消只丢弃参数、不动文件。
 */
object HomeBgEditor {
    private var _ownsFile = false
    private var _base: HomeBackgroundConfig? = null
    private val _draft = MutableStateFlow<HomeBackgroundConfig?>(null)
    /** null = 非编辑模式。 */
    val draft: StateFlow<HomeBackgroundConfig?> = _draft.asStateFlow()
    /** 进入编辑模式时的初始配置，"重置"回到它。 */
    val base: HomeBackgroundConfig? get() = _base

    /**
     * 进入编辑模式：以 [base] 建立 draft。
     * [ownsFile] = base.path 是本次选图产生的未提交临时文件（取消时删它）。
     */
    fun begin(base: HomeBackgroundConfig, ownsFile: Boolean) {
        _base = base
        _ownsFile = ownsFile
        _draft.value = base
    }

    /** 重置到编辑开始时的状态。 */
    fun reset() {
        _base?.let { _draft.value = it }
    }

    /** 编辑期间只改内存 draft（手势/滑块高频调用，零 I/O）。 */
    fun update(transform: (HomeBackgroundConfig) -> HomeBackgroundConfig) {
        val cur = _draft.value ?: return
        _draft.value = transform(cur)
    }

    /**
     * 保存：一次性把 6 个键原子写进 DataStore，再清掉 home_bg 里的旧临时文件，
     * 最后退出编辑模式。draft.path 为空（从未设置背景直接进编辑）不写盘。
     */
    fun commit(context: Context, scope: CoroutineScope) {
        val config = _draft.value ?: return
        val owns = _ownsFile
        _draft.value = null
        _base = null
        if (config.path.isBlank()) return
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[homeBgImageKey] = config.path
                prefs[homeBgScaleKey] = config.scale
                prefs[homeBgOpacityKey] = config.opacity
                prefs[homeBgBlurKey] = config.blur
                prefs[homeBgOffsetXKey] = config.offsetX
                prefs[homeBgOffsetYKey] = config.offsetY
            }
            // 已提交 = config.path；目录里其余文件都是历史临时图，清掉。
            if (owns) HomeBgStore.prune(context, config.path)
        }
    }

    /**
     * 取消：丢弃 draft，还原为 DataStore 原配置。
     * 若本次编辑引入了未提交临时文件（ownsFile），删掉它；否则文件不动。
     */
    fun cancel(context: Context) {
        val config = _draft.value ?: return
        val owns = _ownsFile
        _draft.value = null
        _base = null
        if (owns && config.path.isNotBlank()) {
            HomeBgStore.deleteFile(context, config.path)
        }
    }
}
