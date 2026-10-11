package com.jussicodes.music.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.jussicodes.music.constants.homeTransparentCardKey
import com.jussicodes.music.utils.rememberPreference

/**
 * 卡片容器颜色：「主页透明卡片」开关开 = 全透明，关 = surfaceContainerLow（与改前一致）。
 * 用于收藏歌单卡 / 歌单组卡 / 专辑墙。
 */
@Composable
fun transparentCardColor(): Color {
    val on by rememberPreference(homeTransparentCardKey, false)
    return if (on) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow
}

/**
 * 用户信息卡容器颜色：开 = 薄玻璃（深色近白 / 浅色半透明深灰，随深浅色自适应），关 = surfaceContainerLow。
 */
@Composable
fun userCardGlassColor(): Color {
    val on by rememberPreference(homeTransparentCardKey, false)
    val isDark = isSystemInDarkTheme()
    return when {
        !on -> MaterialTheme.colorScheme.surfaceContainerLow
        isDark -> Color(0xFFFFFFFF).copy(alpha = 0.18f)
        else -> Color(0xFF1B1B1F).copy(alpha = 0.12f)
    }
}
