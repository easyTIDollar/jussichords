package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "arrow_downward"（outlined，24dp）官方矢量数据。
val Down: ImageVector
    get() {
        if (_Down != null) {
            return _Down!!
        }
        _Down = ImageVector.Builder(
            name = "Down",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(20f, 12f)
                lineTo(18.59f, 10.59f)
                lineTo(13f, 16.17f)
                lineTo(13f, 4f)
                lineTo(11f, 4f)
                lineTo(11f, 16.17f)
                lineTo(5.42f, 10.58f)
                lineTo(4f, 12f)
                lineTo(12f, 20f)
                lineTo(20f, 12f)
                close()
            }
        }.build()

        return _Down!!
    }

@Suppress("ObjectPropertyName")
private var _Down: ImageVector? = null
