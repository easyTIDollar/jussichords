package com.jussicodes.music.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import com.jussicodes.music.data.HomeTransparentCardStore

/**
 * 卡片容器颜色：「主页透明卡片」开关开 = 全透明，关 = surfaceContainerLow（与改前一致）。
 * 开关值走进程级 HomeTransparentCardStore（跨 tab 切换稳定，首帧无默认值闪跳）。
 * 用于收藏歌单卡 / 歌单组卡 / 专辑墙。
 *
 * 这些卡在开关开启时是**全透明**的（背景图/壁纸直接透出），不需要玻璃配方，
 * 用 [Color.Transparent] 即可；磨砂与 tint 只给用户信息卡（见 [userCardGlassColor]）。
 */
@Composable
fun transparentCardColor(): Color {
    val on = HomeTransparentCardStore.state.collectAsState().value
    return if (on) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow
}

/**
 * 用户信息卡容器色：**开「卡片透明」= 全透明，关 = surfaceContainerLow**。
 *
 * 早期版本在开关开启时给信息卡上过一层 surface+primary 轻染的半透明「薄玻璃」
 * （深色 0.30 / 浅色 0.42），与其余透明卡（[transparentCardColor] 的纯透明）观感割裂：
 * 同一屏里只有这张卡发灰发闷。按用户要求，开关开启时信息卡不再做薄玻璃，
 * 与其他卡片一致地全透明。原先的玻璃配方说明保留在下面，需要时可直接恢复。
 *
 * 历史问题：旧实现深色用 Color(0xFFFFFFFF).copy(alpha = 0.18f)，白色 alpha 在
 * 深色背景上显得发灰发脏，浅色主题下又几乎不可见。
 */
@Composable
fun userCardGlassColor(): Color {
    val on = HomeTransparentCardStore.state.collectAsState().value
    return if (on) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow
}
