package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Check: ImageVector
    get() {
        if (_Check != null) {
            return _Check!!
        }
        _Check = ImageVector.Builder(
            name = "Check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color(0xFF5F6368))) {
                moveTo(382f, 590f)
                lineTo(206f, 414f)
                horizontalLineTo(144f)
                verticalLineTo(352f)
                horizontalLineTo(224f)
                lineTo(418f, 546f)
                lineTo(736f, 144f)
                horizontalLineTo(808f)
                verticalLineTo(224f)
                lineTo(458f, 624f)
                horizontalLineTo(382f)
                close()
            }
        }.build()

        return _Check!!
    }

@Suppress("ObjectPropertyName")
private var _Check: ImageVector? = null
