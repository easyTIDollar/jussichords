package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "queue_music"（outlined，24dp）官方矢量数据。
val PlaylistInsert: ImageVector
    get() {
        if (_PlaylistInsert != null) {
            return _PlaylistInsert!!
        }
        _PlaylistInsert = ImageVector.Builder(
            name = "PlaylistInsert",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(22f, 6f)
                lineTo(17f, 6f)
                lineTo(17f, 14.18f)
                curveTo(16.69f, 14.07f, 16.35f, 14f, 16f, 14f)
                curveTo(14.34f, 14f, 13f, 15.34f, 13f, 17f)
                curveTo(13f, 18.66f, 14.34f, 20f, 16f, 20f)
                curveTo(17.66f, 20f, 19f, 18.66f, 19f, 17f)
                lineTo(19f, 8f)
                lineTo(22f, 8f)
                lineTo(22f, 6f)
                close()
                moveTo(15f, 6f)
                lineTo(3f, 6f)
                lineTo(3f, 8f)
                lineTo(15f, 8f)
                lineTo(15f, 6f)
                close()
                moveTo(15f, 10f)
                lineTo(3f, 10f)
                lineTo(3f, 12f)
                lineTo(15f, 12f)
                lineTo(15f, 10f)
                close()
                moveTo(11f, 14f)
                lineTo(3f, 14f)
                lineTo(3f, 16f)
                lineTo(11f, 16f)
                lineTo(11f, 14f)
                close()
            }
        }.build()

        return _PlaylistInsert!!
    }

@Suppress("ObjectPropertyName")
private var _PlaylistInsert: ImageVector? = null
