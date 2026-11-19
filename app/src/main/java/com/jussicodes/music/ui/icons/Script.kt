package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "description"（outlined，24dp）官方矢量数据。
val Script: ImageVector
    get() {
        if (_Script != null) {
            return _Script!!
        }
        _Script = ImageVector.Builder(
            name = "Script",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(8f, 16f)
                lineTo(16f, 16f)
                lineTo(16f, 18f)
                lineTo(8f, 18f)
                close()
                moveTo(8f, 12f)
                lineTo(16f, 12f)
                lineTo(16f, 14f)
                lineTo(8f, 14f)
                close()
                moveTo(14f, 2f)
                lineTo(6f, 2f)
                curveTo(4.9f, 2f, 4f, 2.9f, 4f, 4f)
                lineTo(4f, 20f)
                curveTo(4f, 21.1f, 4.89f, 22f, 5.99f, 22f)
                lineTo(18f, 22f)
                curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
                lineTo(20f, 8f)
                lineTo(14f, 2f)
                close()
                moveTo(18f, 20f)
                lineTo(6f, 20f)
                lineTo(6f, 4f)
                lineTo(13f, 4f)
                lineTo(13f, 9f)
                lineTo(18f, 9f)
                lineTo(18f, 20f)
                close()
            }
        }.build()

        return _Script!!
    }

@Suppress("ObjectPropertyName")
private var _Script: ImageVector? = null
