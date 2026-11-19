package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "palette"（outlined，24dp）官方矢量数据。
val Palette: ImageVector
    get() {
        if (_Palette != null) {
            return _Palette!!
        }
        _Palette = ImageVector.Builder(
            name = "Palette",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(12f, 22f)
                curveTo(6.49f, 22f, 2f, 17.51f, 2f, 12f)
                curveTo(2f, 6.49f, 6.49f, 2f, 12f, 2f)
                curveTo(17.51f, 2f, 22f, 6.04f, 22f, 11f)
                curveTo(22f, 14.31f, 19.31f, 17f, 16f, 17f)
                lineTo(14.23f, 17f)
                curveTo(13.95f, 17f, 13.73f, 17.22f, 13.73f, 17.5f)
                curveTo(13.73f, 17.62f, 13.78f, 17.73f, 13.86f, 17.83f)
                curveTo(14.27f, 18.3f, 14.5f, 18.89f, 14.5f, 19.5f)
                arcTo(2.5f, 2.5f, 0f, false, true, 12f, 22f)
                close()
                moveTo(12f, 4f)
                curveTo(7.59f, 4f, 4f, 7.59f, 4f, 12f)
                curveTo(4f, 16.41f, 7.59f, 20f, 12f, 20f)
                curveTo(12.28f, 20f, 12.5f, 19.78f, 12.5f, 19.5f)
                arcTo(0.54f, 0.54f, 0f, false, false, 12.36f, 19.15f)
                curveTo(11.95f, 18.69f, 11.73f, 18.1f, 11.73f, 17.5f)
                arcTo(2.5f, 2.5f, 0f, false, true, 14.23f, 15f)
                lineTo(16f, 15f)
                curveTo(18.21f, 15f, 20f, 13.21f, 20f, 11f)
                curveTo(20f, 7.14f, 16.41f, 4f, 12f, 4f)
                close()
            }
        }.build()

        return _Palette!!
    }

@Suppress("ObjectPropertyName")
private var _Palette: ImageVector? = null
