package com.jussicodes.music.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 桌面上的两款小组件，每个是独立条目，尺寸固定且不可拉伸：
 * 4×1 横条 + 2×3 方块。
 *
 * 拆成独立 receiver 而不是一个自适应组件，是因为用户在桌面选择器里要能直接选到
 * 「4×1」「2×3」这种明确规格；共用一个组件时只能靠拉伸，观感不可控。
 */
class BannerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BannerWidget()
}

class TileWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TileWidget()
}

/** 两款 receiver 的注册表：刷新、实例探测都按这张表走。 */
internal object WidgetRegistry {
    val all: List<Pair<GlanceAppWidget, Class<out GlanceAppWidgetReceiver>>> = listOf(
        BannerWidget() to BannerWidgetReceiver::class.java,
        TileWidget() to TileWidgetReceiver::class.java,
    )

    /**
     * 每秒进度 tick 只推这些：两款布局里都画了进度线/进度条。
     *
     * 两款都得推——两款组件在桌面上各自独立，落下一款就会出现
     * 「同一个播放状态，横条的进度在走、方块的不动」。
     */
    val progressTickers: List<Pair<GlanceAppWidget, Class<out GlanceAppWidgetReceiver>>> = listOf(
        BannerWidget() to BannerWidgetReceiver::class.java,
        TileWidget() to TileWidgetReceiver::class.java,
    )
}
