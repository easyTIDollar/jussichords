package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "delete"（outlined，24dp）官方矢量数据。
val Delete: ImageVector
    get() {
        if (_Delete != null) {
            return _Delete!!
        }
        _Delete = ImageVector.Builder(
            name = "Delete",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(16f, 9f)
                lineTo(16f, 19f)
                lineTo(8f, 19f)
                lineTo(8f, 9f)
                lineTo(16f, 9f)
                moveTo(14.5f, 3f)
                lineTo(9.5f, 3f)
                lineTo(8.5f, 4f)
                lineTo(5f, 4f)
                lineTo(5f, 6f)
                lineTo(19f, 6f)
                lineTo(19f, 4f)
                lineTo(15.5f, 4f)
                lineTo(14.5f, 3f)
                close()
                moveTo(18f, 7f)
                lineTo(6f, 7f)
                lineTo(6f, 19f)
                curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f)
                lineTo(16f, 21f)
                curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f)
                lineTo(18f, 7f)
                close()
            }
        }.build()

        return _Delete!!
    }

@Suppress("ObjectPropertyName")
private var _Delete: ImageVector? = null
