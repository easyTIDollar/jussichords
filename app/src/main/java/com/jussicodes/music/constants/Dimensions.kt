package com.jussicodes.music.constants

import androidx.compose.animation.core.Easing
import androidx.compose.ui.unit.dp
import com.jussicodes.music.animation.MotionDuration
import com.jussicodes.music.animation.MotionEasing

val ListItemHeight = 64.dp
val ListThumbnailSize = 48.dp
val ThumbnailCornerRadius = 8.dp
val GridThumbnailHeight = 128.dp
val MediaItemHeight = 72.dp
val AlbumThumbnailSize = 144.dp
val MiniPlayerHeight = 64.dp
val BottomNavigationHeight = 68.dp
val PlaylistThumbnailSize = 200.dp
val SettingItemHeight = 64.dp
val SettingItemCorner = 16.dp
val SettingItemSubCorner = 4.dp

// 动效常量已迁至 animation/MotionTokens.kt（语义化命名）。这里保留旧名作为兼容层，
// 数值与旧实现完全一致，保证迁移期间行为不变；新代码请直接用 MotionDuration / MotionEasing。
const val DURATION = MotionDuration.SharedArtworkBounds
const val DURATION_ENTER = MotionDuration.ScreenEnter
const val DURATION_EXIT = MotionDuration.PanelFadeIn
const val DURATION_EXIT_SHORT = MotionDuration.ScreenExit

val EmphasizedEasing: Easing = MotionEasing.emphasizedPath
val EmphasizedDecelerateEasing = MotionEasing.decelerate
val EmphasizedAccelerateEasing = MotionEasing.accelerate
