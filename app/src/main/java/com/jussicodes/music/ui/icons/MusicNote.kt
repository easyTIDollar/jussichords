package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "music_note"（outlined，24dp）官方矢量数据。
val MusicNote: ImageVector
    get() {
        if (_MusicNote != null) {
            return _MusicNote!!
        }
        _MusicNote = ImageVector.Builder(
            name = "MusicNote",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(12f, 3f)
                lineTo(12.01f, 13.55f)
                curveTo(11.42f, 13.21f, 10.74f, 13f, 10.01f, 13f)
                arcTo(4.001f, 4.001f, 0f, true, false, 10.01f, 21f)
                curveTo(12.23f, 21f, 14f, 19.21f, 14f, 17f)
                lineTo(14f, 7f)
                lineTo(18f, 7f)
                lineTo(18f, 3f)
                lineTo(12f, 3f)
                close()
                moveTo(10.01f, 19f)
                curveTo(8.91f, 19f, 8.01f, 18.1f, 8.01f, 17f)
                curveTo(8.01f, 15.9f, 8.91f, 15f, 10.01f, 15f)
                curveTo(11.11f, 15f, 12.01f, 15.9f, 12.01f, 17f)
                curveTo(12.01f, 18.1f, 11.11f, 19f, 10.01f, 19f)
                close()
            }
        }.build()

        return _MusicNote!!
    }

@Suppress("ObjectPropertyName")
private var _MusicNote: ImageVector? = null
