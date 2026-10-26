package com.jussicodes.music.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 电台：中心圆点 + 两侧广播信号弧（Lucide radio 官方 path 原样转写）。 */
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
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(16.247f, 7.761f)
                arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16.247f, 16.239f)

                moveTo(19.075f, 4.933f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = false, isPositiveArc = true, 19.075f, 19.067f)

                moveTo(4.925f, 19.067f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = false, isPositiveArc = true, 4.925f, 4.933f)

                moveTo(7.753f, 16.239f)
                arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7.753f, 7.761f)

                moveTo(14f, 12f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 10f, 12f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14f, 12f)
            }
        }.build()

        return _PersonalRadio!!
    }

@Suppress("ObjectPropertyName")
private var _PersonalRadio: ImageVector? = null
