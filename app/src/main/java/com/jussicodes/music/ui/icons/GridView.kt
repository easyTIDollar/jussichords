package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val GridView: ImageVector
    get() {
        if (_GridView != null) {
            return _GridView!!
        }
        _GridView = ImageVector.Builder(
            name = "GridView",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            // 四个 240 方块，中间 80 间隙，读成 2x2 网格
            path(fill = SolidColor(Color(0xFF5F6368))) {
                moveTo(200f, 200f)
                horizontalLineTo(440f)
                verticalLineTo(440f)
                horizontalLineTo(200f)
                close()
                moveTo(520f, 200f)
                horizontalLineTo(760f)
                verticalLineTo(440f)
                horizontalLineTo(520f)
                close()
                moveTo(200f, 520f)
                horizontalLineTo(440f)
                verticalLineTo(760f)
                horizontalLineTo(200f)
                close()
                moveTo(520f, 520f)
                horizontalLineTo(760f)
                verticalLineTo(760f)
                horizontalLineTo(520f)
                close()
            }
        }.build()

        return _GridView!!
    }

@Suppress("ObjectPropertyName")
private var _GridView: ImageVector? = null
