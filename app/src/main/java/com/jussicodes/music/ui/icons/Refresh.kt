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
            // 环形刷新箭头：外层折线弧（右上留缺口）+ 箭头三角 + 内层折线弧，纯直线近似
            path(fill = SolidColor(Color(0xFF5F6368))) {
                moveTo(558f, 190f)
                lineTo(402f, 190f)
                lineTo(268f, 268f)
                lineTo(190f, 402f)
                lineTo(190f, 558f)
                lineTo(268f, 692f)
                lineTo(402f, 770f)
                lineTo(558f, 770f)
                lineTo(692f, 692f)
                lineTo(770f, 558f)
                lineTo(770f, 402f)
                lineTo(820f, 470f)
                lineTo(683f, 425f)
                lineTo(585f, 298f)
                lineTo(535f, 277f)
                close()
            }
        }.build()

        return _Refresh!!
    }

@Suppress("ObjectPropertyName")
private var _Refresh: ImageVector? = null
