package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "emoji_events"（outlined，24dp）官方矢量数据。
val Crown: ImageVector
    get() {
        if (_Crown != null) {
            return _Crown!!
        }
        _Crown = ImageVector.Builder(
            name = "Crown",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(19f, 5f)
                lineTo(17f, 5f)
                lineTo(17f, 3f)
                lineTo(7f, 3f)
                lineTo(7f, 5f)
                lineTo(5f, 5f)
                curveTo(3.9f, 5f, 3f, 5.9f, 3f, 7f)
                lineTo(3f, 8f)
                curveTo(3f, 10.55f, 4.92f, 12.63f, 7.39f, 12.94f)
                arcTo(5.01f, 5.01f, 0f, false, false, 11f, 15.9f)
                lineTo(11f, 19f)
                lineTo(7f, 19f)
                lineTo(7f, 21f)
                lineTo(17f, 21f)
                lineTo(17f, 19f)
                lineTo(13f, 19f)
                lineTo(13f, 15.9f)
                arcTo(5.01f, 5.01f, 0f, false, false, 16.61f, 12.94f)
                curveTo(19.08f, 12.63f, 21f, 10.55f, 21f, 8f)
                lineTo(21f, 7f)
                curveTo(21f, 5.9f, 20.1f, 5f, 19f, 5f)
                close()
                moveTo(5f, 8f)
                lineTo(5f, 7f)
                lineTo(7f, 7f)
                lineTo(7f, 10.82f)
                curveTo(5.84f, 10.4f, 5f, 9.3f, 5f, 8f)
                close()
                moveTo(12f, 14f)
                curveTo(10.35f, 14f, 9f, 12.65f, 9f, 11f)
                lineTo(9f, 5f)
                lineTo(15f, 5f)
                lineTo(15f, 11f)
                curveTo(15f, 12.65f, 13.65f, 14f, 12f, 14f)
                close()
                moveTo(19f, 8f)
                curveTo(19f, 9.3f, 18.16f, 10.4f, 17f, 10.82f)
                lineTo(17f, 7f)
                lineTo(19f, 7f)
                lineTo(19f, 8f)
                close()
            }
        }.build()

        return _Crown!!
    }

@Suppress("ObjectPropertyName")
private var _Crown: ImageVector? = null
