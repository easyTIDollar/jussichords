package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Up: ImageVector
    get() {
        if (_Up != null) {
            return _Up!!
        }
        _Up = ImageVector.Builder(
            name = "Up",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF5F6368))) {
                moveTo(2f, 15.975f)
                lineTo(3.775f, 17.75f)
                lineTo(12f, 9.525f)
                lineTo(20.225f, 17.75f)
                lineTo(22f, 15.975f)
                lineTo(12f, 5.975f)
                lineTo(2f, 15.975f)
                close()
            }
        }.build()

        return _Up!!
    }

@Suppress("ObjectPropertyName")
private var _Up: ImageVector? = null
