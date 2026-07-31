package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val DesktopLyrics: ImageVector
    get() {
        if (_DesktopLyrics != null) {
            return _DesktopLyrics!!
        }
        _DesktopLyrics = ImageVector.Builder(
            name = "DesktopLyrics",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 5f)
                curveTo(4f, 3.9f, 4.9f, 3f, 6f, 3f)
                horizontalLineTo(18f)
                curveTo(19.1f, 3f, 20f, 3.9f, 20f, 5f)
                verticalLineTo(14f)
                curveTo(20f, 15.1f, 19.1f, 16f, 18f, 16f)
                horizontalLineTo(9.2f)
                lineTo(5f, 20f)
                verticalLineTo(16f)
                horizontalLineTo(6f)
                curveTo(4.9f, 16f, 4f, 15.1f, 4f, 14f)
                close()
            }
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(8f, 8f)
                horizontalLineTo(16f)
                verticalLineTo(9.6f)
                horizontalLineTo(8f)
                close()
                moveTo(8f, 11.2f)
                horizontalLineTo(16f)
                verticalLineTo(12.8f)
                horizontalLineTo(8f)
                close()
                moveTo(8f, 14.4f)
                horizontalLineTo(13.5f)
                verticalLineTo(16f)
                horizontalLineTo(8f)
                close()
            }
        }.build()

        return _DesktopLyrics!!
    }

@Suppress("ObjectPropertyName")
private var _DesktopLyrics: ImageVector? = null
