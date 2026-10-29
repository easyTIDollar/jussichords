package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Blur: ImageVector
    get() {
        if (_Blur != null) {
            return _Blur!!
        }
        _Blur = ImageVector.Builder(
            name = "Blur",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // 中心圆点 (r=2.5)
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(12f, 9.5f)
                arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 14.5f)
                arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 9.5f)
                close()
            }
            // 外圈第一环 4 点 (r=1.5)
            val ring1 = listOf(
                Pair(12f, 4.5f),
                Pair(12f, 19.5f),
                Pair(4.5f, 12f),
                Pair(19.5f, 12f)
            )
            for (p in ring1) {
                path(fill = SolidColor(Color(0xFF000000))) {
                    moveTo(p.first, p.second - 1.5f)
                    arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, p.first, p.second + 1.5f)
                    arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, p.first, p.second - 1.5f)
                    close()
                }
            }
            // 四个斜角小点 (r=1.0)
            val ring2 = listOf(
                Pair(6.5f, 6.5f),
                Pair(17.5f, 6.5f),
                Pair(6.5f, 17.5f),
                Pair(17.5f, 17.5f)
            )
            for (p in ring2) {
                path(fill = SolidColor(Color(0xFF000000))) {
                    moveTo(p.first, p.second - 1f)
                    arcTo(1f, 1f, 0f, isMoreThanHalf = true, isPositiveArc = true, p.first, p.second + 1f)
                    arcTo(1f, 1f, 0f, isMoreThanHalf = true, isPositiveArc = true, p.first, p.second - 1f)
                    close()
                }
            }
        }.build()
        return _Blur!!
    }

private var _Blur: ImageVector? = null
