package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "lyrics"（outlined，24dp）官方矢量数据。
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
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(6f, 6f)
                lineTo(13f, 6f)
                lineTo(13f, 8f)
                lineTo(6f, 8f)
                close()
                moveTo(6f, 12f)
                lineTo(10f, 12f)
                lineTo(10f, 14f)
                lineTo(6f, 14f)
                close()
            }
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(15f, 11.97f)
                lineTo(15f, 16f)
                lineTo(6f, 16f)
                lineTo(4f, 18f)
                lineTo(4f, 4f)
                lineTo(15f, 4f)
                lineTo(15f, 6.03f)
                curveTo(15.52f, 5.34f, 16.2f, 4.78f, 17f, 4.43f)
                lineTo(17f, 4f)
                curveTo(17f, 2.9f, 16.1f, 2f, 15f, 2f)
                lineTo(4f, 2f)
                curveTo(2.9f, 2f, 2.01f, 2.9f, 2.01f, 4f)
                lineTo(2f, 22f)
                lineTo(6f, 18f)
                lineTo(15f, 18f)
                curveTo(16.1f, 18f, 17f, 17.1f, 17f, 16f)
                lineTo(17f, 13.58f)
                arcTo(5.16f, 5.16f, 0f, false, true, 15f, 11.97f)
                close()
            }
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(6f, 9f)
                lineTo(13f, 9f)
                lineTo(13f, 11f)
                lineTo(6f, 11f)
                close()
                moveTo(20f, 6.18f)
                curveTo(19.69f, 6.07f, 19.35f, 6f, 19f, 6f)
                curveTo(17.34f, 6f, 16f, 7.34f, 16f, 9f)
                curveTo(16f, 10.66f, 17.34f, 12f, 19f, 12f)
                curveTo(20.66f, 12f, 22f, 10.66f, 22f, 9f)
                lineTo(22f, 3f)
                lineTo(24f, 3f)
                lineTo(24f, 1f)
                lineTo(20f, 1f)
                lineTo(20f, 6.18f)
                close()
            }
        }.build()

        return _DesktopLyrics!!
    }

@Suppress("ObjectPropertyName")
private var _DesktopLyrics: ImageVector? = null
