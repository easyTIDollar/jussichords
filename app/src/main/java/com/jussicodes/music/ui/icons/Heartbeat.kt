package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "monitor_heart"（outlined，24dp）官方矢量数据。
val Heartbeat: ImageVector
    get() {
        if (_Heartbeat != null) {
            return _Heartbeat!!
        }
        _Heartbeat = ImageVector.Builder(
            name = "Heartbeat",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(20f, 4f)
                lineTo(4f, 4f)
                curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
                lineTo(2f, 9f)
                lineTo(4f, 9f)
                lineTo(4f, 6f)
                lineTo(20f, 6f)
                lineTo(20f, 9f)
                lineTo(22f, 9f)
                lineTo(22f, 6f)
                curveTo(22f, 4.9f, 21.1f, 4f, 20f, 4f)
                close()
                moveTo(20f, 18f)
                lineTo(4f, 18f)
                lineTo(4f, 15f)
                lineTo(2f, 15f)
                lineTo(2f, 18f)
                curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
                lineTo(20f, 20f)
                curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
                lineTo(22f, 15f)
                lineTo(20f, 15f)
                lineTo(20f, 18f)
                close()
            }
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(14.89f, 7.55f)
                curveTo(14.55f, 6.87f, 13.44f, 6.87f, 13.1f, 7.55f)
                lineTo(10f, 13.76f)
                lineTo(8.89f, 11.55f)
                arcTo(0.988f, 0.988f, 0f, false, false, 8f, 11f)
                lineTo(2f, 11f)
                lineTo(2f, 13f)
                lineTo(7.38f, 13f)
                lineTo(9.1f, 16.45f)
                curveTo(9.28f, 16.79f, 9.62f, 17f, 10f, 17f)
                curveTo(10.38f, 17f, 10.72f, 16.79f, 10.89f, 16.45f)
                lineTo(14f, 10.24f)
                lineTo(15.11f, 12.45f)
                curveTo(15.28f, 12.79f, 15.62f, 13f, 16f, 13f)
                lineTo(22f, 13f)
                lineTo(22f, 11f)
                lineTo(16.62f, 11f)
                lineTo(14.89f, 7.55f)
                close()
            }
        }.build()

        return _Heartbeat!!
    }

@Suppress("ObjectPropertyName")
private var _Heartbeat: ImageVector? = null
