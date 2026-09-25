package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Refresh: ImageVector
    get() {
        if (_Refresh != null) {
            return _Refresh!!
        }
        _Refresh = ImageVector.Builder(
            name = "Refresh",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color(0xFF5F6368))) {
                moveTo(664f, 204f)
                lineTo(408f, 204f)
                lineTo(408f, 104f)
                lineTo(804f, 480f)
                lineTo(408f, 856f)
                lineTo(408f, 756f)
                horizontalLineTo(600f)
                cubicTo(734f, 756f, 840f, 650f, 840f, 516f)
                cubicTo(840f, 382f, 734f, 276f, 600f, 276f)
                horizontalLineToRelative(-52f)
                close()
            }
        }.build()

        return _Refresh!!
    }

@Suppress("ObjectPropertyName")
private var _Refresh: ImageVector? = null
