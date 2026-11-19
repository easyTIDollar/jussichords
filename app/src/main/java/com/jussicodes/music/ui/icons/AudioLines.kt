package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "graphic_eq"（outlined，24dp）官方矢量数据。
val AudioLines: ImageVector
    get() {
        if (_AudioLines != null) {
            return _AudioLines!!
        }
        _AudioLines = ImageVector.Builder(
            name = "AudioLines",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(7f, 18f)
                lineTo(9f, 18f)
                lineTo(9f, 6f)
                lineTo(7f, 6f)
                lineTo(7f, 18f)
                close()
                moveTo(11f, 22f)
                lineTo(13f, 22f)
                lineTo(13f, 2f)
                lineTo(11f, 2f)
                lineTo(11f, 22f)
                close()
                moveTo(3f, 14f)
                lineTo(5f, 14f)
                lineTo(5f, 10f)
                lineTo(3f, 10f)
                lineTo(3f, 14f)
                close()
                moveTo(15f, 18f)
                lineTo(17f, 18f)
                lineTo(17f, 6f)
                lineTo(15f, 6f)
                lineTo(15f, 18f)
                close()
                moveTo(19f, 10f)
                lineTo(19f, 14f)
                lineTo(21f, 14f)
                lineTo(21f, 10f)
                lineTo(19f, 10f)
                close()
            }
        }.build()

        return _AudioLines!!
    }

@Suppress("ObjectPropertyName")
private var _AudioLines: ImageVector? = null
