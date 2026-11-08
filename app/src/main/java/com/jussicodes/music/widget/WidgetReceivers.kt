package com.jussicodes.music.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 桌面上的四个小组件，每个是独立条目，尺寸规格固定：
 * 4×2 双列卡 / 4×1 横条 / 2×2 方块 / 2×1 胶囊。
 *
 * 拆成四个 receiver 而不是一个自适应组件，是因为用户在桌面选择器里要能直接选到
 * 「4×2」「4×1」这种明确规格；共用一个组件时只能靠拉伸，观感不可控。
 */
class CardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CardWidget()
}

class BannerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BannerWidget()
}

class TileWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TileWidget()
}

class CapsuleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CapsuleWidget()
}

/** 四款 receiver 的注册表：刷新、实例探测都按这张表走。 */
internal object WidgetRegistry {
    val all: List<Pair<GlanceAppWidget, Class<out GlanceAppWidgetReceiver>>> = listOf(
        CardWidget() to CardWidgetReceiver::class.java,
        BannerWidget() to BannerWidgetReceiver::class.java,
        TileWidget() to TileWidgetReceiver::class.java,
        CapsuleWidget() to CapsuleWidgetReceiver::class.java,
    )
}
