package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 筛选漏斗：私信会话列表右上角筛选 userType 用。 */
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
            path(fill = SolidColor(Color(0xFF5F6368))) {
                moveTo(4f, 5f)
                lineTo(20f, 5f)
                lineTo(14f, 12f)
                lineTo(14f, 18f)
                lineTo(10f, 20f)
                lineTo(10f, 12f)
                lineTo(4f, 5f)
                close()
            }
        }.build()

        return _Funnel!!
    }

@Suppress("ObjectPropertyName")
private var _Funnel: ImageVector? = null
