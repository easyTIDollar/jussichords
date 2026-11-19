package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "group"（outlined，24dp）官方矢量数据。
val Users: ImageVector
    get() {
        if (_Users != null) {
            return _Users!!
        }
        _Users = ImageVector.Builder(
            name = "Users",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(9f, 13.75f)
                curveTo(6.66f, 13.75f, 2f, 14.92f, 2f, 17.25f)
                lineTo(2f, 19f)
                lineTo(16f, 19f)
                lineTo(16f, 17.25f)
                curveTo(16f, 14.92f, 11.34f, 13.75f, 9f, 13.75f)
                close()
                moveTo(4.34f, 17f)
                curveTo(5.18f, 16.42f, 7.21f, 15.75f, 9f, 15.75f)
                curveTo(10.79f, 15.75f, 12.82f, 16.42f, 13.66f, 17f)
                lineTo(4.34f, 17f)
                close()
                moveTo(9f, 12f)
                curveTo(10.93f, 12f, 12.5f, 10.43f, 12.5f, 8.5f)
                curveTo(12.5f, 6.57f, 10.93f, 5f, 9f, 5f)
                curveTo(7.07f, 5f, 5.5f, 6.57f, 5.5f, 8.5f)
                curveTo(5.5f, 10.43f, 7.07f, 12f, 9f, 12f)
                close()
                moveTo(9f, 7f)
                curveTo(9.83f, 7f, 10.5f, 7.67f, 10.5f, 8.5f)
                curveTo(10.5f, 9.33f, 9.83f, 10f, 9f, 10f)
                curveTo(8.17f, 10f, 7.5f, 9.33f, 7.5f, 8.5f)
                curveTo(7.5f, 7.67f, 8.17f, 7f, 9f, 7f)
                close()
                moveTo(16.04f, 13.81f)
                curveTo(17.2f, 14.65f, 18f, 15.77f, 18f, 17.25f)
                lineTo(18f, 19f)
                lineTo(22f, 19f)
                lineTo(22f, 17.25f)
                curveTo(22f, 15.23f, 18.5f, 14.08f, 16.04f, 13.81f)
                close()
                moveTo(15f, 12f)
                curveTo(16.93f, 12f, 18.5f, 10.43f, 18.5f, 8.5f)
                curveTo(18.5f, 6.57f, 16.93f, 5f, 15f, 5f)
                curveTo(14.46f, 5f, 13.96f, 5.13f, 13.5f, 5.35f)
                curveTo(14.13f, 6.24f, 14.5f, 7.33f, 14.5f, 8.5f)
                curveTo(14.5f, 9.67f, 14.13f, 10.76f, 13.5f, 11.65f)
                curveTo(13.96f, 11.87f, 14.46f, 12f, 15f, 12f)
                close()
            }
        }.build()

        return _Users!!
    }

@Suppress("ObjectPropertyName")
private var _Users: ImageVector? = null
