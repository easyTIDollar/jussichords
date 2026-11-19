package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "keyboard_arrow_down"（outlined，24dp）官方矢量数据。
val ChevronDown: ImageVector
    get() {
        if (_ChevronDown != null) {
            return _ChevronDown!!
        }
        _ChevronDown = ImageVector.Builder(
            name = "ChevronDown",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(7.41f, 8.59f)
                lineTo(12f, 13.17f)
                lineTo(16.59f, 8.59f)
                lineTo(18f, 10f)
                lineTo(12f, 16f)
                lineTo(6f, 10f)
                lineTo(7.41f, 8.59f)
                close()
            }
        }.build()

        return _ChevronDown!!
    }

@Suppress("ObjectPropertyName")
private var _ChevronDown: ImageVector? = null
