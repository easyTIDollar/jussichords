package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "paid"（outlined，24dp）官方矢量数据。
val Money: ImageVector
    get() {
        if (_Money != null) {
            return _Money!!
        }
        _Money = ImageVector.Builder(
            name = "Money",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
                close()
                moveTo(12f, 20f)
                curveTo(7.59f, 20f, 4f, 16.41f, 4f, 12f)
                curveTo(4f, 7.59f, 7.59f, 4f, 12f, 4f)
                curveTo(16.41f, 4f, 20f, 7.59f, 20f, 12f)
                curveTo(20f, 16.41f, 16.41f, 20f, 12f, 20f)
                close()
                moveTo(12.89f, 11.1f)
                curveTo(11.11f, 10.51f, 10.25f, 10.14f, 10.25f, 9.2f)
                curveTo(10.25f, 8.18f, 11.36f, 7.81f, 12.06f, 7.81f)
                curveTo(13.37f, 7.81f, 13.85f, 8.8f, 13.96f, 9.15f)
                lineTo(15.54f, 8.48f)
                curveTo(15.39f, 8.04f, 14.72f, 6.57f, 12.88f, 6.25f)
                lineTo(12.88f, 5f)
                lineTo(11.13f, 5f)
                lineTo(11.13f, 6.26f)
                curveTo(8.53f, 6.82f, 8.51f, 9.11f, 8.51f, 9.22f)
                curveTo(8.51f, 11.49f, 10.76f, 12.13f, 11.86f, 12.53f)
                curveTo(13.44f, 13.09f, 14.14f, 13.6f, 14.14f, 14.56f)
                curveTo(14.14f, 15.69f, 13.09f, 16.17f, 12.16f, 16.17f)
                curveTo(10.34f, 16.17f, 9.82f, 14.3f, 9.76f, 14.08f)
                lineTo(8.1f, 14.75f)
                curveTo(8.73f, 16.94f, 10.38f, 17.53f, 11.12f, 17.71f)
                lineTo(11.12f, 19f)
                lineTo(12.87f, 19f)
                lineTo(12.87f, 17.76f)
                curveTo(13.39f, 17.67f, 15.89f, 17.17f, 15.89f, 14.54f)
                curveTo(15.9f, 13.15f, 15.29f, 11.93f, 12.89f, 11.1f)
                close()
            }
        }.build()

        return _Money!!
    }

@Suppress("ObjectPropertyName")
private var _Money: ImageVector? = null
