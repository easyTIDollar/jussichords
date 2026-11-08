package com.jussicodes.music.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 桌面组件唯一的 receiver。
 *
 * 一款组件三种尺寸（4×2 / 2×2 / 4×1），不再按外观拆成多个 entry——
 * 桌面的组件列表里只出现一个「Jussi 播放器」条目，避免用户面对三个近似选项。
 */
class MusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = JussiMusicWidget()
}
