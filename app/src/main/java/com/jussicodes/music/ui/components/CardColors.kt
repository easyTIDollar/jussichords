package com.jussicodes.music.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
 * 用户信息卡容器色：开 = 轻透磨砂薄玻璃，关 = surfaceContainerLow（与改前一致）。
 *
 * 玻璃配方（与 ArtworkGlassBackdrop 同一套视觉语言，保证全 App 玻璃质感一致）：
 *   1. 底色 surface：不选纯白/纯黑，选主题 surface，深色主题下不会发灰发闷；
 *   2. 主色 tint：极低 alpha 的 primary，让卡片随壁纸/封面取色走，有「染色玻璃」感；
 *   3. 整体 alpha 压得很低（深色 0.30 / 浅色 0.42）：轻透，背景图若隐若现，
 *      同时保证 onSurface 文字的对比度足够（不会出现白字压白底）。
 *
 * 历史问题：旧实现深色用 Color(0xFFFFFFFF).copy(alpha = 0.18f)，白色 alpha 在
 * 深色背景上显得发灰发脏，浅色主题下又几乎不可见。改为 surface + primary tint
 * 后，深浅色都有稳定的玻璃观感，且 alpha 高于旧值，不会再出现「几乎看不见」。
 */
@Composable
fun userCardGlassColor(): Color {
    val on = HomeTransparentCardStore.state.collectAsState().value
    if (!on) return MaterialTheme.colorScheme.surfaceContainerLow
    val isDark = isSystemInDarkTheme()
    val surface = MaterialTheme.colorScheme.surface
    val tint = MaterialTheme.colorScheme.primary
    return if (isDark) {
        // 深色：surface 为主、primary 轻染，整体 0.30 —— 透得见背景但不吃对比度
        tint.copy(alpha = 0.05f).compositeOver(surface.copy(alpha = 0.30f))
    } else {
        // 浅色：surface 为主、primary 稍重，整体 0.42 —— 白底上玻璃需要更实一点
        tint.copy(alpha = 0.08f).compositeOver(surface.copy(alpha = 0.42f))
    }
}
