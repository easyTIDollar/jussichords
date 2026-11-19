package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Material Icons "filter_alt"（outlined，24dp）官方矢量数据。
val Funnel: ImageVector
    get() {
        if (_Funnel != null) {
            return _Funnel!!
        }
        _Funnel = ImageVector.Builder(
            name = "Funnel",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(7f, 6f)
                lineTo(17f, 6f)
                lineTo(11.99f, 12.3f)
                lineTo(7f, 6f)
                close()
                moveTo(4.25f, 5.61f)
                curveTo(6.27f, 8.2f, 10f, 13f, 10f, 13f)
                lineTo(10f, 19f)
                curveTo(10f, 19.55f, 10.45f, 20f, 11f, 20f)
                lineTo(13f, 20f)
                curveTo(13.55f, 20f, 14f, 19.55f, 14f, 19f)
                lineTo(14f, 13f)
                curveTo(14f, 13f, 17.72f, 8.2f, 19.74f, 5.61f)
                arcTo(0.998f, 0.998f, 0f, false, false, 18.95f, 4f)
                lineTo(5.04f, 4f)
                curveTo(4.21f, 4f, 3.74f, 4.95f, 4.25f, 5.61f)
                close()
            }
        }.build()

        return _Funnel!!
    }

@Suppress("ObjectPropertyName")
private var _Funnel: ImageVector? = null
