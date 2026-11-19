package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "radio"（outlined，24dp）官方矢量数据。
val PersonalRadio: ImageVector
    get() {
        if (_PersonalRadio != null) {
            return _PersonalRadio!!
        }
        _PersonalRadio = ImageVector.Builder(
            name = "PersonalRadio",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(20f, 6f)
                lineTo(8.3f, 6f)
                lineTo(16.56f, 2.66f)
                lineTo(15.88f, 1f)
                lineTo(3.24f, 6.15f)
                curveTo(2.51f, 6.43f, 2f, 7.17f, 2f, 8f)
                lineTo(2f, 20f)
                arcTo(2f, 2f, 0f, false, false, 4f, 22f)
                lineTo(20f, 22f)
                arcTo(2f, 2f, 0f, false, false, 22f, 20f)
                lineTo(22f, 8f)
                curveTo(22f, 6.89f, 21.11f, 6f, 20f, 6f)
                close()
                moveTo(20f, 8f)
                lineTo(20f, 11f)
                lineTo(18f, 11f)
                lineTo(18f, 9f)
                lineTo(16f, 9f)
                lineTo(16f, 11f)
                lineTo(4f, 11f)
                lineTo(4f, 8f)
                lineTo(20f, 8f)
                close()
                moveTo(4f, 20f)
                lineTo(4f, 13f)
                lineTo(20f, 13f)
                lineTo(20f, 20f)
                lineTo(4f, 20f)
                close()
            }
        }.build()

        return _PersonalRadio!!
    }

@Suppress("ObjectPropertyName")
private var _PersonalRadio: ImageVector? = null
