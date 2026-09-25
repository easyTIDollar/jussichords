package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Close: ImageVector
    get() {
        if (_Close != null) {
            return _Close!!
        }
        _Close = ImageVector.Builder(
            name = "Close",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color(0xFF5F6368))) {
                moveTo(760f, 256f)
                lineTo(703.6f, 200f)
                lineTo(480f, 423.6f)
                lineTo(256.4f, 200f)
                lineTo(200f, 256f)
                lineTo(423.6f, 480f)
                lineTo(200f, 703.6f)
                lineTo(256.4f, 760f)
                lineTo(480f, 536.4f)
                lineTo(703.6f, 760f)
                lineTo(760f, 703.6f)
                lineTo(536.4f, 480f)
                close()
            }
        }.build()

        return _Close!!
    }

@Suppress("ObjectPropertyName")
private var _Close: ImageVector? = null
