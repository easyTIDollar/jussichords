package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "explore"（outlined，24dp）官方矢量数据。
val ExploreCompass: ImageVector
    get() {
        if (_ExploreCompass != null) {
            return _ExploreCompass!!
        }
        _ExploreCompass = ImageVector.Builder(
            name = "ExploreCompass",
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
                moveTo(6.5f, 17.5f)
                lineTo(14.01f, 14.01f)
                lineTo(17.5f, 6.5f)
                lineTo(9.99f, 9.99f)
                lineTo(6.5f, 17.5f)
                close()
                moveTo(12f, 10.9f)
                curveTo(12.61f, 10.9f, 13.1f, 11.39f, 13.1f, 12f)
                curveTo(13.1f, 12.61f, 12.61f, 13.1f, 12f, 13.1f)
                curveTo(11.39f, 13.1f, 10.9f, 12.61f, 10.9f, 12f)
                curveTo(10.9f, 11.39f, 11.39f, 10.9f, 12f, 10.9f)
                close()
            }
        }.build()

        return _ExploreCompass!!
    }

@Suppress("ObjectPropertyName")
private var _ExploreCompass: ImageVector? = null
