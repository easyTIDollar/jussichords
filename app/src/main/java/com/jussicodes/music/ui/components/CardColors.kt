package com.jussicodes.music.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import com.jussicodes.music.data.HomeTransparentCardStore

/**
 * 卡片容器颜色：「主页透明卡片」开关开 = 全透明，关 = surfaceContainerLow（与改前一致）。
 * 开关值走进程级 HomeTransparentCardStore（跨 tab 切换稳定，首帧无默认值闪跳）。
 * 用于收藏歌单卡 / 歌单组卡 / 专辑墙。
 */
@Composable
fun transparentCardColor(): Color {
    val on = HomeTransparentCardStore.state.collectAsState().value
    return if (on) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow
}

/**
 * 用户信息卡容器颜色：开 = 薄玻璃（极淡、随深浅色自适应），关 = surfaceContainerLow。
 *
 * 深色下不能用近白（纯白 0.18 会读成"闪白"：从探索划回主页时那块亮白玻璃带从边缘扫过
 * 视野，就是用户看到的"闪白一下"）。改成极淡的中性玻璃（深色微亮 0.06 / 浅色微暗 0.05），
 * 既保留"晶莹剔透"的薄玻璃感，又不是一抹白雾。
 */
@Composable
fun userCardGlassColor(): Color {
    val on = HomeTransparentCardStore.state.collectAsState().value
    val isDark = isSystemInDarkTheme()
    return when {
        !on -> MaterialTheme.colorScheme.surfaceContainerLow
        isDark -> Color(0xFFFFFFFF).copy(alpha = 0.06f)
        else -> Color(0xFF1B1B1F).copy(alpha = 0.05f)
    }
}
